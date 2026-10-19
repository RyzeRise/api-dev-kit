# Atomic API：api-atomic 开发

与 SKILL.md 的共用规范一起执行。本路线只管理 api-atomic-common 的接口/DTO/Latest 契约，以及 api-atomic 的版本实现和底层调用封装，不生成 App Facade、Handler 或 service Delegate。

## 开发时按这个顺序读

1. 先读“一次配置”，确认实际原子框架与 Latest 关系。
2. 按“新能力固定流程”准备一项真实操作的契约，补齐注册注解后预览并生成。
3. 实现底层调用，再按“接线、测试和证据”验收；占位不算完成。
4. 末尾规范表用于检查和查错，不是需要填写的表单。

## 一次配置

读取真实相邻 Atomic 能力的公共接口、版本实现、Latest、版本注解、管理器和底层 REST 封装。维护者核对独立的 `.atomic-api/atomic-profile.yaml`：

- targetJava：实际业务 Java 版本；工具自身仍需 Java 17+。
- layout 与 roles：apiInterface、request、response、implementation、latest。公共接口/DTO 在公共模块，实现放版本模块。directoryPrefix 支持物理目录与 package 不同的仓库。
- latestKind：按实际框架选 interface 或 class。interface 模式中，Latest extends 公共接口，各版本 implements Latest；class 模式中，各版本 implements 公共接口，Latest extends 契约最后一个版本实现。class 模式 Latest 必须放实现模块，不能让公共模块反向依赖实现。不符合这两种关系的实际框架须先适配工具，不能迁就生成器重构框架。
- baseInterfaces：实际公共接口父类型列表；interfaceAnnotations：实际接口注解表达式列表，两者即使为空也显式填写。保留类型参数，生成后用真实编译验证。
- logging：按 SKILL.md 配置真实日志 API，可缺省但日志规范保持 REVIEW_REQUIRED。

init 仅提供 com.example 草稿，不能代表真实项目注册方式。注册通过契约的完整注解表达式显式配置；例如 `@项目实际包.Version(项目实际版本常量)`，必须从已有源码复制并核对，不直接使用示例。工具不会根据类名自动发明管理器、注解或版本值。

类型含泛型逗号时按 YAML 字符串加引号，例如 `type: 'java.util.Map<java.lang.String,java.lang.String>'`。

## 新能力固定流程

```text
sh .atomic-api/tool/atomic-api.sh new --layer atomic --operation getEvplService --business evpl --api-version R26C10
sh .atomic-api/tool/atomic-api.sh generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --apply
sh .atomic-api/tool/atomic-api.sh verify --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --format json
```

用真实操作、业务及首支持版本替换示例。new 生成 Atomic 专属 `layer: atomic, version: 1` 契约，不是 App v1 兼容契约。填写：

- description：真实用途；models.request/response.fields：真实完整 Java 类型、中文字段说明及 required/diagnostic。不同层使用不同 DTO，节点仅单响应专属时用 public static 内部类；当前生成器仅顶层 DTO，复杂节点按实际业务实现。
- releases：仅列实际支持版本，按真实先后排序；每项含 id、固定的 implementation 类名及真实 annotations 列表。首项之前的版本不造伪实现，历史不支持行为单独验证。版本顺序不按字符串推断，也不根据需求号猜首次支持。
- latestAnnotations：实际 Latest 注册注解，框架无需注解时显式空列表；不能为空值。版本 annotations 不可为空。检查注解表达式及可解析类型，语义仍须编译和管理器运行验证。

类名/包/目录按 roles 固定：`AtomicApi<Operation>Interface`、`<Operation>ReqDto/RspDto`、`AtomicApi<Operation><Version>`、`AtomicApi<Operation>Latest`。operation 用 get/modify/create/deleteXxx；内部读取封装沿用实际 get/GetXxx/invokeGet 约定，已有对外 query 名称与路径不变。若项目命名/Latest 结构不同，先由维护者适配，不改外部契约凑模板。

生成器只生成明确失败的版本骨架，包含必填请求校验及 `ATOMIC_API_UNIMPLEMENTED`。verify 必须阻塞占位；接真实能力后按原有异常和响应语义实现，不能用 null、空 DTO、删标记伪装完成。已有业务文件与骨架不同时拒绝覆盖；重复未修改骨架为 NOOP。

