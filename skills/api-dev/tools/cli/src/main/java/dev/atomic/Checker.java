package dev.atomic;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/** AST/type based checks. Unproved relations are INCOMPLETE, never PASS. */
public final class Checker {
  public record Finding(
      String severity, String rule, String file, int line, String message, String fix) {
    @JsonProperty("ruleLabel")
    public String ruleLabel() {
      return RuleCatalog.label(rule);
    }

    public Finding(String severity, String rule, String file, int line, String message) {
      this(severity, rule, file, line, message, RuleGuidance.fix(rule));
    }
  }

  public record Report(
      int discoveredMethods,
      int checkedMethods,
      List<Finding> findings,
      List<String> manualChecks,
      String readiness,
      List<RuleCatalog.Result> rules) {
    public int exitCode(boolean strict) {
      if (discoveredMethods == 0
          || findings.stream().anyMatch(f -> f.severity.equals("INCOMPLETE"))) return 2;
      return findings.stream()
              .anyMatch(f -> f.severity.equals("ERROR") || (strict && f.severity.equals("WARN")))
          ? 1
          : 0;
    }
  }

  private SourceIndex index;
  private Contract activeContract;
  private final Profile profile;

  public Checker() {
    this(null);
  }

  public Checker(Profile profile) {
    this.profile = profile;
  }

  private boolean framework(String spelling, String key, String fallback) {
    return profile == null ? sameSimple(spelling, fallback) : spelling.equals(profile.type(key));
  }

  private final List<Finding> findings = new ArrayList<>();
  private final Set<String> facades = new LinkedHashSet<>();
  private int discovered, checked;

  private void finding(String severity, String rule, Node n, String message) {
    findings.add(
        new Finding(
            severity,
            rule,
            index.path(n).toString(),
            n.getBegin().map(p -> p.line).orElse(0),
            message));
  }

  private void incomplete(Node n, String message) {
    finding("INCOMPLETE", "RESOLUTION", n, message);
  }

  private static boolean sameSimple(String type, String simple) {
    return type.equals(simple) || type.endsWith("." + simple);
  }

  private static boolean owns(Node child, MethodDeclaration method) {
    Node parent = child.getParentNode().orElse(null);
    while (parent != null) {
      if (parent == method) return true;
      if (parent instanceof MethodDeclaration
          || parent instanceof LambdaExpr
          || parent instanceof ClassOrInterfaceDeclaration
          || parent instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent())
        return false;
      parent = parent.getParentNode().orElse(null);
    }
    return false;
  }

  private boolean facadeAnnotation(ClassOrInterfaceDeclaration type) {
    for (var annotation : type.getAnnotations()) {
      if (!sameSimple(
          annotation.getNameAsString(),
          profile == null
              ? "OpsFacadeInterface"
              : Contract.simple(profile.type("facadeAnnotation")))) continue;
      try {
        if (framework(
            annotation.resolve().getQualifiedName(), "facadeAnnotation", "OpsFacadeInterface"))
          return true;
      } catch (RuntimeException e) {
        incomplete(annotation, "Facade annotation cannot be resolved");
      }
    }
    return false;
  }

