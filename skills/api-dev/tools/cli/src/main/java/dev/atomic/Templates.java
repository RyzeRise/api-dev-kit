package dev.atomic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Packaged templates are the single source of generated Java structure. */
final class Templates {
  private static final Pattern SLOT = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)\\}\\}");

  static String resource(String name) {
    try (var stream = Templates.class.getResourceAsStream("/templates/" + name)) {
      Contract.require(stream != null, "Missing packaged template: " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot read template: " + name, e);
    }
  }

  static String render(String name, String... pairs) {
    Contract.require(pairs.length % 2 == 0, "Template bindings must be key/value pairs");
    var values = new HashMap<String, String>();
    for (int i = 0; i < pairs.length; i += 2)
      Contract.require(values.put(pairs[i], pairs[i + 1]) == null, "Duplicate template binding");
    var matcher = SLOT.matcher(resource(name));
    return matcher.replaceAll(match -> {
      String key = match.group(1);
      Contract.require(values.containsKey(key), "Missing template binding: " + name + " / " + key);
      return Matcher.quoteReplacement(values.get(key));
    });
  }

  static String model(Contract.Model model) {
    var fields = new StringBuilder();
    for (var f : model.fields())
      fields.append(render("common/dto-field.java.tpl",
          "description", f.description().replace('\n', ' ').replace('\r', ' ').replace('\\', ' '),
          "fieldType", f.type(), "fieldName", f.name(), "capitalName", Contract.cap(f.name())));
    return render("common/dto.java.tpl", "className", Contract.simple(model.className()),
        "fields", fields.toString());
  }

  static void testPlan(Path root, String layer, String operation, Contract.Models models,
      List<GenerationPlan.Change> changes) throws Exception {
    String name = Contract.cap(operation) + "TestPlan";
    Path target = GenerationPlan.safe(root, root.resolve(
        ".atomic-api/work/" + layer + "-" + operation + "/tests/" + name + ".java"));
    String text = render(layer + "/tests.java.tpl", "operation", operation, "className", name,
        "requestType", models.request().className(), "responseType", models.response().className());
    if (Files.exists(target)) {
      Contract.require(Files.readString(target).equals(text),
          "CONFLICT: test plan customized; preserve it and implement incrementally: " + target);
    } else changes.add(new GenerationPlan.Change(target, null, text));
  }
}
