package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexTest {
  @TempDir Path root;

  @Test
  void discoversActualPackageDespiteDifferentDirectory() throws Exception {
    Fixtures.write(root, "wrong/Place", "package actual; public class Resource {}");
    assertTrue(SourceIndex.scan(root).types().containsKey("actual.Resource"));
  }

  @Test
  void resolvesOverloadByTypes() throws Exception {
    Fixtures.write(
        root, "p/F", "package p; interface F {void query(String s); void query(Integer n);}");
    Fixtures.write(
        root, "wrong/Caller", "package p; class Caller {void run(F f){f.query(\"x\");}}");
    var index = SourceIndex.scan(root);
    assertNotNull(index.type("p.Caller"));
    var call =
        index
            .type("p.Caller")
            .findFirst(com.github.javaparser.ast.expr.MethodCallExpr.class)
            .orElseThrow();
    assertTrue(call.resolve().getQualifiedSignature().contains("java.lang.String"));
  }

  @Test
  void malformedSourceIsNotIgnored() throws Exception {
    Fixtures.write(root, "Bad", "class Bad { syntax");
    assertFalse(SourceIndex.scan(root).issues().isEmpty());
  }

  @Test
  void duplicateTypesAreIncomplete() throws Exception {
    Fixtures.write(root, "one/X", "package p; class X {}");
    Fixtures.write(root, "two/X", "package p; class X {}");
    assertFalse(SourceIndex.scan(root).issues().isEmpty());
  }
}
