package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanTest {
  @TempDir Path root;

  @Test
  void preservesExistingPermissions() throws Exception {
    Assumptions.assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
    Path file = root.resolve("existing.java");
    Files.writeString(file, "before");
    var mode = PosixFilePermissions.fromString("rw-r-----");
    Files.setPosixFilePermissions(file, mode);
    new GenerationPlan(root, List.of(new GenerationPlan.Change(file, "before", "after")), Map.of())
        .apply();
    assertEquals(mode, Files.getPosixFilePermissions(file));
  }

  @Test
  void conflictsArePreflightedBeforeAnyWrite() throws Exception {
    Path first = root.resolve("first.java"), second = root.resolve("second.java");
    Files.writeString(second, "user");
    var plan =
        new GenerationPlan(
            root,
            List.of(
                new GenerationPlan.Change(first, null, "new"),
                new GenerationPlan.Change(second, null, "other")),
            Map.of());
    assertThrows(Exception.class, plan::apply);
    assertFalse(Files.exists(first));
    assertEquals("user", Files.readString(second));
  }

  @Test
  void rendersNewFileUnifiedHunk() {
    var plan =
        new GenerationPlan(
            root,
            List.of(new GenerationPlan.Change(root.resolve("x.java"), null, "one\ntwo\n")),
            Map.of());
    assertTrue(plan.preview().contains("@@ -0,0 +1,2 @@"));
  }
}
