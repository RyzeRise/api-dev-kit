package dev.atomic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Installs project-local assets without overwriting existing files. */
final class Initializer {
  record Result(List<String> files, String profileStatus) {}

  static Result initialize(Path directory) throws Exception {
    Path root = directory.toRealPath();
    Map<String, String> assets = new LinkedHashMap<>();
    Map<Path, String> dependencies = new LinkedHashMap<>();
    Path profilePath = GenerationPlan.safe(root, root.resolve(".atomic-api/profile.yaml"));
    String status = "保留已有配置 .atomic-api/profile.yaml";
    if (!Files.exists(profilePath)) {
      Path legacy = GenerationPlan.safe(root, root.resolve("atomic-api-profile.yaml"));
      if (Files.exists(legacy)) {
        Profile.load(legacy);
        String original = Files.readString(legacy);
        dependencies.put(legacy, original);
        assets.put(".atomic-api/profile.yaml", original);
        status = "已复制旧配置到 .atomic-api/profile.yaml，原文件保留";
      } else
        try {
          SourceIndex source = SourceIndex.scan(root);
          Contract.require(source.issues().isEmpty(), "源码解析未完成");
          Profile profile = Profile.discover(source);
          assets.put(
              ".atomic-api/profile.yaml",
              "# 自动识别的配置，请核对模块和类型。\n"
                  + new Yaml().dump(Contract.JSON.convertValue(profile, Map.class)));
          status = "已自动识别框架；请核对 .atomic-api/profile.yaml";
        } catch (Exception e) {
          assets.put(
              ".atomic-api/profile.yaml",
              "# 待配置：以下为示例类型和目录，必须按实际项目修改。\n" + resource("/demo/atomic-api-profile.yaml"));
          status = "已创建待配置草稿，未能自动识别：" + e.getMessage();
        }
    }
    assets.put(
        ".atomic-api/contracts/example.yaml", resource("/demo/contracts/strict-handler.yaml"));
    assets.put(
        ".atomic-api/contracts/fault-response.yaml",
        resource("/demo/contracts/fault-response.yaml"));
    assets.put(
        ".atomic-api/schemas/api-contract.schema.json",
        resource("/schemas/api-contract.schema.json"));
    assets.put(".atomic-api/atomic-profile.yaml", resource("/demo/atomic-profile.yaml"));
    assets.put(".atomic-api/.gitignore", "reports/\ncache/\nwork/\n");
    assets.put(".atomic-api/work/.gitignore", "*\n");
    for (String file :
        List.of(
            "api-dev/SKILL.md",
            "api-dev/references/api-atomic.md",
            "api-dev/references/app-api.md",
            "api-dev/references/rules.md",
            "api-dev/references/templates.md",
            "api-dev/references/configuration.md",
            "api-dev/references/l2evpn-example.md")) {
      Contract.require(
          !file.contains("..") && !file.startsWith("/"), "Unsafe skill asset: " + file);
      String text = resource("/init/skills/" + file);
      assets.put(".atomic-api/skills/" + file, text);
    }
    for (String file : Templates.files()) {
      Contract.require(!file.contains("..") && !file.startsWith("/"), "Unsafe template asset");
      assets.put(".atomic-api/templates/" + file, Templates.resource(file));
    }
    assets.put(".atomic-api/templates/manifest.txt", Templates.resource("manifest.txt"));
    List<GenerationPlan.Change> changes = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    for (var asset : assets.entrySet()) {
      Path target = GenerationPlan.safe(root, root.resolve(asset.getKey()));
      if (Files.exists(target)) {
        Contract.require(Files.isRegularFile(target), "Target is not a file: " + target);
        messages.add("KEEP " + asset.getKey());
      } else {
        changes.add(new GenerationPlan.Change(target, null, asset.getValue()));
        messages.add("CREATE " + asset.getKey());
      }
    }
    new GenerationPlan(root, changes, dependencies).apply();
    if (Files.exists(root.resolve(".atomic-api/skills/atomic-api-dev"))
        || Files.exists(root.resolve(".atomic-api/skills/app-api-dev")))
      messages.add("MIGRATE: 新入口 .atomic-api/skills/api-dev/SKILL.md；旧 skill 及其定制保留，合并后手动移除旧目录。");
    return new Result(List.copyOf(messages), status);
  }

  private static String resource(String path) throws Exception {
    try (var stream = Initializer.class.getResourceAsStream(path)) {
      Contract.require(stream != null, "Missing packaged asset: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
