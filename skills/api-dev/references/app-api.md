# 开发 App API：以 L2EVPN 为例

先读 SKILL.md。本手册用于 ipran-app-api 业务开发，示例采用本项目已有的两项能力：**查询 EVPL 节点**与**修改 EVPL Service ID**。类名用于定位真实源码；包名、方法大小写、字段及错误消息均以当前业务仓库为准。

阅读顺序：先看第 1～6 步，明确本次文件和调用关系，再看末尾规范表。示例代码已存在时做核对和增量修改，不重新添加一套同名接口。

## 1. 查清入口和已有能力

先找以下文件，阅读方法体而不只看文件名：

| 文件 | 要从中确认什么 |
|---|---|
| `NetChatOpsApiFaultL2EvpnIpranDelegateImpl` | 对外 query/modify 方法、REST 请求响应类型、异常包装 |
| `OpsIpranFaultL2evpnFacade` / `OpsIpranFaultL2evpnFacadeImpl` | 业务方法、ReqDto/RspDto、版本/默认注册约定 |
| `IpranFaultL2evpnQueryEvplNodesApiHandler` | 查询的 pre/invoke/post 分工、真实 Atomic 调用和 Context |
| `IpranFaultL2evpnModifyEvplServiceIdApiHandler` | 修改参数、写入调用、结果及错误处理 |
| `AtomicApiGetEVPLInterface`、`GetEvplServiceReqDto/RspDto` | EVPL 读取能力的真实请求、响应和支持版本 |
| `AtomicApiModifyEVPLInterface`、`ModifyEvplServiceIdReqDto` | 修改 Service ID 能力及字段；不能从名字猜哪些 ID 要改 |
| 当前能力的 Latest、注册和底层调用 | 版本选择、首次支持以及旧版本行为 |

如果某个名称在当前分支已变更，沿 import、引用和方法调用找到实际类型。不要创建一个同名空类补齐依赖。已有底层能力够用就只改 App；底层确实缺失才读 api-atomic.md。

## 2. 写下这次的输入、输出和调用链

L2EVPN 查询已知的公共业务模型是 `QueryL2evpnEvplNodesReqDto` / `QueryL2evpnEvplNodesRspDto`。查询使用 neId；Java 类型和校验从当前源码核实。结果含 evplNodes，各节点包括：

| 字段 | 含义（以实际契约为准） |
|---|---|
| evplNodeId、neId、deviceId、evplId | 节点、网元、设备和 EVPL 标识 |
| localServiceId、remoteServiceId | 本端/远端 Service ID |
| operateStatus、esi | 状态和 Ethernet Segment Identifier（以太网段标识） |

专属节点 DTO 位于 `QueryL2evpnEvplNodesRspDto.L2evpnEvplNodeDto` 内，使用 public static。不要为了生成器只支持顶层 DTO，又把它拆回公共顶层类。

修改使用 `ModifyL2evpnEvplServiceIdReqDto/RspDto`；逐项核对原子修改 DTO 和外部请求，确定哪些字段必填、改哪一端、成功/失败如何表达，本文不虚构其字段。

把查询调用链落实到实际文件：

`service Delegate → RequestConverter → Facade → Query Handler.run → 实际 EVPL Atomic → Handler.post → ResponseConverter → REST 响应`

这表示一次业务调用的经过：Delegate 内先转请求，调用 Facade，再转响应；两次转换和调用都在既有异常边界内。公共业务 DTO 放 ipran-app-api-common，Handler/Facade 实现放对应 ipran-app-api-*，REST 适配放 service。

## 3. 准备操作契约，先确认模板适不适用

contract YAML 是给工具的“本次操作说明”：类在哪里、请求字段是什么、响应字段怎么映射；不是接口报文，也不是业务配置。

**新建且符合当前模板的操作**，在业务仓库根目录运行：

```text
sh .atomic-api/tool/atomic-api.sh new --layer app --operation queryL2evpnEvplNodes --business l2evpn
```

这会创建 `.atomic-api/contracts/queryL2evpnEvplNodes.yaml` 草稿。**该命令不是迁移已有 L2EVPN 实现的命令**：现有 `OpsIpranFaultL2evpnFacade` 等命名和内部节点 DTO 可能不符合通用模板。先比较生成草稿和真实代码，不要直接 apply。

