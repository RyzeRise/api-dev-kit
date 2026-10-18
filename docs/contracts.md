# 新 API contract v2

用 atomic-api new 生成草稿，而非让模型从头编写所有 FQN。维护者先配置 profile.roles；operation/business 决定名称和业务子包。草稿只填写用途、请求/响应字段、外部响应及映射。

v2 需要 business、ReqDto/RspDto、独立且同目录的两个 Converter，以及所有目标与角色位置一致。verify 不接受 v1；降级契约不能通过新增验收。参考 [strict-handler.yaml](../examples/strict-handler.yaml)。

草稿为空时 schema 故意不通过，不虚构真实字段或外部模型。字段用真实类型/描述及必要的 required、diagnostic；不能把凭据设为 diagnostic。

## 通用字段与存量兼容

下列字段说明同时适用 v1/v2；v1 为存量保留，独立响应 Converter 在 v1 可选、v2 必需。

每个 API 使用一份 YAML。Schema 定义见 `schemas/api-contract.schema.json`，字段完整示例见 `examples/`。不创建全局 API 清单。

## 职责与维护

contract 描述设计意图；源码描述当前实现。新增 API 时生成器读取 contract，独立检查器不依赖 contract。提供 contract 时额外对照设计。

后续手工修改实现造成 drift 时报告差异，不自动反向修改 contract，也不据 contract 覆盖业务代码。由开发者确认是更新设计还是修正实现。

## 字段

| 字段 | 含义 |
|---|---|
| version | 新增 2，旧版 1 仅保留兼容 |
| business | v2 必需，Java 标识符，角色包下的业务子包 |
| operation / description | Java 方法名及用途 |
| target | Facade、实现、Delegate、请求 Converter 的完整类型名；现有类型可以自动识别，首版 contract 保留明确目标用于消除歧义 |
| target.responseConverter | 可选独立响应 Converter，v2 必需；只有 v1 省略时保留旧 Delegate 私有映射兼容路径 |
| models | 请求 DTO、业务响应 DTO 的完整类型、sourceRoot、字段及非空约束 |
| externalResponse | 已有外部响应完整类型名，不生成/修改其模型；String 用 java.lang.String |
| errorMode | String 必须显式 exception/message；对象可为 null/exception |
| steps | 按顺序执行的 Handler；声明 id、类、输出类型、输入绑定、输入类型及 sourceRoot |
| assembly | direct 直接返回末步结果，或 builder 汇总所有步骤 |
| responseBindings | 外部响应字段来自 业务结果的字段路径 |

sourceRoot 是仓库内相对源码根目录，与 package 分开；不能指向仓库外。缺失目标文件的创建需要 profile 给出明确模块定位。

字段类型使用 Java 类型字符串；语义验证使用 JavaParser AST，允许基本类型、完整类名、数组及完整限定的泛型集合，例如 java.util.List<java.lang.String>。拒绝简单类名、通配符、泛型基本类型实参和表达式。不执行类型字符串和字段路径中的任何表达式。

请求字段可显式设置 diagnostic: true，将其作为 Delegate 异常中的定位标识。默认 false，不自动序列化整个请求；不要将凭据标为 diagnostic。示例地址字段已明确选择。required 与 diagnostic 相互独立。

request/response 模型必须不同且至少一个字段。第一版不生成不可空的基本类型字段校验；required 仅用于引用类型，具体验证策略由规则明确。

## 输入绑定

允许 `request`、`request.address`、`steps.port`、`steps.port.neResId`。路径仅允许 Java 标识符段，不允许括号、索引、运算符、方法调用或字符串插值。

绑定到整个请求或整个步骤输出时，声明 inputType 必须与已知类型一致。字段绑定按 JavaBean getter 或 record accessor 的已解析 AST 生成；无法确定访问方式或字段类型则阻塞生成。

步骤只能引用前序步骤；拒绝重复 id、自引用、前向引用和环。request 字段必须在 contract 中存在，步骤输出字段是否存在需在生成阶段通过源码解析验证。校验脚本不会假装证明外部类型字段存在。

direct 模式只支持单 Handler，且其输出类型必须等于 业务响应类型；多 Handler 使用 builder，参数固定为完整 request + 按顺序排列的步骤结果，字段映射由开发者补充。

responseBindings 的 key 是外部响应字段，value 为 业务结果字段名。Schema 与设计验证可检查 value 是否声明；外部 setter、getter 和类型兼容性由 AST 检查。没有完整类型信息时报告 INCOMPLETE。

## 不保存的内容

不保存 import 列表、真实方法行号、源文件摘要或源码识别结果。这些应从源码/生成计划获得，避免双份事实。

不在每份 contract 重复异常类、注入注解、Handler 基类等框架设置。这些属于项目级 profile。

## 独立响应适配

`fault-response.yaml` 演示公共业务模块中的 ReqDto/RspDto 和 service 同目录的 RequestConverter/ResponseConverter。所有 target FQN 互不相同，不能与生成 DTO/Handler/Builder 冲突。

当前支持可构造且有 setter 的外部对象，或 String 响应。String 必须绑定 message 到业务 String 字段并指定 errorMode=exception/message；工具不猜已有失败返回形式。内部节点 DTO、请求字段回填和 MapStruct 处理按真实项目人工实现。不要修改公开契约来绕过生成器限制。详见 [skill 的能力边界](../skills/atomic-api-design/references/contracts.md)。
