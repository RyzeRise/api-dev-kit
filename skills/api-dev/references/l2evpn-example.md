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

## 本轮工具源码校准与验证

本节基于工具仓 `6d86d0a` 的源码及随后新增的回归测试。当前会话没有私有 L2EVPN 业务源码、依赖 JAR、POM 或历史测试快照；不能列出它与真实签名的确定差异，更不能确认本例字段或首次支持版本。

### 源码依据与需要调整的指引

| 核对项 | 已读取的工具源码 | 结论及最小边界 |
|---|---|---|
| Handler/Context | app/handler.java.tpl、Generator.validateFramework、Fixtures.framework | 模板固定双泛型、嵌套 BasicApiContext 和三阶段；预检只查方法名，不能证明私有基类的签名/可见性兼容 |
| 异常/组件管理器 | app/delegate.method.java.tpl、Generator.validateFramework | 模板直接用 String 构造器、getErrMsg、initCause、getFacadeImpl(Class)；自动预检未完整证明这些签名，需真实编译/运行 |
| 日志 | LoggingSupport.validate/decorate | 自动日志支持静态工厂(Class)→配置日志类型，以及无参 entry/ret；不能据此断言私有日志工厂满足要求 |
| Atomic 注册/Latest | AtomicContract、AtomicTool.plan/check、atomic/interface/implementation/latest 模板 | 只验证契约声明的注解和继承形状；不自动证明真实管理器发现、最新支持或历史不支持 |
| App 复用已有类 | Contract.validate、Conventions.location、Generator.target/addMethod、IncrementalAppTest | 契约 target 能指向已有类名；包必须符合各角色 packagePrefix + business、sourceRoot 等约束。新方法兼容时可增量插入；同签名旧方法业务体不同则冲突保护，不覆盖 |
| Atomic 补支持版本 | TemplateWorkflowTest.addingVersionToImplementedAtomicDoesNotOverwriteHistoryOrWritePartialFiles | 历史实现已有业务体时，全量 generate 拒绝；已验证手工只添版本、更新 class Latest、扩充 releases 后可编译，旧实现不变，新版本占位仍被阻塞 |

本轮把“固定默认类名必然无法复用”修正为“默认命名与实际契约 target 分开；角色包结构和成员兼容性仍有限制”。并删除 App 字段格式示例中未证实的 neId:String，避免它被当成 L2EVPN 契约。

### TestPlan 接入记录

使用本工具仓真实依赖：`tools/cli/pom.xml` 的 JUnit Jupiter 5.11.4、Maven Surefire 3.5.2；源文件为 `tools/cli/src/test/java/dev/atomic/AppTestPlanTest.java`。它由 App 待办的四类场景接入 @Test，去掉 main/static，先保留失败体，再补调用和断言。

本次执行环境的实际命令如下，红阶段与绿阶段相同：

```text
python /workspace/scratch/649129bc1ddd/build-tools/mvn_env.py -B -f tools/cli/pom.xml -Dtest=AppTestPlanTest test
```

该环境已有适配器仅用于调用 Maven；通常维护环境的对应命令为：

```text
mvn -B -f tools/cli/pom.xml -Dtest=AppTestPlanTest test
```

| 阶段 | 退出码 | Tests | Failures | Errors | Skipped | 证明 |
|---|---:|---:|---:|---:|---:|---|
| 红：四个 AssertionError 占位 | 1 | 4 | 4 | 0 | 0 | Surefire 确实发现用例；每项因 TEST_IMPLEMENTATION_REQUIRED 失败 |
| 绿：替换为实际调用和断言 | 0 | 4 | 0 | 0 | 0 | 生成代码在合成框架中可编译并被调用，不是零用例或跳过 |

绿阶段实际断言：

- legalRequestUsesCorrectAtomicAndStages：运行生成 Handler.run，断言返回字段和底层调用次数。
- invalidInputDoesNotCallAtomic：null/空/空白参数失败，底层调用次数为 0。
- conversionPreservesMappedFieldAndRejectsNull：调用生成的响应 Converter，断言映射值和 null 响应异常。名称有意缩小，不宣称覆盖尚未测试的集合顺序、全部默认值或 wire。
- eachBoundaryFailurePreservesCause：在合成夹具中分别制造请求转换、Facade、响应转换失败，真实调用生成 Delegate，断言外部异常、每个阶段对应的内部 cause 消息及包装消息保留；同时断言请求转换失败不调用 Facade，后两种失败确实调用一次 Facade。

用例内编译运行的是本仓生成的 Java 类，底层/框架来自显式合成夹具，不是私有 Atomic 版本或真实注册容器。测试报告默认输出在 `tools/cli/target/surefire-reports/TEST-dev.atomic.AppTestPlanTest.xml` 及同名文本报告；每次运行可能覆盖，应在验证时分别保存红/绿报告，不能从最终绿文件反推红阶段。

增量路径实际命令（本环境同样通过上述适配器调用）：

```text
mvn -B -f tools/cli/pom.xml -Dtest=IncrementalAppTest,TemplateWorkflowTest test
```

结果：退出 0；5 个用例，0 failure/error/skip。其中包含旧 App 方法不变、新操作占位被阻塞，以及历史 Atomic 不覆盖、手工补版本的回归。

### 未验证项：不得转写为业务结论

- L2EVPN Handler/Context、异常、管理器、日志工厂、注册/Latest 的真实签名与当前分支行为。
- 查询/修改字段类型、必填条件、写入本端还是远端、节点八字段以及 null/空集合、顺序、默认值和旧错误契约。
- 私有业务测试框架版本、TestPlan 在实际业务模块的发现和执行、真实版本选择、REST/JSON 基线及项目 Reactor。

要完成这些项，需提供前节列出的相关源码/签名与同模块一个已执行测试及 POM。没有这些输入时，本轮只能报告“工具框架测试接入和合成回归已验证”，不能声称“L2EVPN 业务集成通过”。升级已安装文件仍须比较合并定制；init 不覆盖旧文件。
