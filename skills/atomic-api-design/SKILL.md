---
name: atomic-api-design
description: 按固定 CLI 流程新增或审查 Java 分层 API：生成规范契约和代码骨架，用 verify 阻止架构违规与未实现占位，再运行项目编译测试。适合需要明确步骤和自动约束的代码代理。
---

# 按工具流程开发 API

目标：只决定业务字段和逻辑。类名、包、模块和分层由项目配置与工具约束。

## 新增 API：按顺序执行

1. 读项目 AGENTS.md、git status、`.atomic-api/profile.yaml` 和目标业务已有接口。确认真实外部模型和能力；不要猜字段或包名。项目维护者应先配置框架、layout 和 roles。缺少配置时按工具 FIX 报告阻塞，不能把 com.example 示例当业务配置。
2. 在项目根目录运行下列命令，把名称换成本次真实操作与业务：

```text
atomic-api new --operation queryCircuit --business circuit
```

3. 编辑输出的 `.atomic-api/contracts/queryCircuit.yaml`，只填写 `description`、`models.request.fields`、`models.response.fields`、`externalResponse`、`responseBindings`。字段用真实 Java 类型，映射值是业务响应字段名；不填表达式。String 外部响应另按真实契约填 `errorMode: exception`（抛异常）或 `message`（返回失败消息），并用 `responseBindings: {message: 业务字符串字段}`；不能猜错误形式。保留 `version: 2` 和自动确定的类名、目录。确需多 Handler/Builder 时先读 [契约说明](references/contracts.md)。
4. 预览 diff，核对任务范围后应用：

```text
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
```

5. 实现 Handler 报告的占位业务。pre 校验并建原子请求；invoke 获取能力并调用；post 校验并转换响应。字段复制放 Converter，Facade 只编排。不要直接在 Delegate 组装响应，不创建空旧版本。需要判断职责时查 [规则](references/rules.md)。
6. 运行验收，按每条 `FIX` 修改源码，然后重跑同一命令：

```text
atomic-api verify --contract .atomic-api/contracts/queryCircuit.yaml
```

7. 业务交付必须同时具备 verify 退出 0/`STRUCTURE_READY`、项目编译和定向测试证据。可以先编译检查接线或生成类型；遇类型缺失时先构建依赖、补 classpath，再重查。报告命令、退出码与统计，不能把结构通过当业务正确或云端通过。按任务已有授权提交推送。

## 只生成骨架或缺少资料

若用户仅要骨架，或没有真实底层能力/请求响应资料：可生成并编译检查接线，保留占位，报告 `SCAFFOLD`、verify 非零及缺失资料。这是骨架交付，不能声称业务已验收；不要虚构成功返回来得到绿色结果。

## 遇到失败

- ERROR：按位置和 FIX 修复；INCOMPLETE：补实际源码/profile/classpath 后重查，不能当通过。
- STRICT-007：实现真实能力和结果处理。不能只删占位文本、返回 null 或空响应。
- 不降低 contract 版本，不改 roles/检查器、不删除断言或改用宽松 check 来伪装验收。
- 新增时不复制旧示例重新设计结构。只读旧接口审查可用 `atomic-api check`，不强制初始化或生成；它不是新增 API 的交付门槛。
- 复杂原子版本族、SimpleApiHandler、嵌套模型生成和超出 schema 的逻辑需要维护者适配；不要改已发布契约迎合工具。

临时日志放 `.atomic-api/work/<operation>/`，构建超时续读同一任务。构建失败、迁移及交付细节仅在需要时读 [验证清单](references/review-checklist.md)。IPRAN 故障业务的 DTO、版本与 JSON 规则使用 `ipran-fault-api-dev`。
