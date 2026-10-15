# 总体设计

## 目标与范围

为采用 Delegate / Facade / ApiHandler / Builder 的项目提供可复用开发工具。将重复分层结构自动生成，将业务行为明确留给实现者，并通过源码检查识别规范偏离。

第一版支持单 Handler 直接返回和多个 Handler 顺序编排后汇总。复杂分支、循环编排、自动 REST 契约变更、生成模型修改、任意表达式求值、自动提交和部署不在第一版范围内。

当前仓库提供 0.1.0 CLI；本设计描述完整目标，实际已实现范围、保守拒绝条件与人工检查项以 docs/cli.md 和工具报告为准。未实现部分不能描述为自动通过。

## 组件及接口

| 组件 | 输入 | 输出 |
|---|---|---|
| ContractLoader | YAML、JSON Schema | 经语法及语义验证的 ApiContract |
| FrameworkProfile | 目标源码 + 必要的项目级框架配置 | 异常、注解、组件管理、转换器及 Handler 基类的类型身份 |
| SourceIndexer | 源码目录、可选依赖 classpath | 类型索引、方法索引、Facade/调用/实现/注入关系和解析诊断 |
| GenerationPlanner | ApiContract + SourceIndex + FrameworkProfile | 不修改源码的 GenerationPlan |
| JavaEmitter | 经验证计划 | 新文件 AST、已有文件 AST 修改与 diff |
| PlanApplier | 计划、原文件摘要、输出快照 | 应用结果或恢复报告 |
| PatternChecker | 源码图、可选 contract | Finding 列表 + 覆盖报告 |

源码索引和检查器独立于生成器工作，不能依赖生成标记判断代码是否符合规范。

## 自动识别

按以下证据构建调用图：

1. `getFacadeImpl(Facade.class)` 中的类字面量、赋值目标及后续使用。
2. Facade 实现注解、`implements` / `extends` 的传递闭包。
3. 局部变量、字段、构造器注入、赋值与有限别名关系。
4. 方法的声明类型、方法名和解析后的完整参数签名；不能只凭方法名和参数数量匹配。
5. 实际调用对象与 Handler / Builder 的解析类型身份。

优先使用 JavaParser AST + Symbol Solver；从各模块 source root 和用户正常配置的 classpath 解析类型。不执行 Maven 项目插件来猜测依赖，不提取其他服务的凭据。

类型缺失、重载歧义、跨模块依赖不全、动态代理、反射、未知别名逃逸、无法证明的继承或分支关系均输出 INCOMPLETE。只能在已覆盖的关系上给出结构结论，不能把失败降级成通过。

项目级 profile 只描述框架身份及目录布局，不能维护 API 方法清单。首次适配可自动提出候选；候选不唯一时列出证据，由调用者指定 profile。源码检查不要求存在任何 API contract。

profile 字段与示例见 [项目级配置](framework-profile.md)。

## 源码布局

自动发现 Maven/Gradle 模块的 source root，同时允许显式 `--source-root`。默认排除生成目录、构建产物和第三方源码。

Java package 与物理目录分别建模。不能假设 package 一定可以直接转换成仓库路径。新增文件需要明确且无歧义的 `sourceRoot`；修改已有类型优先定位其真实路径。

只读检查可以接受脏工作区；写入只针对计划中的路径。其他用户修改必须保留。

## 生成规则

Facade 使用一个请求 DTO，内部 Handler 不强制 DTO 化。Handler 编排的 input bindings 只允许受限字段路径，按顺序引用 request 或之前步骤的输出。

新 DTO 生成显式访问方法，避免强制引入 Lombok。已有 Converter 可生成符合项目方式的方法；无法可靠适配 MapStruct 等框架时输出 INCOMPLETE，不添加一个未实现的普通 Java 方法。

新增 Handler 按现有基类生命周期生成。未实现底层能力处抛明确的 OpsApiException，保留待实现说明，不能返回 null 或空 DTO 伪装完成。

单 Handler 可直接返回 SDK DTO；多个 Handler 需要结果汇总时生成 Builder。Builder 接收完整 request 和按顺序排列的各步骤结果。Delegate 的外部响应构造与 SDK Builder 分开。

Facade 默认方法在生成前要求项目 profile 明确选择抛异常或项目既有占位行为；不根据某一个旧方法推断全项目默认策略。只生成请求中授权的新方法，不顺带改已有默认方法。

对外 REST 模型与接口默认视为已存在。缺少时报告阻塞或输出待修改接口定义说明，不直接编辑生成代码。

## 安全写入与重复运行

默认 dry-run。先构建所有变更的 AST、检查所有冲突、验证所有输出，然后才允许写入。

计划记录相对路径、真实路径、原文件 SHA-256、预期新文件 SHA-256、操作类型和待实现项；报告不包含凭据。apply 前重新验证摘要、父目录和符号链接边界。

已存在的相同生成结构为 NOOP；已存在业务实现或签名冲突为 CONFLICT，禁止覆盖。重复执行不产生重复 import、字段、注解或方法。

AST 修改优先使用 LexicalPreservingPrinter。保留既有编码和换行策略，支持 UTF-8 BOM、CRLF 和非标准源码目录。文本冲突时不退回宽泛字符串替换。

多文件写入无法承诺文件系统级原子性。采用同目录临时文件、原始内容快照及恢复日志；失败时仅在文件仍匹配本次写入摘要的前提下恢复，不能覆盖并发用户修改。恢复失败明确报告，不宣称全部回滚。

不递归清理用户目录，不自动 stash/reset 工作区。

## 检查结果与边界

ERROR 表示有明确证据违反规则；WARN 表示风险需要审查；INCOMPLETE 表示无法完成检查。报告还要区分 SCAFFOLD（业务未实现）和 contract drift。

存在未实现异常占位时，结构可以符合规则，但 readiness 必须是 SCAFFOLD，不能显示业务完成。

单次扫描输出已发现、已检查、未检查的类型/方法及规则覆盖。无检查对象时退出码为 2。发现新 API 不依赖更新配置名单。

检查规则及证据见 Skill 的 references/rules.md；人工审查不能由静态结果替代。

## 仓库布局

当前设计文档、schema、示例和 Skill 位于本仓库；实现时新增 Maven `cli/`，按 contract/index/generate/check/report 分包。

单一 CLI 共用类型索引和模型；规则实现与生成模板独立。CLI 验证不得依赖目标业务仓库的私有父 POM，验收通过自包含 fixtures 编译。
