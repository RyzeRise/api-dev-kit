# L2EVPN 案例：查询 EVPL 节点与修改 EVPL Service ID

本案例用于校准 [App 通用步骤](app-api.md)和 [Atomic 步骤](api-atomic.md)，不要求其他业务继承这些名称、字段或版本。
入口：[Skill](../SKILL.md)、[模板说明](templates.md)；仓库快速使用见根目录 QUICKSTART.md。

资料来自此前开发上下文，**不是完整、权威的私有源码快照**。下列名称是搜索线索，字段与行为是待核对基线；当前分支源码、调用者和测试优先。缺文件时列出缺失，不创建同名空类。

## 1. 给 Agent 的任务

> 请读取 `.atomic-api/skills/api-dev/SKILL.md` 与 L2EVPN 案例，开发“查询 EVPL 节点”和“修改 EVPL Service ID”的 App 接入。先核对当前分支已有方法和真实底层能力；已存在就增量修改。列出拟改文件，按 App 手册选择模板、实现和检查，报告实际编译/测试及未验证项。

仅开发一项时删去另一项。若两项在当前分支已完成，报告核对结果，不为演示修改代码。

## 2. 定位入口与复用能力

| 搜索线索 | 核对内容 |
|---|---|
| NetChatOpsApiFaultL2EvpnIpranDelegateImpl | 对外 query/modify、REST 请求响应、异常边界 |
| OpsIpranFaultL2evpnFacade / OpsIpranFaultL2evpnFacadeImpl | 公共业务方法、DTO、注册及默认实现 |
| IpranFaultL2evpnQueryEvplNodesApiHandler | 查询 pre/invoke/post、Context 与实际调用 |
| IpranFaultL2evpnModifyEvplServiceIdApiHandler | 修改字段、写入目标和失败处理 |
| AtomicApiGetEVPLInterface、GetEvplServiceReqDto/RspDto | 读取能力签名、支持版本、返回字段 |
| AtomicApiModifyEVPLInterface、ModifyEvplServiceIdReqDto | 修改能力、必填项及本端/远端区别 |
| 两项能力的 Latest、版本实现、注解和管理器 | 真实版本选择、首次支持和历史不支持行为 |

名称变动时沿 import、引用与调用找到真实类型。App 能复用底层就只改 App，缺能力才进入 Atomic 路线。

## 3. 核实查询与修改契约

查询模型线索：QueryL2evpnEvplNodesReqDto/RspDto，输入 neId；类型和校验从源码读取。
响应 evplNodes 的节点线索为 QueryL2evpnEvplNodesRspDto.L2evpnEvplNodeDto（public static）。核对以下八个字段：

| 字段 | 待核对含义 |
|---|---|
| evplNodeId、neId、deviceId、evplId | 节点、网元、设备和 EVPL 标识 |
| localServiceId、remoteServiceId | 本端/远端 Service ID |
| operateStatus、esi | 状态和 Ethernet Segment Identifier（以太网段标识） |

既有上下文区分 null 列表（未提供结果）与显式空列表（没有业务节点）；必须用当前实现和测试核对，不能当作通用业务约定。确认字段类型、节点顺序、默认值和缺失消息处理。
修改模型线索：ModifyL2evpnEvplServiceIdReqDto/RspDto。核对原子 DTO 与外部请求后确定改哪一端、哪些标识必填及成功/失败表达；这里不补造修改字段。

## 4. 按职责实现，按模板适配程度选择路径

| 位置 | 查询/修改工作 |
|---|---|
| IpranFaultL2evpnRequestConverter | REST → 公共业务请求，保留实际校验 |
| Facade 实现 | 编排 Query/Modify Handler.run |
| Handler.preHandler | 校验、网元/设备映射与原子请求构造，状态放 Context |
| Handler.invokeAtomicApi | 按实际管理器选择版本/Latest，调用 GetEVPL 或 ModifyEVPL |
| Handler.postHandler | 校验底层结果；查询沿用 IpranFaultL2evpnEvplNodeConverter 等实际节点转换 |
| IpranFaultL2evpnResponseConverter | 业务 → REST，核对完整性和请求回填 |
| Delegate | 在同一异常边界完成请求转换、Facade 调用、响应转换；保留 getErrMsg/cause |

公共业务 DTO 归属 ipran-app-api-common；实现属于实际 ipran-app-api-*，REST 适配在 service，最终以真实模块为准。

现有 OpsIpran... 命名、专属内部节点、请求字段回填可能与通用模板不符。此时按 App“模板不适用”分支增量修改，不执行整套 generate。
新操作只有经真实 profile/契约适配后才执行 App 手册的 new → 预览 → generate；operation/business 替换为本次操作。
Atomic 首支持版本不在本资料中，不能把命令里的 R26C10 当事实；补版本按 Atomic“补版本”流程保留历史实现。

## 5. 验收本例

- 从 Handler.run 验证合法请求选正确版本；非法输入不调用底层；查询成功、查无数据、修改成功及底层失败符合契约。
- 节点转换核对八字段、null/空列表、顺序、默认值；修改请求捕获真实写入参数，不凭接口名推断。
- 分别让请求转换、Facade 和响应转换失败，断言原错误、getErrMsg/cause 和既有 String 错误形式。
- 核对实际 Facade/Atomic 注册、Latest、模块依赖、处理器和 parent.version；用真实 JDK/项目测试证明。

结构已适配：按本层真实契约 verify。未适配：App 辅助 check，Atomic 需真实契约才可 check/verify；两种情况都按 Skill 的完成条件报告状态。

## 需要私有源码核对的项目

按本次受影响调用链请求以下具体文件或签名，无需索取整个仓库：

1. Query/Modify ReqDto、RspDto、内部 L2evpnEvplNodeDto，以及 service REST 请求响应。
2. 对应 Facade 接口/实现、Query/Modify Handler、Delegate、请求/响应/节点 Converter 与已有定向测试。
3. EVPL Get/Modify Atomic 接口、各受影响版本实现、Latest、版本注解及管理器选择/注册约定。
4. Handler 基类与 BasicApiContext 的泛型、生命周期及 run 签名；内部/外部异常构造器、getErrMsg/cause；组件管理器 getFacadeImpl、组件/注入注解；日志工厂、entry()/ret() 的实际签名。
5. 相关公共/实现/service 模块 POM、父 modules、dependencyManagement、classifier、MapStruct processor/binding 与测试框架版本。
6. 有兼容性变更时，ObjectMapper 设置、消费者/JSON 基线、历史默认值与首次支持版本证据。

这些资料未核对前，只能报告通用模板/检查器验证，不能报告 L2EVPN 业务集成通过。