| 草稿位置 | 填写依据 |
|---|---|
| description | 本次真实用途，如查询 L2EVPN 的 EVPL 节点 |
| models.request.fields | 从现有查询请求核对 neId 的完整 Java 类型、中文说明及 required |
| models.response.fields | 仅在模板适配后，从现有响应核对集合和内部节点完整类型；填对类型不会补齐内部类生成能力 |
| externalResponse | 当前 service 方法实际使用的 REST 响应完整类名，不新造假模型 |
| responseBindings | 外部响应字段名 → 业务响应字段名；双方同为 evplNodes 时为 `evplNodes: evplNodes`，类型仍须兼容 |
| errorMode | 只有外部响应为 String 才填写 exception 或 message，按历史错误契约选择 |

models 中的 class/sourceRoot、target、steps 由工具按 profile 生成。不要改成另一套名字强行覆盖既有实现，也不靠修改 roles 绕过检查。泛型类型在 YAML 中加引号；绑定只填字段名，不写 Java 表达式。

### 生成器不适用时怎么做

当前模板生成普通顶层 DTO、固定命名的 Facade/Handler/Delegate 和普通字段映射；**不能原样重建这次完整 L2EVPN 实现**，也不生成 REST 路由定义、Maven 模块接线或真实 Atomic 调用。

若遇到已有 OpsIpran... 命名、专属内部节点、需要请求字段回填的响应等模板不支持的结构：

1. 保留当前类和契约，按本手册直接增量实现；列出模板的具体不适配点。
2. 不在已有文件上强制运行 generate，不删除类、不把复杂 DTO 改简单来迎合工具。
3. 维护者适配生成器/检查契约后，才用同一真实结构执行 verify。适配前，在业务仓库根目录执行只读辅助检查：

```text
sh .atomic-api/tool/atomic-api.sh check --layer app --format json
```

Windows PowerShell：

```powershell
& ".atomic-api/tool/atomic-api.cmd" check --layer app --format json
```

此命令读取实际 profile，扫描可发现的 App 入口；不写文件、不需要虚构操作契约，也不保证只检查 L2EVPN。未提供 v2 契约时，N 规则可能显示 NOT_EVALUATED；它不能替代 v2 verify。明确报告“未完成工具验收”，仍须真实编译和定向测试。缺外部框架类型时先依据项目实际依赖补源码或给同一命令追加可重复的 --classpath 参数，指向真实 JAR；不要捏造空类。


没有真实项目 profile 和适配过的契约时，不声称“把需求发给 Agent 就能自动生成完整可交付接口”。

## 4. 适用模板时，预览并生成结构

```text
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --apply
```

先检查预览的路径、类名、字段和涉及已有文件，再 apply。单步通常生成两个 DTO、Facade 接口及实现、一个 Handler、Delegate、两个 Converter；多来源汇总才需要 Builder。重复未修改骨架应 NOOP；实现业务后再次生成遇到冲突是保护行为。

这些文件只是结构。Handler 的 `ATOMIC_API_UNIMPLEMENTED` 表示尚未接通真实底层，生成成功不是接口开发完成。

## 5. 实现查询和修改逻辑

查询按下面职责编写，并对照已有 L2EVPN 实现核实具体字段：

| 位置 | 具体要做的事 |
|---|---|
| RequestConverter | REST 请求转公共业务请求；不调用外部能力 |
| Facade 实现 | 调用 Query Handler 的公开 run；单步直接返回，不增加无意义 Builder |
| Handler.preHandler | 校验请求；按真实 Atomic DTO 构建请求。网元/设备映射按已有能力处理；请求和中间状态放 Context，不放组件实例字段 |
| Handler.invokeAtomicApi | 按项目管理器获取实际支持的 Atomic/Latest，发起一次真实调用并保存结果；不在这里重新组装 REST 响应 |
| Handler.postHandler | 校验底层响应，再转成业务节点；沿用 `IpranFaultL2evpnEvplNodeConverter` 等既有转换方式 |
| ResponseConverter | 业务响应转 REST 响应，处理既有回填与完整性校验；不查外部能力或重算业务状态 |
| Delegate | 连接请求转换、Facade 调用、响应转换；保留已有 ServiceException、getErrMsg 和 cause 适配 |

修改路径同样经过 Modify Handler。按真实修改请求决定操作字段，复用 `AtomicApiModifyEVPLInterface`；底层失败保留明确英文错误及原因，不能把失败变成空成功。

L2EVPN 已有行为必须保留：null 列表表示未提供结果，不得随意改为空列表；显式空列表表示没有业务节点。字段类型、节点顺序、默认值及缺失消息校验以当前实现和测试为准。JSON 注解先核对实际 ObjectMapper、消费者和 wire 基线，再决定能否删除。已有 MapStruct 沿用 INSTANCE、严格目标策略和项目处理器配置。

