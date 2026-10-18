package dev.atomic;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Cli {
  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  public static int run(String[] args, PrintStream out, PrintStream err) {
    try {
      if (args.length == 0
          || Arrays.asList(args).contains("--help")
          || Arrays.asList(args).contains("-h")
          || args[0].equals("help")) {
        out.println(
            "atomic-api 0.3.1\n"
                + "  atomic-api init [--agent codex|claude|cursor|none]\n"
                + "  atomic-api new --operation <queryXxx> --business <domain>\n"
                + "  atomic-api verify --contract <api.yaml> [--format text|json]\n"
                + "  atomic-api demo --repo <empty-directory>\n"
                + "  atomic-api generate --contract <api.yaml> [--profile <framework.yaml>]"
                + " [--dry-run | --apply]\n"
                + "  atomic-api check [--contract <api.yaml>] [--format text|json]"
                + " [--warnings-as-errors]\n"
                + "  source discovery: [--source-root <path>]... [--classpath <jar>]...\n"
                + "所有命令默认在当前目录运行，可用 --repo <path> 指定项目。\n"
                + "init 的配置、contract、Skill 均放入 .atomic-api/；--agent 可额外安装项目 Skill。\n"
                + "生成默认 dry-run。业务占位会抛明确异常，检查结果为 SCAFFOLD。");
        return 0;
      }
      Contract.require(
          Set.of("init", "demo", "new", "generate", "check", "verify").contains(args[0]),
          "Unknown command");
      Map<String, String> options = new HashMap<>();
      Set<String> flags = new HashSet<>();
      List<Path> roots = new ArrayList<>(), jars = new ArrayList<>();
      for (int i = 1; i < args.length; i++) {
        String key = args[i];
        if (Set.of("--apply", "--dry-run", "--warnings-as-errors").contains(key)) {
          Contract.require(flags.add(key), "Duplicate flag: " + key);
          continue;
        }
        Contract.require(
            Set.of(
                        "--repo",
                        "--contract",
                        "--profile",
                        "--format",
                        "--source-root",
                        "--classpath",
                        "--agent",
                        "--operation",
                        "--business")
                    .contains(key)
                && i + 1 < args.length,
            "Unknown/missing argument: " + key);
        String value = args[++i];
        if (key.equals("--source-root")) roots.add(Path.of(value));
        else if (key.equals("--classpath")) jars.add(Path.of(value));
        else Contract.require(options.putIfAbsent(key, value) == null, "Duplicate option: " + key);
      }
      Contract.require(
          args[0].equals("init") || !options.containsKey("--agent"), "--agent is for init only");
      Contract.require(
          !(flags.contains("--apply") && flags.contains("--dry-run")),
          "--apply and --dry-run are exclusive");
      Contract.require(
          args[0].equals("new")
              || (!options.containsKey("--operation") && !options.containsKey("--business")),
          "--operation/--business are for new only");
      String format = options.getOrDefault("--format", "text");
      Contract.require(Set.of("text", "json").contains(format), "Unknown format");
      Path repo = Path.of(options.getOrDefault("--repo", ".")).toAbsolutePath().normalize();
      if (args[0].equals("init")) {
        Contract.require(
            options.keySet().stream().allMatch(k -> Set.of("--repo", "--agent").contains(k))
                && flags.isEmpty()
                && roots.isEmpty()
                && jars.isEmpty(),
            "init accepts --repo and --agent only");
        var result = Initializer.initialize(repo, options.getOrDefault("--agent", "none"));
        result.files().forEach(out::println);
        out.println(result.profileStatus());
        out.println(
            "下一步：核对 .atomic-api/profile.yaml，配置 roles，然后 atomic-api"
                + " new --operation queryExample --business example。");
        if (options.getOrDefault("--agent", "none").equals("none"))
          out.println(
              "Skill 已保存到 .atomic-api/skills/atomic-api-dev/；自动发现可运行 atomic-api init --agent"
                  + " codex|claude|cursor。");
        return 0;
      }
      if (args[0].equals("demo")) {
        Contract.require(
            options.keySet().stream().allMatch("--repo"::equals)
                && flags.isEmpty()
                && roots.isEmpty()
                && jars.isEmpty(),
            "demo accepts --repo only");
        Demo.create(repo);
        out.println("Created sample project: " + repo);
        return 0;
      }
      Contract.require(
          args[0].equals("generate") || !flags.contains("--apply") && !flags.contains("--dry-run"),
          "check is read-only");
      List<Path> absoluteRoots =
          roots.stream().map(p -> p.isAbsolute() ? p : repo.resolve(p)).toList();
      SourceIndex index = SourceIndex.scan(repo, absoluteRoots, jars);
      Contract contract =
          options.containsKey("--contract")
              ? Contract.load(Path.of(options.get("--contract")))
              : null;
      Path configured =
          options.containsKey("--profile")
              ? Path.of(options.get("--profile"))
              : Files.isRegularFile(repo.resolve(".atomic-api/profile.yaml"))
                  ? repo.resolve(".atomic-api/profile.yaml")
                  : repo.resolve("atomic-api-profile.yaml");
      Contract.require(
          !options.containsKey("--profile") || Files.isRegularFile(configured),
          "Specified profile file missing");
      Profile loadedProfile = Files.isRegularFile(configured) ? Profile.load(configured) : null;
      if (args[0].equals("new")) {
        Contract.require(
            options.keySet().stream()
                    .allMatch(
                        k -> Set.of("--repo", "--profile", "--operation", "--business").contains(k))
                && flags.isEmpty()
                && roots.isEmpty()
                && jars.isEmpty(),
            "new accepts --repo/--profile/--operation/--business only");
        Contract.require(
            options.containsKey("--operation") && options.containsKey("--business"),
            "new requires --operation and --business");
        Path created =
            NewContract.create(
                repo, loadedProfile, options.get("--operation"), options.get("--business"));
        out.println("DRAFT " + created);
        out.println(
            "只填写 description、models.request.fields、models.response.fields、externalResponse、responseBindings；String"
                + " 响应另填 errorMode（exception/message）。字段来自真实需求和外部模型。不要修改自动确定的类名/包/目录。再运行 atomic-api"
                + " generate --contract <本文件> --dry-run。");
        return 0;
      }
      boolean verify = args[0].equals("verify");
      if (verify) {
        Contract.require(
            contract != null && contract.version() == 2,
            "STRICT-001: verify requires a version 2 contract; create it with atomic-api new");
        Conventions.requireRoles(loadedProfile);
      }
      if (args[0].equals("check") || verify) {
        var report = new Checker(loadedProfile).check(index, contract);
        if (format.equals("json"))
          out.println(
              Contract.JSON
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(
                      Map.of(
                          "schemaVersion",
                          2,
                          "toolVersion",
                          "0.3.1",
                          "scope",
                          repo.toString(),
                          "report",
                          report,
                          "status",
                          report.exitCode(flags.contains("--warnings-as-errors")) == 0
                              ? (verify ? "STRUCTURE_READY" : "CHECKED")
                              : "BLOCKED",
                          "nextAction",
                          verify
                              ? "结构检查不能证明业务正确；继续执行项目编译和定向测试，报告命令与退出码。"
                              : "按 finding.fix 修复后重跑原命令。")));
        else {
          report
              .findings()
              .forEach(
                  f ->
                      out.printf(
                          "%s %s %s:%d %s%n",
                          f.severity(),
                          f.rule(),
                          f.file(),
                          f.line(),
                          f.message() + "\n  FIX: " + f.fix()));
          out.printf(
              "Methods discovered=%d checked=%d readiness=%s%n",
              report.discoveredMethods(), report.checkedMethods(), report.readiness());
          out.println("Manual review: " + String.join("; ", report.manualChecks()));
          if (verify)
            out.println(
                report.exitCode(flags.contains("--warnings-as-errors")) == 0
                    ? "STRUCTURE_READY：继续项目编译与定向测试；本命令不证明业务正确。"
                    : "BLOCKED：按 FIX 修复并重跑同一 verify 命令；不要降低 contract 版本或修改规则绕过。");
        }
        return report.exitCode(flags.contains("--warnings-as-errors"));
      }
      Contract.require(contract != null, "generate requires --contract");
      Profile profile = loadedProfile == null ? Profile.discover(index) : loadedProfile;
      var plan = new Generator().plan(index, contract, profile);
      if (flags.contains("--apply")) plan.apply();
      if (format.equals("json"))
        out.println(
            Contract.JSON
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    Map.of(
                        "schemaVersion",
                        1,
                        "applied",
                        flags.contains("--apply"),
                        "readiness",
                        "SCAFFOLD",
                        "changes",
                        plan.changes())));
      else {
        out.print(plan.preview());
        out.println(
            flags.contains("--apply")
                ? "APPLIED; readiness=SCAFFOLD"
                : "DRY-RUN; no files written");
        out.println(
            "可先编译验证接线/补齐类型信息；实现 Handler 和必要 Builder 后运行 verify，再完成项目测试。只交骨架须报告 SCAFFOLD、业务未验收。");
      }
      return 0;
    } catch (Exception e) {
      err.println("INCOMPLETE: " + e.getMessage());
      err.println("FIX: 契约草稿先填业务字段、外部响应和映射；类型/路径按 profile 与真实源码确认。不得降低版本、删除断言或修改检查器绕过失败。");
      for (Throwable recovery : e.getSuppressed())
        err.println("RECOVERY FAILED: " + recovery.getMessage());
      return e.getMessage() != null && e.getMessage().startsWith("CONFLICT:") ? 1 : 2;
    }
  }
}
