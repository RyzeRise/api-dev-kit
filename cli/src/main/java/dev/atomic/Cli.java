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
      if (args.length == 0 || args[0].equals("--help") || args[0].equals("help")) {
        out.println(
            "atomic-api 0.1.0\n"
                + "  demo --repo <empty-directory>\n"
                + "  generate --repo <path> --contract <api.yaml> [--profile <framework.yaml>]"
                + " [--dry-run | --apply]\n"
                + "  check --repo <path> [--contract <api.yaml>] [--format text|json]"
                + " [--warnings-as-errors]\n"
                + "  source discovery: [--source-root <path>]... [--classpath <jar>]...\n"
                + "生成默认 dry-run。业务占位会抛明确异常，检查结果为 SCAFFOLD。");
        return 0;
      }
      Contract.require(Set.of("demo", "generate", "check").contains(args[0]), "Unknown command");
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
            Set.of("--repo", "--contract", "--profile", "--format", "--source-root", "--classpath")
                    .contains(key)
                && i + 1 < args.length,
            "Unknown/missing argument: " + key);
        String value = args[++i];
        if (key.equals("--source-root")) roots.add(Path.of(value));
        else if (key.equals("--classpath")) jars.add(Path.of(value));
        else Contract.require(options.putIfAbsent(key, value) == null, "Duplicate option: " + key);
      }
      Contract.require(options.containsKey("--repo"), "--repo required");
      Contract.require(
          !(flags.contains("--apply") && flags.contains("--dry-run")),
          "--apply and --dry-run are exclusive");
      String format = options.getOrDefault("--format", "text");
      Contract.require(Set.of("text", "json").contains(format), "Unknown format");
      Path repo = Path.of(options.get("--repo")).toAbsolutePath().normalize();
      if (args[0].equals("demo")) {
        Contract.require(
            options.size() == 1 && flags.isEmpty() && roots.isEmpty() && jars.isEmpty(),
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
              : repo.resolve("atomic-api-profile.yaml");
      Contract.require(
          !options.containsKey("--profile") || Files.isRegularFile(configured),
          "Specified profile file missing");
      Profile loadedProfile = Files.isRegularFile(configured) ? Profile.load(configured) : null;
      if (args[0].equals("check")) {
        var report = new Checker(loadedProfile).check(index, contract);
        if (format.equals("json"))
          out.println(
              Contract.JSON
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(
                      Map.of(
                          "schemaVersion",
                          1,
                          "toolVersion",
                          "0.1.0",
                          "scope",
                          repo.toString(),
                          "report",
                          report)));
        else {
          report
              .findings()
              .forEach(
                  f ->
                      out.printf(
                          "%s %s %s:%d %s%n",
                          f.severity(), f.rule(), f.file(), f.line(), f.message()));
          out.printf(
              "Methods discovered=%d checked=%d readiness=%s%n",
              report.discoveredMethods(), report.checkedMethods(), report.readiness());
          out.println("Manual review: " + String.join("; ", report.manualChecks()));
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
        out.println("待实现：Handler 底层调用、必要 Builder 字段汇总；请编译并执行测试。");
      }
      return 0;
    } catch (Exception e) {
      err.println("INCOMPLETE: " + e.getMessage());
      for (Throwable recovery : e.getSuppressed())
        err.println("RECOVERY FAILED: " + recovery.getMessage());
      return e.getMessage() != null && e.getMessage().startsWith("CONFLICT:") ? 1 : 2;
    }
  }
}
