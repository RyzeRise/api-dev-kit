package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.StaticJavaParser;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/** Tool-owned synthetic regression, not L2EVPN source calibration. */
class IncrementalAppTest {
  @TempDir Path root;

  @Test
  @SuppressWarnings("unchecked")
  void newOperationReusesFacadeAndDelegateWithoutChangingExistingBodies() throws Exception {
    var f = new StrictWorkflowTest();
    f.root = root;
    f.setup(true);
    var before = new LinkedHashMap<String, String>();
    for (String name :
        List.of(
            f.c.target().facade(),
            f.c.target().implementation(),
            f.c.target().delegate(),
            f.c.target().converter(),
            f.c.target().responseConverter())) before.put(name, Files.readString(f.source(name)));
    Path draft = NewContract.create(root, f.p, "queryAnother", "resource");
    var data = (Map<String, Object>) Contract.yaml(draft);
    var models = (Map<String, Object>) data.get("models");
    ((Map<String, Object>) models.get("request"))
        .put("fields", Contract.JSON.convertValue(f.c.models().request().fields(), List.class));
    ((Map<String, Object>) models.get("response"))
        .put("fields", Contract.JSON.convertValue(f.c.models().response().fields(), List.class));
    data.put("description", "同业务新增方法");
    data.put("externalResponse", f.c.externalResponse());
    data.put("responseBindings", f.c.responseBindings());
    Files.writeString(draft, new Yaml().dump(data));
    Contract added = Contract.load(draft);
    assertEquals(f.c.target(), added.target());
    var plan = new Generator().plan(root, added, f.p);
    assertTrue(
        plan.changes().stream().anyMatch(c -> c.path().equals(f.source(f.c.target().facade()))));
    plan.apply();
    for (var entry : before.entrySet()) {
      var old = StaticJavaParser.parse(entry.getValue());
      var now = StaticJavaParser.parse(Files.readString(f.source(entry.getKey())));
      for (var method : old.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
        assertTrue(
            now.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
                .anyMatch(m -> m.toString().equals(method.toString())),
            entry.getKey() + " changed " + method.getNameAsString());
      }
    }
    // Both operations and their existing bodies still compile together.
    Path classes = root.resolve("classes");
    Files.createDirectories(classes);
    var args = new ArrayList<>(List.of("--release", "8", "-d", classes.toString()));
    try (var paths = Files.walk(root.resolve("src/main/java"))) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
    }
    assertEquals(
        0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    assertEquals(0, new Checker(f.p).check(SourceIndex.scan(root), f.c).exitCode(false));
    assertEquals(1, new Checker(f.p).check(SourceIndex.scan(root), added).exitCode(false));
  }
}
