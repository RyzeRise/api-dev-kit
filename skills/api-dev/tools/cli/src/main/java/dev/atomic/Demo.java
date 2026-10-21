package dev.atomic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Self-contained user-facing sample project, containing no real service calls. */
final class Demo {
  static void create(Path root) throws Exception {
    if (Files.exists(root)) {
      try (var entries = Files.list(root)) {
        Contract.require(entries.findAny().isEmpty(), "Demo target must be empty");
      }
    }
    Files.createDirectories(root);
    root = root.toRealPath();
    List<GenerationPlan.Change> changes = new ArrayList<>();
    try (var manifest = Demo.class.getResourceAsStream("/demo/manifest.txt")) {
      if (manifest == null) throw new IllegalStateException("Demo resources not packaged");
      for (String name :
          new String(manifest.readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .filter(s -> !s.isBlank())
              .toList()) {
        try (var data = Demo.class.getResourceAsStream("/demo/" + name)) {
          if (data == null) throw new IllegalStateException("Missing demo resource: " + name);
          changes.add(
              new GenerationPlan.Change(
                  GenerationPlan.safe(root, root.resolve(name)),
                  null,
                  new String(data.readAllBytes(), StandardCharsets.UTF_8)));
        }
      }
    }
    new GenerationPlan(root, changes, Map.of()).apply();
  }
}
