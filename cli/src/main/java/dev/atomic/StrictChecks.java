package dev.atomic;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.util.*;

/** Deliberately narrow v2 rules applied only to the declared operation's architecture. */
final class StrictChecks {
  private final SourceIndex index;
  private final Contract c;
  private final Profile profile;
  private final List<Checker.Finding> findings = new ArrayList<>();

  StrictChecks(SourceIndex index, Contract c, Profile profile) {
    this.index = index;
    this.c = c;
    this.profile = profile;
  }

  List<Checker.Finding> check() {
    try {
      Conventions.validate(c, profile);
    } catch (RuntimeException e) {
      findings.add(
          new Checker.Finding("ERROR", "STRICT-001", index.root().toString(), 0, e.getMessage()));
      return findings;
    }
    Map<String, String> targets = Conventions.targets(c);
    targets.forEach(
        (role, name) -> {
          var owner = index.type(name);
          if (owner == null) {
            findings.add(
                new Checker.Finding(
                    "ERROR",
                    "STRICT-001",
                    index.root().toString(),
                    0,
                    "Missing " + role + ": " + name));
            return;
          }
          String sourceRoot = profile.roles().get(role).sourceRoot();
          String expected = sourceRoot + "/" + profile.relativeClassPath(name, sourceRoot);
          String actual = index.root().relativize(index.path(owner)).toString().replace('\\', '/');
          if (!expected.equals(actual))
            error("STRICT-001", owner, "Expected " + expected + "; actual " + actual);
        });
    var delegate = index.type(c.target().delegate());
    if (delegate != null)
      for (var method : delegate.getMethodsByName(c.operation())) delegate(method);
    for (String name : List.of(c.target().converter(), c.target().responseConverter())) {
      var owner = index.type(name);
      if (owner != null) converter(owner, name.equals(c.target().converter()));
    }
    var impl = index.type(c.target().implementation());
    if (impl != null) for (var method : impl.getMethodsByName(c.operation())) facade(method);
    for (var step : c.steps()) {
      var owner = index.type(step.handler());
      if (owner == null) continue;
      String expected =
          step.sourceRoot() + "/" + profile.relativeClassPath(step.handler(), step.sourceRoot());
      String actual = index.root().relativize(index.path(owner)).toString().replace('\\', '/');
      if (!expected.equals(actual))
        error("STRICT-001", owner, "Handler expected " + expected + "; actual " + actual);
      lifecycle(owner);
    }
    for (var model : List.of(c.models().request(), c.models().response())) {
      var owner = index.type(model.className());
      if (owner == null) continue;
      Set<String> forbidden = new HashSet<>();
      for (String role :
          List.of("implementation", "handler", "delegate", "requestConverter", "responseConverter"))
        forbidden.add(profile.roles().get(role).packagePrefix());
      for (var imported : owner.findCompilationUnit().orElseThrow().getImports())
        if (forbidden.stream().anyMatch(p -> imported.getNameAsString().startsWith(p + ".")))
          error(
              "STRICT-006",
              imported,
              "Public DTO imports implementation layer: " + imported.getNameAsString());
      for (var type : owner.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
        try {
          String name = type.resolve().describe();
          if (forbidden.stream().anyMatch(p -> name.startsWith(p + ".")))
            error("STRICT-006", type, "Public DTO depends on implementation layer: " + name);
        } catch (RuntimeException e) {
          incomplete(type, "DTO dependency cannot be resolved");
        }
    }
    return List.copyOf(findings);
  }

  private void error(String rule, Node node, String message) {
    add("ERROR", rule, node, message);
  }

  private void incomplete(Node node, String message) {
    add("INCOMPLETE", "RESOLUTION", node, message);
  }

  private void add(String severity, String rule, Node node, String message) {
    findings.add(
        new Checker.Finding(
            severity,
            rule,
            index.path(node).toString(),
            node.getBegin().map(p -> p.line).orElse(0),
            message));
  }

  private boolean declaredBy(MethodCallExpr call, String type) {
    try {
      return call.resolve().declaringType().getQualifiedName().equals(type);
    } catch (RuntimeException e) {
      incomplete(call, "Cannot resolve conversion receiver: " + call);
      return false;
    }
  }

  private boolean instanceCall(MethodCallExpr call, String owner) {
    return call.getScope()
            .filter(Expression::isFieldAccessExpr)
            .map(s -> s.asFieldAccessExpr().getNameAsString().equals("INSTANCE"))
            .orElse(false)
        && declaredBy(call, owner);
  }

  private void delegate(MethodDeclaration method) {
    boolean request = false, response = false;
    for (var call : method.findAll(MethodCallExpr.class)) {
      if (call.getScope().isPresent() && call.getScope().get().isFieldAccessExpr()) {
        request |= instanceCall(call, c.target().converter());
        response |= instanceCall(call, c.target().responseConverter());
      }
      if (call.getNameAsString().startsWith("set")) {
        try {
          if (call.resolve().declaringType().getQualifiedName().equals(c.externalResponse()))
            error("STRICT-002", call, "Delegate must not set REST response fields");
        } catch (RuntimeException e) {
          incomplete(call, "Cannot resolve Delegate setter");
        }
      }
    }
    var returns =
        method.findAll(ReturnStmt.class).stream()
            .filter(r -> r.getExpression().filter(Expression::isMethodCallExpr).isPresent())
            .toList();
    boolean returnsConverter =
        returns.stream()
            .anyMatch(
                r ->
                    instanceCall(
                        r.getExpression().orElseThrow().asMethodCallExpr(),
                        c.target().responseConverter()));
    if (!request || !response || !returnsConverter)
      error(
          "STRICT-002",
          method,
          "Use both declared Converter.INSTANCE types and return the response conversion result");
    for (var created : method.findAll(ObjectCreationExpr.class))
      try {
        if (created.getType().resolve().describe().equals(c.externalResponse()))
          error("STRICT-002", created, "Delegate constructs REST response inline");
      } catch (RuntimeException e) {
        incomplete(created, "Cannot resolve Delegate constructor");
      }
    for (var caught : method.findAll(CatchClause.class)) {
      try {
        if (!caught
            .getParameter()
            .getType()
            .resolve()
            .describe()
            .equals(profile.type("internalException"))) continue;
        String original = caught.getParameter().getNameAsString();
        for (var statement : caught.getBody().getStatements())
          if (statement.isThrowStmt()) {
            Expression thrown = statement.asThrowStmt().getExpression();
            boolean cause =
                thrown.isObjectCreationExpr()
                    && thrown.asObjectCreationExpr().getArguments().stream()
                        .anyMatch(
                            a ->
                                a.isNameExpr()
                                    && a.asNameExpr().getNameAsString().equals(original));
            if (thrown.isNameExpr()) {
              String local = thrown.asNameExpr().getNameAsString();
              cause =
                  caught.getBody().getStatements().stream()
                      .filter(Statement::isExpressionStmt)
                      .map(st -> st.asExpressionStmt().getExpression())
                      .filter(Expression::isMethodCallExpr)
                      .map(Expression::asMethodCallExpr)
                      .anyMatch(
                          call ->
                              call.getNameAsString().equals("initCause")
                                  && call.getScope()
                                      .map(Object::toString)
                                      .filter(local::equals)
                                      .isPresent()
                                  && call.getArguments().size() == 1
                                  && call.getArgument(0).toString().equals(original));
              cause |=
                  caught.getBody().findAll(VariableDeclarator.class).stream()
                      .filter(v -> v.getNameAsString().equals(local))
                      .flatMap(v -> v.getInitializer().stream())
                      .filter(Expression::isObjectCreationExpr)
                      .anyMatch(
                          e ->
                              e.asObjectCreationExpr().getArguments().stream()
                                  .anyMatch(a -> a.toString().equals(original)));
            }
            if (!cause) error("STRICT-008", statement, "External error discards the caught cause");
          }
      } catch (RuntimeException e) {
        incomplete(caught, "Cannot resolve error adaptation");
      }
    }
    // Verify that the actual Facade argument comes from the request converter, not a decoy call.
    for (var call : method.findAll(MethodCallExpr.class))
      if (call.getNameAsString().equals(c.operation()) && declaredBy(call, c.target().facade())) {
        boolean bound = false;
        if (call.getArguments().size() == 1) {
          Expression arg = call.getArgument(0);
          if (arg.isMethodCallExpr())
            bound = instanceCall(arg.asMethodCallExpr(), c.target().converter());
          if (arg.isNameExpr())
            bound =
                method.findAll(VariableDeclarator.class).stream()
                    .filter(v -> v.getNameAsString().equals(arg.asNameExpr().getNameAsString()))
                    .flatMap(v -> v.getInitializer().stream())
                    .filter(Expression::isMethodCallExpr)
                    .anyMatch(v -> instanceCall(v.asMethodCallExpr(), c.target().converter()));
          if (arg.isNameExpr()
              && method.findAll(AssignExpr.class).stream().anyMatch(a -> a.getTarget().equals(arg)))
            bound = false;
        }
        if (!bound)
          error(
              "STRICT-002", call, "Facade request is not bound to the declared request converter");
      }
  }

  private Set<MethodDeclaration> converterMethods(ClassOrInterfaceDeclaration owner) {
    Set<MethodDeclaration> methods = new LinkedHashSet<>();
    Deque<MethodDeclaration> queue = new ArrayDeque<>();
    var delegate = index.type(c.target().delegate());
    if (delegate != null) queue.addAll(delegate.getMethodsByName(c.operation()));
    Set<MethodDeclaration> visited = new HashSet<>();
    while (!queue.isEmpty()) {
      var current = queue.removeFirst();
      if (!visited.add(current)) continue;
      for (var call : current.findAll(MethodCallExpr.class))
        try {
          var resolved = call.resolve();
          if (resolved
              .declaringType()
              .getQualifiedName()
              .equals(owner.getFullyQualifiedName().orElseThrow())) {
            var ast = resolved.toAst();
            if (ast.isPresent()
                && ast.get() instanceof MethodDeclaration method
                && methods.add(method)) queue.add(method);
          }
        } catch (RuntimeException e) {
          incomplete(call, "Cannot trace converter entry/helper");
        }
    }
    return methods;
  }

  private void converter(ClassOrInterfaceDeclaration owner, boolean request) {
    for (var field : owner.getFields())
      if (!field.isStatic() || !field.isFinal())
        if (!(owner.isInterface()
            && field.getVariables().size() == 1
            && field.getVariable(0).getNameAsString().equals("INSTANCE")))
          error("STRICT-003", field, "Converter must not hold injected or mutable service state");
    for (var annotation : owner.findAll(MemberValuePair.class))
      if (Set.of("expression", "defaultExpression", "uses").contains(annotation.getNameAsString()))
        error(
            "STRICT-003",
            annotation,
            "Executable mapping expressions/external mapper delegates require a reviewed tool"
                + " extension");
    for (var method : converterMethods(owner)) {
      try {
        String result = method.getType().resolve().describe();
        if (request
                && ((!c.stringResponse() && result.equals(c.externalResponse()))
                    || result.equals(c.models().response().className()))
            || !request && result.equals(c.models().request().className()))
          error("STRICT-003", method, "Request/response converter responsibilities are mixed");
      } catch (RuntimeException e) {
        incomplete(method, "Cannot resolve converter return type");
      }
      for (var call : method.findAll(MethodCallExpr.class)) {
        try {
          String declaring = call.resolve().declaringType().getQualifiedName();
          boolean dto =
              declaring.equals(c.externalResponse())
                  || declaring.equals(c.models().request().className())
                  || declaring.startsWith(c.models().request().className() + ".")
                  || declaring.equals(c.models().response().className())
                  || declaring.startsWith(c.models().response().className() + ".");
          boolean pureJava =
              Set.of(
                          "java.util.List",
                          "java.util.Collection",
                          "java.util.ArrayList",
                          "java.util.LinkedList",
                          "java.util.Map",
                          "java.util.HashMap",
                          "java.util.LinkedHashMap",
                          "java.util.Set",
                          "java.util.HashSet",
                          "java.util.LinkedHashSet",
                          "java.util.Iterator",
                          "java.util.Collections",
                          "java.util.Arrays",
                          "java.util.Objects",
                          "java.util.Optional",
                          "java.util.Locale")
                      .contains(declaring)
                  || Set.of(
                          "java.lang.String",
                          "java.lang.Long",
                          "java.lang.Integer",
                          "java.lang.Boolean",
                          "java.lang.Math",
                          "java.lang.Double")
                      .contains(declaring);
          if (!dto && !pureJava && !declaring.equals(owner.getFullyQualifiedName().orElseThrow()))
            error(
                "STRICT-003",
                call,
                "Converter calls outside approved DTO/collection mapping: "
                    + declaring
                    + "."
                    + call.getNameAsString());
        } catch (RuntimeException e) {
          incomplete(call, "Cannot prove converter call is a pure mapping: " + call);
        }
      }
      for (var created : method.findAll(ObjectCreationExpr.class))
        try {
          String type = created.getType().resolve().describe();
          if (request
                  && (type.equals(c.externalResponse())
                      || type.equals(c.models().response().className()))
              || !request && type.equals(c.models().request().className()))
            error("STRICT-003", created, "Converter constructs the opposite side's DTO");
          boolean allowed =
              type.equals(c.externalResponse())
                  || type.equals(profile.type("internalException"))
                  || type.equals(c.models().request().className())
                  || type.startsWith(c.models().request().className() + ".")
                  || type.equals(c.models().response().className())
                  || type.startsWith(c.models().response().className() + ".")
                  || Set.of(
                          "java.util.ArrayList",
                          "java.util.LinkedList",
                          "java.util.HashMap",
                          "java.util.LinkedHashMap",
                          "java.util.HashSet",
                          "java.util.LinkedHashSet")
                      .contains(type.split("<")[0]);
          if (!allowed)
            error(
                "STRICT-003",
                created,
                "Converter constructs a service or unsupported type: " + type);
        } catch (RuntimeException e) {
          incomplete(created, "Cannot resolve converter construction");
        }
    }
  }

  private boolean logHook(MethodCallExpr call) {
    return Set.of("entry", "ret").contains(call.getNameAsString())
        && call.resolve().declaringType().getName().contains("Logger");
  }

  private Set<MethodCallExpr> facadeBindings(MethodDeclaration method) {
    Set<MethodCallExpr> getters = new HashSet<>();
    if (method.getParameters().size() != 1) return getters;
    Map<String, String> variables = new HashMap<>();
    for (var step : c.steps()) {
      var calls =
          method.findAll(MethodCallExpr.class).stream()
              .filter(call -> call.getNameAsString().equals("run") && call.getScope().isPresent())
              .filter(
                  call -> {
                    try {
                      return call.getScope()
                          .orElseThrow()
                          .calculateResolvedType()
                          .describe()
                          .equals(step.handler());
                    } catch (RuntimeException e) {
                      incomplete(call, "Cannot bind Handler input");
                      return false;
                    }
                  })
              .toList();
      if (calls.size() != 1) continue;
      var call = calls.get(0);
      String[] binding = step.input().split("\\.");
      String base =
          binding[0].equals("request")
              ? method.getParameter(0).getNameAsString()
              : variables.get(binding[1]);
      String field =
          binding.length == (binding[0].equals("request") ? 2 : 3)
              ? binding[binding.length - 1]
              : null;
      boolean matches = false;
      if (call.getArguments().size() == 1 && base != null) {
        Expression arg = call.getArgument(0);
        while (arg.isEnclosedExpr()) arg = arg.asEnclosedExpr().getInner();
        if (field != null && arg.isConditionalExpr()) {
          var guarded = arg.asConditionalExpr();
          var condition = guarded.getCondition();
          if (condition.isBinaryExpr()
              && condition.asBinaryExpr().getOperator() == BinaryExpr.Operator.EQUALS
              && condition.asBinaryExpr().getLeft().toString().equals(base)
              && condition.asBinaryExpr().getRight().isNullLiteralExpr()
              && guarded.getThenExpr().isNullLiteralExpr()) arg = guarded.getElseExpr();
        }
        if (field == null)
          matches = arg.isNameExpr() && arg.asNameExpr().getNameAsString().equals(base);
        else if (arg.isMethodCallExpr()) {
          var getter = arg.asMethodCallExpr();
          matches =
              getter.getArguments().isEmpty()
                  && getter.getScope().map(Object::toString).filter(base::equals).isPresent()
                  && Set.of("get" + Contract.cap(field), "is" + Contract.cap(field), field)
                      .contains(getter.getNameAsString());
          if (matches) getters.add(getter);
        }
      }
      if (!matches)
        error(
            "STRICT-004",
            call,
            "Handler " + step.id() + " input differs from contract binding " + step.input());
      call.getParentNode()
          .filter(VariableDeclarator.class::isInstance)
          .map(VariableDeclarator.class::cast)
          .ifPresent(variable -> variables.put(step.id(), variable.getNameAsString()));
    }
    return getters;
  }

  private void facade(MethodDeclaration method) {
    if (method.getBody().isEmpty()) return;
    Set<MethodCallExpr> bindingGetters = facadeBindings(method);
    for (var statement : method.getBody().orElseThrow().getStatements())
      if (!statement.isReturnStmt()
          && !(statement.isExpressionStmt()
              && (statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()
                  || statement.asExpressionStmt().getExpression().isMethodCallExpr()
                      && logHook(statement.asExpressionStmt().getExpression().asMethodCallExpr()))))
        error("STRICT-004", statement, "Facade contains control flow or business statements");
    for (var created : method.findAll(ObjectCreationExpr.class))
      error("STRICT-004", created, "Facade constructs business objects instead of orchestrating");
    for (var variable : method.findAll(VariableDeclarator.class))
      if (variable.getInitializer().filter(Expression::isMethodCallExpr).isEmpty())
        error("STRICT-004", variable, "Facade local must be a Handler/Builder call result");
    for (var returned : method.findAll(ReturnStmt.class))
      if (returned.getExpression().filter(Expression::isMethodCallExpr).isEmpty())
        error("STRICT-004", returned, "Facade must directly return Handler/Builder output");
    for (var call : method.findAll(MethodCallExpr.class))
      try {
        String type = call.resolve().declaringType().getQualifiedName();
        boolean handler =
            call.getNameAsString().equals("run")
                && (type.equals(profile.type("handlerBase"))
                    || c.steps().stream().anyMatch(s -> s.handler().equals(type)));
        boolean builder =
            c.assembly().builder() != null
                && type.equals(c.assembly().builder())
                && call.getNameAsString().equals("build");
        if (!handler && !builder && !bindingGetters.contains(call) && !logHook(call))
          error(
              "STRICT-004",
              call,
              "Facade contains work beyond Handler/Builder orchestration: " + call);
      } catch (RuntimeException e) {
        incomplete(call, "Cannot resolve Facade orchestration");
      }
  }

  private void lifecycle(ClassOrInterfaceDeclaration owner) {
    for (var field : owner.getFields())
      for (var variable : field.getVariables())
        if (variable.getType().isClassOrInterfaceType()
            && variable.getType().asClassOrInterfaceType().getNameAsString().endsWith("Dto"))
          error(
              "STRICT-005",
              field,
              "Handler must not store per-request DTO state; use each invocation's Context");
    for (var method : owner.getMethods()) {
      boolean invoke = method.getNameAsString().equals("invokeAtomicApi");
      boolean other = Set.of("preHandler", "postHandler").contains(method.getNameAsString());
      if (invoke || other)
        for (var returned : method.findAll(ReturnStmt.class))
          if (returned.getExpression().filter(Expression::isNullLiteralExpr).isPresent())
            error(
                "STRICT-007",
                returned,
                "Lifecycle must not return null to hide unimplemented work");
      if (invoke) {
        if (method.getBody().isPresent()
            && (method.getBody().get().getStatements().size() == 1
                    && method.getBody().get().getStatement(0).isThrowStmt()
                || method.findAll(MethodCallExpr.class).isEmpty()))
          error(
              "STRICT-007",
              method,
              "Invocation stage is an unconditional throw or has no capability call");
        for (var created : method.findAll(ObjectCreationExpr.class))
          if (created.getType().getNameAsString().endsWith("ReqDto"))
            error("STRICT-005", created, "Atomic request construction belongs to preHandler");
        for (var call : method.findAll(MethodCallExpr.class))
          if (call.getNameAsString().startsWith("set"))
            try {
              if (call.resolve().declaringType().getQualifiedName().endsWith("ReqDto"))
                error("STRICT-005", call, "Atomic request field mapping belongs to preHandler");
            } catch (RuntimeException e) {
              incomplete(call, "Cannot resolve lifecycle setter");
            }
      }
      if (other)
        for (var call : method.findAll(MethodCallExpr.class))
          if (Set.of("getLatest", "getAtomicApi", "getFacadeImpl").contains(call.getNameAsString()))
            error("STRICT-005", call, "Capability lookup belongs to invokeAtomicApi");
    }
  }
}
