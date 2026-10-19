package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class AtomicToolTest {
  @TempDir Path root;
  AtomicContract.Config config;
  Path contract;

  void fixture(String kind) throws Exception {
    Initializer.initialize(root);
    Path configFile = root.resolve(".atomic-api/atomic-profile.yaml");
    String text =
        Files.readString(configFile).replace("latestKind: interface", "latestKind: " + kind);
    if (kind.equals("class"))
      text =
          text.replace(
              "latest: {packagePrefix: com.example.atomic.common.latest, sourceRoot:"
                  + " api-atomic-common/src/main/java}",
              "latest: {packagePrefix: com.example.atomic.impl.latest, sourceRoot:"
                  + " api-atomic/api-example/src/main/java}");
    Files.writeString(configFile, text);
    config = AtomicContract.Config.load(configFile);
    Path reg = root.resolve("api-atomic-common/src/main/java/com/example/framework/Version.java");
    Files.createDirectories(reg.getParent());
    Files.writeString(
        reg, "package com.example.framework; public @interface Version {String value();}");
    contract = AtomicTool.draft(root, config, "getEvplService", "evpl", "R26C10");
    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) Contract.yaml(contract);
    data.put("description", "获取 EVPL 服务");
    @SuppressWarnings("unchecked")
    var models = (Map<String, Object>) data.get("models");
    for (String side : List.of("request", "response")) {
      @SuppressWarnings("unchecked")
      var model = (Map<String, Object>) models.get(side);
      model.put(
          "fields",
          List.of(
              Map.of(
                  "name",
                  side.equals("request") ? "neId" : "name",
                  "type",
                  "java.lang.String",
                  "description",
                  "业务字段",
                  "required",
                  side.equals("request"),
                  "diagnostic",
                  false)));
    }
    @SuppressWarnings("unchecked")
    var versions = (List<Map<String, Object>>) data.get("releases");
    versions.get(0).put("annotations", List.of("@com.example.framework.Version(\"R26C10\")"));
    data.put("latestAnnotations", List.of("@com.example.framework.Version(\"Latest\")"));
    Files.writeString(contract, new Yaml().dump(data));
  }

  AtomicContract load() throws Exception {
    return AtomicContract.load(contract, config);
  }

  void generate() throws Exception {
    AtomicTool.plan(SourceIndex.scan(root), load(), config).apply();
  }

  AtomicTool.Report check() throws Exception {
    return AtomicTool.check(SourceIndex.scan(root), load(), config);
  }

  Path source(String fqn) {
    return root.resolve(config.paths().rootFor(fqn)).resolve(fqn.replace('.', '/') + ".java");
  }

  void compile() throws Exception {
    var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler);
    Path out = root.resolve("classes");
    Files.createDirectories(out);
    try (var manager = compiler.getStandardFileManager(null, null, null);
        var paths = Files.walk(root)) {
      var sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
      var errors = new StringWriter();
      assertEquals(
          true,
          compiler
              .getTask(
                  errors,
                  manager,
                  null,
                  List.of("--release", "8", "-d", out.toString()),
                  null,
                  manager.getJavaFileObjectsFromFiles(sources))
              .call(),
          errors.toString());
    }
  }

  @Test
  void interfaceLatestGeneratesOnlyAtomicTypesAndBlocksScaffold() throws Exception {
    fixture("interface");
    generate();
    compile();
    var c = load();
    assertTrue(Files.exists(source(c.apiInterface())));
    assertTrue(Files.exists(source(c.latest())));
    assertTrue(
        Files.readString(source(c.releases().get(0).implementation()))
            .contains("implements " + c.latest()));
    assertTrue(AtomicTool.plan(SourceIndex.scan(root), c, config).changes().isEmpty());
    var r = check();
    assertEquals(1, r.exitCode(), r.findings().toString());
    assertEquals("SCAFFOLD", r.readiness());
    assertTrue(
        r.findings().stream()
            .anyMatch(f -> f.rule().equals("A03") && f.message().contains(AtomicTool.MARKER)));
    assertTrue(
        r.findings().stream().noneMatch(f -> !Set.of("A03", "A08").contains(f.rule())),
        r.findings().toString());
    assertEquals(8, r.rules().size());
    assertEquals("PASS", r.rules().get(0).status());
    assertFalse(
        SourceIndex.scan(root).types().keySet().stream()
            .anyMatch(
                s -> s.contains("Facade") || s.contains("Handler") || s.contains("Delegate")));
  }

  @Test
  void classLatestExtendsLastDeclaredVersionAndCompiles() throws Exception {
    fixture("class");
    generate();
    compile();
    var c = load();
    assertTrue(
        Files.readString(source(c.latest()))
            .contains("extends " + c.releases().get(0).implementation()));
    assertFalse(check().findings().stream().anyMatch(f -> f.rule().equals("A05")));
  }

  @Test
  void incompleteDraftAndInventedRegistrationAreRejectedWithoutWrites() throws Exception {
    Initializer.initialize(root);
    config = AtomicContract.Config.load(root.resolve(".atomic-api/atomic-profile.yaml"));
    contract = AtomicTool.draft(root, config, "getEvplService", "evpl", "R26C10");
    assertThrows(IllegalArgumentException.class, this::load);
    assertThrows(
        IllegalArgumentException.class,
        () -> AtomicTool.draft(root, config, "queryOther", "evpl", "R26C10"));
    assertThrows(
        IllegalArgumentException.class,
        () -> AtomicTool.draft(root, config, "getOther", "evpl", null));
  }

  @Test
  void editedSourcesNeverOverwrittenAndEmptySuccessIsBlocked() throws Exception {
    fixture("interface");
    generate();
    var c = load();
    Path impl = source(c.releases().get(0).implementation());
    String before = Files.readString(impl);
    String changed =
        before.replace(
            "throw new UnsupportedOperationException(\""
                + AtomicTool.MARKER
                + ": implement getEvplService for R26C10\");",
            "return null;");
    assertNotEquals(before, changed);
    Files.writeString(impl, changed);
    assertThrows(
        IllegalArgumentException.class, () -> AtomicTool.plan(SourceIndex.scan(root), c, config));
    assertEquals(changed, Files.readString(impl));
    assertTrue(
        check().findings().stream()
            .anyMatch(f -> f.rule().equals("A03") && f.message().contains("empty-success")));
  }

  @Test
  void annotationsLatestAndDtoDriftAreDetected() throws Exception {
    fixture("interface");
    generate();
    var c = load();
    Path impl = source(c.releases().get(0).implementation());
    Files.writeString(
        impl,
        Files.readString(impl)
            .replace(
                "@com.example.framework.Version(\"R26C10\")",
                "@com.example.framework.Version(\"R26C00\")"));
    Path latest = source(c.latest());
    Files.writeString(latest, Files.readString(latest).replace(" extends " + c.apiInterface(), ""));
    Path req = source(c.models().request().className());
    Files.writeString(
        req, Files.readString(req).replace("java.lang.String neId", "java.lang.Long neId"));
    var rules = check().findings().stream().map(Checker.Finding::rule).toList();
    assertTrue(rules.containsAll(List.of("A02", "A04", "A05")), rules.toString());
  }

  @Test
  void unknownAnnotationIsIncompleteAndGenerationWritesNothing() throws Exception {
    fixture("interface");
    Files.writeString(
        contract,
        Files.readString(contract)
            .replace("com.example.framework.Version", "missing.framework.Version"));
    assertThrows(RuntimeException.class, () -> generate());
    assertFalse(Files.exists(source(load().apiInterface())));
  }

  @Test
  void explicitLayerRoutesBeforeAppProfileAndRejectsContractMixing() throws Exception {
    fixture("interface");
    Files.writeString(root.resolve(".atomic-api/profile.yaml"), "invalid App profile");
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    String[] args = {
      "generate",
      "--layer",
      "atomic",
      "--repo",
      root.toString(),
      "--contract",
      contract.toString(),
      "--apply"
    };
    assertEquals(0, Cli.run(args, new PrintStream(out), new PrintStream(err)), err.toString());
    assertEquals(
        1,
        Cli.run(
            new String[] {
              "verify",
              "--layer",
              "atomic",
              "--repo",
              root.toString(),
              "--contract",
              contract.toString(),
              "--format",
              "json"
            },
            new PrintStream(out),
            new PrintStream(err)));
    assertTrue(out.toString().contains("\"layer\" : \"atomic\""));
    assertEquals(
        2,
        Cli.run(
            new String[] {
              "verify",
              "--layer",
              "app",
              "--repo",
              root.toString(),
              "--contract",
              contract.toString()
            },
            new PrintStream(out),
            new PrintStream(err)));
    assertThrows(
        IllegalArgumentException.class,
        () -> AtomicContract.load(root.resolve(".atomic-api/contracts/example.yaml"), config));
  }

  @Test
  void atomicCatalogIsDocumentedAndHasRealCheckRoutes() throws Exception {
    String doc = Files.readString(Path.of("../../skills/api-dev/references/rules.md"));
    for (var rule : AtomicTool.rules()) {
      var line =
          doc.lines().filter(s -> s.startsWith("| " + rule.id() + " |")).findFirst().orElseThrow();
      assertTrue(line.contains(rule.requirement()));
      assertTrue(line.contains(rule.checker()));
      assertTrue(line.contains(rule.generator()));
      assertTrue(line.contains(rule.limitation()));
      assertTrue(
          Arrays.stream(AtomicTool.class.getDeclaredMethods())
              .anyMatch(m -> ("AtomicTool." + m.getName()).equals(rule.checker())));
    }
    var out = new ByteArrayOutputStream();
    assertEquals(
        0,
        Cli.run(
            new String[] {"rules", "--layer", "atomic", "--format", "json"},
            new PrintStream(out),
            System.err));
    assertEquals(8, Contract.JSON.readTree(out.toString()).size());
  }

  @Test
  void multipleVersionsCompileAndLatestTracksFinalEntry() throws Exception {
    fixture("class");
    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) Contract.yaml(contract);
    @SuppressWarnings("unchecked")
    var releases = (List<Map<String, Object>>) data.get("releases");
    releases.add(
        new LinkedHashMap<>(
            Map.of(
                "id",
                "R26C20",
                "implementation",
                config.name("implementation", "evpl", "AtomicApiGetEvplServiceR26C20"),
                "annotations",
                List.of("@com.example.framework.Version(\"R26C20\")"))));
    Files.writeString(contract, new Yaml().dump(data));
    generate();
    compile();
    assertTrue(Files.readString(source(load().latest())).contains("AtomicApiGetEvplServiceR26C20"));
    assertFalse(
        check().findings().stream()
            .anyMatch(f -> Set.of("A01", "A02", "A04", "A05").contains(f.rule())));
  }

  @Test
  void configuredLoggingCompilesAndMissingExitIsBlocked() throws Exception {
    fixture("interface");
    Path profile = root.resolve(".atomic-api/atomic-profile.yaml");
    Files.writeString(
        profile,
        Files.readString(profile)
            + "\n"
            + "logging: {type: com.example.framework.Logger, factoryType:"
            + " com.example.framework.LoggerFactory, factoryMethod: getLogger}\n");
    Path dir = root.resolve("api-atomic-common/src/main/java/com/example/framework");
    Files.writeString(
        dir.resolve("Logger.java"),
        "package com.example.framework;public class Logger {public void entry(){} public void"
            + " ret(){}}");
    Files.writeString(
        dir.resolve("LoggerFactory.java"),
        "package com.example.framework;public class LoggerFactory {public static Logger"
            + " getLogger(Class<?> c){return new Logger();}}");
    config = AtomicContract.Config.load(profile);
    generate();
    compile();
    assertFalse(
        check().findings().stream().anyMatch(f -> f.rule().equals("A06")),
        check().findings().toString());
    Path impl = source(load().releases().get(0).implementation());
    String text = Files.readString(impl);
    assertFalse(text.contains("catch ("));
    assertTrue(text.contains("LOGGER.entry();"));
    Files.writeString(impl, text.replace("LOGGER.ret();", ""));
    assertTrue(
        check().findings().stream()
            .anyMatch(f -> f.rule().equals("A06") && f.message().contains("ret()")));
  }

  @Test
  void migrationPreservesOldCustomizationAndInstallsAllReferences() throws Exception {
    Path old = root.resolve(".atomic-api/skills/atomic-api-dev/SKILL.md");
    Files.createDirectories(old.getParent());
    Files.writeString(old, "user custom instructions");
    var result = Initializer.initialize(root);
    assertEquals("user custom instructions", Files.readString(old));
    assertTrue(result.files().stream().anyMatch(s -> s.startsWith("MIGRATE:")));
    for (String file : List.of("SKILL.md", "references/api-atomic.md", "references/app-api.md"))
      assertTrue(Files.exists(root.resolve(".atomic-api/skills/api-dev/" + file)));
    Path main = root.resolve(".atomic-api/skills/api-dev/SKILL.md");
    Files.writeString(main, "new custom instructions");
    Initializer.initialize(root);
    assertEquals("new custom instructions", Files.readString(main));
  }

  @Test
  void classLatestCannotReverseDependFromCommonModule() throws Exception {
    fixture("interface");
    Path profile = root.resolve(".atomic-api/atomic-profile.yaml");
    Files.writeString(
        profile, Files.readString(profile).replace("latestKind: interface", "latestKind: class"));
    config = AtomicContract.Config.load(profile);
    assertThrows(IllegalArgumentException.class, this::load);
  }

  @Test
  void nestedLambdaReturnsDoNotCountAsAtomicMethodEmptySuccess() throws Exception {
    fixture("interface");
    generate();
    var c = load();
    var impl = source(c.releases().get(0).implementation());
    var unit = com.github.javaparser.StaticJavaParser.parse(Files.readString(impl));
    var method =
        unit.getClassByName(Contract.simple(c.releases().get(0).implementation()))
            .orElseThrow()
            .getMethodsByName(c.operation())
            .get(0);
    method.setBody(
        com.github.javaparser.StaticJavaParser.parseBlock(
            "{ java.util.function.Function<String,String> normalize=x->{if(x.isEmpty())return"
                + " null;return x.trim();}; "
                + c.models().response().className()
                + " response=new "
                + c.models().response().className()
                + "();response.setName(normalize.apply(request.getNeId()));return response;}"));
    Files.writeString(impl, unit.toString());
    compile();
    var r = check();
    assertEquals(0, r.exitCode(), r.findings().toString());
    assertEquals("REVIEW_REQUIRED", r.status());
    assertEquals(
        "REVIEW_REQUIRED",
        r.rules().stream().filter(x -> x.id().equals("A08")).findFirst().orElseThrow().status());
    assertEquals(1, AtomicTool.check(SourceIndex.scan(root), c, config, true).exitCode());
  }

  @Test
  void genericBaseInterfacesCompareByTypeNotWhitespace() throws Exception {
    fixture("interface");
    Path profile = root.resolve(".atomic-api/atomic-profile.yaml");
    Files.writeString(
        root.resolve("api-atomic-common/src/main/java/com/example/framework/AtomicBase.java"),
        "package com.example.framework;public interface AtomicBase<A,B>{}");
    Files.writeString(
        profile,
        Files.readString(profile)
            .replace(
                "baseInterfaces: []",
                "baseInterfaces:"
                    + " ['com.example.framework.AtomicBase<java.lang.String,java.lang.String>']"));
    config = AtomicContract.Config.load(profile);
    Files.writeString(
        contract,
        Files.readString(contract)
            .replace(
                "type: java.lang.String",
                "type: 'java.util.Map<java.lang.String,java.lang.String>'"));
    generate();
    compile();
    assertFalse(
        check().findings().stream().anyMatch(f -> f.rule().equals("A02")),
        check().findings().toString());
    Path api = source(load().apiInterface());
    var unit = com.github.javaparser.StaticJavaParser.parse(Files.readString(api));
    unit.getInterfaceByName(Contract.simple(load().apiInterface()))
        .orElseThrow()
        .getExtendedTypes()
        .clear();
    Files.writeString(api, unit.toString());
    assertTrue(
        check().findings().stream()
            .anyMatch(f -> f.rule().equals("A02") && f.message().contains("base interfaces")));
  }

  @Test
  void atomicHelperAlsoHasSinglePublicEntryIncludingInheritedMethods() throws Exception {
    fixture("interface");
    generate();
    var version = source(load().releases().get(0).implementation());
    Path helper = version.getParent().resolve("SessionHelper.java");
    Files.writeString(
        helper,
        "package "
            + Contract.packageName(load().releases().get(0).implementation())
            + "; class Parent {public void inherited(){}} public class SessionHelper extends Parent"
            + " {public void run(){}}");
    assertTrue(
        check().findings().stream()
            .anyMatch(f -> f.rule().equals("A06") && f.message().contains("exactly one public")));
    Files.writeString(helper, Files.readString(helper).replace("extends Parent", ""));
    assertFalse(
        check().findings().stream().anyMatch(f -> f.rule().equals("A06")),
        check().findings().toString());
  }
}
