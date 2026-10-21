package dev.atomic;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.nio.file.*;
import java.util.*;

/** Project conventions with bounded AST checks; semantic review is never labeled PASS. */
final class CodeRules {
  final SourceIndex index;
  final Contract c;
  final Profile p;
  final List<Checker.Finding> findings = new ArrayList<>();
  final Map<String, List<String>> evidence = new LinkedHashMap<>();
  final Set<String> scope;
  final List<ClassOrInterfaceDeclaration> owners;

  CodeRules(SourceIndex index, Contract c, Profile p) {
    this.index = index;
    this.c = c;
    this.p = p;
    scope = new HashSet<>(Conventions.scope(c));
    String businessPackage = Contract.packageName(c.target().implementation());
    // Include helper classes in this business, not unrelated historical helpers.
    index.types().keySet().stream()
        .filter(n -> n.startsWith(businessPackage + ".") && n.endsWith("Helper"))
        .forEach(scope::add);
    boolean added;
    do {
      added = false;
      for (String name : List.copyOf(scope)) {
        var owner = index.type(name);
        if (owner == null) continue;
        for (var type : owner.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
          try {
            String resolved = type.resolve().describe();
            if (resolved.endsWith("Helper") && index.type(resolved) != null)
              added |= scope.add(resolved);
          } catch (RuntimeException ignored) {
            /* Source resolution findings are handled by structural checks. */
          }
      }
    } while (added);
    owners = scope.stream().map(index::type).filter(Objects::nonNull).toList();
  }

  void finding(String severity, String id, Node node, String message) {
    findings.add(
        new Checker.Finding(
            severity,
            id,
            index.path(node).toString(),
            node.getBegin().map(x -> x.line).orElse(0),
            message));
  }

  void error(String id, Node n, String m) {
    finding("ERROR", id, n, m);
  }

  boolean model(ClassOrInterfaceDeclaration o) {
    return Set.of(c.models().request().className(), c.models().response().className())
        .contains(o.getFullyQualifiedName().orElse(""));
  }

  List<MethodDeclaration> methods(ClassOrInterfaceDeclaration o) {
    if (model(o)) return List.of();
    var list = o.getMethods().stream().filter(m -> m.getBody().isPresent()).toList();
    if (Set.of(c.target().facade(), c.target().implementation(), c.target().delegate())
        .contains(o.getFullyQualifiedName().orElse("")))
      return list.stream().filter(m -> m.getNameAsString().equals(c.operation())).toList();
    return list;
  }

  List<Checker.Finding> check() {
    dependencies();
    registration();
    helpers();
    components();
    logging();
    comments();
    messages();
    nodes();
    json();
    mapstruct();
    atomic();
    compatibility();
    maven();
    tests();
    paths();
    privacy();
    return List.copyOf(findings);
  }

  void note(String id, String text) {
    evidence.computeIfAbsent(id, k -> new ArrayList<>()).add(text);
  }

  String location(Node n) {
    return index.path(n) + ":" + n.getBegin().map(x -> x.line).orElse(0);
  }

  void dependencies() {
    for (var o : owners)
      if (model(o))
        note("N06", "DTO: " + o.getFullyQualifiedName().orElse("") + " at " + location(o));
  }

  void registration() {
    var owner = index.type(c.target().implementation());
    if (owner != null && !annotation(owner, p.type("facadeAnnotation")))
      error("N10", owner, "Facade implementation lacks the configured registration annotation");
    if (p.disabledFacade() != null) {
      var config = index.type(p.disabledFacade().configuration());
      if (config == null)
        findings.add(
            new Checker.Finding(
                "INCOMPLETE",
                "N10",
                index.root().toString(),
                0,
                "Configured disabled Facade registration source unavailable"));
      else if (config.findAll(ClassExpr.class).stream()
          .noneMatch(
              x -> {
                try {
                  return x.getType().resolve().describe().equals(c.target().facade());
                } catch (RuntimeException e) {
                  return false;
                }
              }))
        error("N10", config, "Configured disabled registration does not reference the Facade");
    }
    var facade = index.type(c.target().facade());
    if (facade != null)
      for (var m : facade.getMethodsByName(c.operation())) {
        if (!m.isDefault() || m.getBody().isEmpty())
          error("N10", m, "Facade must preserve the configured default method strategy");
        else if (p.facadeDefault().equals("throw") && m.findAll(ThrowStmt.class).isEmpty())
          error("N10", m, "Configured throw default must throw");
        else if (p.facadeDefault().equals("return-null")
            && m.findAll(ReturnStmt.class).stream()
                .noneMatch(x -> x.getExpression().map(Expression::isNullLiteralExpr).orElse(false)))
          error("N10", m, "Configured return-null default missing");
      }
  }

  boolean annotation(ClassOrInterfaceDeclaration owner, String fqn) {
    return owner.getAnnotations().stream()
        .anyMatch(
            a -> {
              try {
                return a.resolve().getQualifiedName().equals(fqn);
              } catch (RuntimeException e) {
                finding("INCOMPLETE", "N12", a, "Cannot resolve configured annotation");
                return false;
              }
            });
  }

  void helpers() {
    for (var o : owners)
      if (o.getNameAsString().endsWith("Helper") && !o.isAbstract() && !o.isInterface()) {
        try {
          long count =
              o.resolve().getAllMethods().stream()
                  .filter(m -> m.getDeclaration().accessSpecifier().name().equals("PUBLIC"))
                  .filter(
                      m ->
                          !m.getDeclaration()
                              .declaringType()
                              .getQualifiedName()
                              .equals("java.lang.Object"))
                  .map(m -> m.getSignature())
                  .distinct()
                  .count();
          if (count != 1)
            error(
                "N11",
                o,
                "Concrete Helper must expose exactly one public business method including inherited"
                    + " methods; actual="
                    + count);
        } catch (RuntimeException e) {
          finding("INCOMPLETE", "N11", o, "Cannot resolve inherited Helper entrypoints");
        }
      }
  }

  void components() {
    for (var o : owners)
      if (o.getNameAsString().endsWith("Helper") && !o.isAbstract() && !o.isInterface()
          || c.steps().stream()
              .anyMatch(step -> step.handler().equals(o.getFullyQualifiedName().orElse("")))) {
        if (o.isAbstract()) error("N12", o, "Contract Handler must be concrete");
        if (!annotation(o, p.type("componentAnnotation")))
          error("N12", o, "Concrete Handler/Helper needs the configured component annotation");
      }
  }

  void logging() {
    if (p.logging() == null) {
      note("N13", "NOT_VERIFIED: profile.logging missing; no logger API can be inferred safely");
      return;
    }
    note("N13", "Configured logger: " + p.logging().type());
    for (var o : owners)
      for (var m : methods(o)) {
        var body = m.getBody().orElseThrow();
        for (var call : m.findAll(MethodCallExpr.class))
          if (call.getScope().map(Object::toString).orElse("").equals("LOGGER")
              && Set.of("entry", "ret").contains(call.getNameAsString()))
            try {
              if (!call.getScope()
                  .orElseThrow()
                  .calculateResolvedType()
                  .describe()
                  .equals(p.logging().type()))
                error("N13", call, "LOGGER call binds to a different type");
            } catch (RuntimeException e) {
              finding("INCOMPLETE", "N13", call, "Cannot resolve logger call");
            }
        if (body.getStatements().isEmpty()
            || !LoggingSupport.logStatement(body.getStatement(0), "entry"))
          error("N13", m, "Method must start with LOGGER.entry()");
        for (var exit : m.findAll(Statement.class))
          if ((exit.isReturnStmt() || exit.isThrowStmt()) && LoggingSupport.owned(exit, m)) {
            var parent = exit.getParentNode().orElse(null);
            boolean ok =
                parent instanceof BlockStmt block
                    && block.getStatements().indexOf(exit) > 0
                    && LoggingSupport.logStatement(
                        block.getStatement(block.getStatements().indexOf(exit) - 1), "ret");
            if (!ok) error("N13", exit, "Explicit return/throw must be preceded by LOGGER.ret()");
          }
        if (m.getType().isVoidType()
            && (body.getStatements().isEmpty()
                || !LoggingSupport.logStatement(
                    body.getStatement(body.getStatements().size() - 1), "ret")))
          error("N13", m, "Void fall-through exit needs LOGGER.ret()");
        for (var caught : m.findAll(CatchClause.class)) {
          var statements = caught.getBody().getStatements();
          boolean onlyLogging =
              statements.stream()
                  .allMatch(
                      s ->
                          LoggingSupport.logStatement(s, "ret")
                              || s.isThrowStmt()
                                  && s.asThrowStmt()
                                      .getExpression()
                                      .toString()
                                      .equals(caught.getParameter().getNameAsString()));
          if (onlyLogging
              && statements.stream().anyMatch(s -> LoggingSupport.logStatement(s, "ret")))
            error(
                "N13",
                caught,
                "Do not add a catch merely to log ret and rethrow the same exception");
        }
        // No synthetic catch/finally is introduced; legitimacy of existing catches is a review
        // boundary.
      }
  }

  static boolean chinese(String text) {
    return text.codePoints().anyMatch(x -> x >= 0x4e00 && x <= 0x9fff);
  }

  void comments() {
    for (var o : owners)
      for (var m : methods(o))
        if (!m.getComment().map(x -> chinese(x.getContent())).orElse(false)
            && m.getAllContainedComments().stream().noneMatch(x -> chinese(x.getContent())))
          finding(
              "ERROR",
              "N14",
              m,
              "Method needs a concise Chinese business comment; meaning still requires review");
  }

  void messages() {
    for (var o : owners)
      for (var m : methods(o)) {
        for (var t : m.findAll(ThrowStmt.class))
          if (LoggingSupport.owned(t, m))
            for (var literal : t.findAll(StringLiteralExpr.class))
              if (chinese(literal.asString()))
                error("N15", literal, "Exception literal must use English");
        for (var call : m.findAll(MethodCallExpr.class))
          if (call.getNameAsString().equals("format"))
            try {
              if (call.resolve().declaringType().getQualifiedName().equals("java.lang.String")
                  && (call.getArguments().isEmpty()
                      || !call.resolve().getParam(0).getType().describe().equals("java.util.Locale")
                      || !call.getArgument(0).toString().matches("(?:java.util.)?Locale.ROOT")))
                error("N15", call, "String.format requires Locale.ROOT");
            } catch (RuntimeException e) {
              finding("INCOMPLETE", "N15", call, "Cannot resolve format call");
            }
      }
  }

  void nodes() {
    var rsp = index.type(c.models().response().className());
    if (rsp != null)
      for (var member : rsp.getMembers())
        if (member instanceof ClassOrInterfaceDeclaration node
            && (!node.isPublic() || !node.isStatic()))
          error("N16", node, "Response-owned node must be public static");
  }

  void json() {
    note(
        "N17",
        "NOT_VERIFIED: actual ObjectMapper configuration, historical wire fields and all consumers"
            + " required");
    for (var o : owners)
      for (var a : o.findAll(AnnotationExpr.class))
        if (Contract.simple(a.getNameAsString()).startsWith("Json"))
          note("N17", location(a) + " " + a);
  }

  void mapstruct() {
    for (var o : owners)
      for (var a : o.getAnnotations())
        if (a.getNameAsString().endsWith("Mapper"))
          try {
            if (!a.resolve().getQualifiedName().equals("org.mapstruct.Mapper")) continue;
            if (!a.isNormalAnnotationExpr()
                || a.asNormalAnnotationExpr().getPairs().stream()
                    .noneMatch(
                        x ->
                            x.getNameAsString().equals("unmappedTargetPolicy")
                                && x.getValue().toString().endsWith("ReportingPolicy.ERROR")))
              error(
                  "N18", a, "MapStruct Mapper requires unmappedTargetPolicy=ReportingPolicy.ERROR");
            if (o.getFieldByName("INSTANCE").isEmpty())
              error("N18", o, "MapStruct mapping must follow project INSTANCE convention");
          } catch (RuntimeException e) {
            finding("INCOMPLETE", "N18", a, "Cannot resolve MapStruct annotation");
          }
  }

  void atomic() {
    note(
        "N19",
        "NOT_VERIFIED: capability introduction version and runtime registration evidence required;"
            + " atomic families are outside current generation contract");
    Set<String> candidates = new LinkedHashSet<>();
    for (var owner : owners)
      for (var type : owner.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
        try {
          var resolved = type.resolve();
          if (!resolved.isReferenceType()) continue;
          String name = resolved.asReferenceType().getQualifiedName();
          if ((name.contains("AtomicApi") || name.endsWith("Latest"))
              && candidates.size() < 30
              && candidates.add(name)) note("N19", name + " referenced at " + location(type));
        } catch (RuntimeException e) {
          if (type.getNameAsString().contains("AtomicApi")
              || type.getNameAsString().endsWith("Latest"))
            note("N19", "Unresolved capability reference: " + location(type));
        }
  }

  void compatibility() {
    note(
        "N20",
        "NOT_VERIFIED: compare baseline signatures, external paths, field names/defaults, order and"
            + " error assertions");
    for (String name :
        List.of(c.target().facade(), c.target().delegate(), c.models().response().className()))
      if (index.type(name) != null) note("N20", name + " at " + location(index.type(name)));
  }

  void maven() {
    note(
        "N21",
        "NOT_VERIFIED: execute actual Maven command and inspect Reactor/classpath/classifier; local"
            + " parent check alone is insufficient");
    try (var files = Files.walk(index.root())) {
      files
          .filter(
              x -> x.getFileName().toString().equals("pom.xml") && !x.toString().contains("target"))
          .limit(30)
          .forEach(x -> note("N21", "POM: " + x));
    } catch (Exception e) {
      note("N21", "POM enumeration failed: " + e.getMessage());
    }
  }

  void tests() {
    note(
        "N22",
        "NOT_VERIFIED: tool does not execute business tests; report exact command, exit code,"
            + " statistics and covered boundaries");
    try (var files = Files.walk(index.root())) {
      files
          .filter(
              x ->
                  x.toString().replace('\\', '/').contains("src/test/")
                      && x.toString().endsWith(".java"))
          .limit(30)
          .forEach(x -> note("N22", "Candidate test (not proof of pass): " + x));
    } catch (Exception e) {
      note("N22", "Test enumeration failed: " + e.getMessage());
    }
  }

  void paths() {
    for (var o : owners)
      try {
        GenerationPlan.safe(index.root(), index.path(o));
      } catch (Exception e) {
        error("N23", o, "Source path is outside the safe project boundary: " + e.getMessage());
      }
  }

  void privacy() {
    note(
        "N24",
        "NOT_VERIFIED: confirm actual copyright ownership/header and hidden sensitive values"
            + " against project conventions");
    for (var o : owners)
      note(
          "N24",
          "Header candidate: "
              + location(o)
              + "; copyright="
              + o.findCompilationUnit().orElseThrow().getAllComments().stream()
                  .anyMatch(x -> x.getContent().contains("Copyright")));

    for (var f : c.models().request().fields())
      if (f.diagnostic() && sensitive(f.name()))
        findings.add(
            new Checker.Finding(
                "ERROR",
                "N24",
                index.root().toString(),
                0,
                "Sensitive request field must not be diagnostic: " + f.name()));
    for (var o : owners)
      for (var m : methods(o))
        for (var call : m.findAll(MethodCallExpr.class))
          if (Set.of("entry", "ret", "info", "debug", "warn", "error", "trace")
              .contains(call.getNameAsString()))
            for (var n : call.getArguments())
              if (n.findAll(NameExpr.class).stream().anyMatch(x -> sensitive(x.getNameAsString()))
                  || n.findAll(MethodCallExpr.class).stream()
                      .anyMatch(x -> sensitive(x.getNameAsString())))
                error(
                    "N24",
                    call,
                    "Logging a potentially sensitive identifier requires removal or redaction");
  }

  static boolean sensitive(String name) {
    return name.toLowerCase(Locale.ROOT)
        .matches(".*(password|passwd|secret|accesstoken|refreshtoken|authorization|credential).*");
  }
}