  public Report check(SourceIndex input, Contract contract) {
    index = input;
    activeContract = contract;
    boolean strict = contract != null && contract.version() == 2;
    findings.clear();
    findings.addAll(MavenParents.check(index.root()));
    facades.clear();
    discovered = 0;
    checked = 0;
    for (String issue : index.issues())
      findings.add(new Finding("INCOMPLETE", "PARSE", index.root().toString(), 0, issue));
    for (var cu : index.units())
      for (var call : cu.findAll(MethodCallExpr.class)) {
        if (strict && !inScope(call, contract)) continue;
        if (call.getNameAsString().equals("getFacadeImpl")) {
          if (call.getArguments().size() != 1 || !call.getArgument(0).isClassExpr()) {
            incomplete(call, "Dynamic getFacadeImpl target");
            continue;
          }
          try {
            facades.add(
                call.getArgument(0)
                    .asClassExpr()
                    .getType()
                    .resolve()
                    .asReferenceType()
                    .getQualifiedName());
          } catch (RuntimeException e) {
            incomplete(call, "Cannot resolve Facade class: " + e.getMessage());
          }
        }
      }
    for (var type : index.types().values())
      if ((!strict || Conventions.scope(contract).contains(type.getFullyQualifiedName().orElse("")))
          && facadeAnnotation(type)) {
        for (var parent : type.getImplementedTypes())
          try {
            facades.add(parent.resolve().asReferenceType().getQualifiedName());
          } catch (RuntimeException e) {
            incomplete(parent, "Cannot resolve annotated Facade");
          }
      }
    if (strict) facades.retainAll(Set.of(contract.target().facade()));
    for (String name : facades) {
      var facade = index.type(name);
      if (facade == null || !facade.isInterface()) {
        findings.add(
            new Finding(
                "INCOMPLETE",
                "DISCOVERY",
                index.root().toString(),
                0,
                "Facade source unavailable: " + name));
        continue;
      }
      List<MethodDeclaration> methods = apiMethods(facade);
      for (var method : methods) {
        if (strict && !method.getNameAsString().equals(contract.operation())) continue;
        discovered++;
        int before = findings.size();
        signature(method);
        for (var impl : index.types().values()) {
          if (strict
              && !impl.getFullyQualifiedName()
                  .orElse("")
                  .equals(contract.target().implementation())) continue;
          if (impl.isInterface()
              || impl.getImplementedTypes().isEmpty() && impl.getExtendedTypes().isEmpty())
            continue;
          try {
            boolean implementsFacade =
                impl.resolve().getAllAncestors().stream()
                    .anyMatch(a -> a.getQualifiedName().equals(name));
            if (!implementsFacade) continue;
            var usages =
                impl.resolve().getAllMethods().stream()
                    .filter(
                        u ->
                            u.getName().equals(method.getNameAsString())
                                && u.getDeclaration()
                                    .getSignature()
                                    .equals(method.resolve().getSignature()))
                    .toList();
            if (usages.size() != 1) {
              incomplete(
                  impl, "Cannot uniquely resolve implementation: " + method.getNameAsString());
              continue;
            }
            var ast = usages.get(0).getDeclaration().toAst();
            if (ast.isEmpty() || !(ast.get() instanceof MethodDeclaration)) {
              incomplete(impl, "Implementation source unavailable");
              continue;
            }
            var actual = (MethodDeclaration) ast.get();
            signature(actual);
            if (actual.getBody().isPresent()) checkHandlerCalls(actual);
          } catch (RuntimeException e) {
            if (impl.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(facade.getNameAsString())))
              incomplete(impl, "Implementation resolution failed: " + e.getMessage());
          }
        }
        if (findings.subList(before, findings.size()).stream()
            .noneMatch(f -> f.severity.equals("INCOMPLETE"))) checked++;
      }
    }
    for (var cu : index.units()) {
      for (var call : cu.findAll(MethodCallExpr.class)) {
        if (strict
            && (!inScope(call, contract) || !call.getNameAsString().equals(contract.operation())))
          continue;
        if (call.getScope().isEmpty() || call.getNameAsString().equals("getFacadeImpl")) continue;
        try {
          var receiver = call.getScope().get().calculateResolvedType();
          if (!receiver.isReferenceType()
              || !facades.contains(receiver.asReferenceType().getQualifiedName())) continue;
          call.resolve();
          boundary(call);
        } catch (RuntimeException e) {
          if (isKnownFacadeReceiver(call))
            incomplete(call, "Facade call unresolved: " + e.getMessage());
        }
      }
      for (var guard : cu.findAll(IfStmt.class))
        if ((!strict || inScope(guard, contract))
            && guard.getCondition().findAll(BinaryExpr.class).stream()
                .anyMatch(b -> b.getOperator() == BinaryExpr.Operator.OR)
            && !guard.getThenStmt().findAll(ThrowStmt.class).isEmpty())
          finding(
              "WARN", "API-008", guard, "OR leads to exception; review independent failure causes");
    }
    if (contract != null) compare(contract);
    Map<String, List<String>> evidence = new LinkedHashMap<>();
    if (strict) {
      findings.addAll(new StrictChecks(index, contract, profile).check());
      var codeRules = new CodeRules(index, contract, profile);
      findings.addAll(codeRules.check());
      evidence.putAll(codeRules.evidence);
    }
    boolean scaffold =
        index.units().stream()
            .flatMap(c -> c.findAll(StringLiteralExpr.class).stream())
            .filter(s -> !strict || inScope(s, contract))
            .anyMatch(s -> s.asString().startsWith("ATOMIC_API_UNIMPLEMENTED:"));
    if (strict)
      for (var cu : index.units())
        for (var literal : cu.findAll(StringLiteralExpr.class))
          if (inScope(literal, contract)
              && literal.asString().startsWith("ATOMIC_API_UNIMPLEMENTED:"))
            finding("ERROR", "STRICT-007", literal, "Business implementation placeholder remains");
    return new Report(
        discovered,
        checked,
        List.copyOf(findings),
        RuleCatalog.all().stream()
            .filter(r -> !r.coverage().equals("AUTO"))
            .map(r -> r.label() + " " + r.limitation())
            .toList(),
        scaffold ? "SCAFFOLD" : "UNKNOWN",
        RuleCatalog.results(findings, strict, evidence));
  }

  private static boolean inScope(Node node, Contract c) {
    var owner = node.findAncestor(ClassOrInterfaceDeclaration.class);
    if (owner.isEmpty()) return false;
    String name = owner.get().getFullyQualifiedName().orElse("");
    if (!Conventions.scope(c).contains(name)) return false;
    if (Set.of(c.target().facade(), c.target().implementation(), c.target().delegate())
        .contains(name))
      return node.findAncestor(MethodDeclaration.class)
          .map(m -> m.getNameAsString().equals(c.operation()))
          .orElse(true);
    return true;
  }

  private boolean isKnownFacadeReceiver(MethodCallExpr call) {
    if (!call.getScope().get().isNameExpr()) return false;
    String receiver = call.getScope().get().asNameExpr().getNameAsString();
    return call.findCompilationUnit().get().findAll(VariableDeclarator.class).stream()
        .anyMatch(
            v ->
                v.getNameAsString().equals(receiver)
                    && facades.stream().anyMatch(f -> sameSimple(f, v.getType().asString())));
  }

  private List<MethodDeclaration> apiMethods(ClassOrInterfaceDeclaration facade) {
    Map<String, MethodDeclaration> methods = new LinkedHashMap<>();
    try {
      for (var usage : facade.resolve().getAllMethods()) {
        if (usage.getDeclaration().declaringType().getQualifiedName().equals("java.lang.Object"))
          continue;
        var ast = usage.getDeclaration().toAst();
        if (ast.isEmpty() || !(ast.get() instanceof MethodDeclaration)) {
          incomplete(facade, "Inherited API source unavailable: " + usage.getName());
          continue;
        }
        var method = (MethodDeclaration) ast.get();
        if (!method.isStatic() && !method.isPrivate()) methods.put(usage.getSignature(), method);
      }
    } catch (RuntimeException e) {
      incomplete(facade, "Cannot resolve inherited Facade methods: " + e.getMessage());
    }
    return List.copyOf(methods.values());
  }

  private void checkHandlerCalls(MethodDeclaration method) {
    boolean found = false, unresolved = false;
    for (var call : method.findAll(MethodCallExpr.class))
      if (call.getNameAsString().equals("run") && owns(call, method)) {
        try {
          var declaration = call.resolve().declaringType();
          found |=
              framework(declaration.getQualifiedName(), "handlerBase", "ApiHandler")
                  || declaration.getAllAncestors().stream()
                      .anyMatch(a -> framework(a.getQualifiedName(), "handlerBase", "ApiHandler"));
        } catch (RuntimeException e) {
          unresolved = true;
          incomplete(call, "Cannot resolve Handler.run receiver");
        }
      }
    if (!found && !unresolved)
      finding(
          "ERROR",
          "API-004",
          method,
          "Facade must orchestrate the framework Handler, not an unrelated run method");
  }

  private void signature(MethodDeclaration method) {
    try {
      if (method.getParameters().size() != 1
          || !method.getParameter(0).getType().resolve().isReferenceType()
          || !method
              .getParameter(0)
              .getType()
              .resolve()
              .asReferenceType()
              .getQualifiedName()
              .endsWith("ReqDto"))
        finding("ERROR", "API-002", method, "Facade must accept one ReqDto request");
      if (method.getThrownExceptions().size() != 1
          || !framework(
              method.getThrownException(0).resolve().describe(),
              "internalException",
              "OpsApiException"))
        finding("ERROR", "API-006", method, "Facade must declare only the internal API exception");
    } catch (RuntimeException e) {
      incomplete(method, "Signature type unresolved: " + e.getMessage());
    }
  }

  private void boundary(MethodCallExpr call) {
    TryStmt enclosing = null;
    Node child = call;
    Node parent = child.getParentNode().orElse(null);
    while (parent != null && !(parent instanceof MethodDeclaration)) {
      if (parent instanceof LambdaExpr) {
        incomplete(call, "Lambda execution boundary requires review");
        return;
      }
      if (parent instanceof TryStmt t && t.getTryBlock() == child) {
        enclosing = t;
        break;
      }
      child = parent;
      parent = parent.getParentNode().orElse(null);
    }
    if (enclosing == null) {
      finding("ERROR", "API-007", call, "Facade invocation is outside try");
      return;
    }
    var catches =
        enclosing.getCatchClauses().stream()
            .filter(
                c ->
                    framework(
                        c.getParameter().getType().resolve().describe(),
                        "internalException",
                        "OpsApiException"))
            .toList();
    if (catches.size() != 1) {
      finding("ERROR", "API-007", call, "Explicit OpsApiException catch missing");
      return;
    }
    CatchClause caught = catches.get(0);
    String variable = caught.getParameter().getNameAsString();
    boolean wraps =
        caught.getBody().getStatements().stream()
            .filter(Statement::isThrowStmt)
            .map(s -> s.asThrowStmt().getExpression())
            .anyMatch(
                thrown -> {
                  if (thrown.isObjectCreationExpr())
                    return wraps(thrown.asObjectCreationExpr(), variable);
                  if (!thrown.isNameExpr()) return false;
                  String name = thrown.asNameExpr().getNameAsString();
                  // Only a directly initialized, unreassigned local is provable here.
                  if (caught.findAll(AssignExpr.class).stream()
                      .anyMatch(a -> a.getTarget().toString().equals(name))) return false;
                  return caught.getBody().getStatements().stream()
                      .filter(Statement::isExpressionStmt)
                      .map(st -> st.asExpressionStmt().getExpression())
                      .filter(Expression::isVariableDeclarationExpr)
                      .flatMap(e -> e.asVariableDeclarationExpr().getVariables().stream())
                      .filter(v -> v.getNameAsString().equals(name))
                      .flatMap(v -> v.getInitializer().stream())
                      .filter(Expression::isObjectCreationExpr)
                      .anyMatch(e -> wraps(e.asObjectCreationExpr(), variable));
                });
    boolean messageMode = activeContract != null && activeContract.returnsFailureMessage();
    boolean returnsDetail =
        caught.getBody().getStatements().stream()
            .filter(Statement::isReturnStmt)
            .flatMap(st -> st.asReturnStmt().getExpression().stream())
            .anyMatch(
                e ->
                    e.findAll(MethodCallExpr.class).stream()
                        .anyMatch(
                            m ->
                                m.getNameAsString().equals("getErrMsg")
                                    && m.getScope()
                                        .map(Object::toString)
                                        .filter(variable::equals)
                                        .isPresent()));
    if (messageMode && (!returnsDetail || !caught.getBody().findAll(ThrowStmt.class).isEmpty()))
      finding(
          "ERROR",
          "API-007",
          caught,
          "Contract requires a failure message preserving caught.getErrMsg(), not a thrown wrapper"
              + " or discarded detail");
    else if (!messageMode && !wraps && !caught.getBody().findAll(ReturnStmt.class).isEmpty())
      incomplete(
          caught,
          "Failure return contract requires manual review; do not replace it with an exception just"
              + " to satisfy the checker");
    else if (!messageMode && !wraps)
      finding(
          "ERROR",
          "API-007",
          caught,
          "ServiceException must receive the caught getErrMsg(), not discard it");
    responseBoundary(call, enclosing);
    var owner = call.findAncestor(MethodDeclaration.class);
    if (owner.isPresent()) {
      MethodDeclaration m = owner.get();
      boolean converter =
          m.findAll(MethodCallExpr.class).stream()
              .anyMatch(
                  c ->
                      c.getScope().isPresent()
                          && c.getScope().get().isFieldAccessExpr()
                          && c.getScope()
                              .get()
                              .asFieldAccessExpr()
                              .getNameAsString()
                              .equals("INSTANCE"));
      if (!converter) finding("WARN", "API-001", m, "Request Converter.INSTANCE flow needs review");
      boolean root =
          caught.findAll(FieldAccessExpr.class).stream()
              .anyMatch(
                  f ->
                      f.toString().equals("Locale.ROOT")
                          || f.toString().equals("java.util.Locale.ROOT"));
      boolean formatting =
          caught.findAll(MethodCallExpr.class).stream()
              .anyMatch(
                  c ->
                      c.getNameAsString().equals("format")
                          && c.getScope()
                              .map(Object::toString)
                              .filter(v -> v.equals("String") || v.equals("java.lang.String"))
                              .isPresent());
      if (formatting && !root)
        finding("WARN", "API-007", caught, "Locale.ROOT formatting not found");
    }
  }

  private boolean wraps(ObjectCreationExpr created, String variable) {
    return framework(
            created.getType().resolve().describe(), "externalException", "ServiceException")
        && created.getArguments().stream()
            .anyMatch(
                arg ->
                    arg.findAll(MethodCallExpr.class).stream()
                        .anyMatch(
                            m ->
                                m.getNameAsString().equals("getErrMsg")
                                    && m.getArguments().isEmpty()
                                    && m.getScope()
                                        .filter(Expression::isNameExpr)
                                        .map(
                                            scope ->
                                                scope
                                                    .asNameExpr()
                                                    .getNameAsString()
                                                    .equals(variable))
                                        .orElse(false)));
  }

  private static boolean rejectsNull(Expression expression, String parameter) {
    if (expression.isEnclosedExpr())
      return rejectsNull(expression.asEnclosedExpr().getInner(), parameter);
    if (!expression.isBinaryExpr()) return false;
    BinaryExpr binary = expression.asBinaryExpr();
    if (binary.getOperator() == BinaryExpr.Operator.OR)
      return rejectsNull(binary.getLeft(), parameter);
    return binary.getOperator() == BinaryExpr.Operator.EQUALS
        && (binary.getLeft().isNullLiteralExpr() && binary.getRight().toString().equals(parameter)
            || binary.getRight().isNullLiteralExpr()
                && binary.getLeft().toString().equals(parameter));
  }

  private void responseBoundary(MethodCallExpr facadeCall, TryStmt enclosing) {
    var owner = facadeCall.findAncestor(MethodDeclaration.class);
    if (owner.isPresent()
        && owner.get().getType().resolve().describe().equals("java.lang.String")
        && !(activeContract != null
            && activeContract.version() == 2
            && activeContract.stringResponse())) {
      incomplete(
          facadeCall, "String response adaptation requires manual review of the existing contract");
      return;
    }
    var assigned =
        facadeCall
            .getParentNode()
            .filter(VariableDeclarator.class::isInstance)
            .map(VariableDeclarator.class::cast);
    if (assigned.isEmpty()) {
      finding(
          "ERROR",
          "API-009",
          facadeCall,
          "Capture the business result and convert it through a response converter");
      return;
    }
    String result = assigned.get().getNameAsString();
    var returns =
        enclosing.getTryBlock().getStatements().stream()
            .filter(Statement::isReturnStmt)
            .map(s -> s.asReturnStmt().getExpression())
            .flatMap(Optional::stream)
            .filter(Expression::isMethodCallExpr)
            .map(Expression::asMethodCallExpr)
            .toList();
    if (returns.size() != 1) {
      incomplete(facadeCall, "Cannot uniquely trace response conversion");
      return;
    }
    var builder = returns.get(0);
    int position = -1;
    for (int i = 0; i < builder.getArguments().size(); i++)
      if (builder.getArgument(i).isNameExpr()
          && builder.getArgument(i).asNameExpr().getNameAsString().equals(result)) position = i;
    if (position < 0) {
      finding("ERROR", "API-009", builder, "Response builder does not receive business result");
      return;
    }
    try {
      var ast = builder.resolve().toAst();
      if (ast.isEmpty() || !(ast.get() instanceof MethodDeclaration)) {
        incomplete(builder, "Response builder source unavailable");
        return;
      }
      var method = (MethodDeclaration) ast.get();
      String parameter = method.getParameter(position).getNameAsString();
      boolean guarded = false;
      if (method.getBody().isPresent() && !method.getBody().get().getStatements().isEmpty()) {
        var first = method.getBody().get().getStatement(0);
        if (first.isIfStmt()) {
          var condition = first.asIfStmt().getCondition();
          boolean nullEquality = rejectsNull(condition, parameter);
          Statement rejection = first.asIfStmt().getThenStmt();
          if (rejection.isBlockStmt() && !rejection.asBlockStmt().getStatements().isEmpty())
            rejection = rejection.asBlockStmt().getStatement(0);
          guarded =
              nullEquality
                  && rejection.isThrowStmt()
                  && rejection.asThrowStmt().getExpression().isObjectCreationExpr()
                  && framework(
                      rejection
                          .asThrowStmt()
                          .getExpression()
                          .asObjectCreationExpr()
                          .getType()
                          .resolve()
                          .describe(),
                      "internalException",
                      "OpsApiException");
        }
      }
      if (!guarded)
        finding(
            "ERROR",
            "API-009",
            method,
            "Response builder must reject null business result before conversion");
    } catch (RuntimeException e) {
      incomplete(builder, "Response conversion cannot be resolved: " + e.getMessage());
    }
  }

  private void compare(Contract contract) {
    var facade = index.type(contract.target().facade());
    if (facade == null) {
      findings.add(
          new Finding("ERROR", "CONTRACT", index.root().toString(), 0, "Expected Facade missing"));
      return;
    }
    var methods =
        apiMethods(facade).stream()
            .filter(m -> m.getNameAsString().equals(contract.operation()))
            .toList();
    if (methods.size() != 1) {
      finding("ERROR", "CONTRACT", facade, "Expected operation missing or overloaded");
      return;
    }
    try {
      var method = methods.get(0);
      if (method.getParameters().size() != 1
          || !method
              .getParameter(0)
              .getType()
              .resolve()
              .describe()
              .equals(contract.models().request().className())
          || !method
              .getType()
              .resolve()
              .describe()
              .equals(contract.models().response().className()))
        finding("ERROR", "CONTRACT", method, "DTO signature differs from contract");
    } catch (RuntimeException e) {
      incomplete(facade, "Cannot compare contract types");
    }
    for (var model : List.of(contract.models().request(), contract.models().response())) {
      var type = index.type(model.className());
      if (type == null) {
        finding("ERROR", "CONTRACT", facade, "Expected model missing: " + model.className());
        continue;
      }
      for (var field : model.fields()) {
        var actual = type.getFieldByName(field.name());
        if (actual.isEmpty())
          finding("ERROR", "CONTRACT", type, "Expected field missing: " + field.name());
        else
          try {
            if (!actual.get().getVariable(0).getType().resolve().describe().equals(field.type()))
              finding("ERROR", "CONTRACT", actual.get(), "Field type differs");
          } catch (RuntimeException e) {
            incomplete(actual.get(), "Field type unresolved");
          }
      }
    }
    for (var step : contract.steps())
      if (index.type(step.handler()) == null)
        finding("ERROR", "CONTRACT", facade, "Expected Handler missing: " + step.handler());
    var impl = index.type(contract.target().implementation());
    if (impl == null) {
      finding("ERROR", "CONTRACT", facade, "Expected Facade implementation missing");
      return;
    }
    var operations = impl.getMethodsByName(contract.operation());
    if (operations.size() != 1) {
      incomplete(impl, "Cannot uniquely compare implementation steps");
      return;
    }
    List<String> actualSteps = new ArrayList<>();
    for (var call : operations.get(0).findAll(MethodCallExpr.class))
      if (call.getNameAsString().equals("run") && owns(call, operations.get(0))) {
        if (call.getScope().isEmpty()) {
          incomplete(call, "Unscoped Handler invocation");
          continue;
        }
        try {
          actualSteps.add(
              call.getScope().get().calculateResolvedType().asReferenceType().getQualifiedName());
        } catch (RuntimeException e) {
          incomplete(call, "Cannot resolve contract Handler invocation");
        }
      }
    if (!actualSteps.equals(contract.steps().stream().map(Contract.Step::handler).toList()))
      finding("ERROR", "CONTRACT", operations.get(0), "Handler count/order differs from contract");
    if (contract.assembly().mode().equals("builder")
        && index.type(contract.assembly().builder()) == null)
      finding("ERROR", "CONTRACT", impl, "Expected business Builder missing");
  }
}
