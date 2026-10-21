package dev.atomic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/** Every normative row has an executable check route and an explicit proof boundary. */
public final class RuleCatalog {
  public record Rule(
      String id,
      String skill,
      String requirement,
      String checker,
      String coverage,
      String generator,
      String limitation,
      List<String> diagnostics) {
    @JsonProperty("label")
    public String label() {
      return RuleCatalog.label(id);
    }
  }

  public record Result(
      String id,
      String status,
      String checker,
      String generator,
      String limitation,
      List<String> evidence) {
    @JsonProperty("label")
    public String label() {
      return RuleCatalog.label(id);
    }
  }

  /** Readable display names; raw identifiers remain stable for diagnostic mapping. */
  static String label(String id) {
    return switch (id) {
      case "N01" -> "N01-文件与角色命名";
      case "N02" -> "N02-接口请求响应";
      case "N03" -> "N03-转换器职责";
      case "N04" -> "N04-Facade编排";
      case "N05" -> "N05-Handler三阶段";
      case "N06" -> "N06-DTO归属与依赖";
      case "N07" -> "N07-真实业务实现";
      case "N08" -> "N08-异常边界";
      case "N09" -> "N09-响应完整性";
      case "N10" -> "N10-组件注册";
      case "N11" -> "N11-Helper单一入口";
      case "N12" -> "N12-组件与继承";
      case "N13" -> "N13-项目日志";
      case "N14" -> "N14-中文意图注释";
      case "N15" -> "N15-异常文本";
      case "N16" -> "N16-响应节点DTO";
      case "N17" -> "N17-JSON兼容";
      case "N18" -> "N18-MapStruct规范";
      case "N19" -> "N19-原子命名与版本";
      case "N20" -> "N20-公开契约兼容";
      case "N21" -> "N21-Maven接线";
      case "N22" -> "N22-业务测试";
      case "N23" -> "N23-安全生成与写入";
      case "N24" -> "N24-日志隐私与版权";
      case "A01" -> "A01-原子分层与目录";
      case "A02" -> "A02-原子接口与DTO";
      case "A03" -> "A03-版本真实实现";
      case "A04" -> "A04-版本注册";
      case "A05" -> "A05-Latest与支持版本";
      case "A06" -> "A06-原子代码与日志";
      case "A07" -> "A07-契约兼容与依赖";
      case "A08" -> "A08-原子版本测试";
      default -> id;
    };
  }

  static Rule r(
      String id,
      String skill,
      String text,
      String checker,
      String coverage,
      String generator,
      String limit,
      String... diagnostics) {
    return new Rule(id, skill, text, checker, coverage, generator, limit, List.of(diagnostics));
  }

