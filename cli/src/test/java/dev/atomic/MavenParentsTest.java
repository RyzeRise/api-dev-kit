package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenParentsTest {
  @TempDir Path root;

  void pom(String path, String body) throws Exception {
    Path target = root.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(
        target,
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>"
            + body
            + "</project>");
  }

  void parent() throws Exception {
    pom(
        "pom.xml",
        "<groupId>com.example</groupId><artifactId>root</artifactId><version>2.4.2</version>");
  }

  String child(String version, String relative) {
    return "<parent><groupId>com.example</groupId><artifactId>root</artifactId><version>"
        + version
        + "</version>"
        + relative
        + "</parent><artifactId>fault</artifactId>";
  }

  @Test
  void reportsParentMismatchEvenWithoutJavaFacades() throws Exception {
    parent();
    pom("fault/pom.xml", child("2.4.1", ""));
    var report = new Checker().check(SourceIndex.scan(root), null);
    assertTrue(
        report.findings().stream()
            .anyMatch(
                f ->
                    f.rule().equals("MAVEN-001")
                        && f.severity().equals("ERROR")
                        && f.message().contains("2.4.1")
                        && f.message().contains("2.4.2")));
    assertEquals(2, report.exitCode(false)); // No Java coverage is still INCOMPLETE.
  }

  @Test
  void alignedAndDeliberatelyExternalParentsAreNotErrors() throws Exception {
    parent();
    pom("aligned/pom.xml", child("2.4.2", ""));
    pom("external/pom.xml", child("2.4.1", "<relativePath/>"));
    pom("missing/pom.xml", child("2.4.1", "<relativePath>../external-parent.xml</relativePath>"));
    pom(
        "different/pom.xml",
        child("2.4.1", "")
            .replace("<artifactId>root</artifactId>", "<artifactId>other</artifactId>"));
    assertTrue(MavenParents.check(root).isEmpty());
  }

  @Test
  void resolvesRevisionPropertiesAndCustomRelativePath() throws Exception {
    pom(
        "parent/pom.xml",
        "<groupId>com.example</groupId><artifactId>root</artifactId><version>${revision}</version><properties><revision>2.4.2</revision></properties>");
    pom("fault/pom.xml", child("${revision}", "<relativePath>../parent</relativePath>"));
    assertTrue(MavenParents.check(root).isEmpty());
    pom("fault/pom.xml", child("${missing}", "<relativePath>../parent/pom.xml</relativePath>"));
    assertEquals(1, MavenParents.check(root).size());
    assertTrue(MavenParents.check(root).stream().allMatch(f -> f.severity().equals("WARN")));
  }

  @Test
  void skipsBuildAndToolWorkCopies() throws Exception {
    parent();
    pom(".atomic-api/work/repro/pom.xml", "broken<xml");
    pom("target/repro/pom.xml", "broken<xml");
    Fixtures.write(root.resolve(".atomic-api/work/copy"), "com/example/Copy", "not Java");
    assertTrue(MavenParents.check(root).isEmpty());
    assertTrue(SourceIndex.scan(root).issues().isEmpty());
  }

  @Test
  void xmlCannotFetchExternalEntities() throws Exception {
    Files.writeString(
        root.resolve("pom.xml"),
        "<!DOCTYPE project [<!ENTITY secret SYSTEM"
            + " 'file:///not-a-real-secret'>]><project>&secret;</project>");
    assertEquals("INCOMPLETE", MavenParents.check(root).get(0).severity());
  }
}
