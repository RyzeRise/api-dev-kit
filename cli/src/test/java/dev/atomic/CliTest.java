package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
  @TempDir Path root;

  int run(String... args) {
    return Cli.run(
        args,
        new PrintStream(new ByteArrayOutputStream()),
        new PrintStream(new ByteArrayOutputStream()));
  }

  @Test
  void helpAndUnknownArguments() {
    assertEquals(0, run("--help"));
    assertEquals(0, run("-h"));
    assertEquals(0, run("generate", "-h"));
    assertEquals(2, run("generate", "--unknown", "value"));
  }

  @Test
  void initKeepsAllFilesInsideToolDirectoryAndPreservesEdits() throws Exception {
    assertEquals(0, run("init", "--repo", root.toString()));
    Path profile = root.resolve(".atomic-api/profile.yaml");
    Path contract = root.resolve(".atomic-api/contracts/example.yaml");
    assertTrue(Files.readString(profile).contains("待配置"));
    assertDoesNotThrow(() -> Profile.load(profile));
    assertDoesNotThrow(() -> Contract.load(contract));
    assertTrue(Files.exists(root.resolve(".atomic-api/skills/atomic-api-design/SKILL.md")));
    assertTrue(
        Files.exists(root.resolve(".atomic-api/skills/atomic-api-design/references/rules.md")));
    assertTrue(Files.exists(root.resolve(".atomic-api/schemas/api-contract.schema.json")));
    try (var files = Files.list(root)) {
      assertEquals(
          java.util.List.of(".atomic-api"), files.map(p -> p.getFileName().toString()).toList());
    }
    Files.writeString(profile, "user configuration");
    Files.writeString(contract, "user contract");
    assertEquals(0, run("init", "--repo", root.toString()));
    assertEquals("user configuration", Files.readString(profile));
    assertEquals("user contract", Files.readString(contract));
  }

  @Test
  void initDiscoversFrameworkAndRejectsSymlinkWrites() throws Exception {
    Fixtures.framework(root);
    assertEquals(0, run("init", "--repo", root.toString()));
    assertEquals(
        "com.example.framework.ApiHandler",
        Profile.load(root.resolve(".atomic-api/profile.yaml")).type("handlerBase"));
    Path other = Files.createTempDirectory("atomic-init-other-");
    Files.delete(root.resolve(".atomic-api/contracts/example.yaml"));
    Files.delete(root.resolve(".atomic-api/contracts"));
    Files.createSymbolicLink(root.resolve(".atomic-api/contracts"), other);
    assertEquals(2, run("init", "--repo", root.toString()));
    assertFalse(Files.exists(other.resolve("example.yaml")));
  }

  @Test
  void initOptionalAgentSkillAndInvalidAgent() throws Exception {
    assertEquals(2, run("init", "--repo", root.toString(), "--agent", "unknown"));
    assertFalse(Files.exists(root.resolve(".atomic-api")));
    assertEquals(0, run("init", "--repo", root.toString(), "--agent", "codex"));
    assertTrue(Files.exists(root.resolve(".agents/skills/atomic-api-design/SKILL.md")));
    assertEquals(0, run("init", "--repo", root.toString(), "--agent", "claude"));
    assertTrue(Files.exists(root.resolve(".claude/skills/atomic-api-design/references/rules.md")));
  }

  @Test
  void checkLoadsHiddenProfileBeforeLegacyProfile() throws Exception {
    assertEquals(0, run("demo", "--repo", root.toString()));
    Files.createDirectories(root.resolve(".atomic-api"));
    Files.copy(root.resolve("atomic-api-profile.yaml"), root.resolve(".atomic-api/profile.yaml"));
    Files.writeString(root.resolve("atomic-api-profile.yaml"), "invalid legacy config");
    assertEquals(
        0,
        run(
            "generate",
            "--repo",
            root.toString(),
            "--contract",
            root.resolve("contracts/single-handler.yaml").toString(),
            "--apply"));
    assertEquals(0, run("check", "--repo", root.toString(), "--format", "json"));
  }

  @Test
  void initMigratesLegacyProfileWithoutChangingItsContent() throws Exception {
    Path legacy = Fixtures.profile(root);
    String custom =
        Files.readString(legacy)
            .replace(
                "com.example.framework.OpsApiException", "custom.framework.InternalApiException");
    Files.writeString(root.resolve("atomic-api-profile.yaml"), custom);
    assertEquals(0, run("init", "--repo", root.toString()));
    assertEquals(custom, Files.readString(root.resolve(".atomic-api/profile.yaml")));
    assertEquals(custom, Files.readString(root.resolve("atomic-api-profile.yaml")));
  }

  @Test
  void demoGenerateAndCheck() throws Exception {
    assertEquals(0, run("demo", "--repo", root.toString()));
    assertTrue(Files.exists(root.resolve("atomic-api-profile.yaml")));
    String contract = root.resolve("contracts/single-handler.yaml").toString();
    assertEquals(0, run("generate", "--repo", root.toString(), "--contract", contract));
    assertFalse(Files.exists(root.resolve("function-sdk")));
    assertEquals(0, run("generate", "--repo", root.toString(), "--contract", contract, "--apply"));
    assertEquals(
        0, run("check", "--repo", root.toString(), "--contract", contract, "--format", "json"));
    assertEquals(
        0,
        run(
            "generate",
            "--repo",
            root.toString(),
            "--contract",
            root.resolve("contracts/multi-handler.yaml").toString(),
            "--apply"));
    assertEquals(0, run("check", "--repo", root.toString(), "--format", "json"));
  }

  @Test
  void demoRefusesNonemptyDirectory() throws Exception {
    Files.writeString(root.resolve("user.txt"), "preserve");
    assertEquals(2, run("demo", "--repo", root.toString()));
    assertEquals("preserve", Files.readString(root.resolve("user.txt")));
  }
}
