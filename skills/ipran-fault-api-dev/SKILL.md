---
name: ipran-fault-api-dev
description: 新增、重构或审查 IPRAN 故障工具 API，处理原子能力与故障业务分层、公共 DTO、Facade/Handler、REST Converter、支持版本、Maven 模块和 MR 边界；定位相关构建失败。
---

# IPRAN 故障 API 开发

本 skill 定义故障业务规则。通用 CLI 用法见同插件的 `atomic-api-design` skill；本文件不重复其生成命令。源码中的真实包名、框架接口与用户当前授权优先，示例仅为虚构的职责定位。

## 执行顺序

1. 阅读 AGENTS.md、git status 和任务约束，确认是否允许运行测试、提交及推送。保留无关工作，不因历史规则重复询问已经授权的动作。
2. 画清从 service 入口到 Facade、Handler、原子接口/Latest/版本实现/REST 的调用关系，阅读实际类和 POM。先查已有能力与复用点，不能根据类名猜包名或支持版本。
3. 按 [职责与契约](references/architecture.md) 确认 DTO 归属、生命周期和纯转换边界。
4. 按 [版本与兼容性](references/compatibility.md) 处理命名、版本实现、JSON 和错误形式；重构保留既有行为。
5. 按 [验证与交付](references/verification.md) 核对模块版本、测试、构建和 MR 边界。用户反馈之前能构建时，从本次改动做基线对照，不先归因云端。
6. 交付列出调用链、DTO 归属、复用/新增底层能力、变更和保留项、验证证据与未验证项。按任务授权提交推送，不自行部署或操作真实网元。

## 快速决策

| 问题 | 规则 |
|---|---|
| 新业务请求/响应放哪 | 业务公共模块的 fault.dto，Facade 在 fault.facadeinterface；不是 function-sdk。 |
| 节点 DTO 是否独立文件 | 仅属于一个响应契约时用 public static 内部类；被 Handler/Converter 引用不等于共享契约。 |
| Facade 只有一个 Handler | 调用 run 后直接返回，不创建字段复制 Builder。 |
| 三段 Handler 怎么分 | pre 校验并建原子请求；invoke 获取能力并调用；post 校验并转换响应。 |
| 只是复制响应字段 | 业务原子节点到业务节点用业务 Converter；业务响应到 REST 用 service ResponseConverter。 |
| 请求和响应能否共用 Converter | 分开且同业务目录；响应节点映射归 ResponseConverter。 |
| 新能力要不要加旧版本 | 从实际首次支持版本开始，不补空旧版本。 |
| get / query 怎么选 | 内部原子读取用 get；已发布工具名和外部 query 路径保留；REST 内部调用用 invokeGet。 |
| 合并没有冲突就可推送 | 还要检查新增模块 parent.version、依赖坐标和 Reactor；做必要快速检查；按授权决定先推明确修复再继续长验证，或验证后推送。 |

## 不机械套用

- 先确认 ApiHandler 与 SimpleApiHandler 的真实框架能力；单阶段工作不造空生命周期方法，必须三段时每段职责真实。
- MapStruct 沿用项目 INSTANCE 与依赖约定；default 方法可保留检查，不移动业务计算或外部查询进去。
- 同一缺失响应的 null/空消息检查可合并，独立失败原因不要混成通用错误。
- 已有 String 失败响应保留英文返回形式；现有抛异常接口保留 getErrMsg、cause 与异常适配。不能借重构统一成另一种外部行为。
- 本插件生成器只覆盖通用对象响应骨架；内部节点 DTO、String 结果、实际原子调用和框架变体按源码手工实现。不要为了适应生成器改业务契约。
