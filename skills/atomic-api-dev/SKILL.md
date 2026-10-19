---
name: atomic-api-dev
description: 开发或接入 Java 原子能力，并按项目配置生成、检查分层 API。用于原子接口、版本注册、DTO、异常兼容及 Maven 接线；代码代理按固定脚本流程执行。
---

# Atomic API Dev

本文件包含完整操作流程。App 业务另读同目录的 [App API Dev](../app-api-dev/SKILL.md)。不需要 Agent 专用目录或全局命令。

## 入口

1. 切到业务仓库根目录，读 AGENTS.md、git status、`.atomic-api/profile.yaml` 和已有相关接口。确认真实能力、字段、版本、框架与模块，不猜 package 或外部契约。
2. Linux/macOS 入口为 `sh .atomic-api/tool/atomic-api.sh`。Windows PowerShell 使用 `& ".atomic-api/tool/atomic-api.cmd"`，CMD 使用 `.atomic-api\tool\atomic-api.cmd`。下文示例是 Linux/macOS，Windows 只替换入口前缀，保留参数和退出码。
3. 先执行入口加 `-h`。需要 Java 17+ 和完整配套 JAR；缺工具/配置就报告具体缺失位置，不安装全局命令、不修改 PATH、不把 com.example 配置当真实业务。profile 的框架类型、layout、roles 由维护者核对一次。

## 新增操作

```text
sh .atomic-api/tool/atomic-api.sh new --operation queryCircuit --business circuit
```

将名称换成本次真实操作与业务。编辑输出的 `.atomic-api/contracts/queryCircuit.yaml`，仅填写：

- description：实际用途。
- models.request.fields / models.response.fields：真实字段、Java 类型及说明；required 表示必填，diagnostic 只标记可安全出现在错误中的字段，不包含凭据。
- externalResponse：已有对外类型，不创建伪模型迎合工具。
- responseBindings：外部响应字段到业务响应字段名的映射，如 `circuitName: name`；不填表达式。
- String 外部响应另显式填 errorMode：exception 抛包装异常并保留 cause，message 保留失败字符串形式；绑定只能是 `message: 业务字符串字段`。按真实契约选择，不能猜。

保留 v2 及自动确定的类名、目录。默认一个 Handler 直接返回；确需多步时，steps.input 只支持 request、request.field、steps.id、steps.id.field，按前序步骤引用；assembly 选 builder 时只做真实汇总，Builder 位于 implementation 角色。复杂契约先由维护者适配，不降低版本绕过规则。

```text
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
```

先审阅范围。冲突不强行覆盖；不要删除业务文件以迫使应用，重复生成应 NOOP。生成器只提供 Facade/Handler 调用链骨架，不自动生成完整原子版本族或真实底层逻辑。

## 实现边界

| 位置 | 职责 |
|---|---|
| Delegate | 请求转换、组件管理获取 Facade、响应转换及异常适配；三个调用均在错误适配范围内。 |
| RequestConverter / ResponseConverter | 分别转换入口请求、对外响应，分开并同业务目录；仅映射与必要完整性校验，不查询能力或计算业务状态。 |
| Facade | 使用公共 ReqDto/RspDto，只编排 Handler；单步直接返回 run 结果。 |
| Handler pre / invoke / post | 校验并建原子请求 / 获取能力并调用 / 校验转换原子响应；请求状态放 Context。 |
| 业务 Converter / Builder | 原子 DTO 到业务 DTO 的纯映射 / 真正有计算、匹配或聚合的汇总。 |

- 原子接口、请求响应放原子公共模块，不反向依赖 App、Handler 或 service 模型；按真实能力首次支持版本新增实现，不补空旧版本。核对 Latest、动态选择及完整注册链，保留已有旧版本能力和 Facade 默认/禁用注册行为。
- 内部读取采用项目既有 get、GetXxxReqDto/RspDto、REST 适配 invokeGet；内部重命名不能改变公开 query 名、外部路径或 wire 字段。
- 保留字段类型、默认值、null/空集合区别、顺序、英文错误与异常 cause。预期失败用项目内部业务异常，适配时保留 getErrMsg 及必要标识；已发布 String 失败形式不改成异常。
- MapStruct 沿用项目 INSTANCE、严格目标映射及 default 校验；核对 processor、Lombok binding 和生成实现。工具生成的是普通可编译映射，不自动为项目接入 MapStruct。
- 注释精炼中文，异常英文且可诊断；沿用版权头和日志 API，entry/ret 按项目成对，不输出敏感请求。

## 检查与交付

```text
sh .atomic-api/tool/atomic-api.sh verify --contract .atomic-api/contracts/queryCircuit.yaml
```

按每条诊断的位置与 FIX 修复，再重跑。ERROR 必须修正；INCOMPLETE 先补真实源码/profile/classpath。退出码 0 表示覆盖范围内结构可接受，1 是违规/占位，2 是配置或解析不完整；仍须看 readiness、WARN 和人工待查项。

- 业务交付同时需要 verify 退出 0/STRUCTURE_READY、项目编译及定向测试证据。允许先编译检查接线或补类型；validate 不等于 compile，本地通过不等于云端通过。测试沿用业务仓库 JUnit 版本。
- 仅要骨架或缺底层资料时保留占位，报告 SCAFFOLD、verify 非零、缺失资料和未验证项。不得虚构成功返回、只删占位、返回 null/空 DTO、改检查器/profile 或放宽断言制造通过。
- 只读旧接口可用入口加 `check`；v1 仅兼容存量，不替代新增 v2 的 verify。嵌套模型、复杂集合/请求回填、SimpleApiHandler、特定版本和 JSON 语义按真实框架人工实现并测试，不改外部契约适应工具。
- 编译失败先读实际命令与首个错误，对照本次 diff、根/父版本、dependencyManagement、classifier、Reactor/classpath。合并主线无文本冲突不代表新增模块 parent.version 已对齐；输入类型缺失后的 MapStruct 失败可能是连带结果，不先归因扫描器或缓存。
- 先 git diff --check；核对目标 JDK、settings 和本地仓库，业务 JDK 与工具 JDK 分开。先相关模块 -am，再按影响验证 service，测试覆盖真实失败与边界。日志放 `.atomic-api/work/<operation>/`，长任务续读同一日志，不重复启动。
- 报告命令、退出码、测试统计和限制，仅交付任务内改动；按已有授权提交推送，不自动部署或操作网元。
