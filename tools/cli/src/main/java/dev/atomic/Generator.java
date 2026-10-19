package dev.atomic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.nio.file.*;
import java.util.*;

/** Generates fail-closed business skeletons; existing bodies are never replaced. */
public final class Generator {
  private SourceIndex index;
  private Profile profile;
  private Contract contract;
  private final Map<Path, CompilationUnit> edited = new LinkedHashMap<>();
  private final Map<Path, String> before = new LinkedHashMap<>();

  public GenerationPlan plan(Path repo, Contract c, Profile p) throws Exception {
    return plan(SourceIndex.scan(repo.toRealPath()), c, p);
  }

  public GenerationPlan plan(SourceIndex source, Contract c, Profile p) throws Exception {
    contract = c;
    profile = p;
    c.validate();
    p.validate();
    if (c.version() == 2) {
      Conventions.validate(c, p);
      var blockers =
          MavenParents.check(source.root()).stream()
              .filter(f -> !f.severity().equals("WARN"))
              .toList();
      Contract.require(blockers.isEmpty(), "MAVEN: 修复父 POM/模型错误后再生成: " + blockers);
    }
    index = source;
    edited.clear();
    before.clear();
    Contract.require(
        index.issues().isEmpty(), "INCOMPLETE: source parse/type conflicts: " + index.issues());
    validateFramework();
    for (var field : c.models().request().fields())
      Contract.require(
          !field.diagnostic() || !CodeRules.sensitive(field.name()),
          "N24: sensitive request field must not be diagnostic: " + field.name());
    validateExternal();
    LoggingSupport.validate(index, profile.logging());
    model(c.models().request());
    model(c.models().response());
    for (var step : c.steps()) handler(step);
    if (c.assembly().mode().equals("builder")) builder();
    converter();
    facade();
    disabledFacade();
    implementation();
    delegate();
    List<GenerationPlan.Change> changes = new ArrayList<>();
    for (var entry : edited.entrySet()) {
      String old = before.get(entry.getKey());
      String text =
          old == null
              ? entry.getValue().toString()
              : LexicalPreservingPrinter.print(entry.getValue());
      text = ImportLayout.format(text);
      text = text.replace("\r\n", "\n");
      if (old != null && old.contains("\r\n")) text = text.replace("\n", "\r\n");
      if (old != null && old.startsWith("\uFEFF")) text = "\uFEFF" + text;
      if (!Objects.equals(old, text))
        changes.add(new GenerationPlan.Change(entry.getKey(), old, text));
    }
    return new GenerationPlan(index.root(), List.copyOf(changes), Map.copyOf(index.originals()));
  }

  private String type(String key) {
    return profile.type(key);
  }

  private String error(String message) {
    return "new " + type("internalException") + "(" + quote(message) + ")";
  }

  private static String quote(String s) {
    return new com.github.javaparser.ast.expr.StringLiteralExpr(s).toString();
  }

  private Path newPath(String fqn, String root) throws Exception {
    Contract.root(root);
    return GenerationPlan.safe(
        index.root(), index.root().resolve(root).resolve(profile.relativeClassPath(fqn, root)));
  }

  private void validateFramework() {
    for (String key :
        List.of("componentManagement", "handlerBase", "internalException", "externalException"))
      index.declaration(type(key));
    var base = index.declaration(type("handlerBase"));
    for (String method : List.of("preHandler", "invokeAtomicApi", "postHandler", "run"))
      Contract.require(
          base.getAllMethods().stream().anyMatch(m -> m.getName().equals(method)),
          "Unsupported Handler lifecycle: " + method);
    Contract.require(
        index.declaration(type("internalException")).getAllMethods().stream()
            .anyMatch(m -> m.getName().equals("getErrMsg")),
        "Internal exception lacks getErrMsg");
  }

