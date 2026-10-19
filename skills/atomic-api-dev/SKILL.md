---
name: atomic-api-dev
description: 开发或接入 Java 原子能力，并按项目配置生成、检查分层 API。用于原子接口、版本注册、DTO、异常兼容及 Maven 接线；代码代理按固定脚本流程执行。
---

# Atomic API Dev

人类和 Agent 都按下列编号理解规范。共用规范在本文件，App 专属规范另读 [App API Dev](../app-api-dev/SKILL.md)。每条均有代码检查入口；部分自动/需审查项不能解释为已通过。

## 逐条代码规范

源码入口均在 `tools/cli/src/main/java/dev/atomic/`。`rules --format json` 可读取完整清单；verify 输出每条编号的状态和待验证边界。

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| N01 | 类名、包与源码目录服从项目 roles，不猜目录或把公共契约放入 SDK。 | `StrictChecks.check / Conventions.validate`；自动（限定范围）。未覆盖：以本操作 contract/profile 为范围。 | 生成器固定（`FIXED`） |
| N02 | Facade 使用单个业务 ReqDto/RspDto，字段名称和类型符合契约。 | `Checker.signature / compare`；自动（限定范围）。未覆盖：字段业务含义另见 N20。 | 生成器固定（`FIXED`） |
| N03 | 请求和响应 Converter 分开；Delegate 实际使用两者，不直接组装 REST 响应。 | `StrictChecks.delegate / converter`；自动（限定范围）。未覆盖：检查指定转换器及可追踪辅助方法。 | 生成器固定（`FIXED`） |
| N04 | Facade 只编排 Handler；单步直接返回，Builder 只用于真实汇总。 | `StrictChecks.facade / Checker.checkHandlerCalls`；部分自动，仍需审查。未覆盖：汇总是否有业务意义需审查。 | 只生成骨架（`SCAFFOLD`） |
| N05 | pre 校验并建请求，invoke 获取能力并调用，post 校验转换响应；状态放 Context。 | `StrictChecks.lifecycle`；部分自动，仍需审查。未覆盖：仅识别常见错位，业务职责需审查。 | 只生成骨架（`SCAFFOLD`） |
| N06 | 公共 DTO 不反向依赖 service、Handler 或实现层；App/Atomic 模型分别归属公共模块。 | `StrictChecks.check / CodeRules.dependencies`；部分自动，仍需审查。未覆盖：外部依赖 JAR 和所有历史消费者需审查。 | 生成器固定（`FIXED`） |
| N07 | 真实调用未实现时保留明确占位，不以 null、空 DTO 或删标记伪装完成。 | `StrictChecks.lifecycle / Checker.check`；部分自动，仍需审查。未覆盖：无法证明调用有真实业务效果。 | 只生成骨架（`SCAFFOLD`） |
| N08 | 请求、Facade 和响应适配置于异常边界；保留内部业务异常、getErrMsg、cause 及 String 错误形式；独立失败原因分别表达，同因 null/空检查可短路合并。 | `Checker.boundary / StrictChecks.delegate`；部分自动，仍需审查。未覆盖：历史消息兼容需基线资料。 | 生成器固定（`FIXED`） |
| N09 | 响应转换前校验完整性，保留 null/空集合、顺序、类型与默认值。 | `Checker.responseBoundary / compare`；部分自动，仍需审查。未覆盖：历史默认值及全部边界行为需对照测试。 | 只生成骨架（`SCAFFOLD`） |
| N10 | Facade 注解、默认方法和已配置的禁用注册须完整，重构不改变既有注册行为。 | `CodeRules.registration`；部分自动，仍需审查。未覆盖：动态注册和既有行为需运行验证。 | 生成器固定（`FIXED`） |
| N11 | 每个具体 Helper 仅有一个 public 业务方法（含继承，排除继承自 Object 的方法和构造器）；其他辅助方法私有化。 | `CodeRules.helpers`；自动（限定范围）。未覆盖：只检查本业务包或从目标类可追踪的 Helper；继承关系无法解析即 INCOMPLETE。 | 不自动生成（`NOT_GENERATED`） |
| N12 | 新增具体 Handler/Helper 可用单类 Component，不强造父子类；已有继承保持框架约定。 | `CodeRules.components`；部分自动，仍需审查。未覆盖：检查组件标识和抽象 Handler；是否新增及继承兼容需基线。 | 生成 Handler 组件（`HANDLER_FIXED`） |
| N13 | 业务方法入口 entry，每个显式 return/throw 前 ret；不为 ret 强加 try-catch。 | `CodeRules.logging / LoggingSupport.validate`；部分自动，仍需审查。未覆盖：须配置真实 logging API；不证明隐式异常路径，缺配置报告待验证。 | 配置 logging 后生成（`WHEN_LOGGING_CONFIGURED`） |
| N14 | 注释用精炼中文解释业务意图，函数注释说明优先级；英文缩写在说明中给出全拼。 | `CodeRules.comments`；部分自动，仍需审查。未覆盖：仅检查有无中文说明；意义、精炼程度、优先级和缩写准确性待审查。 | 生成中文骨架注释（`CHINESE_SKELETON_DOCS`） |
| N15 | 异常文本用可诊断的英文，包含操作及底层原因；格式化使用 Locale.ROOT。 | `CodeRules.messages / Checker.boundary`；部分自动，仍需审查。未覆盖：只检查字面量语言和 format 的 Locale，诊断充分性待审查。 | 生成器固定（`FIXED`） |
| N23 | 生成前预览，冲突拒绝覆盖，写入不越界；不改配置、断言或规则掩盖失败。 | `GenerationPlan.apply / CodeRules.paths`；部分自动，仍需审查。未覆盖：写入边界由生成器执行；修改规则的意图、任务范围和基线需审查。 | 生成器固定（`FIXED`） |
| N24 | 不在日志或 diagnostic 字段输出密码、令牌等敏感信息，沿用实际版权头和日志 API。 | `CodeRules.privacy`；部分自动，仍需审查。未覆盖：拦截明显敏感字段/日志；隐式内容和版权归属需项目证据。 | 安全默认值，项目细节待补（`SAFE_DEFAULTS`） |

