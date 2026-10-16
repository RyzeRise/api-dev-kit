# Contract 使用约定

在业务项目根目录读取 `.atomic-api/schemas/api-contract.schema.json` 查看完整结构，参考 `.atomic-api/contracts/example.yaml` 编写新 API。这些文件由 init 安装，无需工具源码仓库。

每 API 一份 YAML，描述设计而非登记源码。使用 version: 1，定义 operation、description、target、models、externalResponse、steps、assembly、responseBindings。

步骤输入只允许 request 或前序步骤字段路径，不允许任意 Java 表达式。单 Handler 可 direct 返回，多 Handler 通过 Builder 汇总。

外部响应默认已存在，不通过 contract 暗改生成模型。框架类型由项目级 profile 提供，不在每份 API 重复。

提供 contract 时检查设计差异；没有 contract 依然自动检查源码。发现 drift 要报告，不自动选择一方覆盖。

结构校验不证明 Java getter 或字段存在，必须由 AST 和类型解析验证。

## 字段与约束

| 字段 | 含义 |
|---|---|
| version | 当前固定为 1 |
| operation / description | Java 方法名和用途 |
| target | facade、implementation、delegate、converter 的完整类型名 |
| models.request / response | class、sourceRoot、fields；每个字段声明 name、type、description，可选 required、diagnostic |
| externalResponse | 已存在的对外响应类型，不生成或修改 |
| steps | 顺序列表；每步声明 id、handler、sourceRoot、input、inputType、outputType |
| assembly | mode 为 direct，或 builder 并声明 builder 完整类名和 sourceRoot |
| responseBindings | key 为外部响应属性名，value 为 SDK 响应字段名 |

请求和响应至少各一个字段，类型只能为基本类型、完整类名或数组。required 只用于引用类型；diagnostic 默认 false，仅明确选中的标识进入异常文本，不标记凭据。

sourceRoot 是相对业务仓库的源码根，与 package 分开。新增目标类通过 `.atomic-api/profile.yaml` 的 layout 定位，不在 contract 重复框架注解、异常类和 Handler 基类。

输入仅支持 `request`、`request.address`、`steps.port`、`steps.port.neResId` 等受限路径；不支持表达式、索引和任意方法调用。步骤只能引用前序结果。声明类型必须匹配，getter/setter 由源码解析确认，未知即停止。

direct 仅用于一个 Handler，输出等于 SDK 响应类型。多 Handler 使用 builder，输入为完整 request 加各步骤结果，汇总字段由开发者实现。结构通过和骨架生成不表示业务完成。
