# 模板选择与替换项

从 [入口](../SKILL.md)选择 [Atomic](api-atomic.md) 或 [App](app-api.md) 后查本表。
模板随 Skill 位于 `templates/`（源码仓为 `skills/api-dev/templates/`），并打包进 JAR 的 `/templates/` 供生成器读取。

- **纯文档**：直接阅读并改写所需片段，无需 JAR 或生成器；按下方手工步骤装配。
- **工具辅助**：生成器读取 JAR 里的同一套模板，不动态读取目录中的文件；修改生成行为须重新构建 JAR 和打包。升级与定制见 [配置手册](configuration.md#升级与定制)。

## 文件名与角色命名

Java 文件名必须与其顶层公开类或接口名一致（含大小写）。以下规则用于新增类；已有类增量修改保留原命名，不为套模板批量重命名。

| 角色 | 命名规则 | 文件名示例 |
|---|---|---|
| Facade、请求/响应 Converter | 按业务域命名，同一业务域复用已有类，不为每个操作另建一套 | `<业务域>Facade.java`、`<业务域>RequestConverter.java`、`<业务域>ResponseConverter.java` |
| Handler | `<业务域前缀><操作>ApiHandler` | `IpranFaultSnapshotSaveApiHandler.java` |
| 请求 DTO | `<动作><业务对象>ReqDto` | `SaveFaultSnapshotReqDto.java` |
| 响应 DTO | `<动作><业务对象>RspDto` | `SaveFaultSnapshotRspDto.java` |

同一操作的请求/响应 DTO 使用相同名称主体，如 `SaveFaultSnapshot`。业务域前缀、动作和对象从真实需求及相邻代码确认，不把示例 Ipran/FaultSnapshot 当通用前缀，也不要求 Handler 的操作段与 DTO 主体机械拼接为同一字符串。

- **纯文档**：按此表命名所需新文件，文件内声明与文件名一致；复用类只新增成员。
- **工具辅助**：new 的类名是草稿，先核对 target、models.*.class、steps[].handler；Facade/Converter 默认按 business 命名，DTO 默认取 operation 主体，Handler 默认 `<Operation>ApiHandler`，须按真实业务域前缀调整后再预览生成。生成器按契约完整类名落盘，不自动猜测或拆解业务域/动作/对象。

工具会核对契约目标的路径/文件名，但不能自动证明角色语义命名或哪些类属于存量；按需求和修改前基线人工核对，不用新命名规则拒绝既有类的正常增量修改。

## Atomic

| 结构 | 实际模板 | 谁读取与装配 |
|---|---|---|
| 公共接口 | `atomic/interface.java.tpl` | AtomicTool.plan |
| 请求/响应 DTO | `common/dto.java.tpl`、`common/dto-field.java.tpl`、`common/unit.java.tpl` | AtomicTool.model → Templates.model |
| 每个版本实现与必填字段校验 | `atomic/implementation.java.tpl`、`atomic/required-field.java.tpl` | AtomicTool.plan |
| Latest（class/interface） | `atomic/latest.java.tpl` | AtomicTool.plan |
| 业务测试待办 | `atomic/tests.java.tpl` | Templates.testPlan，由 AtomicTool.plan 调用 |

## App

| 结构 | 实际模板 | 谁读取与装配 |
|---|---|---|
| DTO | 同上三个 common 模板 | Generator.model → Templates.model |
| 类/接口外壳 | `common/type.java.tpl` | Generator.target，随后用 AST 安全增量装配成员 |
| Facade 接口方法与实现方法 | `app/facade.method.java.tpl`、`app/implementation.method.java.tpl` | Generator.facade / implementation |
| Handler 三阶段与 Context | `app/handler.java.tpl` | Generator.handler，通过 common/unit 包装 package |
| 请求/响应转换方法 | `app/request-converter.method.java.tpl`、`app/response-converter.method.java.tpl` | Generator.converter / delegate |
| Converter 单例与依赖注入 | `app/converter-instance.java.tpl`、`app/injected-field.java.tpl` | Generator.target/field 装配 |
| service Delegate 异常边界 | `app/delegate.method.java.tpl` | Generator.delegate，按实际公开签名装配参数 |
| 多来源结果汇总 | `app/builder.java.tpl` | Generator.builder，仅 assembly=builder |
| 业务测试待办 | `app/tests.java.tpl` | Templates.testPlan，由 Generator.plan 调用 |

`.method.java.tpl` 是可复用的 Java 成员模板，不是独立编译单元；生成器将它放入已选角色的类/接口。
循环字段、步骤绑定、局部变量避冲突、AST 增量插入和 import 整理由 Java 代码处理，不另保留一套整类硬编码模板。
生成器只补中文意图注释，不自动插入日志；项目既有日志及统一覆盖按 N13-项目日志 审查，避免重复添加。

## 手工使用模板

两种方式都从真实源码确认类型与版本。纯文档开发，以及工具不支持的增量场景，按以下步骤装配；工具支持时由生成器完成同样的结构展开。

1. 从角色对应表选所需片段。新文件参考 `common/unit.java.tpl` 的 package 外壳与对应类模板；已有类只借用成员片段，不覆盖整类。`.method.java.tpl` 不能直接改后缀当源码。
2. 从实际接口、DTO、框架和调用者取得替换值；`fields/requiredFields/validation/handlerCalls/fieldBindings` 等是待展开的代码块，逐字段、逐步骤写真实 Java，不把它们当单个字段名。Handler 按阶段向 Context 补原子请求/原始响应字段，post 做真实转换，不能把模板 result 当作已完成映射。
3. 补全 import、真实访问器/构造器/throws、注入/注册、版本接线、项目日志（统一覆盖时不重复添加）。这些 AST 装配与项目接线不会因复制模板自动发生；所有 `{{...}}` 都须消除。
4. 保留明确失败占位直到接通真实业务，按所选层手册实现，再接入下方测试。模板只提供职责结构，getFacadeImpl、Handler/Context、getErrMsg、setter 等具体签名均按真实源码调整。

## 替换值来自哪里

`{{名称}}` 表示一个替换项，渲染为单次文本替换，缺项会报错，不执行模板表达式。
纯文档按真实源码替换；工具辅助由生成器读取 profile/contract。下表的配置键是同一事实在工具中的存储方式，不要求纯文档创建配置。已有实现和补版本只参考相关片段。

| 替换项 | 来源 |
|---|---|
| packageName、className、kind、接口/实现/DTO 的完整类型 | profile 的 roles/layout + contract 固定目标；business 用于业务子包，operation 用于方法/类名 |
| operation、methodName、apiVersion | 本次真实操作名、转换方法名、releases.id；不把 L2EVPN 示例版本当默认值 |
| requestType/responseType、inputType/outputType、externalResponse | 两层独立契约中的 models、steps、externalResponse；完整 Java 类型 |
| fields、fieldType/fieldName/capitalName/description、fieldBindings/fieldMapping | 契约字段、required/diagnostic、responseBindings；字段说明作为注释数据处理 |
| componentAnnotation、injectionAnnotation、handlerBase、internalException、throwsClause | App profile.types 与真实公开签名、errorMode |
| annotations、extendsClause、interfaceType、latestKind、parentType | Atomic 的真实注解、父接口、Latest 模式和支持版本顺序 |
| validation、requiredFields | 按请求字段 required 展开校验；增量开发时依据真实规则补充 |
| handlerCalls、parameters、arguments、各 Variable、failureAdaptation、defaultBehavior | 契约步骤、真实签名和异常策略；生成器负责组装，不能用它们藏真实业务实现 |

模板结构中的 getFacadeImpl、双泛型 Handler/BasicApiContext、getErrMsg、REST setter 等仍是工具当前支持的框架形状；配置类名不等于签名适配。
缺失私有签名时参照 [L2EVPN 源码核对表](l2evpn-example.md#源码依据与模板差异)报告，不创建假框架类充当业务适配。

## 测试模板怎样使用

- **纯文档**：直接按项目现有框架编写或扩展测试；可从 `atomic/tests.java.tpl` / `app/tests.java.tpl` 取场景，无需先生成 TestPlan。
- **工具辅助**：generate 将待办放在 `.atomic-api/work/<layer>-<operation>/tests/<Operation>TestPlan.java`，再按以下相同步骤接入。

模板待办是普通 Java 类：main 直接运行会失败，但没有测试框架注解，**只复制到 src/test/java 不代表 Maven 会执行它**。

1. 找同模块一个已实际执行的测试，确认框架版本、包名、类名发现规则、可见性、测试插件及定向运行命令。
2. 按该测试接入场景，复用项目夹具与依赖。若借用模板，补 package/框架注解，调整可见性与 static，移除待办 main，不另引一套框架。
3. 新增测试先确认发现与失败：模板待办保留 AssertionError，报告须包含新类/场景且因 TEST_IMPLEMENTATION_REQUIRED 失败；直接编写的测试用目标缺陷或受控错误确认断言能失败。零用例/跳过不算发现成功，编译错误不算测试红阶段；已有测试回归无需插入占位。
4. 补真实调用和断言，撤销受控错误后运行同一命令；核对目标场景实际执行、无失败且无意外跳过，保存本轮报告和退出码。

工作区待办不自动接入 Maven；check/verify 和编译成功均不能证明测试执行。定制待办受冲突保护，后续直接增量开发。
所有工具回归使用合成框架，只证明机制，不代表私有业务集成；无法确认的框架/命令应交由能读取源码的会话核对。

### 已执行的测试接入示例

工具仓已有 `AppTestPlanTest`，使用本仓实际 JUnit 5/Surefire，完成过四个占位失败→四个调用/断言通过的接入。这是工具生成机制的回归，不是私有业务框架或 L2EVPN 成功案例，不把 JUnit 5 强加给业务模块。

L2EVPN 已另以项目实际 JUnit 4.13.2 / Surefire 2.22.2 完成红绿验证，见 [真实业务测试记录](l2evpn-example.md#真实业务测试接入)。业务测试使用 org.junit.Test、public 非 static 测试方法、*Test 类名；工具回归仍使用 JUnit 5。报告应核对本轮时间；本轮编译失败时，target 下的旧测试报告不能算本轮结果。