  public static List<Rule> all() {
    return List.of(
        r(
            "N01",
            "api-dev/references/rules.md",
            "Java 文件名与顶层公开类或接口名一致；新增 Facade/Converter 按业务域、Handler/请求响应 DTO 按操作命名，请求响应 DTO 主体一致；已有类增量保留原名，包和目录服从项目 roles，不把公共契约放入 SDK。",
            "StrictChecks.check / Conventions.validate",
            "PARTIAL",
            "CONTRACT_DRIVEN",
            "自动核对本操作契约目标的路径/文件名；业务域前缀、动作/对象、DTO 同名主体及存量命名保留须对照需求与基线人工审查。",
            "STRICT-001",
            "CONTRACT"),
        r(
            "N02",
            "api-dev/references/rules.md",
            "Facade 使用单个业务 ReqDto/RspDto，字段名称和类型符合契约。",
            "Checker.signature / compare",
            "AUTO",
            "FIXED",
            "字段业务含义另见 N20-公开契约兼容。",
            "API-002",
            "CONTRACT"),
        r(
            "N03",
            "api-dev/references/rules.md",
            "请求和响应 Converter 分开；Delegate 实际使用两者，不直接组装 REST 响应。",
            "StrictChecks.delegate / converter",
            "AUTO",
            "FIXED",
            "检查指定转换器及可追踪辅助方法。",
            "STRICT-002",
            "STRICT-003",
            "API-001"),
        r(
            "N04",
            "api-dev/references/rules.md",
            "Facade 只编排 Handler；单步直接返回，Builder 只用于真实汇总。",
            "StrictChecks.facade / Checker.checkHandlerCalls",
            "PARTIAL",
            "SCAFFOLD",
            "汇总是否有业务意义需审查。",
            "STRICT-004",
            "API-004"),
        r(
            "N05",
            "api-dev/references/rules.md",
            "pre 校验并建请求，invoke 获取能力并调用，post 校验转换响应；状态放 Context。",
            "StrictChecks.lifecycle",
            "PARTIAL",
            "SCAFFOLD",
            "仅识别常见错位，业务职责需审查。",
            "STRICT-005"),
        r(
            "N06",
            "api-dev/references/rules.md",
            "用于公共接口、跨模块共享或已明确后续跨模块使用的 DTO 放公共模块，且不反向依赖实现；仅模块内部使用的 DTO 可放业务模块。",
            "StrictChecks.check / CodeRules.dependencies",
            "PARTIAL",
            "FIXED",
            "只自动检查契约中的公共 DTO 依赖；内部 DTO、全部消费者、后续共享计划及实际模块归属需审查。",
            "STRICT-006",
            "N06"),
        r(
            "N07",
            "api-dev/references/rules.md",
            "真实调用未实现时保留明确占位，不以 null、空 DTO 或删标记伪装完成。",
            "StrictChecks.lifecycle / Checker.check",
            "PARTIAL",
            "SCAFFOLD",
            "无法证明调用有真实业务效果。",
            "STRICT-007"),
        r(
            "N08",
            "api-dev/references/rules.md",
            "请求、Facade 和响应适配置于异常边界；保留内部业务异常、getErrMsg、cause 及 String 错误形式；独立失败原因分别表达，同因"
                + " null/空检查可短路合并。",
            "Checker.boundary / StrictChecks.delegate",
            "PARTIAL",
            "FIXED",
            "历史消息兼容需基线资料。",
            "API-006",
            "API-007",
            "STRICT-008",
            "API-008"),
        r(
            "N09",
            "api-dev/references/rules.md",
            "响应转换前校验完整性，保留 null/空集合、顺序、类型与默认值。",
            "Checker.responseBoundary / compare",
            "PARTIAL",
            "SCAFFOLD",
            "历史默认值及全部边界行为需对照测试。",
            "API-009",
            "CONTRACT"),
        r(
            "N10",
            "api-dev/references/rules.md",
            "Facade 注解、默认方法和已配置的禁用注册须完整，重构不改变既有注册行为。",
            "CodeRules.registration",
            "PARTIAL",
            "FIXED",
            "动态注册和既有行为需运行验证。",
            "N10"),
        r(
            "N11",
            "api-dev/references/rules.md",
            "每个具体 Helper 仅有一个 public 业务方法（含继承，排除继承自 Object 的方法和构造器）；其他辅助方法私有化。",
            "CodeRules.helpers",
            "AUTO",
            "NOT_GENERATED",
            "只检查本业务包或从目标类可追踪的 Helper；继承关系无法解析即 INCOMPLETE。",
            "N11"),
        r(
            "N12",
            "api-dev/references/rules.md",
            "新增具体 Handler/Helper 可用单类 Component，不强造父子类；已有继承保持框架约定。",
            "CodeRules.components",
            "PARTIAL",
            "HANDLER_FIXED",
            "检查组件标识和抽象 Handler；是否新增及继承兼容需基线。",
            "N12"),
        r(
            "N13",
            "api-dev/references/rules.md",
            "遵循项目现有日志框架，不强制 entry/ret 等方法名；统一日志已覆盖时不重复添加，缺失的诊断信息按项目约定补充。",
            "CodeRules.logging",
            "REVIEW",
            "PRESERVE_EXISTING",
            "仅定位日志及配置证据；统一日志覆盖、诊断充分性和重复记录须结合基类、切面、拦截器及运行证据审查。",
            "N13"),
        r(
            "N14",
            "api-dev/references/rules.md",
            "注释用精炼中文解释业务意图，函数注释说明优先级；英文缩写在说明中给出全拼。",
            "CodeRules.comments",
            "PARTIAL",
            "CHINESE_SKELETON_DOCS",
            "仅检查有无中文说明；意义、精炼程度、优先级和缩写准确性待审查。",
            "N14"),
        r(
            "N15",
            "api-dev/references/rules.md",
            "异常文本用可诊断的英文，包含操作及底层原因；格式化使用 Locale.ROOT。",
            "CodeRules.messages / Checker.boundary",
            "PARTIAL",
            "FIXED",
            "只检查字面量语言和 format 的 Locale，诊断充分性待审查。",
            "N15",
            "API-007"),
        r(
            "N16",
            "api-dev/references/rules.md",
            "单响应专属节点使用 RspDto 的 public static 内部类；真实跨契约共享才用顶层 DTO。",
            "CodeRules.nodes",
            "PARTIAL",
            "NOT_GENERATED",
            "检查内部节点修饰符；是否专属或共享需消费者证据。",
            "N16"),
        r(
            "N17",
            "api-dev/references/rules.md",
            "JSON 注解仅在确认冗余时删除；保留 wire 名、兼容别名及实际未知字段策略。",
            "CodeRules.json",
            "REVIEW",
            "PRESERVE_EXISTING",
            "扫描注解并报告位置；须核对 ObjectMapper、消费者和基线，不能静态证明冗余。",
            "N17"),
        r(
            "N18",
            "api-dev/references/rules.md",
            "MapStruct 沿用 INSTANCE、严格目标策略及校验，确认 processor/binding 和生成实现。",
            "CodeRules.mapstruct",
            "PARTIAL",
            "PLAIN_MAPPING",
            "检查 Mapper 的静态配置；处理器和实际生成实现由编译证明。",
            "N18"),
        r(
            "N19",
            "api-dev/references/rules.md",
            "内部读取按 get/GetXxx/invokeGet 命名；原子能力从真实首次支持版本开始，核对 Latest 及注册。",
            "CodeRules.atomic",
            "REVIEW",
            "NOT_GENERATED",
            "原子版本族不在当前生成 contract 中；列出相关源码，须提供能力版本证据。",
            "N19"),
        r(
            "N20",
            "api-dev/references/rules.md",
            "内部重命名不得改变公开 query、外部路径、wire 字段、默认值、顺序和错误契约。",
            "CodeRules.compatibility",
            "REVIEW",
            "PRESERVE_EXISTING",
            "需要修改前基线和业务断言，当前源码无法证明未变。",
            "N20"),
        r(
            "N21",
            "api-dev/references/rules.md",
            "新增模块接通 modules、dependencyManagement、消费依赖和 classifier，主线升级后对齐父版本。",
            "MavenParents.check / CodeRules.maven",
            "PARTIAL",
            "NOT_GENERATED",
            "自动校验本地父版本；完整 Reactor/classpath 及依赖接线由 Maven 证明。",
            "MAVEN-001",
            "MAVEN-PARSE"),
        r(
            "N22",
            "api-dev/references/rules.md",
            "从 Handler.run 验证生命周期、非法输入和异常；覆盖转换、回填、集合及版本边界。",
            "CodeRules.tests",
            "REVIEW",
            "FAILING_TEST_PLAN",
            "工具不替代业务测试执行；报告未验证，不能从测试文件存在推断通过。",
            "N22"),
        r(
            "N23",
            "api-dev/references/rules.md",
            "生成前预览，冲突拒绝覆盖，写入不越界；不改配置、断言或规则掩盖失败。",
            "GenerationPlan.apply / CodeRules.paths",
            "PARTIAL",
            "FIXED",
            "写入边界由生成器执行；修改规则的意图、任务范围和基线需审查。",
            "N23"),
        r(
            "N24",
            "api-dev/references/rules.md",
            "不在日志或 diagnostic 字段输出密码、令牌等敏感信息，沿用实际版权头和日志 API。",
            "CodeRules.privacy",
            "PARTIAL",
            "SAFE_DEFAULTS",
            "拦截明显敏感字段/日志；隐式内容和版权归属需项目证据。",
            "N24"));
  }

  static List<Result> results(
      List<Checker.Finding> findings, boolean evaluated, Map<String, List<String>> evidence) {
    return all().stream()
        .map(
            r -> {
              var relevant =
                  findings.stream().filter(f -> r.diagnostics().contains(f.rule())).toList();
              String status =
                  !evaluated
                      ? "NOT_EVALUATED"
                      : (relevant.stream().anyMatch(f -> f.severity().equals("INCOMPLETE"))
                              || findings.stream()
                                  .anyMatch(
                                      f ->
                                          f.severity().equals("INCOMPLETE")
                                              && Set.of("PARSE", "RESOLUTION").contains(f.rule())))
                          ? "INCOMPLETE"
                          : relevant.stream().anyMatch(f -> f.severity().equals("ERROR"))
                              ? "FAIL"
                              : relevant.stream().anyMatch(f -> f.severity().equals("WARN"))
                                  ? "REVIEW_REQUIRED"
                                  : r.coverage().equals("AUTO") ? "PASS" : "REVIEW_REQUIRED";
              return new Result(
                  r.id(),
                  status,
                  r.checker(),
                  r.generator(),
                  r.limitation(),
                  evidence.getOrDefault(r.id(), List.of()));
            })
        .toList();
  }
}
