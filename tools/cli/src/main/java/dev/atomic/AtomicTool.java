package dev.atomic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Separate Atomic generation/checking route; framework registration stays explicit. */
final class AtomicTool {
  static final String MARKER = "ATOMIC_API_UNIMPLEMENTED";

  static List<RuleCatalog.Rule> rules() {
    return List.of(
        rule(
            "A01",
            "原子接口、公共 DTO、版本实现和 Latest 分别服从 Atomic roles，不能生成 App 编排类。",
            "layout",
            "AUTO",
            "FIXED",
            "限定本契约类型；不推断项目模块名称。"),
        rule(
            "A02",
            "原子接口只声明本操作的 ReqDto→RspDto；公共 DTO 字段符合契约且不反向依赖实现。",
            "signature",
            "PARTIAL",
            "FIXED",
            "检查源码类型和直接依赖；字段语义、外部 JAR 及传递依赖需审查。"),
        rule(
            "A03",
            "每个声明的支持版本实现相同原子签名，底层未接通时明确失败，不返回空成功。",
            "versions",
            "PARTIAL",
            "SCAFFOLD",
            "占位和明显空返回会阻塞；真实调用效果需业务测试。"),
        rule(
            "A04",
            "版本注册注解来自实际框架，完整保留配置值，不凭空生成注册机制。",
            "registration",
            "PARTIAL",
            "EXPLICIT_ANNOTATIONS",
            "校验注解类型可解析及契约表达式；管理器动态发现、注解适用性和参数类型由编译/运行证明。"),
        rule(
            "A05",
            "Latest 按实际 class/interface 体系连接最新支持实现；首支持版本和版本顺序必须有历史证据。",
            "latest",
            "PARTIAL",
            "CONFIGURED_KIND",
            "校验显式继承关系；契约末版本是否最新及历史不支持路径需审查。"),
        rule(
            "A06",
            "Helper 保持单一 public 业务入口；沿用中文意图注释、英文异常；日志按 N13 核对真实 API，不支持时记录 A06 未满足及依据，不造包装器或 try-catch；不泄露凭据。",
            "conventions",
            "PARTIAL",
            "WHEN_LOGGING_CONFIGURED",
            "检查本业务或直接引用的源码 Helper（含继承）、中文存在、明显中文错误/敏感日志及显式出口；外部 Helper、注释含义和隐式路径待审查。"),
        rule(
            "A07",
            "公共契约迁移保留 JSON、公开接口及错误语义，Maven 接线和父版本保持一致。",
            "integration",
            "PARTIAL",
            "NOT_GENERATED",
            "校验本地父版本；wire 基线、完整 Reactor/classifier 和兼容性需构建测试。"),
        rule(
            "A08",
            "通过真实版本选择、Latest、成功/非法输入/底层失败及旧版本不支持测试后交付。",
            "tests",
            "REVIEW",
            "FAILING_TEST_PLAN",
            "报告测试未执行；源码存在不代表已运行，必须附实际命令和结果。"));
  }

  private static RuleCatalog.Rule rule(
      String id, String text, String method, String coverage, String generator, String limit) {
    return RuleCatalog.r(
        id,
        "api-dev/references/rules.md",
        text,
        "AtomicTool." + method,
        coverage,
        generator,
        limit,
        id);
  }

