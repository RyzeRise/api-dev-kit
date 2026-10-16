---
name: atomic-api-design
description: 为采用 Delegate、SDK Facade、ApiHandler、Builder 分层架构的 Java API 定义 contract、设计调用链、规划代码骨架生成和审查开发范式。适用于新增 API、重构接口、核对异常边界、结果映射和校验规则。
---

# 原子 API 设计与开发

在业务项目根目录使用 `atomic-api` 命令。先运行 `atomic-api -h` 确认工具已安装；尚未初始化时运行 `atomic-api init`。配置、contract 和 Skill 统一存放在项目的 `.atomic-api/` 中。

## 目录与临时产物

`.atomic-api/` 根目录只放 `profile.yaml`、`.gitignore` 和约定子目录。产物按以下位置保存：

| 内容 | 位置 |
|---|---|
| 接口契约 | `.atomic-api/contracts/<operation>.yaml` |
| Schema、Skill | `.atomic-api/schemas/`、`.atomic-api/skills/` |
| 当前操作的计划、日志、报告、备份、参数文件、调试脚本及编译产物 | `.atomic-api/work/<operation>/` |
| 可复用缓存 | `.atomic-api/cache/` |

开始需要落盘的操作前，先创建 `.atomic-api/work/<operation>/`，后续重定向输出、临时源码、JAR 和 class 文件均写入该目录；不在 `.atomic-api/` 根目录逐个创建带操作名前缀的文件。需要 Java 参数文件时也放在此目录，内部路径以业务仓库根目录为基准。CLI 的标准输出默认无需保存；需要留存才重定向到工作目录。

例如为 `queryPwe3AlarmByCsn` 保存检查结果：先创建 `.atomic-api/work/queryPwe3AlarmByCsn/`，再执行：

```text
atomic-api check --contract .atomic-api/contracts/query-pwe3-alarm-by-csn.yaml --format json > .atomic-api/work/queryPwe3AlarmByCsn/check.json
```

交付前检查 `.atomic-api/` 的第一层；本次产生的临时文件应全部位于对应工作目录。`init` 不自动移动或删除已有文件。

## 读取与范围

1. 阅读适用 AGENTS.md、git status、目标接口及完整调用链。
2. 开发或审查前阅读 [开发规则](references/rules.md)。
3. 定义新 API 时阅读 [Contract 约定](references/contracts.md)，参考 `.atomic-api/contracts/example.yaml`。
4. 交付前完成 [审查清单](references/review-checklist.md)。

只对框架调用链应用范式，不套用到所有 Java 方法。不自动变更外部契约、生成模型和无关代码。

## 定义 API

将需求整理为单份 YAML contract：操作、请求与 SDK 响应字段、现有外部响应类型、Handler 步骤和绑定。

选择单 Handler 直接返回，或多 Handler 顺序执行后 Builder 汇总。Facade 使用请求 DTO；内部 Handler 可以使用字符串、资源对象或前步输出。

自动发现类型和目录，无法唯一定位时列出候选，要求明确目标；不按同名类猜测。Java package 与 sourceRoot 分开。

不创建全局 API 清单，不在 contract 重复真实路径、import 和实际方法位置。源码检查无需 contract，指定 contract 才增加设计对照。

## 实现或生成

所有命令默认使用当前目录，也可用 `--repo <path>` 指定项目。使用以下命令：

```text
atomic-api init
atomic-api generate --contract .atomic-api/contracts/example.yaml --dry-run
atomic-api generate --contract .atomic-api/contracts/example.yaml --apply
atomic-api check --format json
atomic-api check --contract .atomic-api/contracts/example.yaml --format json
```

先审阅全量计划与 diff，再按授权应用。新文件使用 AST 模板，已有文件使用 AST 定点插入。拒绝签名冲突、覆盖业务实现和路径越界；保留无关修改与换行。

Facade 生成遵循开发规则中的 LDP 结构基准。profile 的 `facadeDefault` 支持 `throw` 与显式选择的 `return-null`，不改变既有 default 行为。底层业务未实现时明确抛异常并列出待实现项，不返回 null 或空 DTO 伪装完成。

不因生成恢复用户要求删除的业务测试；工具自己的测试独立执行。

## 检查与报告

`atomic-api check` 执行 Java AST 检查；使用业务项目自身命令进行编译和业务测试。

AST 检查输出 ERROR、WARN、INCOMPLETE、覆盖与 readiness。解析失败或无适用对象不能显示通过。OR 条件仅为风险线索，独立失败原因合并才构成违规。读取输出的 manualChecks，不能把未自动证明的规则当成通过。

逐层核对请求转换、组件管理获取、Facade 编排、Handler 生命周期、Builder 汇总、异常和响应映射。特别确认 getErrMsg 实际传入最终异常。

分别报告修改、结构检查、人工审查、编译测试、未检查项与业务待实现项。结构符合不代表业务完成，未实现骨架为 SCAFFOLD。

不自动提交、推送、部署或调用真实系统；这些动作需要任务本身明确授权。
