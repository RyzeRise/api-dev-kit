# L2EVPN 案例：查询 EVPL 节点与修改 EVPL Service ID

本案例用于校准 [App 通用步骤](app-api.md)和 [Atomic 步骤](api-atomic.md)，不要求其他业务继承这些名称、字段或版本。
入口：[Skill](../SKILL.md)、[模板说明](templates.md)；仓库快速使用见根目录 QUICKSTART.md。

2026-10-19 已直接核对业务仓主分支及 L2EVPN 分支，具体提交、适配差异与实际测试见文末。以下是该次源码基线；后续开发仍以当前分支源码、调用者和测试为准，不能据本例补造缺失类型。

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

查询模型：QueryL2evpnEvplNodesReqDto/RspDto。请求 neId、evplId 均为 Long 且都必填；当前 Handler 只检查 null，不新增正数/范围约束。
响应 evplNodes 的节点线索为 QueryL2evpnEvplNodesRspDto.L2evpnEvplNodeDto（public static）。核对以下八个字段：

| 字段及源码类型 | 含义 |
|---|---|
| evplNodeId:String、neId:Long、deviceId:String、evplId:Long | 节点、网元、设备和 EVPL 标识 |
| localServiceId:Long、remoteServiceId:Long | 本端/远端 Service ID |
| operateStatus:String、esi:String | 状态和 Ethernet Segment Identifier（以太网段标识） |

查询 Handler 对响应、service、evplNodes 容器、nodes 列表逐层判 null，列表中 null 节点也报错；显式空列表合法。循环映射保留全部节点和原顺序，不筛选为单端。
修改请求 neId、evplId、localServiceId、remoteServiceId 均为 Long 且必填；本端与远端分别原样传入 Atomic DTO，不互换。修改响应 message 为 String；底层空白成功正文仍生成成功说明。

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
当前源码把 getServiceByEvpl 定义在 R26C10，并由 Latest extends R26C10；R26C00 只有原有查询方法。修改接口只有 R26C10 与 Latest，没有 R26C00。以上证明当前声明位置，不等于完整历史或部署支持范围；补版本按 Atomic 的嵌套版本增量路线保留历史实现。

## 5. 验收本例

- 从 Handler.run 验证合法请求选正确版本；非法输入不调用底层；查询成功、查无数据、修改成功及底层失败符合契约。
- 节点转换核对八字段、null/空列表、顺序、默认值；修改请求捕获真实写入参数，不凭接口名推断。
- 分别让请求转换、Facade 和响应转换失败，断言原错误、getErrMsg/cause 和既有 String 错误形式。
- 核对实际 Facade/Atomic 注册、Latest、模块依赖、处理器和 parent.version；用真实 JDK/项目测试证明。

结构已适配：按本层真实契约 verify。未适配：App 辅助 check，Atomic 需真实契约才可 check/verify；两种情况都按 Skill 的完成条件报告状态。

## 真实源码校准（2026-10-19）

源码基线：业务主分支 `b72b55e4b89d9c3886f409e70560fa6a9a405559`、L2EVPN 分支 `4d30ac120741804331ac624e7e79cb59c198978d`；工具基线 `186568d`（0.7.2）。已逐文件比较的 Query/Modify Handler、Facade 接口/实现、Delegate、两个 service Converter 共七个文件完全一致。未为演练重建这些接口。

### 源码依据与模板差异

下表路径按模块/文件名定位；以文件 package 声明为准，物理目录前缀不可用于推断 Java 包名。

