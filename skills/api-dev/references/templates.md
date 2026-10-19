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
缺失私有签名时按 [L2EVPN 待核对资料](l2evpn-example.md#需要私有源码核对的项目)报告，不创建假框架类充当业务适配。

## 测试模板怎样使用

generate 的计划包含 `.atomic-api/work/<layer>-<operation>/tests/<Operation>TestPlan.java`。
这是无测试框架依赖的 Java 测试结构：每项先抛 `AssertionError("TEST_IMPLEMENTATION_REQUIRED: ...")`，main 可直接调用它们验证待办没有被误当成功。
按真实项目 JUnit 版本迁入相关模块 `src/test/java`，保留场景并将失败体替换为真实调用、夹具和断言；不要仅删除失败体或跳过测试。
工作区待办不是自动接入 Maven 的测试，不会因 verify/编译退出 0 就自动完成。工具不假定私有项目使用 JUnit 4 还是 5。
生成器会保护已定制的测试待办，后续开发走增量路线；不为重新 generate 删除用户断言。

所有编译、NOOP、冲突和占位回归使用仓库自己的合成测试框架，只证明工具机制，不代表真实业务项目集成。
