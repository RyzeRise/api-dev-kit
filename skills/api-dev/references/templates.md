# 模板选择与替换项

从 [入口](../SKILL.md)选择 [Atomic](api-atomic.md) 或 [App](app-api.md) 后查本表。
源码模板在 `tools/templates/`；打包进 JAR 的 `/templates/`，init 复制到业务仓库 `.atomic-api/templates/` 供阅读和增量开发参考。
生成器读取 JAR 里的同一套模板，不读取安装副本；修改副本不会改变生成行为。升级与定制见 [配置手册](configuration.md#升级与定制)。

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
日志由 LoggingSupport 校验真实 API 后装饰方法；配置缺失时不发明日志类，保持待审。

## 替换值来自哪里

`{{名称}}` 表示一个替换项，渲染为单次文本替换，缺项会报错，不执行模板表达式。
新增操作由生成器替换；已有实现和补版本时只参考相关片段，不将整套模板覆盖已有文件。

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

generate 将待办放在 `.atomic-api/work/<layer>-<operation>/tests/<Operation>TestPlan.java`。
它是普通 Java 类：main 直接运行会失败，但没有测试框架注解，**只复制到 src/test/java 不代表 Maven 会执行它**。

1. 找同模块一个已实际执行的测试，确认框架版本、包名、类名发现规则、可见性、测试插件及定向运行命令。
2. 按该测试接入场景：补真实 package/框架注解，调整方法可见性与 static，移除待办 main；复用项目夹具与依赖，不另引一套框架。
3. **先保留 AssertionError 占位运行一次。** 报告应包含新类和场景，并因 TEST_IMPLEMENTATION_REQUIRED 失败；成功、零用例或跳过都说明接入尚未验证。
4. 将各失败体替换为真实调用和断言，再运行同一命令；核对这些场景确实执行、无失败且无意外跳过，保存报告和退出码。

工作区待办不自动接入 Maven；check/verify 和编译成功均不能证明测试执行。定制待办受冲突保护，后续直接增量开发。
所有工具回归使用合成框架，只证明机制，不代表私有业务集成；无法确认的框架/命令应交由能读取源码的会话核对。

### 已执行的测试接入示例

工具仓已有 `AppTestPlanTest`，使用本仓实际 JUnit 5/Surefire，完成过四个占位失败→四个调用/断言通过的接入。这是工具生成机制的回归，不是私有业务框架或 L2EVPN 成功案例，不把 JUnit 5 强加给业务模块。

L2EVPN 已另以项目实际 JUnit 4.13.2 / Surefire 2.22.2 完成红绿验证，见 [真实业务测试记录](l2evpn-example.md#真实业务测试接入)。业务测试使用 org.junit.Test、public 非 static 测试方法、*Test 类名；工具回归仍使用 JUnit 5。报告应核对本轮时间；本轮编译失败时，target 下的旧测试报告不能算本轮结果。