## 6. 检查、编译、测试，再交付

模板及契约已适配时运行：

```text
sh .atomic-api/tool/atomic-api.sh verify --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --format json
```

看 findings 中的文件、message 和 fix；FAIL 修源码，INCOMPLETE 补实际依赖，REVIEW_REQUIRED 用源码/历史基线/测试确认。仅交骨架时保留占位，verify 非零属预期；不得删标记伪装可用。

接着执行实际项目的编译和测试，不用一条虚构的 mvn 命令冒充验证：

- Handler 测试从 run 入口验证：合法请求传给正确原子版本，非法参数不调用底层，成功/查无数据/底层异常得到约定结果。
- 节点转换测试核对八个字段、null/空集合与列表顺序；修改测试核对真实写入字段和失败原因。
- Delegate 分别覆盖请求转换、Facade 调用、响应转换失败，验证 getErrMsg/cause、请求回填与既有 String 错误形式。
- 检查 Facade 注册和默认实现、Maven modules/dependencyManagement/消费依赖/classifier。合主线后新增模块 parent.version 必须与当前父模块一致；先相关模块 -am，再按影响编译 service。源码检查无法证明处理器、注册运行或 Reactor 完整。

交付说明：“改了哪些文件；复用了哪条 Atomic；查询/修改怎么走；运行了什么命令及结果；什么尚未验证”。不把 check、validate、生成成功或测试文件存在写成编译/业务测试通过。

## 首次接入时怎样填写 profile

本节只在首次接入或框架变化时使用。读取 `.atomic-api/profile.yaml` 的现有完整结构并修改真实值；没有配置时先运行 init。不要手工只建下面几行导致缺字段。

| types 下的键 | 从哪里找到真实完整类名 |
|---|---|
| componentManagement | Delegate 使用的组件管理器，核对 getFacadeImpl 等实际方法 |
| facadeAnnotation | 现有 Facade 接口的注册注解 |
| handlerBase | 现有 Query/Modify Handler 的框架基类 |
| internalException | Handler/Facade 的内部业务异常，核对构造器和 getErrMsg |
| externalException | service 对外包装异常，核对构造器及 cause 保留方式 |
| injectionAnnotation | 当前代码使用的注入注解 |
| componentAnnotation | 具体 Handler/Helper 使用的组件注解 |

当前 App 生成模板按双泛型 Handler 基类和 BasicApiContext 生命周期编写，使用 preHandler、invokeAtomicApi、postHandler、run，以及内部异常 getErrMsg、管理器 getFacadeImpl、REST 响应 setter。同名不代表完整签名兼容；若实际框架不同，先适配工具，不能伪造项目框架。最终用真实编译证明可用。

| roles 下的键 | 应指向哪类代码的 packagePrefix 与 sourceRoot |
|---|---|
| facade | 公共模块的 OpsIpranFaultL2evpnFacade 等接口 |
| request | 公共模块的 Query/Modify 请求 DTO |
| response | 公共模块的 Query/Modify 响应 DTO |
| implementation | 业务模块的 OpsIpranFaultL2evpnFacadeImpl |
| handler | 业务模块的 Query/Modify Handler |
| delegate | service 模块的 NetChatOpsApiFaultL2EvpnIpranDelegateImpl |
| requestConverter | service 模块的 IpranFaultL2evpnRequestConverter |
| responseConverter | service 模块的 IpranFaultL2evpnResponseConverter |

每个 role 都写真实包前缀与模块源码根目录；layout 配置相应 packagePrefix/sourceRoot。磁盘目录前缀与 package 不一致时用 directoryPrefix，不批量搬迁历史源码。这里只配置归属，不会改变生成器固定类名，也不能单靠正确 roles 消除 L2EVPN 的模板限制。

例如 request 的 sourceRoot 从当前公共 DTO 所在模块截到 src/main/java，packagePrefix 从其 package 声明归纳；不要从文件系统目录猜 package。targetJava 与业务编译版本一致。facadeDefault 按既有默认实现选 throw 或 return-null；已有禁用注册还需核对 disabledFacade.configuration/mapField。

logging 可选，字段及当前支持的工厂签名见 SKILL.md 末尾；未配置仍需人工核对日志，不宣称通过。更多未知类型通过真实源码或 --classpath 的实际 JAR 解析，不改 profile 以掩盖源码违规。

## 完整规范与检查器对照

共用 N07/N11～N15/N23/N24 在 SKILL.md；下表保留 App 专属规范及自动检查边界。

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
