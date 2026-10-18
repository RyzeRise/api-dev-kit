package dev.atomic;

import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Produces an intentionally incomplete business draft without inventing fields. */
final class NewContract {
  private static Map<String, Object> ordered(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
    return result;
  }

  static Path create(Path root, Profile profile, String operation, String business)
      throws Exception {
    Conventions.requireRoles(profile);
    Contract.id(operation);
    Contract.id(business);
    String cap = Contract.cap(operation), domain = Contract.cap(business);
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("facade", Conventions.name(profile, "facade", business, domain + "Facade"));
    target.put(
        "implementation",
        Conventions.name(profile, "implementation", business, domain + "FacadeImpl"));
    target.put(
        "delegate", Conventions.name(profile, "delegate", business, domain + "DelegateImpl"));
    target.put(
        "converter",
        Conventions.name(profile, "requestConverter", business, domain + "RequestConverter"));
    target.put(
        "responseConverter",
        Conventions.name(profile, "responseConverter", business, domain + "ResponseConverter"));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("version", 2);
    data.put("business", business);
    data.put("operation", operation);
    data.put("description", "填写业务用途");
    data.put("target", target);
    String request = Conventions.name(profile, "request", business, cap + "ReqDto");
    String response = Conventions.name(profile, "response", business, cap + "RspDto");
    data.put(
        "models",
        ordered(
            "request",
            ordered(
                "class",
                request,
                "sourceRoot",
                profile.roles().get("request").sourceRoot(),
                "fields",
                new ArrayList<>()),
            "response",
            ordered(
                "class",
                response,
                "sourceRoot",
                profile.roles().get("response").sourceRoot(),
                "fields",
                new ArrayList<>())));
    data.put("externalResponse", null);
    data.put("errorMode", null);
    data.put(
        "steps",
        List.of(
            ordered(
                "id",
                "result",
                "handler",
                Conventions.name(profile, "handler", business, cap + "ApiHandler"),
                "sourceRoot",
                profile.roles().get("handler").sourceRoot(),
                "input",
                "request",
                "inputType",
                request,
                "outputType",
                response)));
    data.put("assembly", Map.of("mode", "direct"));
    data.put("responseBindings", Map.of());
    Path path =
        GenerationPlan.safe(root, root.resolve(".atomic-api/contracts/" + operation + ".yaml"));
    Contract.require(!Files.exists(path), "CONFLICT: contract already exists: " + path);
    String text =
        "# 业务草稿：先填写 description、models.*.fields、externalResponse 和 responseBindings。String 还需 errorMode。\n"
            + "# 类名/包/目录已按项目 roles 固定；不要为了绕过检查修改 roles 或降低 version。\n"
            + new Yaml().dump(data);
    new GenerationPlan(root, List.of(new GenerationPlan.Change(path, null, text)), Map.of())
        .apply();
    return path;
  }
}
