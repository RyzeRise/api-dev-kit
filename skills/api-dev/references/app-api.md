# App API：ipran-app-api 开发

与 SKILL.md 的共用规范一起执行。本路线管理业务公共契约、Facade/Handler/Builder、service Delegate 和请求/响应 Converter；不在此生成原子版本族。

## 分层与规范

公共 ReqDto/RspDto 位于 ipran-app-api-common；业务编排位于 ipran-app-api-*；REST 模型/Delegate 位于 service。仅调用真实 Atomic 能力，不把原子 DTO 用作公共 App 契约。Facade 只编排，Handler pre/invoke/post 分责，Builder 仅用于真实汇总，Converter 仅做输入输出适配。

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| N01 | 类名、包与源码目录服从项目 roles，不猜目录或把公共契约放入 SDK。 | `StrictChecks.check / Conventions.validate`；自动（限定范围）。未覆盖：以本操作 contract/profile 为范围。 | 生成器固定（`FIXED`） |
| N02 | Facade 使用单个业务 ReqDto/RspDto，字段名称和类型符合契约。 | `Checker.signature / compare`；自动（限定范围）。未覆盖：字段业务含义另见 N20。 | 生成器固定（`FIXED`） |
| N03 | 请求和响应 Converter 分开；Delegate 实际使用两者，不直接组装 REST 响应。 | `StrictChecks.delegate / converter`；自动（限定范围）。未覆盖：检查指定转换器及可追踪辅助方法。 | 生成器固定（`FIXED`） |
| N04 | Facade 只编排 Handler；单步直接返回，Builder 只用于真实汇总。 | `StrictChecks.facade / Checker.checkHandlerCalls`；部分自动，仍需审查。未覆盖：汇总是否有业务意义需审查。 | 只生成骨架（`SCAFFOLD`） |
| N05 | pre 校验并建请求，invoke 获取能力并调用，post 校验转换响应；状态放 Context。 | `StrictChecks.lifecycle`；部分自动，仍需审查。未覆盖：仅识别常见错位，业务职责需审查。 | 只生成骨架（`SCAFFOLD`） |
| N06 | 公共 DTO 不反向依赖 service、Handler 或实现层；App/Atomic 模型分别归属公共模块。 | `StrictChecks.check / CodeRules.dependencies`；部分自动，仍需审查。未覆盖：外部依赖 JAR 和所有历史消费者需审查。 | 生成器固定（`FIXED`） |
| N08 | 请求、Facade 和响应适配置于异常边界；保留内部业务异常、getErrMsg、cause 及 String 错误形式；独立失败原因分别表达，同因 null/空检查可短路合并。 | `Checker.boundary / StrictChecks.delegate`；部分自动，仍需审查。未覆盖：历史消息兼容需基线资料。 | 生成器固定（`FIXED`） |
| N09 | 响应转换前校验完整性，保留 null/空集合、顺序、类型与默认值。 | `Checker.responseBoundary / compare`；部分自动，仍需审查。未覆盖：历史默认值及全部边界行为需对照测试。 | 只生成骨架（`SCAFFOLD`） |
| N10 | Facade 注解、默认方法和已配置的禁用注册须完整，重构不改变既有注册行为。 | `CodeRules.registration`；部分自动，仍需审查。未覆盖：动态注册和既有行为需运行验证。 | 生成器固定（`FIXED`） |
| N16 | 单响应专属节点使用 RspDto 的 public static 内部类；真实跨契约共享才用顶层 DTO。 | `CodeRules.nodes`；部分自动，仍需审查。未覆盖：检查内部节点修饰符；是否专属或共享需消费者证据。 | 不自动生成（`NOT_GENERATED`） |
| N17 | JSON 注解仅在确认冗余时删除；保留 wire 名、兼容别名及实际未知字段策略。 | `CodeRules.json`；仅定位证据，需业务审查。未覆盖：扫描注解并报告位置；须核对 ObjectMapper、消费者和基线，不能静态证明冗余。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N18 | MapStruct 沿用 INSTANCE、严格目标策略及校验，确认 processor/binding 和生成实现。 | `CodeRules.mapstruct`；部分自动，仍需审查。未覆盖：检查 Mapper 的静态配置；处理器和实际生成实现由编译证明。 | 生成普通映射，不接入 MapStruct（`PLAIN_MAPPING`） |
| N19 | 内部读取按 get/GetXxx/invokeGet 命名；原子能力从真实首次支持版本开始，核对 Latest 及注册。 | `CodeRules.atomic`；仅定位证据，需业务审查。未覆盖：原子版本族不在当前生成 contract 中；列出相关源码，须提供能力版本证据。 | 不自动生成（`NOT_GENERATED`） |
| N20 | 内部重命名不得改变公开 query、外部路径、wire 字段、默认值、顺序和错误契约。 | `CodeRules.compatibility`；仅定位证据，需业务审查。未覆盖：需要修改前基线和业务断言，当前源码无法证明未变。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N21 | 新增模块接通 modules、dependencyManagement、消费依赖和 classifier，主线升级后对齐父版本。 | `MavenParents.check / CodeRules.maven`；部分自动，仍需审查。未覆盖：自动校验本地父版本；完整 Reactor/classpath 及依赖接线由 Maven 证明。 | 不自动生成（`NOT_GENERATED`） |
| N22 | 从 Handler.run 验证生命周期、非法输入和异常；覆盖转换、回填、集合及版本边界。 | `CodeRules.tests`；仅定位证据，需业务审查。未覆盖：工具不替代业务测试执行；报告未验证，不能从测试文件存在推断通过。 | 不自动生成（`NOT_GENERATED`） |

