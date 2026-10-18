package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.StaticJavaParser;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class StrictWorkflowTest {
  @TempDir Path root;
  boolean fieldBinding;
  Profile p;
  Contract c;
  Path contract;

  void setup(boolean implement) throws Exception {
    setup(implement, null);
  }

  void setup(boolean implement, String errorMode) throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    Path config = Fixtures.profile(root);
    Files.writeString(
        config,
        Files.readString(config)
            + "\n"
            + "roles:\n"
            + "  facade: {packagePrefix: com.example.common.facade, sourceRoot: src/main/java}\n"
            + "  request: {packagePrefix: com.example.common.dto, sourceRoot: src/main/java}\n"
            + "  response: {packagePrefix: com.example.common.dto, sourceRoot: src/main/java}\n"
            + "  implementation: {packagePrefix: com.example.app, sourceRoot: src/main/java}\n"
            + "  handler: {packagePrefix: com.example.app, sourceRoot: src/main/java}\n"
            + "  delegate: {packagePrefix: com.example.service, sourceRoot: src/main/java}\n"
            + "  requestConverter: {packagePrefix: com.example.service.converter, sourceRoot:"
            + " src/main/java}\n"
            + "  responseConverter: {packagePrefix: com.example.service.converter, sourceRoot:"
            + " src/main/java}\n");
    p = Profile.load(config);
    contract = NewContract.create(root, p, "queryResourceByAddress", "resource");
    var data = (Map<String, Object>) Contract.yaml(contract);
    var models = (Map<String, Object>) data.get("models");
    ((Map<String, Object>) models.get("request"))
        .put(
            "fields",
            List.of(
                Map.of(
                    "name",
                    "address",
                    "type",
                    "java.lang.String",
                    "description",
                    "查询地址",
                    "required",
                    true)));
    ((Map<String, Object>) models.get("response"))
        .put(
            "fields",
            List.of(
                Map.of("name", "resourceName", "type", "java.lang.String", "description", "资源名称")));
    if (fieldBinding) {
      ((Map<String, Object>) models.get("request"))
          .put(
              "fields",
              List.of(
                  Map.of("name", "address", "type", "java.lang.String", "description", "目标地址"),
                  Map.of(
                      "name", "otherAddress", "type", "java.lang.String", "description", "另一地址")));
      var step = (Map<String, Object>) ((List<?>) data.get("steps")).get(0);
      step.put("input", "request.address");
      step.put("inputType", "java.lang.String");
    }
    data.put("description", "根据地址查询资源");
    data.put("externalResponse", "com.example.model.QueryResourceByAddressRsp");
    data.put("responseBindings", Map.of("resourceName", "resourceName"));
    if (errorMode != null) {
      data.put("externalResponse", "java.lang.String");
      data.put("responseBindings", Map.of("message", "resourceName"));
      data.put("errorMode", errorMode);
    }
    Files.writeString(contract, new Yaml().dump(data));
    c = Contract.load(contract);
    if ("message".equals(errorMode)) {
      Fixtures.write(
          root,
          "com/example/model/StringApi",
          "package com.example.model; public interface StringApi {String "
              + c.operation()
              + "(String address);}");
      Fixtures.write(
          root,
          c.target().delegate().replace('.', '/'),
          "package "
              + Contract.packageName(c.target().delegate())
              + "; public class "
              + Contract.simple(c.target().delegate())
              + " implements com.example.model.StringApi {}");
    }
    new Generator().plan(root, c, p).apply();
    if (implement) implement();
  }

  Path source(String name) {
    return root.resolve("src/main/java/" + name.replace('.', '/') + ".java");
  }

  void edit(String name, java.util.function.UnaryOperator<String> fn) throws Exception {
    Path file = source(name);
    Files.writeString(file, fn.apply(Files.readString(file)));
  }

  Checker.Report check() throws Exception {
    return new Checker(p).check(SourceIndex.scan(root), c);
  }

  void has(String rule) throws Exception {
    var report = check();
    assertTrue(
        report.findings().stream()
            .anyMatch(
                f -> f.rule().equals(rule) && f.severity().equals("ERROR") && !f.fix().isBlank()),
        report.toString());
    assertNotEquals(0, report.exitCode(false));
  }

  void implement() throws Exception {
    Fixtures.write(
        root,
        "com/example/atomic/ResourceApi",
        "package com.example.atomic; public class ResourceApi { public String get(String"
            + " address){return address;} }");
    Path file = source(c.steps().get(0).handler());
    var unit = StaticJavaParser.parse(Files.readString(file));
    var owner = unit.getClassByName(Contract.simple(c.steps().get(0).handler())).orElseThrow();
    owner.getMembers().stream()
        .filter(
            m ->
                m.isClassOrInterfaceDeclaration()
                    && m.asClassOrInterfaceDeclaration().getNameAsString().equals("Context"))
        .findFirst()
        .orElseThrow()
        .asClassOrInterfaceDeclaration()
        .addField("String", "atomicResponse", com.github.javaparser.ast.Modifier.Keyword.PRIVATE);
    owner
        .getMethodsByName("invokeAtomicApi")
        .get(0)
        .setBody(
            StaticJavaParser.parseBlock(
                "{Context ctx=(Context)context;ctx.atomicResponse=new"
                    + " com.example.atomic.ResourceApi().get(ctx.input.getAddress());return"
                    + " ctx;}"));
    owner
        .getMethodsByName("postHandler")
        .get(0)
        .setBody(
            StaticJavaParser.parseBlock(
                "{Context ctx=(Context)context;if(ctx.atomicResponse==null){throw new"
                    + " com.example.framework.OpsApiException(\"atomic response is"
                    + " missing\");}ctx.result=new "
                    + c.models().response().className()
                    + "();ctx.result.setResourceName(ctx.atomicResponse);return ctx.result;}"));
    Files.writeString(file, unit.toString());
  }

  @Test
  void newDraftRequiresBusinessFactsAndNeverOverwrites() throws Exception {
    setup(false);
    assertEquals(2, c.version());
    assertEquals("resource", c.business());
    assertTrue(c.target().responseConverter().endsWith("resource.ResourceResponseConverter"));
    String original = Files.readString(contract);
    assertThrows(
        IllegalArgumentException.class,
        () -> NewContract.create(root, p, c.operation(), c.business()));
    assertEquals(original, Files.readString(contract));
    Path draft = NewContract.create(root, p, "queryAnother", "resource");
    assertThrows(Exception.class, () -> Contract.load(draft));
    assertThrows(
        IllegalArgumentException.class, () -> NewContract.create(root, p, "../escape", "resource"));
  }

  @Test
  void missingRolesNeverGuessesProjectArchitecture() throws Exception {
    Fixtures.framework(root);
    Profile old = Profile.load(Fixtures.profile(root));
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> NewContract.create(root, old, "queryCircuit", "circuit"));
    assertTrue(error.getMessage().contains("roles"));
    assertFalse(Files.exists(root.resolve(".atomic-api/contracts/queryCircuit.yaml")));
  }

  @Test
  void strictContractCannotOmitResponseConverterOrChangeModelSuffix() throws Exception {
    setup(false);
    var data = (Map<String, Object>) Contract.yaml(contract);
    ((Map<String, Object>) data.get("target")).remove("responseConverter");
    Files.writeString(contract, new Yaml().dump(data));
    assertThrows(Exception.class, () -> Contract.load(contract));
  }

  @Test
  void scaffoldAndTrivialNullAreBlocked() throws Exception {
    setup(false);
    has("STRICT-007");
    Path file = source(c.steps().get(0).handler());
    var unit = StaticJavaParser.parse(Files.readString(file));
    unit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
        .filter(m -> m.getNameAsString().equals("invokeAtomicApi"))
        .findFirst()
        .orElseThrow()
        .setBody(StaticJavaParser.parseBlock("{return null;}"));
    Files.writeString(file, unit.toString());
    has("STRICT-007");
  }

  @Test
  void completedFixtureCompilesAndVerifyExplainsItsLimit() throws Exception {
    setup(true);
    var report = check();
    assertEquals(0, report.exitCode(false), report.toString());
    var args =
        new ArrayList<>(
            List.of(
                "--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var files = Files.walk(root)) {
      files.filter(f -> f.toString().endsWith(".java")).forEach(f -> args.add(f.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int code =
        Cli.run(
            new String[] {
              "verify",
              "--repo",
              root.toString(),
              "--profile",
              root.resolve("framework.yaml").toString(),
              "--contract",
              contract.toString(),
              "--format",
              "json"
            },
            new PrintStream(output),
            System.err);
    assertEquals(0, code, output.toString());
    assertTrue(output.toString().contains("STRUCTURE_READY"));
    assertTrue(output.toString().contains("编译"));
  }

  @Test
  void delegateCannotConstructResponseEvenIfItCallsConverter() throws Exception {
    setup(true);
    edit(
        c.target().delegate(),
        s -> s.replace("try {", "try { new " + c.externalResponse() + "();"));
    has("STRICT-002");
  }

  @Test
  void decoyRequestConverterDoesNotSatisfyBinding() throws Exception {
    setup(true);
    edit(
        c.target().delegate(),
        s ->
            s.replace(
                ".queryResourceByAddress(request)",
                ".queryResourceByAddress(new " + c.models().request().className() + "())"));
    has("STRICT-002");
  }

  @Test
  void convertersCannotCallServicesOrReverseTheirRole() throws Exception {
    setup(true);
    edit(
        c.target().responseConverter(),
        s ->
            s.replace(
                "return response;",
                "new com.example.framework.OpsComponentManagement().getFacadeImpl("
                    + c.target().facade()
                    + ".class); return response;"));
    has("STRICT-003");
  }

  @Test
  void converterCannotHideEffectsInJavaUtilServices() throws Exception {
    setup(true);
    edit(
        c.target().responseConverter(),
        s ->
            s.replace(
                "return response;",
                "java.util.ServiceLoader.load(java.lang.Runnable.class);return response;"));
    has("STRICT-003");
  }

  @Test
  void delegateMustPreserveExceptionCause() throws Exception {
    setup(true);
    edit(c.target().delegate(), s -> s.replace("wrappedException.initCause(e);", ""));
    has("STRICT-008");
  }

  @Test
  void facadeCannotGrowBusinessControlFlow() throws Exception {
    setup(true);
    edit(
        c.target().implementation(),
        s ->
            s.replace(
                "return resultApiHandler.run(request);",
                "if(request==null){return null;}return resultApiHandler.run(request);"));
    has("STRICT-004");
  }

  @Test
  void invocationCannotBuildAtomicRequest() throws Exception {
    setup(true);
    Fixtures.write(
        root,
        "com/example/atomic/AtomicReqDto",
        "package com.example.atomic;public class AtomicReqDto {}");
    edit(
        c.steps().get(0).handler(),
        s ->
            s.replace(
                "ctx.atomicResponse = new com.example.atomic.ResourceApi()",
                "new com.example.atomic.AtomicReqDto(); ctx.atomicResponse = new"
                    + " com.example.atomic.ResourceApi()"));
    has("STRICT-005");
  }

  @Test
  void publicDtoCannotDependOnServiceImplementation() throws Exception {
    setup(true);
    edit(
        c.models().response().className(),
        s ->
            s.replace(
                "private String resourceName;",
                "private " + c.target().delegate() + " forbidden; private String resourceName;"));
    has("STRICT-006");
  }

  @Test
  void unrelatedOldScaffoldsAreNotPartOfThisOperation() throws Exception {
    setup(true);
    Fixtures.write(
        root,
        "com/example/legacy/Work",
        "package com.example.legacy;public class Work {void pending(){throw new"
            + " RuntimeException(\"ATOMIC_API_UNIMPLEMENTED: other operation\");}}");
    var report = check();
    assertEquals(0, report.exitCode(false), report.toString());
    assertEquals(1, report.discoveredMethods());
    assertEquals("UNKNOWN", report.readiness());
  }

  @Test
  void wrongRoleIsRejectedBeforeGeneration() throws Exception {
    setup(true);
    var data = (Map<String, Object>) Contract.yaml(contract);
    var response = (Map<String, Object>) ((Map<String, Object>) data.get("models")).get("response");
    response.put("sourceRoot", "other/src/main/java");
    Files.writeString(contract, new Yaml().dump(data));
    Contract wrong = Contract.load(contract);
    assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, wrong, p));
    assertFalse(Files.exists(root.resolve("other")));
  }

  @Test
  void handlerCannotStoreRequestState() throws Exception {
    setup(true);
    Path file = source(c.steps().get(0).handler());
    var unit = StaticJavaParser.parse(Files.readString(file));
    unit.getClassByName(Contract.simple(c.steps().get(0).handler()))
        .orElseThrow()
        .addField(c.models().request().className(), "currentRequest");
    Files.writeString(file, unit.toString());
    has("STRICT-005");
  }

  @Test
  void generationStopsAtKnownMavenMismatch() throws Exception {
    setup(true);
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><groupId>com.example</groupId><artifactId>root</artifactId><version>2.0</version></project>");
    Path child = root.resolve("module/pom.xml");
    Files.createDirectories(child.getParent());
    Files.writeString(
        child,
        "<project><parent><groupId>com.example</groupId><artifactId>root</artifactId><version>1.0</version></parent><artifactId>module</artifactId></project>");
    String before = Files.readString(source(c.target().delegate()));
    var error =
        assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, c, p));
    assertTrue(error.getMessage().startsWith("MAVEN:"), error.toString());
    assertEquals(before, Files.readString(source(c.target().delegate())));
  }

  @Test
  void stringResponseSupportsBothExplicitFailureContractsAtRuntime() throws Exception {
    Path base = root;
    for (String mode : List.of("exception", "message")) {
      root = Files.createDirectories(base.resolve(mode));
      setup(true, mode);
      var report = check();
      assertEquals(0, report.exitCode(false), report.toString());
      Fixtures.write(
          root,
          "com/example/fixture/FailingFacade",
          "package com.example.fixture; public class FailingFacade implements "
              + c.target().facade()
              + " {public "
              + c.models().response().className()
              + " "
              + c.operation()
              + "("
              + c.models().request().className()
              + " request) throws com.example.framework.OpsApiException {throw new"
              + " com.example.framework.OpsApiException(\"actual detail\");}}");
      Fixtures.write(
          root,
          "com/example/fixture/Manager",
          "package com.example.fixture; public class Manager extends"
              + " com.example.framework.OpsComponentManagement { public <T>T getFacadeImpl(Class<T>"
              + " type){return type.cast(new FailingFacade());}}");
      List<String> args =
          new ArrayList<>(
              List.of(
                  "--release",
                  "8",
                  "-encoding",
                  "UTF-8",
                  "-d",
                  root.resolve("classes").toString()));
      try (var files = Files.walk(root)) {
        files.filter(f -> f.toString().endsWith(".java")).forEach(f -> args.add(f.toString()));
      }
      assertEquals(
          0,
          ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
      try (var loader =
          new java.net.URLClassLoader(
              new java.net.URL[] {root.resolve("classes").toUri().toURL()}, null)) {
        Class<?> response = loader.loadClass(c.models().response().className());
        Object value = response.getConstructor().newInstance();
        response.getMethod("setResourceName", String.class).invoke(value, "success message");
        Class<?> converter = loader.loadClass(c.target().responseConverter());
        Object instance = converter.getField("INSTANCE").get(null);
        var convert =
            converter.getMethod("to" + Contract.cap(c.operation()) + "Response", response);
        assertEquals("success message", convert.invoke(instance, value));
        response.getMethod("setResourceName", String.class).invoke(value, " ");
        assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> convert.invoke(instance, value));
        Class<?> delegate = loader.loadClass(c.target().delegate());
        Object entry = delegate.getConstructor().newInstance();
        var manager = delegate.getDeclaredField("opsComponentManagement");
        manager.setAccessible(true);
        manager.set(
            entry, loader.loadClass("com.example.fixture.Manager").getConstructor().newInstance());
        var operation = delegate.getMethod(c.operation(), String.class);
        if (mode.equals("message"))
          assertTrue(operation.invoke(entry, "address").toString().contains("actual detail"));
        else {
          var thrown =
              assertThrows(
                  java.lang.reflect.InvocationTargetException.class,
                  () -> operation.invoke(entry, "address"));
          assertEquals("actual detail", thrown.getCause().getCause().getMessage());
        }
      }
    }
  }

  @Test
  void stringFailureModeIsMandatoryAndPreservesErrorDetail() throws Exception {
    setup(true, "message");
    edit(c.target().delegate(), s -> s.replace("e.getErrMsg()", "\"lost\""));
    has("API-007");
    var data = (Map<String, Object>) Contract.yaml(contract);
    data.put("errorMode", null);
    Files.writeString(contract, new Yaml().dump(data));
    assertThrows(IllegalArgumentException.class, () -> Contract.load(contract));
  }

  @Test
  void sharedConvertersSupportMoreThanOneOperationAndResponseShape() throws Exception {
    setup(true);
    Contract first = c;
    var original = (Map<String, Object>) Contract.yaml(contract);
    Path next = NewContract.create(root, p, "queryResourceMessage", "resource");
    var data = (Map<String, Object>) Contract.yaml(next);
    var models = (Map<String, Object>) data.get("models");
    var oldModels = (Map<String, Object>) original.get("models");
    for (String role : List.of("request", "response"))
      ((Map<String, Object>) models.get(role))
          .put("fields", ((Map<String, Object>) oldModels.get(role)).get("fields"));
    data.put("description", "查询消息");
    data.put("externalResponse", "java.lang.String");
    data.put("errorMode", "message");
    data.put("responseBindings", Map.of("message", "resourceName"));
    Files.writeString(next, new Yaml().dump(data));
    c = Contract.load(next);
    new Generator().plan(root, c, p).apply();
    implement();
    var second = check();
    assertEquals(0, second.exitCode(false), second.toString());
    var previous = new Checker(p).check(SourceIndex.scan(root), first);
    assertEquals(0, previous.exitCode(false), previous.toString());
  }

  @Test
  void requestConverterFailuresStayInsideErrorBoundary() throws Exception {
    setup(true);
    Path file = source(c.target().converter());
    var unit = StaticJavaParser.parse(Files.readString(file));
    var method =
        unit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().startsWith("to"))
            .findFirst()
            .orElseThrow();
    method.addThrownException(
        StaticJavaParser.parseClassOrInterfaceType("com.example.framework.OpsApiException"));
    method
        .getBody()
        .orElseThrow()
        .addStatement(
            0,
            StaticJavaParser.parseStatement(
                "if(address==null){throw new com.example.framework.OpsApiException(\"request"
                    + " conversion failed\");}"));
    Files.writeString(file, unit.toString());
    var report = check();
    assertEquals(0, report.exitCode(false), report.toString());
    List<String> args =
        new ArrayList<>(
            List.of(
                "--release", "8", "-encoding", "UTF-8", "-d", root.resolve("classes").toString()));
    try (var files = Files.walk(root)) {
      files.filter(f -> f.toString().endsWith(".java")).forEach(f -> args.add(f.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    try (var loader =
        new java.net.URLClassLoader(
            new java.net.URL[] {root.resolve("classes").toUri().toURL()}, null)) {
      var delegate = loader.loadClass(c.target().delegate());
      Object entry = delegate.getConstructor().newInstance();
      var operation = delegate.getMethod(c.operation(), String.class);
      var error =
          assertThrows(
              java.lang.reflect.InvocationTargetException.class,
              () -> operation.invoke(entry, new Object[] {null}));
      assertEquals("request conversion failed", error.getCause().getCause().getMessage());
    }
  }

  @Test
  void facadeFieldBindingsAreAcceptedAndWrongFieldIsRejected() throws Exception {
    fieldBinding = true;
    setup(false);
    var report = check();
    assertFalse(
        report.findings().stream().anyMatch(f -> f.rule().equals("STRICT-004")), report.toString());
    edit(
        c.target().implementation(),
        s -> s.replace("request.getAddress()", "request.getOtherAddress()"));
    has("STRICT-004");
  }

  @Test
  void facadeAllowsResolvedEntryRetLoggingHooks() throws Exception {
    setup(true);
    Fixtures.write(
        root,
        "com/example/logging/ApiLogger",
        "package com.example.logging;public class ApiLogger {public static void entry(){} public"
            + " static <T>T ret(T value){return value;}}");
    edit(
        c.target().implementation(),
        s ->
            s.replace(
                "return resultApiHandler.run(request);",
                "com.example.logging.ApiLogger.entry();return"
                    + " com.example.logging.ApiLogger.ret(resultApiHandler.run(request));"));
    var report = check();
    assertEquals(0, report.exitCode(false), report.toString());
  }

  @Test
  void verifyRejectsLegacyContracts() throws Exception {
    setup(false);
    assertEquals(
        2,
        Cli.run(
            new String[] {
              "verify",
              "--repo",
              root.toString(),
              "--profile",
              root.resolve("framework.yaml").toString(),
              "--contract",
              "../examples/single-handler.yaml"
            },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())));
  }
}
