package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationTest {
  @TempDir Path root;

  Contract contract(boolean multi) throws Exception {
    return Contract.load(
        Path.of(multi ? "src/main/resources/demo/contracts/multi-handler.yaml" : "src/main/resources/demo/contracts/single-handler.yaml"));
  }

  @Test
  void separateResponseConverterCompilesChecksAndReruns() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = Contract.load(Path.of("src/main/resources/demo/contracts/fault-response.yaml"));
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    compileAll();
    var index = SourceIndex.scan(root);
    String delegate = Files.readString(index.path(index.type(c.target().delegate())));
    String converter = Files.readString(index.path(index.type(c.target().responseConverter())));
    assertTrue(delegate.contains("ResourceResponseConverter.INSTANCE.toQueryResourceByAddressResponse(result)"), delegate);
    assertFalse(delegate.contains("builderQueryResourceByAddressRsp"), delegate);
    assertTrue(delegate.contains(".initCause(e)"), delegate);
    assertTrue(delegate.contains("转换业务请求"), delegate);
    assertTrue(converter.contains("result == null"), converter);
    assertTrue(new Generator().plan(root, c, p).changes().isEmpty());
    var report = new Checker(p).check(index, c);
    assertEquals(0, report.exitCode(false), report.toString());
    // Exception from response adaptation stays in the same boundary as the Facade invocation.
    assertTrue(delegate.indexOf("try {") < delegate.indexOf("ResourceResponseConverter.INSTANCE"));
  }

  @Test
  void nullFirstShortCircuitGuardIsAcceptedButAndGuardIsNot() throws Exception {
    Fixtures.framework(root); Fixtures.external(root, false);
    var c = Contract.load(Path.of("src/main/resources/demo/contracts/fault-response.yaml"));
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    var index = SourceIndex.scan(root);
    Path path = index.path(index.type(c.target().responseConverter()));
    String original = Files.readString(path);
    assertTrue(original.contains("result == null"));
    Files.writeString(path, original.replace("result == null", "(result == null || result.getResourceName() == null)"));
    var report = new Checker(p).check(SourceIndex.scan(root), c);
    assertFalse(report.findings().stream().anyMatch(f -> f.rule().equals("API-009")), report.toString());
    assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("API-008")), report.toString());
    Files.writeString(path, original.replace("result == null", "result == null && false"));
    report = new Checker(p).check(SourceIndex.scan(root), c);
    assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("API-009") && f.severity().equals("ERROR")), report.toString());
  }

  Profile registrationProfile() throws Exception {
    Path file=Fixtures.profile(root);
    Files.writeString(file, Files.readString(file)+"\ndisabledFacade:\n  configuration: com.example.sdk.DisabledConfiguration\n  mapField: disableMap\n");
    return Profile.load(file);
  }

  void registrationFixture(boolean existing) throws Exception {
    Fixtures.write(root,"com/example/sdk/DisabledConfiguration",
        "package com.example.sdk;\nimport java.util.Map;\nimport java.util.HashMap;\npublic class DisabledConfiguration {\n"
        + "    private final Map<Class<?>,Object> disableMap = new HashMap<>();\n"
        + "    public DisabledConfiguration() {\n"
        + (existing ? "        disableMap.put(com.example.sdk.ResourceFacade.class, new com.example.sdk.ResourceFacade() {});\n" : "")
        + "    }\n    public int original() { return 7; }\n}\n");
  }

  @Test
  void disabledRegistrationUsesImportsAndIsIdempotent() throws Exception {
    Fixtures.framework(root); Fixtures.external(root,false); registrationFixture(false);
    var c=contract(false); var p=registrationProfile();
    new Generator().plan(root,c,p).apply();
    var index=SourceIndex.scan(root); var owner=index.type("com.example.sdk.DisabledConfiguration");
    String text=Files.readString(index.path(owner));
    assertTrue(text.contains("disableMap.put(ResourceFacade.class, new ResourceFacade()"), text);
    assertTrue(text.contains("public int original() { return 7; }"), text);
    compileAll(); assertTrue(new Generator().plan(root,c,p).changes().isEmpty());
  }

  @Test
  void existingQualifiedDisabledRegistrationIsShortenedWithoutDuplication() throws Exception {
    Fixtures.framework(root); Fixtures.external(root,false); registrationFixture(true);
    var c=contract(false); var p=registrationProfile();
    new Generator().plan(root,c,p).apply();
    var index=SourceIndex.scan(root); var owner=index.type("com.example.sdk.DisabledConfiguration");
    assertEquals(1,owner.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream().filter(m->m.getNameAsString().equals("put")).count());
    assertFalse(owner.toString().contains("com.example.sdk.ResourceFacade"), owner.toString());
    compileAll(); assertTrue(new Generator().plan(root,c,p).changes().isEmpty());
  }

  @Test
  void customizedDisabledImplementationIsNeverOverwritten() throws Exception {
    Fixtures.framework(root); Fixtures.external(root,false); registrationFixture(true);
    Path file=root.resolve("src/main/java/com/example/sdk/DisabledConfiguration.java");
    String customized=Files.readString(file).replace("new com.example.sdk.ResourceFacade() {}",
        "new com.example.sdk.ResourceFacade() { public String marker() { return \"keep\"; } }");
    Files.writeString(file,customized);
    var p=registrationProfile();
    var error=assertThrows(IllegalArgumentException.class,()->new Generator().plan(root,contract(false),p));
    assertTrue(error.getMessage().contains("customized"),error.toString());
    assertEquals(customized,Files.readString(file));
  }

  @Test
  void generatedImportsJoinTheirExistingGroups() throws Exception {
    Fixtures.framework(root); Fixtures.external(root, false);
    Fixtures.write(root, "com/example/service/ResourceDelegateImpl",
        "package com.example.service;\n\nimport com.example.framework.OpsComponentManagement;\n\nimport java.util.Objects;\n\n// keep class comment\npublic class ResourceDelegateImpl { public int original() { return 7; } }\n");
    var c = contract(false); var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root,c,p).apply();
    var index = SourceIndex.scan(root);
    String text = Files.readString(index.path(index.type(c.target().delegate())));
    assertTrue(text.indexOf("import " + c.target().converter()) < text.indexOf("import java.util.Objects"), text);
    assertTrue(text.contains("import java.util.Locale;\nimport java.util.Objects;"), text);
    assertTrue(text.contains("// keep class comment"), text);
    assertTrue(text.contains("public int original() { return 7; }"), text);
    compileAll(); assertTrue(new Generator().plan(root,c,p).changes().isEmpty());
  }

  @Test
  void existingGeneratedImportsAreReorderedWithoutChangingMethods() throws Exception {
    Fixtures.framework(root); Fixtures.external(root, false);
    var c=contract(false); var p=Profile.load(Fixtures.profile(root));
    new Generator().plan(root,c,p).apply();
    var index=SourceIndex.scan(root); Path file=index.path(index.type(c.target().delegate()));
    String original=Files.readString(file);
    String line="import "+c.target().converter()+";";
    String shuffled=original.replace(line+"\n", "").replace("public class ResourceDelegateImpl", line+"\n\npublic class ResourceDelegateImpl");
    Files.writeString(file,shuffled);
    new Generator().plan(root,c,p).apply();
    assertEquals(original,Files.readString(file));
  }

  @Test
  void singleFacadeMatchesLdpReference() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = contract(false);
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    var index = SourceIndex.scan(root);
    var owner = index.type(c.target().implementation());
    var body = owner.getMethodsByName(c.operation()).get(0).getBody().orElseThrow();
    assertEquals(1, body.getStatements().size(), body.toString());
    assertTrue(body.getStatement(0).isReturnStmt(), body.toString());
    for (var fqn : List.of(c.target().implementation(), c.steps().get(0).handler(), c.models().request().className())) {
      var type = index.type(fqn);
      for (var t : type.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
        assertFalse(t.getNameWithScope().startsWith("com.example."), type.toString());
      for (var annotation : type.findAll(com.github.javaparser.ast.expr.AnnotationExpr.class))
        assertFalse(annotation.getNameAsString().contains("."), type.toString());
    }
    compileAll();
    assertTrue(new Generator().plan(root, c, p).changes().isEmpty());
  }

  @Test
  void multiFacadeKeepsOrderedHandlersAndBuilderWithShortTypes() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, true);
    var c = contract(true);
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    var index = SourceIndex.scan(root);
    var owner = index.type(c.target().implementation());
    var calls = owner.getMethodsByName(c.operation()).get(0)
        .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
        .map(m -> m.getNameAsString()).filter(n -> n.equals("run") || n.equals("build")).toList();
    assertEquals(List.of("run", "run", "build"), calls);
    for (var t : owner.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class))
      assertFalse(t.getNameWithScope().startsWith("com.example."), owner.toString());
    compileAll();
    assertTrue(new Generator().plan(root, c, p).changes().isEmpty());
  }

  @Test
  void generatesExplicitNullFallbackAndKeepsBusinessPlaceholder() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var original = Profile.load(Fixtures.profile(root));
    var profile = new Profile(1, 21, original.types(), "return-null", original.layout());
    new Generator().plan(root, contract(false), profile).apply();
    var index = SourceIndex.scan(root);
    var method = index.type(contract(false).target().facade()).getMethodsByName(contract(false).operation()).get(0);
    assertTrue(method.getBody().orElseThrow().toString().contains("return null;"));
    assertFalse(method.getBody().orElseThrow().toString().contains("throw"));
    assertTrue(index.type(contract(false).steps().get(0).handler()).toString().contains("ATOMIC_API_UNIMPLEMENTED"));
  }

  @Test
  void insertedDelegateUsesImportsAndReadableStatements() throws Exception {
    Fixtures.write(root, "com/example/service/ResourceDelegateImpl",
        "package com.example.service;\n// keep this comment\npublic class ResourceDelegateImpl {\n    public int original() { return 7; }\n}\n");
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = contract(false);
    var profile = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, profile).apply();
    var index = SourceIndex.scan(root);
    var owner = index.type(c.target().delegate());
    var text = Files.readString(index.path(owner));
    assertTrue(text.contains("import " + c.target().facade() + ";"), text);
    assertTrue(text.contains("import " + c.target().converter() + ";"), text);
    var method = owner.getMethodsByName(c.operation()).get(0);
    assertFalse(method.toString().contains(c.target().facade()), method.toString());
    assertTrue(text.contains("try {\n"), text);
    assertFalse(text.contains(";return"), text);
    assertFalse(text.contains(";}catch"), text);
    assertTrue(text.contains("\n        try {\n"), text);
    assertTrue(text.contains("\n            return builder"), text);
    assertTrue(text.contains("// keep this comment"), text);
    assertTrue(text.contains("public int original() { return 7; }"), text);
    compileAll();
    assertTrue(new Generator().plan(root, c, profile).changes().isEmpty());
  }

  @Test
  void generatedImportsKeepConflictingSimpleNamesQualified() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Fixtures.write(root, "other/ResourceFacade", "package other; public interface ResourceFacade {}");
    var c = contract(false);
    var delegate = root.resolve("src/main/java/com/example/service/ResourceDelegateImpl.java");
    Fixtures.write(root, "com/example/service/ResourceDelegateImpl",
        "package com.example.service;\nimport other.ResourceFacade;\npublic class ResourceDelegateImpl {}\n");
    var profile = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, profile).apply();
    var text = Files.readString(delegate);
    assertTrue(text.contains("import other.ResourceFacade;"));
    assertFalse(text.contains("import " + c.target().facade() + ";"));
    assertTrue(text.contains(c.target().facade() + " facade"), text);
    compileAll();
    assertTrue(new Generator().plan(root, c, profile).changes().isEmpty());
  }

  @Test
  void generatedHandlerAcceptsFileCopyrightHeaderOnRerun() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = contract(false);
    var profile = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, profile).apply();
    var index = SourceIndex.scan(root);
    var handler = index.path(index.type(c.steps().get(0).handler()));
    Files.writeString(handler, "/*\n * Copyright (c) Example 2026. All rights reserved.\n */\n\n" + Files.readString(handler));
    assertTrue(new Generator().plan(root, c, profile).changes().isEmpty());
  }

  @Test
  void singleEndToEndAndIdempotency() throws Exception {
    generateAndCompile(false);
  }

  @Test
  void multiEndToEndAndIdempotency() throws Exception {
    generateAndCompile(true);
  }

  void generateAndCompile(boolean multi) throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, multi);
    Profile profile = Profile.load(Fixtures.profile(root));
    var plan = new Generator().plan(root, contract(multi), profile);
    assertFalse(plan.changes().isEmpty());
    assertFalse(Files.exists(root.resolve("function-sdk")));
    plan.apply();
    List<String> args =
        new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    var index = SourceIndex.scan(root);
    var report = new Checker().check(index, contract(multi));
    assertTrue(report.discoveredMethods() > 0);
    assertEquals("SCAFFOLD", report.readiness());
    assertTrue(
        report.findings().stream().noneMatch(f -> f.severity().equals("ERROR")), report.toString());
    assertTrue(new Generator().plan(root, contract(multi), profile).changes().isEmpty());
  }

  @Test
  void stalePlanDoesNotOverwriteUserChanges() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var plan = new Generator().plan(root, contract(false), Profile.load(Fixtures.profile(root)));
    assertFalse(plan.changes().isEmpty());
    var change = plan.changes().get(0);
    Files.createDirectories(change.path().getParent());
    Files.writeString(change.path(), "user content");
    assertThrows(Exception.class, plan::apply);
    assertEquals("user content", Files.readString(change.path()));
  }

  @Test
  void editedBusinessCodeConflicts() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = contract(false);
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    var file =
        root.resolve("app/src/main/java/com/example/app/GetResourceByAddressApiHandler.java");
    Files.writeString(
        file,
        Files.readString(file).replace("ATOMIC_API_UNIMPLEMENTED:", "business implementation:"));
    assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, c, p));
  }

  @Test
  void rejectsSymlinkTarget() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path outside = Files.createTempDirectory("atomic-api-outside-");
    try {
      Files.createSymbolicLink(root.resolve("function-sdk"), outside);
    } catch (java.nio.file.FileSystemException e) {
      org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"), "Windows symlink privilege unavailable: " + e.getReason());
      throw e;
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new Generator().plan(root, contract(false), Profile.load(Fixtures.profile(root))));
  }

  @Test
  void generatesOverrideForExternalInterfaceWithContext() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Fixtures.write(
        root,
        "com/example/service/ExternalApi",
        "package com.example.service; public interface ExternalApi"
            + " {com.example.model.QueryResourceByAddressRsp queryResourceByAddress(Object context,"
            + " String address) throws com.example.framework.ServiceException;}");
    Fixtures.write(
        root,
        "com/example/service/ResourceDelegateImpl",
        "package com.example.service; public class ResourceDelegateImpl implements ExternalApi {}");
    var plan = new Generator().plan(root, contract(false), Profile.load(Fixtures.profile(root)));
    plan.apply();
    String code =
        Files.readString(
            root.resolve("src/main/java/com/example/service/ResourceDelegateImpl.java"));
    assertTrue(code.contains("context"));
    List<String> args =
        new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
  }

  @Test
  void checkerRejectsMissingResultNullGuard() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    var c = contract(false);
    new Generator().plan(root, c, Profile.load(Fixtures.profile(root))).apply();
    Path file = root.resolve("src/main/java/com/example/service/ResourceDelegateImpl.java");
    var ast = com.github.javaparser.StaticJavaParser.parse(Files.readString(file));
    ast.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
        .filter(m -> m.getNameAsString().startsWith("builder"))
        .forEach(m -> m.getBody().orElseThrow().getStatement(0).remove());
    Files.writeString(file, ast.toString());
    assertTrue(
        new Checker()
            .check(SourceIndex.scan(root), null).findings().stream()
                .anyMatch(f -> f.rule().equals("API-009") && f.severity().equals("ERROR")));
  }

  @Test
  void requestFieldNamedRequestDoesNotCollideWithLocals() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path input = root.resolve("api.yaml");
    Files.writeString(
        input,
        Files.readString(Path.of("src/main/resources/demo/contracts/single-handler.yaml"))
            .replace("name: address", "name: request"));
    var plan =
        new Generator().plan(root, Contract.load(input), Profile.load(Fixtures.profile(root)));
    plan.apply();
    List<String> args =
        new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
  }

  @Test
  void preservesBomCrLfAndExistingComments() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path facade =
        Fixtures.write(
            root,
            "com/example/sdk/ResourceFacade",
            "\uFEFFpackage com.example.sdk;\r\n"
                + "// preserve this comment\r\n"
                + "public interface ResourceFacade {}\r\n");
    var c = contract(false);
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    String result = Files.readString(facade);
    assertTrue(result.startsWith("\uFEFF"));
    assertTrue(result.contains("// preserve this comment\r\n"));
    assertFalse(result.replace("\r\n", "").contains("\n"));
    assertTrue(new Generator().plan(root, c, p).changes().isEmpty());
  }

  @Test
  void contractDetectsExtraHandlerInvocation() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, true);
    var c = contract(true);
    new Generator().plan(root, c, Profile.load(Fixtures.profile(root))).apply();
    Path file = root.resolve("src/main/java/com/example/app/ResourceFacadeImpl.java");
    var ast = com.github.javaparser.StaticJavaParser.parse(Files.readString(file));
    var method = ast.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).get(0);
    var run =
        method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
            .filter(m -> m.getNameAsString().equals("run"))
            .findFirst()
            .orElseThrow();
    method
        .getBody()
        .orElseThrow()
        .addStatement(0, new com.github.javaparser.ast.stmt.ExpressionStmt(run.clone()));
    Files.writeString(file, ast.toString());
    assertTrue(
        new Checker()
            .check(SourceIndex.scan(root), c).findings().stream()
                .anyMatch(f -> f.rule().equals("CONTRACT") && f.severity().equals("ERROR")));
  }

  @Test
  void newTypesSupportPhysicalPackageMapping() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path path = Fixtures.profile(root);
    Files.writeString(path, Files.readString(path) + "    directoryPrefix: physical/shared\n");
    Profile profile = assertDoesNotThrow(() -> Profile.load(path));
    new Generator().plan(root, contract(false), profile).apply();
    assertTrue(Files.exists(root.resolve("src/main/java/physical/shared/sdk/ResourceFacade.java")));
    assertTrue(SourceIndex.scan(root).types().containsKey("com.example.sdk.ResourceFacade"));
  }

  @Test
  void includesExplicitDiagnosticIdentifiers() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path file = root.resolve("contract.yaml");
    Files.writeString(file, Files.readString(Path.of("src/main/resources/demo/contracts/single-handler.yaml")));
    var c = assertDoesNotThrow(() -> Contract.load(file));
    new Generator().plan(root, c, Profile.load(Fixtures.profile(root))).apply();
    String source =
        Files.readString(
            root.resolve("src/main/java/com/example/service/ResourceDelegateImpl.java"));
    assertTrue(source.contains("address: %s"));
  }

  void compileAll() throws Exception {
    List<String> args =
        new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
  }

  @Test
  void existingModelAccessorsAreUsedConsistently() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Fixtures.write(
        root,
        "com/example/sdk/QueryResourceByAddressReqDto",
        "package com.example.sdk; public class QueryResourceByAddressReqDto {private String"
            + " address;public String address(){return address;} public void setAddress(String"
            + " v){address=v;}}");
    Fixtures.write(
        root,
        "com/example/sdk/QueryResourceByAddressResultDto",
        "package com.example.sdk; public class QueryResourceByAddressResultDto {private String"
            + " resourceName;public String resourceName(){return resourceName;} public void"
            + " setResourceName(String v){resourceName=v;}}");
    new Generator().plan(root, contract(false), Profile.load(Fixtures.profile(root))).apply();
    compileAll();
  }

  @Test
  void preservesOtherInterfacesAndAddsFacade() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Fixtures.write(
        root, "com/example/app/Marker", "package com.example.app; public interface Marker {}");
    Path impl =
        Fixtures.write(
            root,
            "com/example/app/ResourceFacadeImpl",
            "package com.example.app; public class ResourceFacadeImpl implements Marker {}");
    var c = contract(false);
    var p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, c, p).apply();
    compileAll();
    assertTrue(Files.readString(impl).contains("Marker"));
    assertTrue(new Generator().plan(root, c, p).changes().isEmpty());
  }

  @Test
  void deferredHandlerAndNullGuardDoNotCount() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Profile p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, contract(false), p).apply();
    Path impl = root.resolve("src/main/java/com/example/app/ResourceFacadeImpl.java");
    String code = Files.readString(impl);
    assertTrue(code.contains("return resourceApiHandler.run(request);"));
    Files.writeString(
        impl,
        code.replace(
            "return resourceApiHandler.run(request);",
            "java.util.concurrent.Callable<com.example.sdk.QueryResourceByAddressResultDto>"
                + " delayed = () -> resourceApiHandler.run(request); return null;"));
    Path delegate = root.resolve("src/main/java/com/example/service/ResourceDelegateImpl.java");
    code = Files.readString(delegate);
    assertTrue(
        code.contains("throw new OpsApiException(\"result is empty\");"));
    Files.writeString(
        delegate,
        code.replace(
            "throw new OpsApiException(\"result is empty\");",
            "java.util.concurrent.Callable<Object> delayed = () -> {throw new"
                + " com.example.framework.OpsApiException(\"result is empty\");};"));
    compileAll();
    var report = new Checker(p).check(SourceIndex.scan(root), contract(false));
    for (String rule : List.of("API-004", "API-009", "CONTRACT"))
      assertTrue(
          report.findings().stream()
              .anyMatch(f -> f.rule().equals(rule) && f.severity().equals("ERROR")),
          report.toString());
  }

  @Test
  void explicitProfileRejectsSameSimpleNameException() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Profile p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, contract(false), p).apply();
    Fixtures.write(
        root,
        "com/example/other/OpsApiException",
        "package com.example.other; public class OpsApiException extends Exception {public"
            + " OpsApiException(String s){super(s);} public String getErrMsg(){return"
            + " getMessage();}}");
    try (var walk = Files.walk(root)) {
      for (Path file : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
        if (file.toString().replace('\\', '/').endsWith("framework/OpsApiException.java")) continue;
        String code =
            Files.readString(file)
                .replace(
                    "com.example.framework.OpsApiException", "com.example.other.OpsApiException");
        if (file.toString().replace('\\', '/').endsWith("framework/ApiHandler.java"))
          code = code.replace("throws OpsApiException", "throws com.example.other.OpsApiException");
        Files.writeString(file, code);
      }
    }
    compileAll();
    var report = new Checker(p).check(SourceIndex.scan(root), null);
    assertTrue(
        report.findings().stream()
            .anyMatch(f -> f.rule().equals("API-006") && f.severity().equals("ERROR")),
        report.toString());
  }

  @Test
  void sequentialApisCompileAndRerunWithoutFieldConflicts() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Fixtures.external(root, true);
    Profile p = Profile.load(Fixtures.profile(root));
    new Generator().plan(root, contract(false), p).apply();
    new Generator().plan(root, contract(true), p).apply();
    compileAll();
    assertTrue(new Generator().plan(root, contract(false), p).changes().isEmpty());
    assertTrue(new Generator().plan(root, contract(true), p).changes().isEmpty());
  }
}