  static int run(
      String command,
      Path repo,
      Map<String, String> options,
      Set<String> flags,
      SourceIndex index,
      PrintStream out)
      throws Exception {
    Contract.require(
        Set.of("new", "generate", "check", "verify").contains(command),
        "Atomic supports new/generate/check/verify/rules");
    Set<String> allowed =
        command.equals("new")
            ? Set.of("--repo", "--profile", "--layer", "--operation", "--business", "--api-version")
            : Set.of("--repo", "--profile", "--layer", "--contract", "--format");
    Contract.require(
        options.keySet().stream().allMatch(allowed::contains),
        "Unknown option for Atomic " + command);
    Contract.require(
        command.equals("generate") || (!flags.contains("--apply") && !flags.contains("--dry-run")),
        "Atomic check/verify/new are read-only except draft creation");
    Contract.require(!command.equals("new") || flags.isEmpty(), "Atomic new accepts no flags");
    Path profile =
        Path.of(
            options.getOrDefault(
                "--profile", repo.resolve(".atomic-api/atomic-profile.yaml").toString()));
    var config = AtomicContract.Config.load(profile);
    if (command.equals("new")) {
      Path draft =
          draft(
              repo,
              config,
              options.get("--operation"),
              options.get("--business"),
              options.get("--api-version"));
      out.println("DRAFT " + draft + "\n填写真实字段、用途及版本注册注解；只生成 Atomic，不生成 Facade/Handler/Delegate。");
      return 0;
    }
    Contract.require(options.containsKey("--contract"), "Atomic requires --contract");
    var c = AtomicContract.load(Path.of(options.get("--contract")), config);
    if (command.equals("generate")) {
      var plan = plan(index, c, config);
      if (flags.contains("--apply")) plan.apply();
      if ("json".equals(options.get("--format")))
        out.println(
            Contract.JSON.writeValueAsString(
                Map.of(
                    "layer",
                    "atomic",
                    "readiness",
                    "SCAFFOLD",
                    "applied",
                    flags.contains("--apply"),
                    "changes",
                    plan.changes())));
      else
        out.println(
            plan.preview() + "readiness=SCAFFOLD；实现真实底层调用，再运行 --layer atomic verify，完成实际编译与版本测试。");
      return 0;
    }
    var report = check(index, c, config, flags.contains("--warnings-as-errors"));
    if ("json".equals(options.get("--format")))
      out.println(Contract.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    else {
      report.findings.forEach(
          f ->
              out.println(
                  f.severity()
                      + " "
                      + f.rule()
                      + " "
                      + f.file()
                      + ":"
                      + f.line()
                      + " "
                      + f.message()
                      + "\n FIX: "
                      + f.fix()));
      report.rules.forEach(
          r ->
              out.println(
                  "RULE "
                      + r.id()
                      + " "
                      + r.status()
                      + " CHECK "
                      + r.checker()
                      + "\n LIMIT: "
                      + r.limitation()));
      out.println(
          "layer=atomic readiness="
              + report.readiness
              + " status="
              + report.status
              + "；未验证注册运行、历史兼容及业务测试。");
    }
    return report.exitCode;
  }

  static Path draft(
      Path root, AtomicContract.Config p, String operation, String business, String version)
      throws Exception {
    Contract.id(operation);
    Contract.id(business);
    Contract.require(
        operation.matches("(?:get|modify|create|delete)[A-Z].*"),
        "Atomic operation must use get/modify/create/deleteXxx");
    Contract.require(
        version != null && version.matches("R[0-9]{2}C[0-9]{2}[A-Za-z0-9_]*"),
        "Atomic new requires --api-version from actual first support evidence");
    String cap = Contract.cap(operation);
    var request =
        new Contract.Model(
            p.name("request", business, cap + "ReqDto"),
            p.roles().get("request").sourceRoot(),
            List.of());
    var response =
        new Contract.Model(
            p.name("response", business, cap + "RspDto"),
            p.roles().get("response").sourceRoot(),
            List.of());
    var c =
        new AtomicContract(
            1,
            "atomic",
            operation,
            business,
            "填写原子能力用途",
            p.name("apiInterface", business, "AtomicApi" + cap + "Interface"),
            p.name("latest", business, "AtomicApi" + cap + "Latest"),
            new Contract.Models(request, response),
            List.of(
                new AtomicContract.Release(
                    version,
                    p.name("implementation", business, "AtomicApi" + cap + version),
                    List.of())),
            List.of());
    Path path =
        GenerationPlan.safe(
            root, root.resolve(".atomic-api/contracts/atomic-" + operation + ".yaml"));
    Contract.require(!Files.exists(path), "CONFLICT: atomic draft exists: " + path);
    String text =
        "# Atomic 草稿；填写真实字段、description、releases.annotations/latestAnnotations。版本按实际支持顺序。\n"
            + new Yaml().dump(Contract.JSON.convertValue(c, Map.class));
    new GenerationPlan(root, List.of(new GenerationPlan.Change(path, null, text)), Map.of())
        .apply();
    return path;
  }

  static GenerationPlan plan(SourceIndex index, AtomicContract c, AtomicContract.Config p)
      throws Exception {
    Contract.require(index.issues().isEmpty(), "Atomic source parse incomplete: " + index.issues());
    LoggingSupport.validate(index, p.logging());
    for (String annotation : allAnnotations(c, p))
      index.declaration(StaticJavaParser.parseAnnotation(annotation).getNameAsString());
    for (String base : p.baseInterfaces())
      Contract.require(
          index
              .declaration(StaticJavaParser.parseClassOrInterfaceType(base).getNameWithScope())
              .isInterface(),
          "Atomic baseInterfaces must resolve to interfaces");
    var output = new LinkedHashMap<String, String>();
    output.put(c.models().request().className(), model(c.models().request()));
    output.put(c.models().response().className(), model(c.models().response()));
    output.put(c.apiInterface(), Templates.render("atomic/interface.java.tpl",
        "packageName", Contract.packageName(c.apiInterface()),
        "annotations", String.join("\n", p.interfaceAnnotations()),
        "className", Contract.simple(c.apiInterface()),
        "extendsClause", p.baseInterfaces().isEmpty() ? "" : " extends " + String.join(",", p.baseInterfaces()),
        "requestType", c.models().request().className(),
        "responseType", c.models().response().className(), "operation", c.operation()));
    for (var v : c.releases()) {
      StringBuilder required = new StringBuilder();
      for (var f : c.models().request().fields())
        if (f.required()) required.append(Templates.render("atomic/required-field.java.tpl",
            "capitalName", Contract.cap(f.name()), "fieldName", f.name()));
      String source = Templates.render("atomic/implementation.java.tpl",
          "packageName", Contract.packageName(v.implementation()),
          "annotations", String.join("\n", v.annotations()),
          "className", Contract.simple(v.implementation()),
          "interfaceType", p.latestKind().equals("interface") ? c.latest() : c.apiInterface(),
          "requestType", c.models().request().className(),
          "responseType", c.models().response().className(), "operation", c.operation(),
          "requiredFields", required.toString(), "apiVersion", v.id());
      var unit = StaticJavaParser.parse(source);
      var type = unit.getClassByName(Contract.simple(v.implementation())).orElseThrow();
      LoggingSupport.decorate(type, type.getMethodsByName(c.operation()).get(0), p.paths());
      output.put(v.implementation(), unit.toString());
    }
    String parent = p.latestKind().equals("interface") ? c.apiInterface()
        : c.releases().get(c.releases().size() - 1).implementation();
    output.put(c.latest(), Templates.render("atomic/latest.java.tpl",
        "packageName", Contract.packageName(c.latest()),
        "annotations", String.join("\n", c.latestAnnotations()), "latestKind", p.latestKind(),
        "className", Contract.simple(c.latest()), "parentType", parent));
    var changes = new ArrayList<GenerationPlan.Change>();
    for (var entry : output.entrySet()) {
      String fqn = entry.getKey();
      Path target = path(index.root(), p, fqn);
      String after = StaticJavaParser.parse(entry.getValue()).toString();
      if (Files.exists(target)) {
        String before = Files.readString(target);
        Contract.require(
            Generator.semanticText(StaticJavaParser.parse(before))
                .equals(Generator.semanticText(StaticJavaParser.parse(after))),
            "CONFLICT: existing atomic source differs: " + target);
      } else changes.add(new GenerationPlan.Change(target, null, after));
    }
    Templates.testPlan(index.root(), "atomic", c.operation(), c.models(), changes);
    return new GenerationPlan(index.root(), changes, index.originals());
  }

  private static Path path(Path root, AtomicContract.Config p, String type) throws Exception {
    var paths = p.paths();
    return GenerationPlan.safe(
        root,
        root.resolve(paths.rootFor(type))
            .resolve(paths.relativeClassPath(type, paths.rootFor(type))));
  }

  private static String model(Contract.Model m) {
    return Templates.render("common/unit.java.tpl", "packageName", Contract.packageName(m.className()),
        "declaration", Templates.model(m));
  }

  private static List<String> allAnnotations(AtomicContract c, AtomicContract.Config p) {
    var all = new ArrayList<>(p.interfaceAnnotations());
    c.releases().forEach(v -> all.addAll(v.annotations()));
    all.addAll(c.latestAnnotations());
    return all;
  }

  record Report(
      int schemaVersion,
      String layer,
      String readiness,
      String status,
      String structureStatus,
      int exitCode,
      List<Checker.Finding> findings,
      List<RuleCatalog.Result> rules) {}

  static Report check(SourceIndex index, AtomicContract c, AtomicContract.Config p)
      throws Exception {
    return check(index, c, p, false);
  }

  static Report check(SourceIndex index, AtomicContract c, AtomicContract.Config p, boolean strict)
      throws Exception {
    var f = new ArrayList<Checker.Finding>();
    for (String issue : index.issues()) add(f, "INCOMPLETE", "A01", index.root().toString(), issue);
    layout(index, c, p, f);
    signature(index, c, p, f);
    versions(index, c, p, f);
    registration(index, c, p, f);
    latest(index, c, p, f);
    conventions(index, c, p, f);
    integration(index, f);
    tests(index, f);
    boolean incomplete = f.stream().anyMatch(x -> x.severity().equals("INCOMPLETE"));
    int exit =
        incomplete
            ? 2
            : f.stream()
                    .anyMatch(
                        x -> x.severity().equals("ERROR") || strict && x.severity().equals("WARN"))
                ? 1
                : 0;
    var results =
        rules().stream()
            .map(
                r -> {
                  boolean fail =
                      f.stream()
                          .anyMatch(x -> x.rule().equals(r.id()) && x.severity().equals("ERROR"));
                  return new RuleCatalog.Result(
                      r.id(),
                      fail
                          ? "FAIL"
                          : incomplete
                              ? "INCOMPLETE"
                              : r.coverage().equals("AUTO") ? "PASS" : "REVIEW_REQUIRED",
                      r.checker(),
                      r.generator(),
                      r.limitation(),
                      r.id().equals("A01") && !incomplete && !fail
                          ? List.of("CHECKED: contract-owned source paths")
                          : List.of(
                              "SCOPE: " + String.join(", ", owners(c)),
                              "NOT_VERIFIED: " + r.limitation(),
                              "loggingConfigured=" + (p.logging() != null)));
                })
            .toList();
    boolean scaffold = f.stream().anyMatch(x -> x.message().contains(MARKER));
    return new Report(
        1,
        "atomic",
        scaffold ? "SCAFFOLD" : "UNKNOWN",
        exit == 0 ? "REVIEW_REQUIRED" : "BLOCKED",
        exit == 0 ? "STRUCTURE_READY" : "BLOCKED",
        exit,
        List.copyOf(f),
        results);
  }

  private static void add(
      List<Checker.Finding> f, String severity, String rule, String file, String message) {
    f.add(
        new Checker.Finding(
            severity,
            rule,
            file,
            0,
            message,
            "按 api-dev/references/rules.md 的 "
                + rule
                + " 修复并重跑 --layer atomic；不能修改规则或删除占位伪装完成。"));
  }

  private static List<String> owners(AtomicContract c) {
    var result =
        new ArrayList<>(
            List.of(
                c.apiInterface(),
                c.latest(),
                c.models().request().className(),
                c.models().response().className()));
    c.releases().forEach(v -> result.add(v.implementation()));
    return result;
  }

  static void layout(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f)
      throws Exception {
    for (String name : owners(c)) {
      var t = i.type(name);
      Path expected = path(i.root(), p, name);
      if (t == null)
        add(f, "INCOMPLETE", "A01", expected.toString(), "Atomic type missing: " + name);
      else if (!i.path(t).equals(expected))
        add(f, "ERROR", "A01", i.path(t).toString(), "Atomic type outside configured role path");
    }
  }

  private static boolean resolved(com.github.javaparser.ast.type.Type t, String expected) {
    return StaticJavaParser.parseType(t.resolve().describe())
        .asString()
        .equals(StaticJavaParser.parseType(expected).asString());
  }

  static void signature(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f) {
    var api = i.type(c.apiInterface());
    if (api != null) {
      if (!api.isInterface()
          || !api.isPublic()
          || api.getMethods().size() != 1
          || !validMethod(api, c, false))
        add(
            f,
            "ERROR",
            "A02",
            i.path(api).toString(),
            "Atomic interface must declare exactly the contract signature");
    }
    if (api != null)
      try {
        var actual =
            api.getExtendedTypes().stream()
                .map(t -> StaticJavaParser.parseType(t.resolve().describe()).asString())
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(
            p.baseInterfaces().stream()
                .map(t -> StaticJavaParser.parseType(t).asString())
                .collect(java.util.stream.Collectors.toSet())))
          add(
              f,
              "ERROR",
              "A02",
              i.path(api).toString(),
              "Atomic base interfaces differ from profile");
      } catch (RuntimeException e) {
        add(f, "INCOMPLETE", "A02", i.path(api).toString(), "Atomic base interface unresolved");
      }
    for (var m : List.of(c.models().request(), c.models().response())) {
      var t = i.type(m.className());
      if (t == null) continue;
      if (t.isInterface() || !t.isPublic())
        add(f, "ERROR", "A02", i.path(t).toString(), "DTO must be public class");
      var actualFields =
          t.getFields().stream()
              .filter(x -> !x.isStatic())
              .flatMap(x -> x.getVariables().stream())
              .map(x -> x.getNameAsString())
              .collect(java.util.stream.Collectors.toSet());
      var expectedFields =
          m.fields().stream()
              .map(Contract.Field::name)
              .collect(java.util.stream.Collectors.toSet());
      if (!actualFields.equals(expectedFields))
        add(f, "ERROR", "A02", i.path(t).toString(), "DTO fields differ from contract");
      for (var field : m.fields()) {
        var declaration = t.getFieldByName(field.name());
        try {
          if (declaration.isEmpty()
              || !resolved(declaration.get().getVariable(0).getType(), field.type()))
            add(f, "ERROR", "A02", i.path(t).toString(), "DTO field mismatch: " + field.name());
        } catch (RuntimeException e) {
          add(
              f,
              "INCOMPLETE",
              "A02",
              i.path(t).toString(),
              "Unresolved DTO field: " + field.name());
        }
      }
      for (var ref : t.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
        try {
          if (ref.getParentNode().orElse(null)
                  instanceof com.github.javaparser.ast.type.ClassOrInterfaceType parent
              && parent.getScope().orElse(null) == ref) continue;
          String type = ref.resolve().describe();
          if (type.equals(c.latest())
              || c.releases().stream().anyMatch(v -> type.equals(v.implementation()))
              || type.startsWith(p.roles().get("implementation").packagePrefix() + ".")
              || type.contains(".service.")
              || type.contains(".handler."))
            add(
                f,
                "ERROR",
                "A02",
                i.path(t).toString(),
                "Public DTO depends on implementation: " + type);
        } catch (RuntimeException e) {
          add(f, "INCOMPLETE", "A02", i.path(t).toString(), "Unresolved DTO dependency");
        }
    }
  }

  private static boolean validMethod(
      ClassOrInterfaceDeclaration t, AtomicContract c, boolean body) {
    try {
      return t.getMethodsByName(c.operation()).stream()
          .anyMatch(
              m ->
                  m.getParameters().size() == 1
                      && !m.isStatic()
                      && m.getBody().isPresent() == body
                      && resolved(m.getType(), c.models().response().className())
                      && resolved(m.getParameter(0).getType(), c.models().request().className()));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static boolean extendsType(
      ClassOrInterfaceDeclaration t, String parent, boolean implemented) {
    try {
      return (implemented ? t.getImplementedTypes() : t.getExtendedTypes())
          .stream().anyMatch(x -> x.resolve().describe().equals(parent));
    } catch (RuntimeException e) {
      return false;
    }
  }

  static void versions(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f) {
    for (var v : c.releases()) {
      var t = i.type(v.implementation());
      if (t == null) continue;
      String parent = p.latestKind().equals("interface") ? c.latest() : c.apiInterface();
      if (t.isInterface()
          || t.isAbstract()
          || !t.isPublic()
          || !extendsType(t, parent, true)
          || !validMethod(t, c, true))
        add(
            f,
            "ERROR",
            "A03",
            i.path(t).toString(),
            "Version must implement contract with public concrete method");
      for (var m : t.getMethodsByName(c.operation())) {
        if (!m.isPublic())
          add(f, "ERROR", "A03", i.path(t).toString(), "Atomic operation must be public");
        if (m.toString().contains(MARKER))
          add(
              f,
              "ERROR",
              "A03",
              i.path(t).toString(),
              MARKER + ": real lower capability not connected");
        for (var r : m.findAll(com.github.javaparser.ast.stmt.ReturnStmt.class))
          if (LoggingSupport.owned(r, m)
              && (r.getExpression().isEmpty()
                  || r.getExpression().get().isNullLiteralExpr()
                  || r.getExpression().get().isObjectCreationExpr()
                      && r.getExpression().get().asObjectCreationExpr().getArguments().isEmpty()))
            add(
                f,
                "ERROR",
                "A03",
                i.path(t).toString(),
                "Obvious empty-success return; implement actual capability");
      }
    }
  }

  static void registration(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f) {
    var expected = new LinkedHashMap<String, List<String>>();
    expected.put(c.apiInterface(), p.interfaceAnnotations());
    expected.put(c.latest(), c.latestAnnotations());
    c.releases().forEach(v -> expected.put(v.implementation(), v.annotations()));
    expected.forEach(
        (name, annotations) -> {
          var t = i.type(name);
          if (t == null) return;
          for (String a : annotations)
            try {
              var expr = StaticJavaParser.parseAnnotation(a);
              i.declaration(expr.getNameAsString());
              boolean found =
                  t.getAnnotations().stream()
                      .anyMatch(
                          actual -> {
                            try {
                              return actual
                                      .resolve()
                                      .getQualifiedName()
                                      .equals(expr.getNameAsString())
                                  && annotationValue(actual).equals(annotationValue(expr));
                            } catch (RuntimeException e) {
                              return false;
                            }
                          });
              if (!found)
                add(
                    f,
                    "ERROR",
                    "A04",
                    i.path(t).toString(),
                    "Registration annotation differs/missing: " + a);
            } catch (RuntimeException e) {
              add(
                  f,
                  "INCOMPLETE",
                  "A04",
                  i.path(t).toString(),
                  "Unresolved registration annotation: " + a);
            }
        });
  }

  private static String annotationValue(AnnotationExpr a) {
    var copy = a.clone();
    copy.setName("Registration");
    return copy.toString();
  }

  static void latest(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f) {
    var t = i.type(c.latest());
    if (t == null) return;
    String parent =
        p.latestKind().equals("interface")
            ? c.apiInterface()
            : c.releases().get(c.releases().size() - 1).implementation();
    if (t.isInterface() != p.latestKind().equals("interface")
        || !t.isPublic()
        || !extendsType(t, parent, false))
      add(
          f,
          "ERROR",
          "A05",
          i.path(t).toString(),
          "Latest kind/parent differs from actual configured version chain");
    if (!t.getMethods().isEmpty())
      add(
          f,
          "ERROR",
          "A05",
          i.path(t).toString(),
          "Latest must preserve inherited capability, not override behavior");
  }

  static void conventions(
      SourceIndex i, AtomicContract c, AtomicContract.Config p, List<Checker.Finding> f) {
    var helpers = new LinkedHashSet<ClassOrInterfaceDeclaration>();
    String scope = p.roles().get("implementation").packagePrefix() + "." + c.business() + ".";
    i.types()
        .forEach(
            (name, type) -> {
              if (name.startsWith(scope) && type.getNameAsString().endsWith("Helper"))
                helpers.add(type);
            });
    for (var version : c.releases()) {
      var owner = i.type(version.implementation());
      if (owner == null) continue;
      for (var ref : owner.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
        if (ref.getNameAsString().endsWith("Helper"))
          try {
            var related = i.type(ref.resolve().asReferenceType().getQualifiedName());
            if (related != null) helpers.add(related);
          } catch (RuntimeException e) {
            add(f, "INCOMPLETE", "A06", i.path(owner).toString(), "Referenced Helper unresolved");
          }
    }
    for (var helper : helpers)
      if (!helper.isInterface() && !helper.isAbstract())
        try {
          long entries =
              helper.resolve().getAllMethods().stream()
                  .filter(
                      m ->
                          m.getDeclaration().accessSpecifier()
                                  == com.github.javaparser.ast.AccessSpecifier.PUBLIC
                              && !m.getDeclaration()
                                  .declaringType()
                                  .getQualifiedName()
                                  .equals("java.lang.Object"))
                  .map(
                      m ->
                          m.getName()
                              + "("
                              + java.util.stream.IntStream.range(0, m.getNoParams())
                                  .mapToObj(n -> m.getParamType(n).describe())
                                  .collect(java.util.stream.Collectors.joining(","))
                              + ")")
                  .distinct()
                  .count();
          if (entries != 1)
            add(
                f,
                "ERROR",
                "A06",
                i.path(helper).toString(),
                "Helper must have exactly one public business method, found " + entries);
        } catch (RuntimeException e) {
          add(f, "INCOMPLETE", "A06", i.path(helper).toString(), "Helper inheritance unresolved");
        }
    try {
      LoggingSupport.validate(i, p.logging());
    } catch (RuntimeException e) {
      add(f, "INCOMPLETE", "A06", i.root().toString(), e.getMessage());
    }
    for (var v : c.releases()) {
      var t = i.type(v.implementation());
      if (t == null) continue;
      for (var m : t.getMethodsByName(c.operation())) {
        if (!m.getAllContainedComments().toString().matches("(?s).*[\\u4e00-\\u9fff].*")
            && !m.getComment()
                .map(Object::toString)
                .orElse("")
                .matches("(?s).*[\\u4e00-\\u9fff].*"))
          add(f, "ERROR", "A06", i.path(t).toString(), "Chinese intent comment missing");
        for (var created : m.findAll(ObjectCreationExpr.class))
          if (created.getType().getNameAsString().endsWith("Exception"))
            for (var lit : created.findAll(StringLiteralExpr.class))
              if (lit.asString().matches("(?s).*[\\u4e00-\\u9fff].*"))
                add(f, "ERROR", "A06", i.path(t).toString(), "Exception literal must use English");
        for (var call : m.findAll(MethodCallExpr.class))
          if (call.getScope().map(Object::toString).orElse("").matches("(?i).*(logger|log)"))
            for (var arg : call.getArguments())
              if (arg.toString()
                  .toLowerCase(Locale.ROOT)
                  .matches(".*(password|secret|token|credential|authorization).*"))
                add(f, "ERROR", "A06", i.path(t).toString(), "Sensitive log argument");
        if (p.logging() != null && m.getBody().isPresent()) {
          for (var call : m.findAll(MethodCallExpr.class))
            if (Set.of("entry", "ret").contains(call.getNameAsString())
                && call.getScope().map(Object::toString).orElse("").equals("LOGGER"))
              try {
                if (!call.getScope()
                    .orElseThrow()
                    .calculateResolvedType()
                    .describe()
                    .equals(p.logging().type()))
                  add(
                      f,
                      "ERROR",
                      "A06",
                      i.path(t).toString(),
                      "LOGGER receiver differs from configured type");
              } catch (RuntimeException e) {
                add(f, "INCOMPLETE", "A06", i.path(t).toString(), "LOGGER receiver unresolved");
              }
          var body = m.getBody().get();
          if (body.isEmpty() || !LoggingSupport.logStatement(body.getStatement(0), "entry"))
            add(f, "ERROR", "A06", i.path(t).toString(), "entry() missing at method start");
          for (var exit : m.findAll(com.github.javaparser.ast.stmt.Statement.class))
            if ((exit.isReturnStmt() || exit.isThrowStmt()) && LoggingSupport.owned(exit, m)) {
              var parent = exit.getParentNode().orElse(null);
              if (!(parent instanceof com.github.javaparser.ast.stmt.BlockStmt b)
                  || b.getStatements().indexOf(exit) < 1
                  || !LoggingSupport.logStatement(
                      b.getStatement(b.getStatements().indexOf(exit) - 1), "ret"))
                add(f, "ERROR", "A06", i.path(t).toString(), "Explicit exit missing ret()");
            }
        }
      }
    }
  }

  static void integration(SourceIndex i, List<Checker.Finding> f) {
    f.addAll(
        MavenParents.check(i.root()).stream()
            .map(
                x ->
                    new Checker.Finding(
                        x.severity(), "A07", x.file(), x.line(), x.message(), x.fix()))
            .toList());
  }

  static void tests(SourceIndex i, List<Checker.Finding> f) {
    add(
        f,
        "WARN",
        "A08",
        i.root().toString(),
        "NOT_VERIFIED: run actual version/Latest/business tests and preserve baseline evidence");
  }
}
