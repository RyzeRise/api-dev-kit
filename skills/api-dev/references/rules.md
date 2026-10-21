# 规范与检查器对照

用于按诊断编号定位检查代码、自动覆盖范围和人工证据，不是开发步骤或填写表单。
主要业务约束见 [Skill 入口](../SKILL.md)，开发路线见 [Atomic](api-atomic.md) / [App](app-api.md)。
纯文档逐项人工审查本次涉及的规范并保留证据；表内检查器名/生成器限制是工具覆盖信息，不要求执行工具。工具限定的 roles、固定签名等按真实框架核对，不为满足检查改造业务契约。工具辅助可额外运行以下命令：

```text
sh "$SKILL_DIR/scripts/atomic-api.sh" rules --layer app --format json
sh "$SKILL_DIR/scripts/atomic-api.sh" rules --layer atomic --format json
```

## App：N01～N24

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
| N13 | 先核对真实日志 API；支持时业务方法入口 entry、每个显式 return/throw 前 ret，不为 ret 强加 try-catch；不支持时保留原 API，记录 N13 未满足及源码依据，不造包装器。 | `CodeRules.logging / LoggingSupport.validate`；部分自动，仍需审查。未覆盖：须配置真实 logging API；缺配置报告 NOT_VERIFIED，不证明隐式异常路径；API 不支持时列出日志类型/签名、受影响方法及待适配项，不计规范通过。 | 配置 logging 后生成（`WHEN_LOGGING_CONFIGURED`） |
| N14 | 注释用精炼中文解释业务意图，函数注释说明优先级；英文缩写在说明中给出全拼。 | `CodeRules.comments`；部分自动，仍需审查。未覆盖：仅检查有无中文说明；意义、精炼程度、优先级和缩写准确性待审查。 | 生成中文骨架注释（`CHINESE_SKELETON_DOCS`） |
| N15 | 异常文本用可诊断的英文，包含操作及底层原因；格式化使用 Locale.ROOT。 | `CodeRules.messages / Checker.boundary`；部分自动，仍需审查。未覆盖：只检查字面量语言和 format 的 Locale，诊断充分性待审查。 | 生成器固定（`FIXED`） |
| N16 | 单响应专属节点使用 RspDto 的 public static 内部类；真实跨契约共享才用顶层 DTO。 | `CodeRules.nodes`；部分自动，仍需审查。未覆盖：检查内部节点修饰符；是否专属或共享需消费者证据。 | 不自动生成（`NOT_GENERATED`） |
| N17 | JSON 注解仅在确认冗余时删除；保留 wire 名、兼容别名及实际未知字段策略。 | `CodeRules.json`；仅定位证据，需业务审查。未覆盖：扫描注解并报告位置；须核对 ObjectMapper、消费者和基线，不能静态证明冗余。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N18 | MapStruct 沿用 INSTANCE、严格目标策略及校验，确认 processor/binding 和生成实现。 | `CodeRules.mapstruct`；部分自动，仍需审查。未覆盖：检查 Mapper 的静态配置；处理器和实际生成实现由编译证明。 | 生成普通映射，不接入 MapStruct（`PLAIN_MAPPING`） |
| N19 | 内部读取按 get/GetXxx/invokeGet 命名；原子能力从真实首次支持版本开始，核对 Latest 及注册。 | `CodeRules.atomic`；仅定位证据，需业务审查。未覆盖：原子版本族不在当前生成 contract 中；列出相关源码，须提供能力版本证据。 | 不自动生成（`NOT_GENERATED`） |
| N20 | 内部重命名不得改变公开 query、外部路径、wire 字段、默认值、顺序和错误契约。 | `CodeRules.compatibility`；仅定位证据，需业务审查。未覆盖：需要修改前基线和业务断言，当前源码无法证明未变。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N21 | 新增模块接通 modules、dependencyManagement、消费依赖和 classifier，主线升级后对齐父版本。 | `MavenParents.check / CodeRules.maven`；部分自动，仍需审查。未覆盖：自动校验本地父版本；完整 Reactor/classpath 及依赖接线由 Maven 证明。 | 不自动生成（`NOT_GENERATED`） |
| N22 | 从 Handler.run 验证生命周期、非法输入和异常；覆盖转换、回填、集合及版本边界。 | `CodeRules.tests`；仅定位证据，需业务审查。未覆盖：工具不替代业务测试执行；报告未验证，不能从测试文件存在推断通过。 | 生成明确失败的测试待办（`FAILING_TEST_PLAN`） |
| N23 | 生成前预览，冲突拒绝覆盖，写入不越界；不改配置、断言或规则掩盖失败。 | `GenerationPlan.apply / CodeRules.paths`；部分自动，仍需审查。未覆盖：写入边界由生成器执行；修改规则的意图、任务范围和基线需审查。 | 生成器固定（`FIXED`） |
| N24 | 不在日志或 diagnostic 字段输出密码、令牌等敏感信息，沿用实际版权头和日志 API。 | `CodeRules.privacy`；部分自动，仍需审查。未覆盖：拦截明显敏感字段/日志；隐式内容和版权归属需项目证据。 | 安全默认值，项目细节待补（`SAFE_DEFAULTS`） |