  private void validateExternal() {
    if (contract.stringResponse()) return;
    var external = index.declaration(contract.externalResponse());
    for (var binding : contract.responseBindings().entrySet()) {
      String expected =
          contract.models().response().fields().stream()
              .filter(f -> f.name().equals(binding.getValue()))
              .findFirst()
              .orElseThrow()
              .type();
      Contract.require(
          external.getAllMethods().stream()
              .anyMatch(
                  m ->
                      m.getName().equals("set" + Contract.cap(binding.getKey()))
                          && m.getNoParams() == 1
                          && m.getParamType(0).describe().equals(expected)),
          "INCOMPLETE: external response setter/type missing: " + binding.getKey());
    }
  }

  private ClassOrInterfaceDeclaration target(String fqn, boolean iface) {
    var existing = index.type(fqn);
    if (existing != null) {
      Contract.require(existing.isInterface() == iface, "Target kind mismatch: " + fqn);
      Path path = index.path(existing);
      if (!edited.containsKey(path)) {
        try {
          GenerationPlan.safe(index.root(), path);
        } catch (Exception e) {
          throw new IllegalArgumentException(e);
        }
        var cu = existing.findCompilationUnit().orElseThrow();
        LexicalPreservingPrinter.setup(cu);
        edited.put(path, cu);
        before.put(path, index.originals().get(path));
      }
      return existing;
    }
    try {
      Path path = newPath(fqn, profile.rootFor(fqn));
      CompilationUnit cu = edited.get(path);
      if (cu == null) {
        Contract.require(!Files.exists(path), "Unindexed existing source: " + path);
        cu = new CompilationUnit(fqn.substring(0, fqn.lastIndexOf('.')));
        edited.put(path, cu);
        before.put(path, null);
      }
      var found = cu.getClassByName(Contract.simple(fqn));
      if (found.isPresent()) return found.get();
      return iface
          ? cu.addInterface(Contract.simple(fqn)).setPublic(true)
          : cu.addClass(Contract.simple(fqn)).setPublic(true);
    } catch (Exception e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  private void emit(String fqn, String root, String code) throws Exception {
    Path path =
        index.type(fqn) == null
            ? newPath(fqn, root)
            : GenerationPlan.safe(index.root(), index.path(index.type(fqn)));
    var generated =
        StaticJavaParser.parse("package " + fqn.substring(0, fqn.lastIndexOf('.')) + ";\n" + code);
    var generatedOwner = generated.getTypes().get(0).asClassOrInterfaceDeclaration();
    if (!Set.of(contract.models().request().className(), contract.models().response().className())
        .contains(fqn))
      for (var method : List.copyOf(generatedOwner.getMethods())) {
        if (method.getBody().isPresent())
          method.setJavadocComment(LoggingSupport.documentation(method));
        LoggingSupport.decorate(generatedOwner, method, profile);
      }
    shortenGeneratedTypes(
        generated.getTypes().get(0).asClassOrInterfaceDeclaration(), generated.getTypes().get(0));
    String old = Files.exists(path) ? Files.readString(path) : null;
    if (old != null) {
      String source = old.startsWith("\uFEFF") ? old.substring(1) : old;
      // A file copyright header is independent of the generated implementation.
      source = source.replaceFirst("(?s)\\A\\s*/\\*\\s*\\*?\\s*Copyright\\b.*?\\*/\\s*", "");
      var previous = StaticJavaParser.parse(source);
      shortenGeneratedTypes(
          previous.getTypes().get(0).asClassOrInterfaceDeclaration(), previous.getTypes().get(0));
      previous.getImports().sort(Comparator.comparing(Object::toString));
      generated.getImports().sort(Comparator.comparing(Object::toString));
      Contract.require(
          semanticText(previous).equals(semanticText(generated)),
          "CONFLICT: existing implementation differs: " + path);
      return;
    }
    Contract.require(!edited.containsKey(path), "Generated file collision: " + path);
    edited.put(path, generated);
    before.put(path, null);
  }

  private void addMethod(ClassOrInterfaceDeclaration owner, String source) {
    MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(source);
    if (index.type(owner.getFullyQualifiedName().orElseThrow()) == null
        && method.getBody().isPresent())
      method.setJavadocComment(LoggingSupport.documentation(method));
    LoggingSupport.decorate(owner, method, profile);
    var parameterTypes = method.getParameters().stream().map(p -> p.getType().asString()).toList();
    shortenGeneratedTypes(owner, method);
    var sameName = owner.getMethodsByName(method.getNameAsString());
    for (var existing : sameName) {
      if (existing.getParameters().size() != method.getParameters().size()) continue;
      boolean same = true;
      for (int i = 0; i < method.getParameters().size(); i++) {
        String actual;
        try {
          actual = existing.getParameter(i).getType().resolve().describe();
        } catch (RuntimeException e) {
          actual = existing.getParameter(i).getType().asString();
        }
        same &= actual.equals(parameterTypes.get(i));
      }
      if (same) {
        Contract.require(
            semanticText(shortenGeneratedTypes(owner, existing.clone()))
                .equals(semanticText(method)),
            "CONFLICT: existing method "
                + owner.getNameAsString()
                + "."
                + method.getNameAsString());
        return;
      }
    }
    // Format new members before preserving their tokens in the existing source.
    method =
        StaticJavaParser.parseMethodDeclaration(
            "    " + method.toString().replace("\r\n", "\n").replace("\n", "\n    "));
    LexicalPreservingPrinter.setup(method);
    owner.addMember(method);
  }

  static String semanticText(Node node) {
    Node copy = node.clone();
    copy.walk(n -> n.removeComment());
    return copy.toString().replace("\r\n", "\n");
  }

  private <N extends Node> N shortenGeneratedTypes(ClassOrInterfaceDeclaration owner, N method) {
    var cu = owner.findCompilationUnit().orElseThrow();
    Set<String> candidates = new LinkedHashSet<>();
    for (var t : method.findAll(ClassOrInterfaceType.class)) {
      String name = t.getNameWithScope();
      if (name.contains(".")
          && Character.isLowerCase(name.charAt(0))
          && Character.isUpperCase(Contract.simple(name).charAt(0))) candidates.add(name);
    }
    for (var annotation : method.findAll(com.github.javaparser.ast.expr.AnnotationExpr.class)) {
      if (annotation.getNameAsString().contains(".")) candidates.add(annotation.getNameAsString());
    }
    // Static converter/symbol references are expressions rather than type nodes.
    candidates.add(contract.target().converter());
    candidates.add("java.util.Locale");
    for (String fqn : candidates) {
      String simple = Contract.simple(fqn);
      boolean conflict =
          cu.getImports().stream()
              .anyMatch(
                  i ->
                      !i.isStatic()
                          && (i.isAsterisk()
                              || (Contract.simple(i.getNameAsString()).equals(simple)
                                  && !i.getNameAsString().equals(fqn))));
      conflict |=
          cu.findAll(TypeDeclaration.class).stream()
              .anyMatch(t -> t.getNameAsString().equals(simple));
      conflict |=
          index.types().keySet().stream()
              .anyMatch(
                  name ->
                      name.equals(
                              cu.getPackageDeclaration()
                                      .map(p -> p.getNameAsString() + ".")
                                      .orElse("")
                                  + simple)
                          && !name.equals(fqn));
      if (conflict) continue;
      boolean used = false;
      for (var t : method.findAll(ClassOrInterfaceType.class)) {
        if (t.getNameWithScope().equals(fqn)) {
          t.setScope((ClassOrInterfaceType) null);
          t.setName(simple);
          used = true;
        }
      }
      for (var annotation : method.findAll(com.github.javaparser.ast.expr.AnnotationExpr.class)) {
        if (annotation.getNameAsString().equals(fqn)) {
          annotation.setName(simple);
          used = true;
        }
      }
      for (var expression : method.findAll(FieldAccessExpr.class)) {
        if (expression.getScope().toString().equals(fqn)) {
          expression.setScope(new NameExpr(simple));
          used = true;
        }
      }
      if (used
          && !fqn.substring(0, fqn.lastIndexOf('.')).equals("java.lang")
          && !fqn.equals(
              cu.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("") + simple)) {
        cu.addImport(fqn);
      }
    }
    return method;
  }

  private String field(ClassOrInterfaceDeclaration owner, String fqn, String preferred) {
    List<VariableDeclarator> matches = new ArrayList<>();
    for (var declaration : owner.getFields())
      for (var variable : declaration.getVariables()) {
        String actual;
        try {
          actual = variable.getType().resolve().describe();
        } catch (RuntimeException e) {
          actual = variable.getType().asString();
        }
        if (actual.equals(fqn)) matches.add(variable);
      }
    Contract.require(matches.size() < 2, "Ambiguous injected type: " + fqn);
    if (matches.size() == 1) return matches.get(0).getNameAsString();
    if (owner.getFieldByName(preferred).isPresent()) {
      String simple = Contract.simple(fqn);
      preferred = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
      while (owner.getFieldByName(preferred).isPresent()) preferred += "_";
    }
    owner.addMember(
        shortenGeneratedTypes(
            owner,
            StaticJavaParser.parseBodyDeclaration(
                "@" + type("injectionAnnotation") + " private " + fqn + " " + preferred + ";")));
    return preferred;
  }

  private void model(Contract.Model m) throws Exception {
    var existing = index.type(m.className());
    if (existing != null) {
      for (var f : m.fields()) {
        Contract.require(
            existing.getFieldByName(f.name()).isPresent(),
            "INCOMPLETE: existing DTO field absent: " + f.name());
        getter(m.className(), f.name(), f.type());
        Contract.require(
            existing.resolve().getAllMethods().stream()
                .anyMatch(
                    u ->
                        u.getName().equals("set" + Contract.cap(f.name()))
                            && u.getNoParams() == 1
                            && u.getParamType(0).describe().equals(f.type())),
            "INCOMPLETE: existing DTO setter absent");
      }
      return;
    }
    StringBuilder code =
        new StringBuilder("public class " + Contract.simple(m.className()) + " {\n");
    for (var f : m.fields()) {
      code.append("private ").append(f.type()).append(' ').append(f.name()).append(";\n");
      code.append("public ")
          .append(f.type())
          .append(" get")
          .append(Contract.cap(f.name()))
          .append("(){return ")
          .append(f.name())
          .append(";}\n");
      code.append("public void set")
          .append(Contract.cap(f.name()))
          .append('(')
          .append(f.type())
          .append(" value){this.")
          .append(f.name())
          .append("=value;}\n");
    }
    emit(m.className(), m.sourceRoot(), code.append('}').toString());
  }

  private void handler(Contract.Step step) throws Exception {
    String simple = Contract.simple(step.handler()), exception = type("internalException");
    StringBuilder pre =
        new StringBuilder("if(request==null){throw " + error("request is null") + ";}");
    if (step.inputType().equals("java.lang.String"))
      pre.append("if(request.trim().isEmpty()){throw ")
          .append(error("input is blank"))
          .append(";}");
    if (step.inputType().equals(contract.models().request().className()))
      for (var f : contract.models().request().fields())
        if (f.required()) {
          pre.append("if(request.")
              .append(modelGetter(contract.models().request(), f.name()))
              .append("==null){throw ")
              .append(error(f.name() + " is empty"))
              .append(";}");
          if (f.type().equals("java.lang.String"))
            pre.append("if(request.")
                .append(modelGetter(contract.models().request(), f.name()))
                .append(".trim().isEmpty()){throw ")
                .append(error(f.name() + " is blank"))
                .append(";}");
        }
    String code =
        "@"
            + type("componentAnnotation")
            + " public class "
            + simple
            + " extends "
            + type("handlerBase")
            + "<"
            + step.inputType()
            + ","
            + step.outputType()
            + "> {"
            + "@Override protected BasicApiContext preHandler("
            + step.inputType()
            + " request) throws "
            + exception
            + " {"
            + pre
            + " Context ctx=new Context();ctx.input=request;return ctx;}"
            + "@Override protected BasicApiContext invokeAtomicApi(BasicApiContext context) throws "
            + exception
            + " {throw "
            + error("ATOMIC_API_UNIMPLEMENTED: " + simple + " underlying capability")
            + ";}"
            + "@Override protected "
            + step.outputType()
            + " postHandler(BasicApiContext context) throws "
            + exception
            + " {Context ctx=(Context)context;if(ctx.result==null){throw "
            + error("result is null")
            + ";}return ctx.result;}"
            + "private static class Context extends BasicApiContext {private "
            + step.inputType()
            + " input;private "
            + step.outputType()
            + " result;}}";
    emit(step.handler(), step.sourceRoot(), code);
  }

  private void builder() throws Exception {
    var b = contract.assembly();
    StringBuilder params = new StringBuilder(contract.models().request().className() + " request");
    for (var step : contract.steps())
      params.append(',').append(step.outputType()).append(' ').append(stepVariable(step.id()));
    emit(
        b.builder(),
        b.sourceRoot(),
        "@"
            + type("componentAnnotation")
            + " public class "
            + Contract.simple(b.builder())
            + " { public "
            + contract.models().response().className()
            + " build("
            + params
            + ") throws "
            + type("internalException")
            + " {throw "
            + error("ATOMIC_API_UNIMPLEMENTED: map Handler results to business response")
            + ";}}");
  }

  private String parameters() {
    return String.join(
        ",",
        contract.models().request().fields().stream().map(f -> f.type() + " " + f.name()).toList());
  }

  private String local(String preferred) {
    var names = contract.models().request().fields().stream().map(Contract.Field::name).toList();
    while (names.contains(preferred)) preferred += "_";
    return preferred;
  }

  private static String stepVariable(String id) {
    return id + "Result";
  }

  private String converterMethod() {
    return "to" + Contract.cap(contract.operation()) + "Request";
  }

  private void converter() {
    String fqn = contract.target().converter();
    var owner = target(fqn, true);
    if (owner.getFieldByName("INSTANCE").isEmpty())
      owner.addMember(
          StaticJavaParser.parseBodyDeclaration(fqn + " INSTANCE=new " + fqn + "(){};"));
    String req = contract.models().request().className(), variable = local("request");
    StringBuilder body = new StringBuilder(req + " " + variable + "=new " + req + "();");
    for (var f : contract.models().request().fields())
      body.append(variable)
          .append(".set")
          .append(Contract.cap(f.name()))
          .append('(')
          .append(f.name())
          .append(");");
    addMethod(
        owner,
        "default "
            + req
            + " "
            + converterMethod()
            + "("
            + parameters()
            + "){"
            + body
            + "return "
            + variable
            + ";}");
  }

  private void facade() {
    var owner = target(contract.target().facade(), true);
    addMethod(
        owner,
        "default "
            + contract.models().response().className()
            + " "
            + contract.operation()
            + "("
            + contract.models().request().className()
            + " request) throws "
            + type("internalException")
            + " {"
            + (profile.facadeDefault().equals("return-null")
                ? "return null;"
                : "throw " + error("Facade not assembled: " + contract.operation()) + ";")
            + "}");
  }

  private String getter(String type, String name, String expected) {
    var methods = index.declaration(type).getAllMethods();
    for (String candidate : List.of("get" + Contract.cap(name), "is" + Contract.cap(name), name)) {
      var found =
          methods.stream()
              .filter(
                  m ->
                      m.getName().equals(candidate)
                          && m.getNoParams() == 0
                          && m.returnType().describe().equals(expected))
              .toList();
      if (found.size() == 1) return candidate + "()";
    }
    throw new IllegalArgumentException(
        "INCOMPLETE: cannot resolve accessor " + type + "." + name + " -> " + expected);
  }

  private String binding(Contract.Step step) {
    String[] p = step.input().split("\\.");
    if (p[0].equals("request"))
      return p.length == 1
          ? "request"
          : "(request==null?null:request." + modelGetter(contract.models().request(), p[1]) + ")";
    if (p.length == 2) return stepVariable(p[1]);
    var source =
        contract.steps().stream().filter(s -> s.id().equals(p[1])).findFirst().orElseThrow();
    return "("
        + stepVariable(p[1])
        + "==null?null:"
        + stepVariable(p[1])
        + "."
        + getter(source.outputType(), p[2], step.inputType())
        + ")";
  }

  private String modelGetter(Contract.Model model, String fieldName) {
    var field =
        model.fields().stream().filter(f -> f.name().equals(fieldName)).findFirst().orElseThrow();
    return index.type(model.className()) == null
        ? "get" + Contract.cap(fieldName) + "()"
        : getter(model.className(), fieldName, field.type());
  }

  private boolean isFacadeType(com.github.javaparser.ast.type.Type value) {
    if (!value.isClassOrInterfaceType()) return false;
    String name = value.asClassOrInterfaceType().getNameWithScope();
    if (name.equals(contract.target().facade())) return true;
    try {
      return value
          .resolve()
          .asReferenceType()
          .getQualifiedName()
          .equals(contract.target().facade());
    } catch (RuntimeException unresolved) {
      var cu = value.findCompilationUnit().orElseThrow();
      String fqn = contract.target().facade();
      return name.equals(Contract.simple(fqn))
          && (cu.getImports().stream()
                  .anyMatch(
                      i -> !i.isStatic() && !i.isAsterisk() && i.getNameAsString().equals(fqn))
              || cu.getPackageDeclaration()
                  .map(p -> (p.getNameAsString() + "." + name).equals(fqn))
                  .orElse(false));
    }
  }

  private void disabledFacade() {
    var registration = profile.disabledFacade();
    if (registration == null) return;
    Contract.require(
        index.type(registration.configuration()) != null,
        "INCOMPLETE: disabled Facade configuration missing: " + registration.configuration());
    var owner = target(registration.configuration(), false);
    Contract.require(
        owner.getFieldByName(registration.mapField()).isPresent(),
        "INCOMPLETE: disabled Facade map field missing");
    String scope = registration.mapField();
    var matches =
        owner.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
            .filter(
                m ->
                    m.getNameAsString().equals("put")
                        && m.getArguments().size() == 2
                        && m.getScope()
                            .map(
                                x ->
                                    x.toString().equals(scope)
                                        || x.toString().equals("this." + scope))
                            .orElse(false)
                        && m.getArgument(0).isClassExpr()
                        && isFacadeType(m.getArgument(0).asClassExpr().getType()))
            .toList();
    Contract.require(matches.size() < 2, "CONFLICT: duplicate disabled Facade registration");
    if (!matches.isEmpty()) {
      var call = matches.get(0);
      Contract.require(
          call.getArgument(1).isObjectCreationExpr(),
          "CONFLICT: disabled Facade implementation is customized");
      var creation = call.getArgument(1).asObjectCreationExpr();
      Contract.require(
          isFacadeType(creation.getType())
              && creation.getArguments().isEmpty()
              && creation.getAnonymousClassBody().map(List::isEmpty).orElse(false),
          "CONFLICT: disabled Facade implementation is customized");
      shortenGeneratedTypes(owner, call);
      return;
    }
    var constructors =
        owner.getConstructors().stream().filter(c -> c.getParameters().isEmpty()).toList();
    Contract.require(
        constructors.size() == 1,
        "INCOMPLETE: explicit no-argument disabled Facade constructor required");
    var statement =
        StaticJavaParser.parseStatement(
            scope
                + ".put("
                + contract.target().facade()
                + ".class, new "
                + contract.target().facade()
                + "() {});");
    shortenGeneratedTypes(owner, statement);
    constructors.get(0).getBody().addStatement(statement);
  }

  private void implementation() {
    var owner = target(contract.target().implementation(), false);
    boolean implementsFacade =
        owner.getImplementedTypes().stream()
            .anyMatch(
                t -> {
                  if (t.asString().equals(contract.target().facade())) return true;
                  var resolved = t.resolve().asReferenceType();
                  return resolved.getQualifiedName().equals(contract.target().facade())
                      || resolved.getAllAncestors().stream()
                          .anyMatch(a -> a.getQualifiedName().equals(contract.target().facade()));
                });
    if (!implementsFacade)
      owner
          .getImplementedTypes()
          .add(
              shortenGeneratedTypes(
                  owner, StaticJavaParser.parseClassOrInterfaceType(contract.target().facade())));
    if (owner.getAnnotations().stream()
        .noneMatch(
            a -> a.getName().getIdentifier().equals(Contract.simple(type("facadeAnnotation")))))
      owner.addAnnotation(
          shortenGeneratedTypes(
              owner, StaticJavaParser.parseAnnotation("@" + type("facadeAnnotation"))));
    StringBuilder body = new StringBuilder();
    int n = 0;
    for (var step : contract.steps()) {
      String variable = field(owner, step.handler(), step.id() + "ApiHandler");
      body.append("// ").append(++n).append(". 执行 ").append(step.id()).append(" 业务处理\n");
      if (contract.assembly().mode().equals("direct")) {
        body.append("return ")
            .append(variable)
            .append(".run(")
            .append(binding(step))
            .append(");\n");
      } else {
        body.append(step.outputType())
            .append(' ')
            .append(stepVariable(step.id()))
            .append('=')
            .append(variable)
            .append(".run(")
            .append(binding(step))
            .append(");\n\n");
      }
    }
    if (!contract.assembly().mode().equals("direct")) {
      String variable = field(owner, contract.assembly().builder(), "resultBuilder");
      body.append("// 汇总以上 ")
          .append(n)
          .append(" 个 Handler 的业务结果。\nreturn ")
          .append(variable)
          .append(".build(request");
      for (var step : contract.steps()) body.append(',').append(stepVariable(step.id()));
      body.append(");");
    }
    addMethod(
        owner,
        "@Override public "
            + contract.models().response().className()
            + " "
            + contract.operation()
            + "("
            + contract.models().request().className()
            + " request) throws "
            + type("internalException")
            + " {"
            + body
            + "}");
  }

  private void delegate() {
    var owner = target(contract.target().delegate(), false);
    String manager = field(owner, type("componentManagement"), "opsComponentManagement");
    String request = contract.models().request().className(),
        response = contract.models().response().className();
    String args =
        String.join(
            ",", contract.models().request().fields().stream().map(Contract.Field::name).toList());
    String builder = "builder" + Contract.simple(contract.externalResponse());
    String responseConverter = contract.target().responseConverter();
    String responseMethod = "to" + Contract.cap(contract.operation()) + "Response";
    String responseCall =
        responseConverter == null ? builder : responseConverter + ".INSTANCE." + responseMethod;
    String reqVar = local("request"),
        facadeVar = local("facade"),
        resultVar = local("result"),
        errorVar = local("e"),
        wrappedVar = local("wrappedException");
    StringBuilder diagnosticPattern = new StringBuilder(contract.operation() + " failed"),
        diagnosticArgs = new StringBuilder();
    for (var f : contract.models().request().fields())
      if (f.diagnostic()) {
        diagnosticPattern.append(", ").append(f.name()).append(": %s");
        diagnosticArgs.append(f.name()).append(',');
      }
    diagnosticPattern.append(", detailErrMsg: %s");
    diagnosticArgs.append(errorVar).append(".getErrMsg()");
    String formattedError =
        "String.format(java.util.Locale.ROOT,"
            + quote(diagnosticPattern.toString())
            + ","
            + diagnosticArgs
            + ")";
    String failure =
        contract.returnsFailureMessage()
            ? "return " + formattedError + ";"
            : type("externalException")
                + " "
                + wrappedVar
                + "=new "
                + type("externalException")
                + "("
                + formattedError
                + ");"
                + wrappedVar
                + ".initCause("
                + errorVar
                + ");throw "
                + wrappedVar
                + ";";
    String body =
        "try {\n// 1. 转换业务请求\n"
            + request
            + " "
            + reqVar
            + "="
            + contract.target().converter()
            + ".INSTANCE."
            + converterMethod()
            + "("
            + args
            + ");\n"
            + "// 2. 调用业务 Facade\n"
            + contract.target().facade()
            + " "
            + facadeVar
            + "=this."
            + manager
            + ".getFacadeImpl("
            + contract.target().facade()
            + ".class);\n"
            + response
            + " "
            + resultVar
            + "="
            + facadeVar
            + "."
            + contract.operation()
            + "("
            + reqVar
            + ");return "
            + responseCall
            + "("
            + resultVar
            + ");}catch("
            + type("internalException")
            + " "
            + errorVar
            + "){"
            + failure
            + "}";
    String declarationParameters = parameters();
    String annotation = "";
    if (!owner.getImplementedTypes().isEmpty() || !owner.getExtendedTypes().isEmpty()) {
      var signatures =
          owner.resolve().getAllMethods().stream()
              .filter(m -> m.getName().equals(contract.operation()))
              .toList();
      Contract.require(
          signatures.size() == 1, "INCOMPLETE: external interface signature missing or overloaded");
      var signature = signatures.get(0);
      Contract.require(
          signature.returnType().describe().equals(contract.externalResponse()),
          "External response signature differs");
      List<String> actualParameters = new ArrayList<>();
      Map<String, String> actualTypes = new HashMap<>();
      for (int i = 0; i < signature.getNoParams(); i++) {
        String name = signature.getDeclaration().getParam(i).getName(),
            javaType = signature.getParamType(i).describe();
        Contract.require(
            !Set.of(reqVar, facadeVar, resultVar, errorVar, wrappedVar).contains(name),
            "INCOMPLETE: extra external parameter collides with a generated local: " + name);
        actualParameters.add(javaType + " " + name);
        actualTypes.put(name, javaType);
      }
      for (var f : contract.models().request().fields())
        Contract.require(
            f.type().equals(actualTypes.get(f.name())),
            "INCOMPLETE: external parameter binding missing/type mismatch: " + f.name());
      declarationParameters = String.join(",", actualParameters);
      annotation = "@Override ";
    }
    addMethod(
        owner,
        annotation
            + "public "
            + contract.externalResponse()
            + " "
            + contract.operation()
            + "("
            + declarationParameters
            + ")"
            + (contract.returnsFailureMessage() ? "" : " throws " + type("externalException"))
            + " {"
            + body
            + "}");
    StringBuilder mapping =
        new StringBuilder("if(result==null){throw " + error("result is empty") + ";}");
    if (contract.stringResponse()) {
      mapping
          .append("String response=result.")
          .append(
              modelGetter(contract.models().response(), contract.responseBindings().get("message")))
          .append(";if(response==null){throw ")
          .append(error("response message is missing"))
          .append(";}if(response.trim().isEmpty()){throw ")
          .append(error("response message is missing"))
          .append(";}");
    } else {
      mapping
          .append(contract.externalResponse())
          .append(" response=new ")
          .append(contract.externalResponse())
          .append("();");
      contract
          .responseBindings()
          .forEach(
              (dest, src) ->
                  mapping
                      .append("response.set")
                      .append(Contract.cap(dest))
                      .append("(result.")
                      .append(modelGetter(contract.models().response(), src))
                      .append(");"));
    }
    var responseOwner = responseConverter == null ? owner : target(responseConverter, true);
    if (responseConverter != null) {
      if (responseOwner.getFieldByName("INSTANCE").isEmpty())
        responseOwner.addMember(
            StaticJavaParser.parseBodyDeclaration(
                responseConverter + " INSTANCE = new " + responseConverter + "() {};"));
    }
    addMethod(
        responseOwner,
        (responseConverter == null ? "private " : "default ")
            + contract.externalResponse()
            + " "
            + (responseConverter == null ? builder : responseMethod)
            + "("
            + response
            + " result) throws "
            + type("internalException")
            + " {"
            + mapping
            + "return response;}");
  }
}
