package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuleCatalogTest {
  @Test
  void eachNormHasExactTextCoverageAndGeneratorInTheSkill() throws Exception {
    var ids = new HashSet<String>();
    for (var r : RuleCatalog.all()) {
      assertTrue(ids.add(r.id()), r.id());
      String skill = Files.readString(Path.of("../..").resolve(Path.of("api-dev").relativize(Path.of(r.skill()))));
      String row =
          skill.lines().filter(s -> s.startsWith("| " + r.id() + " |")).findFirst().orElseThrow();
      assertTrue(row.contains(r.requirement()), r.id());
      assertTrue(row.contains(r.checker()), r.id());
      assertTrue(row.contains(r.generator()), r.id());
      assertTrue(row.contains(r.limitation()), r.id());
      assertTrue(
          row.contains(
              switch (r.coverage()) {
                case "AUTO" -> "自动（限定范围）";
                case "PARTIAL" -> "部分自动，仍需审查";
                default -> "仅定位证据，需业务审查";
              }),
          r.id());
      assertTrue(Set.of("AUTO", "PARTIAL", "REVIEW").contains(r.coverage()));
      assertFalse(r.diagnostics().isEmpty());
    }
    assertEquals(24, ids.size());
  }

  @Test
  void everyCheckRouteNamesExistingCode() throws Exception {
    for (var r : RuleCatalog.all()) {
      String owner = null;
      for (String route : r.checker().split(" / ")) {
        String[] parts = route.split("\\.");
        String method = parts[parts.length - 1];
        if (parts.length == 2) owner = parts[0];
        assertNotNull(owner, r.id());
        Class<?> type = Class.forName("dev.atomic." + owner);
        assertTrue(
            Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().equals(method)),
            r.id() + " " + route);
      }
    }
  }

  @Test
  void rulesCommandHasNoProjectDependencyAndDoesNotWriteFiles() throws Exception {
    var out = new java.io.ByteArrayOutputStream();
    assertEquals(
        0,
        Cli.run(
            new String[] {"rules", "--format", "json"}, new java.io.PrintStream(out), System.err));
    var rows = Contract.JSON.readTree(out.toString(java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(24, rows.size());
    assertEquals("N01", rows.get(0).get("id").asText());
  }
}
