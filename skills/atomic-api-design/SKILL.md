---
name: atomic-api-design
description: 设计、生成和审查采用 Delegate、Facade、Handler、Converter 分层的 Java API；处理 contract、DTO 归属、异常边界、代码骨架及 Maven 模块接线。IPRAN 故障业务同时使用 ipran-fault-api-dev。
---

# 原子 API 设计与开发

在业务仓库根目录使用 `atomic-api`，先运行 `atomic-api -h`。工具未安装时按插件 README 安装；需要生成或安装项目资源而尚未初始化时执行 `atomic-api init`；只读审查不强制初始化或写入文件。源码和实际框架签名优先于示例，不要求业务项目复制示例包名、SDK 模块或 Java 版本。

## 工作流

1. 阅读适用 AGENTS.md、用户约束和 git status，定位入口到原子能力的完整调用链。记录公开方法、JSON 字段、null/空集合、错误返回形式和版本支持范围，明确本次允许改变什么。
2. 阅读 [分层与实现规则](references/rules.md)。IPRAN 故障业务再读取同插件的 `ipran-fault-api-dev` skill，不把通用示例的 SDK 归属套到业务公共契约上。
3. 新接口或生成骨架时读取 [Contract 约定](references/contracts.md)，先核对真实类型、模块与 sourceRoot，再定义 contract。不为普通审查强制补 contract，不创建全局 API 注册表。
4. 先审阅 `generate --dry-run` 全量计划，在任务授权内应用；生成器只提供明确标记未实现的骨架。已有业务体、签名冲突或不确定的映射不得覆盖。
5. 按 [审查与构建清单](references/review-checklist.md) 检查源码、Maven 模型及业务行为。自动检查与人工审查各自给出证据；结构通过不代表业务完成。
6. 按用户授权提交、推送。无需重复确认已经授权的操作；不要自行部署、操作网元或发送消息。

## 分层选择

Facade 接收单个业务 ReqDto；公开业务响应使用项目约定的 RspDto。只有确属 SDK 的契约才放 SDK。单个 Handler 可以直接返回；多步结果需要匹配、聚合或计算时使用 Builder。字段复制放 Converter，不额外创建业务 Builder。

REST 请求与响应转换分开。新 contract 指定 `target.responseConverter` 后，生成独立响应 Converter，并在 Delegate 的既有异常适配范围内调用。省略该字段保留旧生成行为，避免破坏既有 contract；这不是新业务推荐结构。人工重构已有实现时遵循当前分层规范，不因生成器兼容路径退回旧设计。

## 常用命令

```text
atomic-api init --agent codex
atomic-api generate --contract .atomic-api/contracts/fault-response.yaml --dry-run
atomic-api generate --contract .atomic-api/contracts/fault-response.yaml --apply
atomic-api check --format json
atomic-api check --contract .atomic-api/contracts/fault-response.yaml --format json
```

所有命令默认当前目录，可用 `--repo <path>` 指定。首次检查 profile 中框架 FQN、`layout`、`targetJava` 和禁用态注册。多候选不能靠同名类猜测。Java package 与源码物理路径分别配置。`init` 保留已有文件，升级时对照新插件审阅差异，不假定它覆盖了本地旧 skill/profile。

`check` 输出 ERROR、WARN、INCOMPLETE、覆盖数、readiness 和 manualChecks。解析失败或无适用对象不能显示通过；SCAFFOLD 需要补业务实现。Maven 本地父坐标检查不替代 effective POM、Reactor 和真实编译。OR 条件只是人工检查线索，不能无差别拆开同一失败原因的短路校验。String 失败响应、复杂数据流和框架变体需按已有契约人工复核，不为消除工具告警改变外部行为。

## 工作文件

`.atomic-api/` 根目录只放 `profile.yaml`、`.gitignore` 和约定子目录。

| 内容 | 位置 |
|---|---|
| 接口 contract | `.atomic-api/contracts/<operation>.yaml` |
| Schema、Skill | `.atomic-api/schemas/`、`.atomic-api/skills/` |
| 当前操作计划、日志、报告、备份、参数文件和临时源码/编译产物 | `.atomic-api/work/<operation>/` |
| 可复用缓存 | `.atomic-api/cache/` |

落盘前先创建 `.atomic-api/work/<operation>/`，日志只读增量或关键上下文；超时后续读原任务，不重复启动构建。交付前检查元数据根目录，没有任务授权不移动或删除旧文件。

报告明确列出：修改与保留的契约、自动/人工检查结果、实际构建命令与退出码、测试统计、未验证项。`validate`、`compile`、`test` 和云端检查分别说明，不互相替代。不因生成恢复用户要求删除的业务测试。