| 源码依据 | 当前事实 | 指引/模板应如何使用 |
|---|---|---|
| api-atomic-common/.../apihandler/ApiHandler.java:18,36–53,107 | 双泛型 ApiHandler<I,O>；protected BasicApiContext preHandler(I)、invokeAtomicApi(BasicApiContext)、O postHandler(BasicApiContext)；public O run(I)，均 throws OpsApiException；Context 为 protected static | 三阶段骨架相符。实际基类为异常追加阶段与请求；非 OpsApiException 会被转成 RuntimeException，不能宣称任意错误都会保留 cause |
| common/.../exception/OpsApiException.java:18–55 | extends ServiceException；String 构造器保存 errMsg；Lombok @Getter 生成 getErrMsg；getMessage 另加类/方法前缀 | 源码解析不执行 Lombok，getAllMethods 可能漏 getter；不能为过预检改异常或手写重复 getter。外部错误文本沿用 getErrMsg，不擅改为 getMessage |
| common/.../component/OpsComponentManagement.java:12–14 | `<T> T getFacadeImpl(Class<T> intf)` | 与 Delegate 模板泛型入口相符；参数是 Facade 接口 Class |
| common/.../annotation/OpsFacadeInterface.java；function-sdk/.../OpsComponentManagementImpl.java:89–167 | 注解在实现类上，元注解 Component；运行器 initFacadeInterfaces 扫描并匹配注册接口 | 不是给 Facade 接口加该注解；现有 disableMap 已注册 OpsIpranFaultL2evpnFacade。增量方法复用原注册，不另造实现 Bean |
| api-atomic-common/.../atomicapi/AtomicApiManagement.java:31–69；AtomicApi.java:23–48；EvplApiOperation.java:17–19 | @AtomicApi(category,oper,superInterface)；管理器按 Class 唯一注册，不做版本排序；EVPL 操作 getEvpl/modifyEvpl 已定义 | 补版本时移交 Latest 注册；不能给所有历史实现补注解造成重复注册 |
| AtomicApiGetEVPLInterface.java:21–35；AtomicApiGetEVPLR26C10.java:21–28 | 嵌套 Latest extends R26C10 extends R26C00；新实现 extends 旧实现 implements Latest,R26C10 | 当前独立 Latest 类型模板、固定命名和每 release 非空注解都不能如实表达此结构。手工增量，不把 interface 模式误当已经兼容 |
| AtomicApiModifyEVPLInterface.java:11–19；AtomicApiModifyEVPLR26C10.java:16–25 | 修改接口从 R26C10 开始；String modifyEvplServiceId(ModifyEvplServiceIdReqDto) throws OpsApiException | 不凭空加 R26C00，不强改 String 为通用 RspDto |
| L2EVPN Facade、ApiHandler、AtomicApiManagement 的日志声明 | 实际 @Slf4j 或 org.slf4j.LoggerFactory.getLogger(Class)，未见 entry/ret 调用 | 已通过实际 slf4j-api 2.0.17 JAR 的 javap 核实 Logger 无 entry/ret、工厂有 getLogger(Class)。该方案不满足自动日志要求；保留实际 API 并标明待适配，不伪造 entry/ret 或改 profile 声称已满足 |
| service/.../NetChatOpsApiFaultL2EvpnIpranDelegateImpl.java:33–60 | 查询公开返回 List<L2EvpnEvplNode>，失败包装 ServiceException 并 initCause；修改公开返回 String，OpsApiException 转失败字符串 | 查询不能套“构造外部响应对象并 setter”模板；修改保持 message 模式。两者不可被统一异常改写 |
| service/.../IpranFaultL2evpnResponseConverter.java:26–58 | 查询逐节点映射，拒绝 null 响应/列表/节点；修改拒绝缺失或空白 message | 保留 MapStruct 严格目标策略、内部节点及列表映射。复杂转换走既有 Converter 增量 |

根 AGENTS/knowledge-spec 部分模块位置仍是旧结构，而本次已读源码公共类型位于 api-atomic-common / ipran-app-api-common。已有接口不搬迁；新建结构与仓库硬规则冲突时，须列出具体冲突后解决。主分支也已将 L2EVPN parent.version 更新到 16.10.346；旧特性分支为 16.10.340，跨分支操作须重新核对，不能写死案例版本。

### 新方法复用已有 Facade/Delegate

1. 先读真实公开参数，包括 HttpContext 和请求对象；已有方法体保留。复用现有 Facade 接口/实现、Delegate 和请求/响应 Converter，仅添加本次操作所需成员。
2. 新 Handler 参考已确认的三阶段签名：pre 校验并构造 Atomic 请求，invoke 通过 getAtomicApi(Interface.Latest.class) 调用，post 校验转换结果；每次请求状态存在 Context。
3. 只有实际 target、角色包位置、外部响应及公开参数均适配才 dry-run/apply。本例 List 返回、Lombok getter 解析及嵌套节点使整套生成不适用；手工增量，不能调整既有公开签名来凑模板。
4. 本次未新增业务方法，仅核对真实路径；工具仓 IncrementalAppTest 另外验证兼容合成结构可以复用目标类、保留旧方法体。不能据此宣称任意真实类都可自动增量。

