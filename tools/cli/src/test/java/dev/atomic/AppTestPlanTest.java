package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.StaticJavaParser;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adapted from app/tests.java.tpl to this tool's actual JUnit 5 runner. Exercises generated code
 * with synthetic framework/Atomic fixtures, NOT private L2EVPN.
 */
class AppTestPlanTest {
  @TempDir Path root;
  StrictWorkflowTest f;

  URLClassLoader compiledFixture() throws Exception {
    f = new StrictWorkflowTest();
    f.root = root;
    f.setup(true);
    Fixtures.write(
        root,
        "com/example/atomic/ResourceApi",
        "package com.example.atomic;public class ResourceApi {public static int calls;public String"
            + " get(String address){calls++;return address;}}");
    // Give the synthetic external DTO observable state, preserving its actual setter signature.
    Fixtures.write(
        root,
        "com/example/model/QueryResourceByAddressRsp",
        "package com.example.model;public class QueryResourceByAddressRsp {private String"
            + " resourceName; public void setResourceName(String value){resourceName=value;}public"
            + " String getResourceName(){return resourceName;}}");
    String req = f.c.models().request().className(), rsp = f.c.models().response().className();
    String facade = f.c.target().facade(), exception = f.p.type("internalException");
    Fixtures.write(
        root,
        "com/example/framework/TestManager",
        "package com.example.framework;public class TestManager extends OpsComponentManagement"
            + " {public <T>T getFacadeImpl(Class<T> type){return type.cast(new TestFacade());}}");
    Fixtures.write(
        root,
        "com/example/framework/TestFacade",
        "package com.example.framework;public class TestFacade implements "
            + facade
            + " {public static int calls;public "
            + rsp
            + " "
            + f.c.operation()
            + "("
            + req
            + " request)throws "
            + exception
            + " {calls++;if(request.getAddress().equals(\"facade-failure\"))throw new "
            + exception
            + "(\"facade failure\");if(request.getAddress().equals(\"response-failure\"))return"
            + " null; "
            + rsp
            + " response=new "
            + rsp
            + "();response.setResourceName(request.getAddress());return response;}}");
    // Inject a checked conversion failure in the fixture to exercise the real Delegate boundary.
    Path converter = f.source(f.c.target().converter());
    var unit = StaticJavaParser.parse(Files.readString(converter));
    var method =
        unit.getInterfaceByName(Contract.simple(f.c.target().converter()))
            .orElseThrow()
            .getMethodsByName("to" + Contract.cap(f.c.operation()) + "Request")
            .get(0);
    method.addThrownException(StaticJavaParser.parseClassOrInterfaceType(exception));
    method
        .getBody()
        .orElseThrow()
        .addStatement(
            0,
            StaticJavaParser.parseStatement(
                "if(\"request-failure\".equals(address))throw new "
                    + exception
                    + "(\"request conversion failure\");"));
    Files.writeString(converter, unit.toString());
    Path classes = root.resolve("classes");
    Files.createDirectories(classes);
    var args =
        new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", classes.toString()));
    try (var paths = Files.walk(root.resolve("src/main/java"))) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    return new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, null);
  }

  Object request(ClassLoader loader, String address) throws Exception {
    var type = loader.loadClass(f.c.models().request().className());
    var request = type.getConstructor().newInstance();
    type.getMethod("setAddress", String.class).invoke(request, address);
    return request;
  }

  Object instance(ClassLoader loader, String name) throws Exception {
    return loader.loadClass(name).getConstructor().newInstance();
  }

  Object runHandler(ClassLoader loader, String address) throws Exception {
    var handler = instance(loader, f.c.steps().get(0).handler());
    return handler
        .getClass()
        .getMethod("run", Object.class)
        .invoke(handler, request(loader, address));
  }

  @Test
  void legalRequestUsesCorrectAtomicAndStages() throws Exception {
    try (var loader = compiledFixture()) {
      Object result = runHandler(loader, "node-1");
      assertEquals("node-1", result.getClass().getMethod("getResourceName").invoke(result));
      assertEquals(
          1, loader.loadClass("com.example.atomic.ResourceApi").getField("calls").getInt(null));
    }
  }

  @Test
  void invalidInputDoesNotCallAtomic() throws Exception {
    try (var loader = compiledFixture()) {
      for (String invalid : Arrays.asList(null, "", "  ")) {
        var error =
            assertThrows(InvocationTargetException.class, () -> runHandler(loader, invalid));
        assertEquals(f.p.type("internalException"), error.getCause().getClass().getName());
      }
      assertEquals(
          0, loader.loadClass("com.example.atomic.ResourceApi").getField("calls").getInt(null));
    }
  }

  @Test
  void conversionPreservesMappedFieldAndRejectsNull() throws Exception {
    try (var loader = compiledFixture()) {
      Class<?> responseType = loader.loadClass(f.c.models().response().className());
      Object result = runHandler(loader, "mapped-value");
      Class<?> converter = loader.loadClass(f.c.target().responseConverter());
      Object singleton = converter.getField("INSTANCE").get(null);
      Method mapping =
          converter.getMethod("to" + Contract.cap(f.c.operation()) + "Response", responseType);
      Object external = mapping.invoke(singleton, result);
      assertEquals(
          "mapped-value", external.getClass().getMethod("getResourceName").invoke(external));
      var error =
          assertThrows(
              InvocationTargetException.class,
              () -> mapping.invoke(singleton, new Object[] {null}));
      assertEquals(f.p.type("internalException"), error.getCause().getClass().getName());
    }
  }

  @Test
  void eachBoundaryFailurePreservesCause() throws Exception {
    try (var loader = compiledFixture()) {
      Object delegate = instance(loader, f.c.target().delegate());
      Field manager =
          Arrays.stream(delegate.getClass().getDeclaredFields())
              .filter(field -> field.getType().getName().equals(f.p.type("componentManagement")))
              .findFirst()
              .orElseThrow();
      manager.setAccessible(true);
      manager.set(delegate, instance(loader, "com.example.framework.TestManager"));
      Method call = delegate.getClass().getMethod(f.c.operation(), String.class);
      var expectedMessages =
          Map.of(
              "request-failure", "request conversion failure",
              "facade-failure", "facade failure",
              "response-failure", "result is empty");
      Field facadeCalls = loader.loadClass("com.example.framework.TestFacade").getField("calls");
      for (var input : List.of("request-failure", "facade-failure", "response-failure")) {
        int before = facadeCalls.getInt(null);
        var error =
            assertThrows(InvocationTargetException.class, () -> call.invoke(delegate, input));
        Throwable wrapped = error.getCause();
        assertEquals(f.p.type("externalException"), wrapped.getClass().getName(), input);
        assertNotNull(wrapped.getCause(), input);
        assertEquals(f.p.type("internalException"), wrapped.getCause().getClass().getName(), input);
        assertEquals(expectedMessages.get(input), wrapped.getCause().getMessage(), input);
        assertEquals(
            input.equals("request-failure") ? 0 : 1, facadeCalls.getInt(null) - before, input);
        assertTrue(wrapped.getMessage().contains(wrapped.getCause().getMessage()), input);
      }
    }
  }
}
