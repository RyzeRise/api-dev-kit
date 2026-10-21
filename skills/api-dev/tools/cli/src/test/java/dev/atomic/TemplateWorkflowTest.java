package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/** Synthetic framework verification, never private-project integration evidence. */
class TemplateWorkflowTest {
  @TempDir Path root;

  Path testPlan(String layer, String operation) {
    return root.resolve(".atomic-api/work/" + layer + "-" + operation + "/tests/"
        + Contract.cap(operation) + "TestPlan.java");
  }

  void compileAndRejectUnimplementedTest(Path plan, String operation) throws Exception {
    assertTrue(Files.isRegularFile(plan), "Generation must include a failing business-test plan");
    Path classes = root.resolve("test-classes");
    Files.createDirectories(classes);
    var args = new ArrayList<>(List.of("--release", "8", "-encoding", "UTF-8", "-d", classes.toString()));
    try (var paths = Files.walk(root)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    try (var loader = new java.net.URLClassLoader(new java.net.URL[] {classes.toUri().toURL()})) {
      var type = loader.loadClass(Contract.cap(operation) + "TestPlan");
      for (var scenario : type.getDeclaredMethods()) {
        if (scenario.getParameterCount() != 0) continue;
        scenario.setAccessible(true);
        var pending = assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> scenario.invoke(null), scenario.getName());
        assertInstanceOf(AssertionError.class, pending.getCause(), scenario.getName());
        assertTrue(pending.getCause().getMessage().contains("TEST_IMPLEMENTATION_REQUIRED"));
      }
      var main = type.getMethod("main", String[].class);
      var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
          () -> main.invoke(null, (Object) new String[0]));
      assertInstanceOf(AssertionError.class, error.getCause());
      assertTrue(error.getCause().getMessage().contains("TEST_IMPLEMENTATION_REQUIRED"));
    }
  }

  @Test
  void appTemplateProducesCompilableBlockedScaffoldAndProtectedTestPlan() throws Exception {
    var f = new StrictWorkflowTest();
    f.root = root;
    f.setup(false);
    Path plan = testPlan("app", f.c.operation());
    compileAndRejectUnimplementedTest(plan, f.c.operation());
    assertEquals(1, new Checker(f.p).check(SourceIndex.scan(root), f.c).exitCode(false));
    assertTrue(new Generator().plan(root, f.c, f.p).changes().isEmpty());
    Files.writeString(plan, "user business assertions");
    assertThrows(IllegalArgumentException.class, () -> new Generator().plan(root, f.c, f.p));
    assertEquals("user business assertions", Files.readString(plan));
  }

  @Test
  void atomicTemplateProducesCompilableBlockedScaffoldAndProtectedTestPlan() throws Exception {
    var f = new AtomicToolTest();
    f.root = root;
    f.fixture("interface");
    f.generate();
    compileAndRejectUnimplementedTest(testPlan("atomic", f.load().operation()), f.load().operation());
    assertEquals(1, f.check().exitCode());
    assertTrue(AtomicTool.plan(SourceIndex.scan(root), f.load(), f.config).changes().isEmpty());
    Path plan = testPlan("atomic", f.load().operation());
    Files.writeString(plan, "user version tests");
    assertThrows(IllegalArgumentException.class, f::generate);
    assertEquals("user version tests", Files.readString(plan));
  }

  @Test
  void preTemplateAtomicSkeletonRemainsUnchangedOnUpgrade() throws Exception {
    var f = new AtomicToolTest();
    f.root = root;
    f.fixture("interface");
    var c = f.load();
    String implementation = c.releases().get(0).implementation();
    Path target = f.source(implementation);
    Files.createDirectories(target.getParent());
    // Literal structure emitted by 0.7.1 before templates, including unbraced guards.
    String old = "package com.example.atomic.impl.api.evpl;\n"
        + "@com.example.framework.Version(\"R26C10\") public class AtomicApiGetEvplServiceR26C10"
        + " implements com.example.atomic.common.latest.evpl.AtomicApiGetEvplServiceLatest {"
        + " @Override public com.example.atomic.common.dto.evpl.GetEvplServiceRspDto getEvplService("
        + "com.example.atomic.common.dto.evpl.GetEvplServiceReqDto request) {"
        + " if(request==null) throw new IllegalArgumentException(\"Request must not be null\");"
        + " if(request.getNeId()==null) throw new IllegalArgumentException(\"Required field missing: neId\");"
        + " throw new UnsupportedOperationException(\"ATOMIC_API_UNIMPLEMENTED: implement getEvplService for R26C10\"); }}";
    Files.writeString(target, old);
    var plan = AtomicTool.plan(SourceIndex.scan(root), c, f.config);
    assertTrue(plan.changes().stream().noneMatch(x -> x.path().equals(target)));
    plan.apply();
    assertEquals(old, Files.readString(target));
    assertTrue(AtomicTool.plan(SourceIndex.scan(root), c, f.config).changes().isEmpty());
    f.compile();
  }

  @Test
  @SuppressWarnings("unchecked")
  void addingVersionToImplementedAtomicDoesNotOverwriteHistoryOrWritePartialFiles() throws Exception {
    var f = new AtomicToolTest();
    f.root = root;
    f.fixture("class");
    f.generate();
    var original = f.load();
    Path implementation = f.source(original.releases().get(0).implementation());
    String before = Files.readString(implementation);
    String implemented = before.replace("throw new UnsupportedOperationException(\""
        + AtomicTool.MARKER + ": implement getEvplService for R26C10\");",
        original.models().response().className() + " result = new " + original.models().response().className()
            + "(); result.setName(request.getNeId()); return result;");
    assertNotEquals(before, implemented);
    Files.writeString(implementation, implemented);
    var data = (Map<String, Object>) Contract.yaml(f.contract);
    var releases = (List<Map<String, Object>>) data.get("releases");
    String added = original.releases().get(0).implementation().replace("R26C10", "R26C20");
    releases.add(Map.of("id", "R26C20", "implementation", added,
        "annotations", List.of("@com.example.framework.Version(\"R26C20\")")));
    Files.writeString(f.contract, new Yaml().dump(data));
    assertThrows(IllegalArgumentException.class, f::generate);
    assertEquals(implemented, Files.readString(implementation));
    assertFalse(Files.exists(f.source(added)));
    // Safe incremental path: add only the new version, preserve history, update class Latest.
    Files.writeString(f.source(added), before.replace("R26C10", "R26C20"));
    Path latest = f.source(original.latest());
    Files.writeString(latest, Files.readString(latest).replace("R26C10", "R26C20"));
    f.compile();
    assertTrue(f.check().findings().stream().anyMatch(x -> x.rule().equals("A03")));
    assertTrue(f.check().findings().stream().filter(x -> x.rule().equals("A03"))
        .allMatch(x -> x.file().equals(f.source(added).toString())));
    assertFalse(f.check().findings().stream().anyMatch(x -> Set.of("A01", "A02", "A04", "A05").contains(x.rule())));
    assertEquals(implemented, Files.readString(implementation));
  }
}
