---
name: app-api-dev
description: 开发或审查 App API 的公共契约、业务编排和 service 入口，用固定生成和自动验收流程，约束公共 DTO 归属、Facade/Handler/Converter 分工、版本和外部契约；处理相关 Maven 构建失败。
---

# App API Dev

先执行同插件的 [固定开发流程](../atomic-api-dev/SKILL.md)。新增使用 `new → generate → 补业务 → verify → 项目编译测试`；不要自由设计目录或以旧 contract/check 代替验收。

模块与包名以实际 profile/roles 和现有业务为准；参考资料中的 IPRAN fault 路径只适用于该业务，不能复制到其他 App 模块。

## App API 必须保留的边界

| 内容 | 放置或行为 |
|---|---|
| ReqDto/RspDto、Facade | 业务公共模块的 DTO / Facade 目录，按已配置 roles 生成，不放 SDK。 |
| 原子 DTO → 业务 DTO | 对应 App 实现模块的业务 Converter。 |
| 业务响应 → REST 模型/String | service 的 ResponseConverter，与 RequestConverter 同目录。 |
| 单个 Handler | Facade 直接返回 run 结果，不建字段复制 Builder。 |
| 单响应专属节点 | RspDto 的 public static 内部类，引用次数不等于公共契约共享。 |
| 原子读取命名 | 内部 get、GetXxxReqDto/RspDto，REST 适配 invokeGet；保留公开 query 工具名和外部 query 路径。 |
| 新原子能力 | 从真实首次支持版本开始，不补空旧版本，核对 Latest 和注册。 |
| 提取/重命名 | 保留字段类型、默认值、null/空集合、顺序、消息、JSON wire 名称、异常 cause 和已有 String 失败形式。 |

## 按问题读取资料

- 不确定 DTO、生命周期或转换职责：读 [职责与契约](references/architecture.md)。
- 修改命名、支持版本、JSON 或错误形式：读 [兼容性](references/compatibility.md)。
- 编译失败、主线合并、测试或拆 MR：读 [验证与交付](references/verification.md)。先查失败命令、首因、本次 diff、父版本和实际 classpath。

`verify` 能拦截结构违规和明显占位，但不能证明原子调用、版本选择、全部 JSON/字段语义与业务结果正确。它未支持的内部节点等复杂生成不能靠改契约绕过；由维护者扩展工具或明确人工接管并列出验证证据。
