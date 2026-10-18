# Contract 与源码的边界

Contract 是单个操作的设计输入，不是全局 API 目录。真实包名、方法签名和模块位置从源码确认；`sourceRoot` 与 package 独立配置。只写声明式绑定，不写 Java 表达式。

| 字段 | 含义 |
|---|---|
| `target.facade` / `implementation` | 公共业务 Facade 及业务实现 FQN |
| `target.delegate` | REST 入口实现 FQN，已有接口签名优先 |
| `target.converter` | 请求转换器 FQN |
| `target.responseConverter` | 可选的独立响应转换器 FQN；新实现推荐指定 |
| `models.request` / `response` | 业务请求/响应模型、sourceRoot 和字段，不预设 SDK 归属 |
| `externalResponse` | 已有 REST 响应类型；当前生成器支持可构造且有 setter 的对象 |
| `steps` | Handler 次序与输入绑定 `request`、`request.field`、`steps.id`、`steps.id.field` |
| `assembly` | 单步 `direct` 或有业务汇总需要的 `builder` |
| `responseBindings` | 外部响应字段到业务响应字段的映射，类型通过 AST 核对 |

参考 `examples/fault-response.yaml` 的独立响应适配方式。`single-handler.yaml` 与 `multi-handler.yaml` 保留旧 contract 兼容示例；省略 responseConverter 会沿用 Delegate 私有映射方法，不能据此要求新故障业务回退旧分层。

所有目标类型相互不同，不能与生成的 DTO/Handler/Builder 冲突；路径不能越界。profile 的 `facadeDefault` 显式选择 `throw` 或 `return-null`，保留原 default 行为。`disabledFacade` 只配置已有禁用态注册容器，不凭空构造注册机制。

## 生成能力限制

生成器创建 Fail-closed 骨架，原子能力查找、原子请求组装和真实业务判断需要实现。生成的 Converter 默认方法可以编译，不注入 MapStruct 依赖。项目采用 MapStruct 时人工按现有风格迁移并验证生成实现。

String 响应、嵌套节点模型、复杂集合/字段回填、SimpleApiHandler、特有 JSON 注解及原子版本族不由当前 schema 自动建模。按真实框架与领域 skill 手工实现，不伪造响应对象、改外部契约或在 YAML 填表达式绕过限制。`responseBindings` 不表达请求字段回填；有该需求时扩展人工转换实现并单测。

已有实现体与生成计划不同会拒绝覆盖。更新设计后先审阅 dry-run，不用删除业务文件的方式强行应用。重复执行相同生成应 NOOP。
