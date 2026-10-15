package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileTest {
  @TempDir Path root;

  @Test
  void loadsAndMapsLongestPrefix() throws Exception {
    var profile = Profile.load(Fixtures.profile(root));
    assertEquals("src/main/java", profile.rootFor("com.example.sdk.Request"));
  }

  @Test
  void discoversUniqueFrameworkButRejectsAmbiguity() throws Exception {
    Fixtures.framework(root);
    Fixtures.external(root, false);
    assertEquals(
        "com.example.framework.OpsApiException",
        Profile.discover(SourceIndex.scan(root)).type("internalException"));
    Fixtures.write(
        root,
        "other/OpsApiException",
        "package other; public class OpsApiException extends Exception {}");
    assertThrows(IllegalArgumentException.class, () -> Profile.discover(SourceIndex.scan(root)));
  }

  @Test
  void rejectsUnsupportedDefaultStrategy() throws Exception {
    Path p = Fixtures.profile(root);
    Files.writeString(
        p, Files.readString(p).replace("facadeDefault: throw", "facadeDefault: project-existing"));
    assertThrows(IllegalArgumentException.class, () -> Profile.load(p));
  }
}
