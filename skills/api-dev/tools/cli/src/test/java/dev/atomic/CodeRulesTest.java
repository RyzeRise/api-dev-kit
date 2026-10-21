package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeRulesTest {
  @TempDir Path root;
  StrictWorkflowTest f;

  void setup() throws Exception {
    f = new StrictWorkflowTest();
    f.root = root;
    f.setup(true);
  }

  List<Checker.Finding> findings() throws Exception {
    return new CodeRules(SourceIndex.scan(root), f.c, f.p).check();
  }

  void error(String id) throws Exception {
    assertTrue(
        findings().stream().anyMatch(x -> x.rule().equals(id) && x.severity().equals("ERROR")),
        findings().toString());
  }

  @Test
  void helperSinglePublicMethodAndConcreteComponent() throws Exception {
    setup();
    String name = Contract.packageName(f.c.target().implementation()) + ".PortHelper";
    Fixtures.write(
        root,
        name.replace('.', '/'),
        "package "
            + Contract.packageName(name)
            + "; @com.example.framework.Component public class PortHelper {public void run(){}"
            + " private void detail(){} }");
    assertTrue(findings().stream().noneMatch(x -> Set.of("N11", "N12").contains(x.rule())));
    f.edit(name, s -> s.replace("private void detail", "public void detail"));
    error("N11");
    f.edit(name, s -> s.replace("@com.example.framework.Component", ""));
    error("N12");
  }

  @Test
  void helperOverloadsAndStaticPublicMethodsAlsoCount() throws Exception {
    setup();
    String name = Contract.packageName(f.c.target().implementation()) + ".PortHelper";
    Fixtures.write(
        root,
        name.replace('.', '/'),
        "package "
            + Contract.packageName(name)
            + "; @com.example.framework.Component public class PortHelper {public void run(){}"
            + " public static void run(int x){} }");
    error("N11");
  }

  @Test
  void unrelatedHistoricalHelperIsNotSweptIntoOperation() throws Exception {
    setup();
    Fixtures.write(
        root,
        "com/example/unrelated/BadHelper",
        "package com.example.unrelated;public class BadHelper {public void a(){}public void"
            + " b(){}}");
    assertTrue(findings().stream().noneMatch(x -> x.rule().equals("N11")));
  }

  @Test
  void handlerComponentAndFacadeRegistrationCannotBeRemoved() throws Exception {
    setup();
    f.edit(f.c.steps().get(0).handler(), s -> s.replace("@Component", ""));
    error("N12");
    f.edit(f.c.target().implementation(), s -> s.replace("@OpsFacadeInterface", ""));
    error("N10");
  }

  @Test
  void configuredFacadeDefaultCannotSilentlyReturnNull() throws Exception {
    setup();
    f.edit(
        f.c.target().facade(),
        s -> s.replaceAll("throw new OpsApiException\\([^;]+;", "return null;"));
    error("N10");
  }

  @Test
  void nestedResponseNodeRequiresPublicStatic() throws Exception {
    setup();
    f.edit(f.c.models().response().className(), s -> s.replaceFirst("\\{", "{ class Node {}"));
    error("N16");
    f.edit(
        f.c.models().response().className(),
        s -> s.replace("class Node", "public static class Node"));
    assertTrue(findings().stream().noneMatch(x -> x.rule().equals("N16")));
  }

  @Test
  void chineseExceptionAndLocaleDefaultAreRejected() throws Exception {
    setup();
    f.edit(f.c.steps().get(0).handler(), s -> s.replace("atomic response is missing", "响应为空"));
    error("N15");
    f.edit(
        f.c.steps().get(0).handler(),
        s -> s.replace("\"响应为空\"", "String.format(\"operation failed %s\", context)"));
    error("N15");
  }

  @Test
  void commentLanguageIsCheckedWithoutClaimingCommentMeaning() throws Exception {
    setup();
    f.edit(
        f.c.steps().get(0).handler(),
        s -> s.replaceAll("(?m)//[^\\n]*", "").replaceAll("(?s)/\\*\\*.*?\\*/", ""));
    assertTrue(
        findings().stream().anyMatch(x -> x.rule().equals("N14") && x.severity().equals("ERROR")));
  }

  @Test
  void sensitiveDiagnosticFieldIsRejected() throws Exception {
    setup();
    var data = (Map<String, Object>) Contract.yaml(f.contract);
    var models = (Map<String, Object>) data.get("models");
    var request = (Map<String, Object>) models.get("request");
    request.put(
        "fields",
        List.of(
            Map.of(
                "name",
                "password",
                "type",
                "java.lang.String",
                "description",
                "密码",
                "diagnostic",
                true)));
    Files.writeString(f.contract, new org.yaml.snakeyaml.Yaml().dump(data));
    f.c = Contract.load(f.contract);
    error("N24");
  }

  @Test
  void reviewRulesReportEvidenceAndNeverPass() throws Exception {
    setup();
    var report = new Checker(f.p).check(SourceIndex.scan(root), f.c);
    for (String id : List.of("N13", "N17", "N19", "N20", "N21", "N22")) {
      var result = report.rules().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
      assertEquals("REVIEW_REQUIRED", result.status(), id);
      assertFalse(result.evidence().isEmpty(), id);
    }
    assertEquals(24, report.rules().size());
  }

  @Test
  void failedStructuralRuleIsMappedToNormativeIdentifier() throws Exception {
    // An explicit canonical failure maps to its documented rule, independently of text formatting.
    var mapped =
        RuleCatalog.results(
            List.of(new Checker.Finding("ERROR", "STRICT-003", "file", 1, "bad mapping")),
            true,
            Map.of());
    assertEquals(
        "FAIL",
        mapped.stream().filter(x -> x.id().equals("N03")).findFirst().orElseThrow().status());
    assertTrue(
        RuleCatalog.results(List.of(), false, Map.of()).stream()
            .allMatch(x -> x.status().equals("NOT_EVALUATED")));
  }

  void loggedGeneration() throws Exception {
    setup();
    Fixtures.write(
        root,
        "com/example/log/ProjectLogger",
        "package com.example.log; public class ProjectLogger {public void info(String message){} public static ProjectLogger getLogger(Class<?> c){return new"
            + " ProjectLogger();}}");
    f.p =
        new Profile(
            f.p.version(),
            f.p.targetJava(),
            f.p.types(),
            f.p.facadeDefault(),
            f.p.layout(),
            f.p.disabledFacade(),
            f.p.roles(),
            new Profile.Logging(
                "com.example.log.ProjectLogger", "com.example.log.ProjectLogger", "getLogger"));
    for (String name : Conventions.scope(f.c)) {
      Path file = f.source(name);
      assertTrue(file.toRealPath().startsWith(root.toRealPath()));
      Files.delete(file);
    }
    new Generator().plan(root, f.c, f.p).apply();
  }

  @Test
  void generationDoesNotAddDuplicateLoggingAndCompiles() throws Exception {
    loggedGeneration();
    var list = findings();
    assertTrue(list.stream().noneMatch(x -> x.rule().equals("N13")), list.toString());
    assertTrue(new Generator().plan(root, f.c, f.p).changes().isEmpty());
    String handler = Files.readString(f.source(f.c.steps().get(0).handler()));
    assertFalse(handler.contains("LOGGER"), handler);
    assertFalse(handler.contains("catch ("));
    assertFalse(handler.contains("finally"));
    Path output = root.resolve("compiled");
    Files.createDirectories(output);
    var args = new ArrayList<>(List.of("--release", "8", "-d", output.toString()));
    try (var paths = Files.walk(root.resolve("src/main/java"))) {
      paths.filter(x -> x.toString().endsWith(".java")).forEach(x -> args.add(x.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    var report = new Checker(f.p).check(SourceIndex.scan(root), f.c);
    assertTrue(
        report.findings().stream()
            .noneMatch(x -> x.rule().equals("N13") || x.rule().equals("STRICT-003")),
        report.toString());
  }

  @Test
  void projectLoggerDoesNotNeedEntryRetOrPerMethodCalls() throws Exception {
    loggedGeneration();
    // A project logger with info(String) is sufficient metadata; shared coverage is reviewed.
    var report = new Checker(f.p).check(SourceIndex.scan(root), f.c);
    assertTrue(report.findings().stream().noneMatch(x -> x.rule().equals("N13")), report.toString());
    var result = report.rules().stream().filter(x -> x.id().equals("N13")).findFirst().orElseThrow();
    assertEquals("REVIEW_REQUIRED", result.status());
    assertTrue(result.evidence().stream().anyMatch(x -> x.contains("unified")), result.toString());
  }

  @Test
  void legacyLoggingMetadataDoesNotImposeFactorySignature() throws Exception {
    setup();
    Fixtures.write(
        root,
        "com/example/log/ProjectLogger",
        "package com.example.log;public class ProjectLogger {public void info(String message){}"
            + "public static ProjectLogger create(int x){return new ProjectLogger();}}");
    var p = new Profile(f.p.version(), f.p.targetJava(), f.p.types(), f.p.facadeDefault(),
        f.p.layout(), null, f.p.roles(), new Profile.Logging(
            "com.example.log.ProjectLogger", "com.example.log.ProjectLogger", "create"));
    for (String name : Conventions.scope(f.c)) Files.delete(f.source(name));
    new Generator().plan(root, f.c, p).apply();
    assertFalse(Files.readString(f.source(f.c.steps().get(0).handler())).contains("LOGGER"));
  }

  @Test
  void inheritedPublicEntrypointsAreCountedAndOverridesNotDuplicated() throws Exception {
    setup();
    String pkg = Contract.packageName(f.c.target().implementation());
    String name = pkg + ".PortHelper";
    Fixtures.write(
        root,
        (pkg + ".Parent").replace('.', '/'),
        "package " + pkg + "; public class Parent {public void run(){}}");
    Fixtures.write(
        root,
        name.replace('.', '/'),
        "package "
            + pkg
            + ";@com.example.framework.Component public class PortHelper extends Parent {public"
            + " void run(){}}");
    assertTrue(findings().stream().noneMatch(x -> x.rule().equals("N11")), findings().toString());
    f.edit(name, s -> s.replace("public void run", "public void another"));
    error("N11");
  }

  @Test
  void mapstructStrictTargetPolicyAndInstanceAreChecked() throws Exception {
    setup();
    Fixtures.write(
        root,
        "org/mapstruct/Mapper",
        "package org.mapstruct;public @interface Mapper {String unmappedTargetPolicy() default"
            + " \"IGNORE\";}");
    Fixtures.write(
        root,
        "org/mapstruct/ReportingPolicy",
        "package org.mapstruct;public class ReportingPolicy {public static final String"
            + " ERROR=\"ERROR\";}");
    String mapper = f.c.target().responseConverter();
    f.edit(mapper, s -> s.replace("public interface", "@org.mapstruct.Mapper public interface"));
    error("N18");
    f.edit(
        mapper,
        s ->
            s.replace(
                "@org.mapstruct.Mapper",
                "@org.mapstruct.Mapper(unmappedTargetPolicy=org.mapstruct.ReportingPolicy.ERROR)"));
    assertTrue(findings().stream().noneMatch(x -> x.rule().equals("N18")), findings().toString());
  }

  @Test
  void customizedProjectLogsArePreservedOnRegenerationConflict() throws Exception {
    loggedGeneration();
    String handler = f.c.steps().get(0).handler();
    f.edit(handler, s -> s.replace("Context ctx = new Context();",
        "com.example.log.ProjectLogger.getLogger(getClass()).info(\"request\");"
            + "Context ctx = new Context();"));
    String before = Files.readString(f.source(handler));
    assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, f.c, f.p));
    assertEquals(before, Files.readString(f.source(handler)));
    assertTrue(findings().stream().noneMatch(x -> x.rule().equals("N13")));
  }

  @Test
  void sensitiveDiagnosticIsRejectedBeforeGenerationWrites() throws Exception {
    setup();
    var data = (Map<String, Object>) Contract.yaml(f.contract);
    var models = (Map<String, Object>) data.get("models");
    var request = (Map<String, Object>) models.get("request");
    request.put(
        "fields",
        List.of(
            Map.of(
                "name",
                "accessToken",
                "type",
                "java.lang.String",
                "description",
                "令牌",
                "diagnostic",
                true)));
    Files.writeString(f.contract, new org.yaml.snakeyaml.Yaml().dump(data));
    var c = Contract.load(f.contract);
    var error =
        assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, c, f.p));
    assertTrue(error.getMessage().contains("N24-日志隐私与版权"));
  }

  @Test
  void atomicEvidenceDoesNotDumpUnrelatedCapabilities() throws Exception {
    setup();
    Fixtures.write(
        root,
        "com/example/unrelated/AtomicApiUnrelatedLatest",
        "package com.example.unrelated;public class AtomicApiUnrelatedLatest {}");
    var checker = new CodeRules(SourceIndex.scan(root), f.c, f.p);
    checker.check();
    assertTrue(
        checker.evidence.get("N19").stream()
            .noneMatch(x -> x.contains("AtomicApiUnrelatedLatest")));
  }
}