## 入口

1. 切到业务仓库根目录，读 AGENTS.md、git status、`.atomic-api/profile.yaml` 和已有相关接口。确认真实能力、字段、版本、框架与模块，不猜 package 或外部契约。
2. Linux/macOS 入口为 `sh .atomic-api/tool/atomic-api.sh`。Windows PowerShell 使用 `& ".atomic-api/tool/atomic-api.cmd"`，CMD 使用 `.atomic-api\tool\atomic-api.cmd`。下文示例是 Linux/macOS，Windows 只替换入口前缀，保留参数和退出码。
3. 先执行入口加 `-h`。需要 Java 17+ 和完整配套 JAR；缺工具/配置就报告具体缺失位置，不安装全局命令、不修改 PATH、不把 com.example 配置当真实业务。profile 的框架类型、layout、roles 由维护者核对一次。

## 新增操作

```text
sh .atomic-api/tool/atomic-api.sh new --operation queryCircuit --business circuit
```

将名称换成本次真实操作与业务。编辑输出的 `.atomic-api/contracts/queryCircuit.yaml`，仅填写：

- description：实际用途。
- models.request.fields / models.response.fields：真实字段、Java 类型及说明；required 表示必填，diagnostic 只标记可安全出现在错误中的字段，不包含凭据。
- externalResponse：已有对外类型，不创建伪模型迎合工具。
- responseBindings：外部响应字段到业务响应字段名的映射，如 `circuitName: name`；不填表达式。
- String 外部响应另显式填 errorMode：exception 抛包装异常并保留 cause，message 保留失败字符串形式；绑定只能是 `message: 业务字符串字段`。按真实契约选择，不能猜。

保留 v2 及自动确定的类名、目录。默认一个 Handler 直接返回；确需多步时，steps.input 只支持 request、request.field、steps.id、steps.id.field，按前序步骤引用；assembly 选 builder 时只做真实汇总，Builder 位于 implementation 角色。复杂契约先由维护者适配，不降低版本绕过规则。

```text
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
```

先审阅范围。冲突不强行覆盖；不要删除业务文件以迫使应用，重复生成应 NOOP。生成器只提供 Facade/Handler 调用链骨架，不自动生成完整原子版本族或真实底层逻辑。规范中标明不自动生成的部分由 Agent 依据真实资料实现，再检查。


## 日志配置与检查

N13 不猜项目日志 API。维护者在 profile 中显式配置，例如：

```yaml
logging:
  type: com.example.logging.ProjectLogger
  factoryType: com.example.logging.LoggerFactory
  factoryMethod: getLogger
```

类型必须是实际项目的完整类名；工厂为静态方法，接收 Class 并返回日志类型，日志类型提供无参 entry()/ret()。生成器为新业务方法添加 LOGGER 字段及显式入口/出口日志，不额外构造 try-catch。其他日志签名需适配工具，不能伪造项目 API。缺配置时 N13 是 REVIEW_REQUIRED，不能声称日志规范通过；新逻辑中的出口也要重新检查。版权头和日志载荷按 N24 另行核对。

## 验证与交付

```text
sh .atomic-api/tool/atomic-api.sh rules
sh .atomic-api/tool/atomic-api.sh verify --contract .atomic-api/contracts/queryCircuit.yaml --format json
```

- 每条都有编号、检查入口、生成支持和未覆盖项。FAIL 修源码；INCOMPLETE 补实际类型/配置后重查；REVIEW_REQUIRED 按清单取业务证据，不能当 PASS。未检查的旧契约显示 NOT_EVALUATED。
- verify 退出 0/STRUCTURE_READY 只代表自动结构检查无阻塞，不能替代待审规范、真实项目编译和定向测试。JSON 的 structureStatus 与规范 status 分开；status=REVIEW_REQUIRED 不是完整规范验收通过。退出 1 是违规/占位，2 是解析或配置不完整。可以提前编译补齐类型，再重查。
- 仅要骨架或缺底层资料时保留占位，报告 SCAFFOLD、verify 非零及缺失资料。不得删标记、返回空成功、改规则/profile 或放宽断言制造通过。
- 代码注释“有中文”不等于业务说明正确；JSON 注解“存在”不证明冗余；测试文件“存在”不证明已运行。N14/N17/N19/N20/N22 的语义仍需明确证据。
- 先 git diff --check，核对实际 JDK、settings 和本地仓库。失败先看真实命令、首错和本次 diff，再查 Reactor/classpath、版本和 classifier；缺失输入类型后的 MapStruct 失败可能是连带结果。
- 日志放 `.atomic-api/work/<operation>/`，长任务续读原日志，不重复启动。交付说明命令、退出码、统计及逐条未验证项；只交任务改动，按已有授权提交推送，不自动部署/操作网元。
