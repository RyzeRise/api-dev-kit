package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic nested-version evolution; does not exercise the private registration container. */
class NestedVersionEvolutionTest {
  @TempDir Path root;

  private static final String HISTORICAL_BODY =
      "public String history() { return \"old-result\"; }";

  @Test
  void keepingLatestOnOldClassFailsWhenNewVersionAddsAbstractMethod() throws Exception {
    var diagnostics = new ByteArrayOutputStream();
    assertEquals(1, compile(true, diagnostics));
    String message = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(message.contains("Old"), message);
    assertTrue(message.contains("added"), message);
  }

  @Test
  void detachOldLatestPreservesHistoryAndNewVersionImplementsNewMethod() throws Exception {
    var diagnostics = new ByteArrayOutputStream();
    assertEquals(0, compile(false, diagnostics), diagnostics.toString(StandardCharsets.UTF_8));
    assertTrue(Files.readString(root.resolve("Old.java")).contains(HISTORICAL_BODY));
    try (var loader = new URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, null)) {
      Class<?> oldType = loader.loadClass("Old");
      Class<?> newType = loader.loadClass("New");
      Class<?> latest = loader.loadClass("Api$Latest");
      assertTrue(loader.loadClass("Api$V1").isAssignableFrom(oldType));
      assertFalse(latest.isAssignableFrom(oldType));
      assertTrue(latest.isAssignableFrom(newType));
      assertEquals(
          "old-result",
          oldType.getMethod("history").invoke(oldType.getConstructor().newInstance()));
      Object instance = newType.getConstructor().newInstance();
      assertEquals("old-result", newType.getMethod("history").invoke(instance));
      assertEquals("new-result", newType.getMethod("added").invoke(instance));
      assertThrows(NoSuchMethodException.class, () -> oldType.getMethod("added"));
    }
  }

  private int compile(boolean oldKeepsLatest, ByteArrayOutputStream diagnostics) throws Exception {
    Files.writeString(
        root.resolve("Api.java"),
        """
        public interface Api {
          interface V1 { String history(); }
          interface V2 extends V1 { String added(); }
          interface Latest extends V2 {}
        }
        """);
    Files.writeString(
        root.resolve("Old.java"),
        "public class Old implements Api.V1"
            + (oldKeepsLatest ? ", Api.Latest" : "")
            + " { "
            + HISTORICAL_BODY
            + " }");
    Files.writeString(
        root.resolve("New.java"),
        """
        public class New extends Old implements Api.Latest, Api.V2 {
          public String added() { return "new-result"; }
        }
        """);
    return ToolProvider.getSystemJavaCompiler()
        .run(
            null,
            diagnostics,
            diagnostics,
            "--release",
            "8",
            "-d",
            root.toString(),
            root.resolve("Api.java").toString(),
            root.resolve("Old.java").toString(),
            root.resolve("New.java").toString());
  }
}
