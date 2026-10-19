---
name: app-api-dev
description: 开发或审查 App API 的公共契约、Facade/Handler、转换器和 service 入口；按仓库内脚本生成与检查代码，保留版本、JSON、错误和 Maven 接线兼容性。
---

# App API Dev

先读并执行 [Atomic API Dev 的固定工具流程](../atomic-api-dev/SKILL.md)，再按本文件约束实现 App 层。两份 skill 统一位于 `.atomic-api/skills/`，脚本位于 `.atomic-api/tool/`；通过文件路径调用，不依赖 Agent 自动发现。

## App 层边界

| 内容 | 归属及行为 |
|---|---|
| ReqDto/RspDto、Facade | 业务公共模块，实际包/目录来自 profile.roles；不因旧接口在 SDK 就继续放 SDK。 |
| Facade 实现、Handler、业务 Converter/Builder | 对应 App 业务实现模块，不反向污染公共契约。 |
| 原子 DTO → 业务 DTO | App 实现模块的纯 Converter；字段相同也不混用 App/Atomic DTO。 |
| 业务响应 → REST 模型/String | service 的 ResponseConverter，与 RequestConverter 同目录；必要时回填请求字段并校验完整性，不再查能力或重算状态。 |
| 单个 Handler | Facade 直接返回 run 结果，不为字段复制建立 Builder。 |
| 专属响应节点 | 该 RspDto 的 public static 内部类，有自己的 Lombok 注解；只有多个独立公共契约真正共享才用顶层 DTO，引用次数不是依据。 |

实际模块以当前业务为准；IPRAN fault 的 common.fault.dto、fault.facadeinterface、ipran-app-api-fault-* 只适用于该场景，不能复制到所有 App。

## 生命周期和适配

- pre 做输入校验、规范化及原子请求构造；invoke 获取能力并调用；post 校验响应并转成业务结果。检查实际代码职责，不以方法名代替审查。
- 请求、Facade 调用和响应转换均放在原有异常适配范围内。分别验证三者失败能够按契约返回 ServiceException 或失败 String，并保留 getErrMsg/cause。
- 独立失败原因分别表达；同一响应缺失的 null/空消息短路检查可以合并。保持 null 列表与显式空列表、null 节点、缺失消息的原有语义。
- 字段映射保留类型、默认值、顺序及请求回填。不要为通过测试改业务状态、吞异常或伪造空成功。

## 重构、版本与 JSON

- 重命名同时核对接口、实现、Latest、管理器调用、DTO、Converter、测试和注册。内部 get 命名不改变公开 query 工具名和外部 query 路径。
- 新原子能力从真实首次支持版本开始，不能补空旧版；测试首次支持、后续 Latest 和较早不支持版本，沿用框架的“不支持”路径。
- 删除 JSON 注解前核对真实 ObjectMapper、所有消费者和 wire 契约；只有同名且冗余的注解才删除。连字符、大小写、命名空间及兼容别名必须保留。全局忽略未知字段需证实覆盖实际消费者后才去掉重复类注解。
- MapStruct 用项目既有方式，严格核对未映射字段、processor/binding 和生成类；响应转换不新增无业务含义的 Builder/Helper 层。

## 测试与接线

- Facade 测试 mock Handler/Builder；Handler 从公开 run 验证生命周期、传给原子接口的规范化请求，以及非法输入不触发调用；Builder 覆盖匹配、反向和缺失数据。
- Delegate mock 具体 Facade，覆盖请求/调用/响应转换各自失败、请求字段回填和 null/空集合契约。保留错误文本与 cause 断言，不放宽测试换取绿色。
- 新模块同时接入父 modules、根 dependencyManagement 与消费模块；核对 classifier、版本来源、Facade 兜底注册和动态绑定。主线升级后检查新增模块 parent.version，先同批源码 Reactor，再确认依赖 JAR 来源。
- Atomic 与 App 改动分别规划 MR，App 依赖新原子能力时关联 Atomic MR，先合入 Atomic 再更新 App 基线。用户明确豁免的现有 MR 不追溯拆分；不要未经要求重写已有历史。
- 结构验收通过不证明原子调用、版本选择、wire 兼容性或业务结果正确。缺真实资料时只交骨架、列出未验收项，按固定流程运行 verify 和相关编译测试。
