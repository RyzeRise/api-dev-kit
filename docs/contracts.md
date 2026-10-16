# API contract 规范

每个 API 使用一份 YAML。Schema 定义见 `schemas/api-contract.schema.json`，字段完整示例见 `examples/`。不创建全局 API 清单。

## 职责与维护

contract 描述设计意图；源码描述当前实现。新增 API 时生成器读取 contract，独立检查器不依赖 contract。提供 contract 时额外对照设计。

后续手工修改实现造成 drift 时报告差异，不自动反向修改 contract，也不据 contract 覆盖业务代码。由开发者确认是更新设计还是修正实现。

## 字段

| 字段 | 含义 |
|---|---|
| version | 当前固定 1 |
| operation / description | Java 方法名及用途 |
| target | Facade、实现、Delegate、Converter 的完整类型名；现有类型可以自动识别，首版 contract 保留明确目标用于消除歧义 |
| models | 请求 DTO、SDK 响应 DTO 的完整类型、sourceRoot、字段及非空约束 |
| externalResponse | 已有外部响应完整类型名，不生成/修改其模型 |
| steps | 按顺序执行的 Handler；声明 id、类、输出类型、输入绑定、输入类型及 sourceRoot |
| assembly | direct 直接返回末步结果，或 builder 汇总所有步骤 |
| responseBindings | 外部响应字段来自 SDK 结果的字段路径 |

sourceRoot 是仓库内相对源码根目录，与 package 分开；不能指向仓库外。缺失目标文件的创建需要 profile 给出明确模块定位。

字段类型使用 Java 类型字符串；语义验证使用 JavaParser AST，允许基本类型、完整类名、数组及完整限定的泛型集合，例如 java.util.List<java.lang.String>。拒绝简单类名、通配符、泛型基本类型实参和表达式。不执行类型字符串和字段路径中的任何表达式。

请求字段可显式设置 diagnostic: true，将其作为 Delegate 异常中的定位标识。默认 false，不自动序列化整个请求；不要将凭据标为 diagnostic。示例地址字段已明确选择。required 与 diagnostic 相互独立。

request/response 模型必须不同且至少一个字段。第一版不生成不可空的基本类型字段校验；required 仅用于引用类型，具体验证策略由规则明确。

## 输入绑定

允许 `request`、`request.address`、`steps.port`、`steps.port.neResId`。路径仅允许 Java 标识符段，不允许括号、索引、运算符、方法调用或字符串插值。

绑定到整个请求或整个步骤输出时，声明 inputType 必须与已知类型一致。字段绑定按 JavaBean getter 或 record accessor 的已解析 AST 生成；无法确定访问方式或字段类型则阻塞生成。

步骤只能引用前序步骤；拒绝重复 id、自引用、前向引用和环。request 字段必须在 contract 中存在，步骤输出字段是否存在需在生成阶段通过源码解析验证。校验脚本不会假装证明外部类型字段存在。

direct 模式只支持单 Handler，且其输出类型必须等于 SDK 响应类型；多 Handler 使用 builder，参数固定为完整 request + 按顺序排列的步骤结果，字段映射由开发者补充。

responseBindings 的 key 是外部响应字段，value 为 SDK 结果字段名。Schema 与设计验证可检查 value 是否声明；外部 setter、getter 和类型兼容性由 AST 检查。没有完整类型信息时报告 INCOMPLETE。

## 不保存的内容

不保存 import 列表、真实方法行号、源文件摘要或源码识别结果。这些应从源码/生成计划获得，避免双份事实。

不在每份 contract 重复异常类、注入注解、Handler 基类等框架设置。这些属于项目级 profile。
