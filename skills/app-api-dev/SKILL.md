---
name: app-api-dev
description: 开发或审查 App API 的公共契约、Facade/Handler、转换器和 service 入口；按仓库内脚本生成与检查代码，保留版本、JSON、错误和 Maven 接线兼容性。
---

# App API Dev

先读 [Atomic API Dev](../atomic-api-dev/SKILL.md) 的共用规范和脚本流程，再执行下列 App 专属规范。所有指令统一位于 `.atomic-api/skills/`。模块和包名服从实际 profile，IPRAN fault 只是既有场景示例。

## 逐条代码规范

源码入口均在 `tools/cli/src/main/java/dev/atomic/`。`rules --format json` 可读取完整清单；verify 输出每条编号的状态和待验证边界。

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| N16 | 单响应专属节点使用 RspDto 的 public static 内部类；真实跨契约共享才用顶层 DTO。 | `CodeRules.nodes`；部分自动，仍需审查。未覆盖：检查内部节点修饰符；是否专属或共享需消费者证据。 | 不自动生成（`NOT_GENERATED`） |
| N17 | JSON 注解仅在确认冗余时删除；保留 wire 名、兼容别名及实际未知字段策略。 | `CodeRules.json`；仅定位证据，需业务审查。未覆盖：扫描注解并报告位置；须核对 ObjectMapper、消费者和基线，不能静态证明冗余。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N18 | MapStruct 沿用 INSTANCE、严格目标策略及校验，确认 processor/binding 和生成实现。 | `CodeRules.mapstruct`；部分自动，仍需审查。未覆盖：检查 Mapper 的静态配置；处理器和实际生成实现由编译证明。 | 生成普通映射，不接入 MapStruct（`PLAIN_MAPPING`） |
| N19 | 内部读取按 get/GetXxx/invokeGet 命名；原子能力从真实首次支持版本开始，核对 Latest 及注册。 | `CodeRules.atomic`；仅定位证据，需业务审查。未覆盖：原子版本族不在当前生成 contract 中；列出相关源码，须提供能力版本证据。 | 不自动生成（`NOT_GENERATED`） |
| N20 | 内部重命名不得改变公开 query、外部路径、wire 字段、默认值、顺序和错误契约。 | `CodeRules.compatibility`；仅定位证据，需业务审查。未覆盖：需要修改前基线和业务断言，当前源码无法证明未变。 | 保留已有实现，人工改动（`PRESERVE_EXISTING`） |
| N21 | 新增模块接通 modules、dependencyManagement、消费依赖和 classifier，主线升级后对齐父版本。 | `MavenParents.check / CodeRules.maven`；部分自动，仍需审查。未覆盖：自动校验本地父版本；完整 Reactor/classpath 及依赖接线由 Maven 证明。 | 不自动生成（`NOT_GENERATED`） |
| N22 | 从 Handler.run 验证生命周期、非法输入和异常；覆盖转换、回填、集合及版本边界。 | `CodeRules.tests`；仅定位证据，需业务审查。未覆盖：工具不替代业务测试执行；报告未验证，不能从测试文件存在推断通过。 | 不自动生成（`NOT_GENERATED`） |

## 使用和审查证据

固定流程：读取共用 skill → new → 填真实契约 → dry-run/apply → 实现业务 → verify → 项目编译与定向测试。不得以旧版 check 或降低 contract 版本替代验收。

- N16：更新内部节点所有引用、Lombok 注解及转换器；引用次数不能证明公共共享。
- N17：提供实际 ObjectMapper 配置、消费者及修改前 wire 契约；没有证据就保留注解。大小写、连字符、命名空间、历史别名都属于外部兼容性。
- N18：沿用项目 MapStruct INSTANCE/default 校验；构建验证 processor、Lombok binding 与生成实现。源码配置检查不等于处理器可用。
- N19/N20：对照接口、实现、Latest、管理器、注册、DTO、转换器及测试；验证首次支持、后续 Latest 和旧版本“不支持”路径。不根据需求号或分支名推断首次支持版本。
- N21：新增模块接入父 modules、根 dependencyManagement、消费依赖；合并主线无文本冲突不证明新模块 parent.version 已对齐。先相关模块 -am，再按影响编译 service 链路。
- N22：Facade mock Handler/Builder；Handler 从公开 run 验证规范化请求和非法输入不触发原子调用；Builder 覆盖多来源匹配、反向和缺失数据；Delegate 覆盖请求/调用/响应适配各自失败、请求回填、null/空集合及 cause。测试沿用业务仓库 JUnit 版本，报告实际执行结果。
- Atomic/App 分别规划 MR；App 依赖新 Atomic 时关联并先合入 Atomic，再更新 App 基线。明确豁免的现有 MR 不追溯拆分，不擅自改写历史。这是交付流程要求，工具不自动操作 MR。