类型含泛型逗号时按 YAML 字符串加引号，例如 `type: 'java.util.Map<java.lang.String,java.lang.String>'`。

## 生成与检查

```text
sh .atomic-api/tool/atomic-api.sh new --layer app --operation queryCircuit --business circuit
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryCircuit.yaml --apply
sh .atomic-api/tool/atomic-api.sh verify --layer app --contract .atomic-api/contracts/queryCircuit.yaml --format json
```

维护者一次核对 `.atomic-api/profile.yaml` 的框架 types、layout 与八个 roles：facade/request/response/implementation/handler/delegate/requestConverter/responseConverter。模块/package 由 roles 确定，不每次由模型猜。

编辑 new 生成的 v2 契约：description、models.request.fields、models.response.fields、已有 externalResponse 和 responseBindings。字段填写完整 Java 类型、中文说明，required 为必填引用字段，diagnostic 只用于可安全诊断的字段。绑定是外部字段→业务字段名，例如 `resourceName: circuitName`，不写表达式。

外部 String 响应还须显式选择 errorMode：exception 保留 cause 并抛包装异常，message 保留失败字符串契约，映射只能为 `message: 业务字符串字段`，不猜历史行为。

默认一个 Handler 直接返回。多步 input 仅支持 request、request.field、steps.id、steps.id.field，引用前序结果。assembly=builder 只用于真实汇总。生成器生成分层骨架，不会生成真实底层业务；按项目资料实现后再 verify。MapStruct 若项目已有则按 N18 沿用；当前生成器生成普通映射，不冒充处理器已经验证。

## 审查证据


固定流程：读取共用 skill → new → 填真实契约 → dry-run/apply → 实现业务 → verify → 项目编译与定向测试。不得以旧版 check 或降低 contract 版本替代验收。

- N16：更新内部节点所有引用、Lombok 注解及转换器；引用次数不能证明公共共享。
- N17：提供实际 ObjectMapper 配置、消费者及修改前 wire 契约；没有证据就保留注解。大小写、连字符、命名空间、历史别名都属于外部兼容性。
- N18：沿用项目 MapStruct INSTANCE/default 校验；构建验证 processor、Lombok binding 与生成实现。源码配置检查不等于处理器可用。
- N19/N20：对照接口、实现、Latest、管理器、注册、DTO、转换器及测试；验证首次支持、后续 Latest 和旧版本“不支持”路径。不根据需求号或分支名推断首次支持版本。
- N21：新增模块接入父 modules、根 dependencyManagement、消费依赖；合并主线无文本冲突不证明新模块 parent.version 已对齐。先相关模块 -am，再按影响编译 service 链路。
- N22：Facade mock Handler/Builder；Handler 从公开 run 验证规范化请求和非法输入不触发原子调用；Builder 覆盖多来源匹配、反向和缺失数据；Delegate 覆盖请求/调用/响应适配各自失败、请求回填、null/空集合及 cause。测试沿用业务仓库 JUnit 版本，报告实际执行结果。
- Atomic/App 分别规划 MR；App 依赖新 Atomic 时关联并先合入 Atomic，再更新 App 基线。明确豁免的现有 MR 不追溯拆分，不擅自改写历史。这是交付流程要求，工具不自动操作 MR。
