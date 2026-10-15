# Contract 使用约定

仓库 `docs/contracts.md` 定义格式，`schemas/api-contract.schema.json` 定义结构，`examples/` 提供示例。

每 API 一份 YAML，描述设计而非登记源码。使用 version: 1，定义 operation、description、target、models、externalResponse、steps、assembly、responseBindings。

步骤输入只允许 request 或前序步骤字段路径，不允许任意 Java 表达式。单 Handler 可 direct 返回，多 Handler 通过 Builder 汇总。

外部响应默认已存在，不通过 contract 暗改生成模型。框架类型由项目级 profile 提供，不在每份 API 重复。

提供 contract 时检查设计差异；没有 contract 依然自动检查源码。发现 drift 要报告，不自动选择一方覆盖。

结构校验不证明 Java getter 或字段存在，必须由 AST 和类型解析验证。