### 真实业务测试接入

在 L2EVPN 分支的 `ipran-app-api-fault/ipran-app-api-fault-l2evpn/src/test/java/.../handler/L2evpnHandlerTest.java` 接入四个 Handler 场景。真实框架为 JUnit 4.13.2、Surefire/surefire-junit47 2.22.2；JDK 21.0.11、Maven 3.9.0。通过项目真实依赖编译，Mockito 只模拟 AtomicApiManagement 和具体 Latest，不调用网络。

按业务仓 `.docs/本地maven构建.md` 在同一 PowerShell 中设置 JAVA_HOME、mavenExe、mavenCommonArgs（含真实 settings、本地仓库、container、离线、禁用颜色）；红绿阶段执行同一命令：

执行前先确认**当前目标分支存在该测试类**，且其 package、类名和四个测试方法可被本项目运行器发现。这里的测试文件是本次本地接入产物，不保证其他检出分支已有它。

```powershell
& $mavenExe @mavenCommonArgs -pl ipran-app-api-fault/ipran-app-api-fault-l2evpn -am test '-Dtest=L2evpnHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=false' '-Dsurefire.rerunFailingTestsCount=0'
```

`failIfNoSpecifiedTests=false` 只用于让 Reactor 中没有该类的依赖模块继续，**也可能使目标类缺失时零用例退出 0**。因此成功必须同时满足：退出码 0、目标模块本轮 Surefire 报告时间不早于启动时间、测试类全名正确、四个指定场景均实际执行（tests=4、failures/errors/skipped=0）。缺类、缺新报告、零用例、场景缺失或跳过均算未完成验证；不能只看 BUILD SUCCESS。扩展用例后按实际场景名单核对，不机械写死四项。

| 阶段 | 退出码 | 执行 | 失败 | 错误 | 跳过 | 完成时间（UTC+8） |
|---|---:|---:|---:|---:|---:|---|
| red-confirmed：保留失败占位 | 1 | 4 | 4 | 0 | 0 | 2026-10-19 15:41:33 |
| green-verified：真实调用和断言 | 0 | 4 | 0 | 0 | 0 | 2026-10-19 15:44:28 |

七个 Reactor 模块全部构建通过。四项实际断言：

- Query Handler.run 捕获真实 Atomic 请求 neId/evplId；两个节点八字段全部映射且顺序不变。
- null 请求、缺失 neId/evplId 在 pre 阶段失败，管理器零交互；校验 getErrMsg 和阶段信息。
- 响应、service、容器、列表逐层缺失及 null 节点在 post 阶段报对应原因；显式空列表返回非 null 空结果。
- Modify Handler.run 捕获 neId、evplId、不同的 localServiceId/remoteServiceId；底层空正文仍返回既有成功说明。

日志和分阶段报告保存在业务分支 `.atomic-api/work/source-calibration-20261019/`：`red-confirmed-status.json`、`red-confirmed.log`、`green-verified-status.json`、`green-verified.log`，以及对应前缀的 Surefire XML/文本报告。正式报告原位为本模块 target/surefire-reports 下的 L2evpnHandlerTest 报告。本次修复了长路径归档，并按报告时间排除编译失败后残留的旧报告；中途编译错误不能计作红阶段发现成功或绿阶段执行成功。

### 已验证与仍未验证

已验证上述静态源码事实、真实 Handler/DTO/MapStruct 代码编译，以及四项 Handler 行为。Query/Modify Handler、Facade 接口/实现、Delegate、请求/响应 Converter 共七个文件已比较，未修改生产实现。

未验证：真实 Spring/Atomic 注册容器启动和运行时版本选择、Delegate 的三处失败执行、REST JSON 序列化/未知字段策略及真网元调用、历史部署支持范围、弱模型独立完成新增任务。这里不把 mock Latest 说成真实管理器选版通过，也不把当前接口声明当作历史支持证明。

当前工具 check/verify 对本例的完整适配仍未完成。升级已安装 Skill 要比较合并定制；init 不覆盖旧文件。后续分支变动时重新读取本节引用源码和同模块实际测试。
