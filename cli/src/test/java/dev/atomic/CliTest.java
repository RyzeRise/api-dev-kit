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
    assertEquals(2, run("generate", "--unknown", "value"));
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
