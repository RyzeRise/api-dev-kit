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
            "atomic-api 0.6.0\n"
                + "  atomic-api rules [--format text|json]\n"
                + "  atomic-api init [--repo <path>]\n"
                + "  atomic-api new --operation <queryXxx> --business <domain>\n"
                + "  atomic-api verify --contract <api.yaml> [--format text|json]\n"
                + "  atomic-api demo --repo <empty-directory>\n"
                + "  atomic-api generate --contract <api.yaml> [--profile <framework.yaml>]"
                + " [--dry-run | --apply]\n"
                + "  atomic-api check [--contract <api.yaml>] [--format text|json]"
                + " [--warnings-as-errors]\n"
                + "  source discovery: [--source-root <path>]... [--classpath <jar>]...\n"
                + "所有命令默认在当前目录运行，可用 --repo <path> 指定项目。\n"
                + "init 的配置、contract、Skill 均放入 .atomic-api/；Skill 统一由提示中的文件路径读取。\n"
                + "生成默认 dry-run。业务占位会抛明确异常，检查结果为 SCAFFOLD。");
        return 0;
      }
      Contract.require(
          Set.of("init", "demo", "new", "generate", "check", "verify", "rules").contains(args[0]),
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
          !(flags.contains("--apply") && flags.contains("--dry-run")),
          "--apply and --dry-run are exclusive");
      Contract.require(
          args[0].equals("new")
              || (!options.containsKey("--operation") && !options.containsKey("--business")),
          "--operation/--business are for new only");
      String format = options.getOrDefault("--format", "text");
      Contract.require(Set.of("text", "json").contains(format), "Unknown format");
      if (args[0].equals("rules")) {
        Contract.require(
            options.keySet().stream().allMatch("--format"::equals)
                && flags.isEmpty()
                && roots.isEmpty()
                && jars.isEmpty(),
            "rules accepts --format only");
        if (format.equals("json"))
          out.println(
              Contract.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(RuleCatalog.all()));
        else
          RuleCatalog.all()
              .forEach(
                  r ->
                      out.printf(
                          "%s %s%n  CHECK: %s [%s]%n  GENERATOR: %s%n  LIMIT: %s%n",
                          r.id(),
                          r.requirement(),
                          r.checker(),
                          r.coverage(),
                          r.generator(),
                          r.limitation()));
        return 0;
      }
      Path repo = Path.of(options.getOrDefault("--repo", ".")).toAbsolutePath().normalize();
      if (args[0].equals("init")) {
        Contract.require(
            options.keySet().stream().allMatch("--repo"::equals)
                && flags.isEmpty()
                && roots.isEmpty()
                && jars.isEmpty(),
            "init accepts --repo only");
        var result = Initializer.initialize(repo);
        result.files().forEach(out::println);
        out.println(result.profileStatus());
        out.println(
            "下一步：核对 .atomic-api/profile.yaml，配置 roles，然后 atomic-api"
                + " new --operation queryExample --business example。");
        out.println("提示 Agent：请读取 .atomic-api/skills/app-api-dev/SKILL.md，按其中流程完成任务。");
        out.println("仓库内便携工具约定放在 .atomic-api/tool/，Skill 会优先调用该目录的脚本。");
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
        boolean reviewRequired =
            report.rules().stream().anyMatch(r -> r.status().equals("REVIEW_REQUIRED"));
        if (format.equals("json"))
          out.println(
              Contract.JSON
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(
                      Map.of(
                          "schemaVersion",
                          3,
                          "toolVersion",
                          "0.6.0",
                          "scope",
                          repo.toString(),
                          "report",
                          report,
                          "status",
                          report.exitCode(flags.contains("--warnings-as-errors")) == 0
                              ? (verify
                                  ? (reviewRequired ? "REVIEW_REQUIRED" : "STRUCTURE_READY")
                                  : "CHECKED")
                              : "BLOCKED",
                          "structureStatus",
                          report.exitCode(flags.contains("--warnings-as-errors")) == 0
                              ? "STRUCTURE_READY"
                              : "BLOCKED",
                          "nextAction",
                          verify
                              ? "逐条补 REVIEW_REQUIRED 所需业务证据，再执行项目编译和定向测试；结构通过不代表规范全部通过。"
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
          report
              .rules()
              .forEach(
                  r ->
                      out.printf(
                          "RULE %s %s | CHECK %s | GENERATOR %s%n  LIMIT: %s%n",
                          r.id(), r.status(), r.checker(), r.generator(), r.limitation()));
          report
              .rules()
              .forEach(
                  r -> r.evidence().forEach(e -> out.println("  EVIDENCE " + r.id() + ": " + e)));
          out.printf(
              "Norms: total=%d pass=%d fail=%d review=%d incomplete=%d%n",
              report.rules().size(),
              report.rules().stream().filter(r -> r.status().equals("PASS")).count(),
              report.rules().stream().filter(r -> r.status().equals("FAIL")).count(),
              report.rules().stream().filter(r -> r.status().equals("REVIEW_REQUIRED")).count(),
              report.rules().stream().filter(r -> r.status().equals("INCOMPLETE")).count());
          out.println("Manual review: " + String.join("; ", report.manualChecks()));
          if (verify)
            out.println(
                report.exitCode(flags.contains("--warnings-as-errors")) == 0
                    ? "STRUCTURE_READY：仅自动结构检查通过；REVIEW_REQUIRED 仍需逐条审查，继续编译测试，不得报告规范全部通过。"
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