## 接线、测试和证据

- 核对每个支持版本的注册、管理器选择与 Latest；注解存在不代表动态发现成功。生成器不修改实际注册表/容器配置。
- 公共 DTO 不导入实现/service/Handler。保留 JSON wire 名、别名和未知字段策略；删注解前先拿 ObjectMapper、消费者与基线证据。工具不证明 JSON 冗余。
- 变更公共 DTO 时检查全部调用者，保留响应字段类型、默认值、列表顺序及错误/cause。历史兼容通过对照测试证明，不能只看当前源码。
- 新模块接父 modules、根 dependencyManagement、消费依赖与 classifier。合主线后核对新增模块 parent.version；无 Git 冲突不代表版本一致。先相关模块 -am，再按影响编译 service。
- 测试版本选择、Latest、实际首次支持、旧版本不支持、成功响应、非法参数、查无数据和底层失败。捕获真实底层请求，核对规范化字段及异常 cause；测试沿用项目 JUnit 版本，报告实际结果。
- A02～A08 的部分自动/审查项即使没有结构错误也保留 REVIEW_REQUIRED；工具不执行业务 Maven 测试、不证明动态注册或底层效果。真实日志未配置同样待验证。
- Atomic/App 分开规划提交和 MR。App 依赖新 Atomic 时先合 Atomic，再更新 App 基线；明确豁免的现有 MR 不追溯拆分，不擅自改写历史。

## 完整规范与检查器对照

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| A01 | 原子接口、公共 DTO、版本实现和 Latest 分别服从 Atomic roles，不能生成 App 编排类。 | `AtomicTool.layout`；自动（限定范围）。未覆盖：限定本契约类型；不推断项目模块名称。 | `FIXED` |
| A02 | 原子接口只声明本操作的 ReqDto→RspDto；公共 DTO 字段符合契约且不反向依赖实现。 | `AtomicTool.signature`；部分自动，仍需审查。未覆盖：检查源码类型和直接依赖；字段语义、外部 JAR 及传递依赖需审查。 | `FIXED` |
| A03 | 每个声明的支持版本实现相同原子签名，底层未接通时明确失败，不返回空成功。 | `AtomicTool.versions`；部分自动，仍需审查。未覆盖：占位和明显空返回会阻塞；真实调用效果需业务测试。 | `SCAFFOLD` |
| A04 | 版本注册注解来自实际框架，完整保留配置值，不凭空生成注册机制。 | `AtomicTool.registration`；部分自动，仍需审查。未覆盖：校验注解类型可解析及契约表达式；管理器动态发现、注解适用性和参数类型由编译/运行证明。 | `EXPLICIT_ANNOTATIONS` |
| A05 | Latest 按实际 class/interface 体系连接最新支持实现；首支持版本和版本顺序必须有历史证据。 | `AtomicTool.latest`；部分自动，仍需审查。未覆盖：校验显式继承关系；契约末版本是否最新及历史不支持路径需审查。 | `CONFIGURED_KIND` |
| A06 | Helper 保持单一 public 业务入口；沿用中文意图注释、英文异常、真实 entry/ret 日志 API，不为 ret 强造 try-catch；不泄露凭据。 | `AtomicTool.conventions`；部分自动，仍需审查。未覆盖：检查本业务或直接引用的源码 Helper（含继承）、中文存在、明显中文错误/敏感日志及显式出口；外部 Helper、注释含义和隐式路径待审查。 | `WHEN_LOGGING_CONFIGURED` |
| A07 | 公共契约迁移保留 JSON、公开接口及错误语义，Maven 接线和父版本保持一致。 | `AtomicTool.integration`；部分自动，仍需审查。未覆盖：校验本地父版本；wire 基线、完整 Reactor/classifier 和兼容性需构建测试。 | `NOT_GENERATED` |
| A08 | 通过真实版本选择、Latest、成功/非法输入/底层失败及旧版本不支持测试后交付。 | `AtomicTool.tests`；仅定位证据，需业务审查。未覆盖：报告测试未执行；源码存在不代表已运行，必须附实际命令和结果。 | `NOT_GENERATED` |