## Atomic：A01～A08

A02/A03 的统一 ReqDto→RspDto 与同签名检查仅覆盖当前工具可表达的平面契约。嵌套版本或历史 String 返回按真实公开契约增量维护，不据表回填历史能力或改返回类型；走 [嵌套版本路线](api-atomic.md#本项目嵌套版本接口的增量路线)；纯文档记录人工审查证据，工具辅助报告未完成工具验收。A07 的兼容审查还须覆盖旧类退出 Latest 的接口归属变化：保留历史业务体不等于二进制兼容，必须核对并迁移相关消费者。

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| A01 | 原子接口、公共 DTO、版本实现和 Latest 分别服从 Atomic roles，不能生成 App 编排类。 | `AtomicTool.layout`；自动（限定范围）。未覆盖：限定本契约类型；不推断项目模块名称。 | `FIXED` |
| A02 | 原子接口只声明本操作的 ReqDto→RspDto；公共 DTO 字段符合契约且不反向依赖实现。 | `AtomicTool.signature`；部分自动，仍需审查。未覆盖：检查源码类型和直接依赖；字段语义、外部 JAR 及传递依赖需审查。 | `FIXED` |
| A03 | 每个声明的支持版本实现相同原子签名，底层未接通时明确失败，不返回空成功。 | `AtomicTool.versions`；部分自动，仍需审查。未覆盖：占位和明显空返回会阻塞；真实调用效果需业务测试。 | `SCAFFOLD` |
| A04 | 版本注册注解来自实际框架，完整保留配置值，不凭空生成注册机制。 | `AtomicTool.registration`；部分自动，仍需审查。未覆盖：校验注解类型可解析及契约表达式；管理器动态发现、注解适用性和参数类型由编译/运行证明。 | `EXPLICIT_ANNOTATIONS` |
| A05 | Latest 按实际 class/interface 体系连接最新支持实现；首支持版本和版本顺序必须有历史证据。 | `AtomicTool.latest`；部分自动，仍需审查。未覆盖：校验显式继承关系；契约末版本是否最新及历史不支持路径需审查。 | `CONFIGURED_KIND` |
| A06 | Helper 保持单一 public 业务入口；沿用中文意图注释、英文异常；日志按 N13 核对真实 API，不支持时记录 A06 未满足及依据，不造包装器或 try-catch；不泄露凭据。 | `AtomicTool.conventions`；部分自动，仍需审查。未覆盖：检查本业务或直接引用的源码 Helper（含继承）、中文存在、明显中文错误/敏感日志及显式出口；外部 Helper、注释含义和隐式路径待审查。 | `WHEN_LOGGING_CONFIGURED` |
| A07 | 公共契约迁移保留 JSON、公开接口及错误语义，Maven 接线和父版本保持一致。 | `AtomicTool.integration`；部分自动，仍需审查。未覆盖：校验本地父版本；wire 基线、完整 Reactor/classifier 和兼容性需构建测试。 | `NOT_GENERATED` |
| A08 | 通过真实版本选择、Latest、成功/非法输入/底层失败及旧版本不支持测试后交付。 | `AtomicTool.tests`；仅定位证据，需业务审查。未覆盖：报告测试未执行；源码存在不代表已运行，必须附实际命令和结果。 | `FAILING_TEST_PLAN` |
