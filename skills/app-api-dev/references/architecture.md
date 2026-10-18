# 职责与公共契约

使用实际 package 与 sourceRoot，不把物理目录拼写当作 Java 包名。以下 `<business>` 表示对应业务目录，前缀以仓库已有源码为准。下表的 IPRAN fault 模块名是既有场景示例，其他 App 使用其实际 profile/roles。

| 层 | 位置与职责 |
|---|---|
| 公共请求/响应 | `ipran-app-api-common` 的 `common.fault.dto.<business>`；ReqDto/RspDto 表达业务契约。 |
| 公共 Facade | 同公共模块 `common.fault.facadeinterface.<business>`；不要再套一层 facadeinterface。 |
| 故障业务实现 | 对应 `ipran-app-api-fault-<business>`；Facade 实现、Handler、业务 Builder、业务 Converter。 |
| 原子契约 | 原子 common 模块；原子接口和原子 ReqDto/RspDto，不依赖故障业务模型。 |
| 原子实现 | 对应能力版本模块；调用底层 REST 能力，支持版本和注册链来自真实源码。 |
| service 入口 | Delegate 与该业务 RequestConverter、ResponseConverter，业务响应转换为 REST 模型或 String。 |

公共契约不能反向依赖 Handler、SDK、service 模型或 Spring 查找。只有真实 SDK 专属契约继续放 SDK。App 与 Atomic DTO 不因字段相同混用；使用纯 Converter 解耦。

## DTO 与转换

只属于一个 RspDto 的子节点定义为该响应的 `public static` 内部类，有自己的 Lombok 注解；更新所有引用和转换器。只有多个独立公共契约真正复用时才使用顶层共享 DTO。源码引用次数不是判断标准。

- RequestConverter：REST 请求转业务 ReqDto；不承担响应模型和节点映射。
- 业务 Converter：原子 DTO/节点转业务 DTO/节点；留在对应 App 实现模块，保留映射字段与顺序。
- ResponseConverter：与 service 的 RequestConverter 同业务目录；复制业务 RspDto 字段、必要时回填请求字段并校验完整性，返回 REST 模型或字符串。节点到 REST 模型的映射也放这里。

Converter 不查询外部能力、不重新计算业务状态、不引入业务 Builder。MapStruct 使用项目既有 INSTANCE 模式；严格未映射目标策略可暴露新增字段遗漏。必要校验放 default 方法。核对 processor 和 Lombok binding，仅在证实缺失时补齐，再检查生成实现，不能只验证源码注解。

## Facade、Handler、Builder

Facade 只做编排，单个 Handler.run 直接返回。真正多来源匹配、方向归一、状态计算或聚合才使用业务 Builder；不为了字段复制造 Builder。

| 阶段 | 应做的事 | 不应做的事 |
|---|---|---|
| preHandler | 校验输入、规范化、构造原子请求并放入当前调用 Context | 外部能力调用、跨请求共享状态 |
| invokeAtomicApi | 获取管理器/Latest 并以 Context 原子请求调用，保存原子响应 | 再逐字段复制请求、重复校验和业务组装 |
| postHandler | 检查原子响应完整性、调用业务 Converter/Builder 返回 RspDto | 重发底层请求、吞掉异常 |

如项目提供合适的 SimpleApiHandler，单阶段实现可采用它，不生成空三段来满足结构。Context 属于每次请求，不能把请求/响应放到单例 Handler 字段。公开 Helper 保持必要的单一业务入口，框架 override 和 Converter 映射接口不受此限制。

## Delegate 的异常范围

沿用 OpsComponentManagement 获取 Facade。Facade 调用、可能抛 OpsApiException 的响应适配放入原有 try 范围，继续使用既有异常适配并保留 getErrMsg、cause 和可诊断请求标识。新请求转换若会抛同类异常，也在该范围内执行。单测分别触发 Facade 与 ResponseConverter 的失败，确认都转换为原有外部错误形式。

保留 null 列表（未提供结果）与显式空列表（结果为空）的差别；检查 null 节点、缺失消息、字段类型/默认值、返回次序和错误文本，不因提取 Converter 简化掉这些契约。
