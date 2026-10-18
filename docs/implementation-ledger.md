# 当前版本说明

0.2.0 将通用工作流和故障业务规范分开；独立响应 Converter、父 POM 版本检查、异常局部变量追踪、cause 保留及完整 skill 分发加入 CLI。当前能力与边界见 [CLI 文档](cli.md)。下文为历史实施记录，不作为现行规则；其中早期“只允许 facadeDefault=throw”等限制已由后续实现取代。

验证记录：Maven verify 成功，76 项 Java 测试通过；25 项 Python 测试通过，无跳过。打包 CLI 在独立空目录完成新响应 Converter 的预览/应用、Java 8 编译、检查与重复 NOOP；完整安装两份 skill 及 references，并复现本地父版本错配、修正后恢复检查通过。Windows 启动脚本已更新，当前主机未执行 Windows 测试，其结果以 CI 为准。

# 实现记录

计划：docs/superpowers/plans/2026-10-15-atomic-api-design.md。

工作在独立功能分支，逐步实现 CLI、源码检查、代码生成与中文 Skill。

Pre-flight：Task 1 提供 contract/profile，Task 2 提供 SourceIndex，Task 3/4 消费它们，Task 5 消费 GenerationPlan。模型使用不可变 Java records（工具 JDK 17），目标源码仍可为 Java 8。

Ruling：首次交付保留保守边界，无法证明的数据流必须报告 INCOMPLETE，不为覆盖复杂框架而生成可能错误的代码。代价是部分现有业务工程需要补充 profile/classpath 或手工实现。

Task 1：contract 已实现 schema + 安全 YAML + 语义验证，profile 支持明确配置及保守自动发现。Scalar coercion 与额外字段先由失败测试证明，再修正。

Task 2：AST + Symbol Solver 索引已实现；内存类型索引按真实 package 注册，避免物理目录假设。覆盖重载、继承、解析失败及重复类型。

Task 3：自动发现 Facade 并检查 DTO/异常/调用边界/空结果，使用 contract 对照字段和 Handler 顺序。未自动证明的职责与字段语义在 manualChecks 中列出。

Task 4/5：单/多 Handler 骨架、default Converter、外部接口签名适配、AST 增量插入、预览、安全应用和 demo CLI 已实现。生成源码在测试中通过 Java 8 编译。

Ruling：0.1.0 只允许 facadeDefault=throw；项目既有默认方法不改动。代价是其他 default 策略需要后续适配，而不是隐式生成空响应。

Ruling：当前输入绑定仅支持一个字段段和前序步骤，通用字段映射/业务条件仍需手工实现。代价是复杂业务不能一次生成完成，报告保留 SCAFFOLD。

写入保护验证：冲突全量预检查、摘要与内容一致性、重复 NOOP、符号链接、CRLF/BOM、POSIX 权限。多文件写入不是文件系统事务；恢复为有限尽力恢复，不承诺对抗恶意并发路径交换。

新增回归测试时修正过一个缺失 import 与早期 record stub 拼写；不将编译错误计为业务规则的红灯证据。后续相关回归已单独验证失败与恢复通过。

最终独立审查发现并修复：明确 profile 仍接受其他包同名异常；lambda 内未执行的 Handler 调用与 throw 被误计；已有非标准访问器被接受却未用于生成；已有其他接口时未补目标 Facade。增加针对性回归，并验证生成后 Java 8 编译。测试初稿中响应字段名与 contract 不符的 fixture 已修正，该配置错误不计为回归红灯证据。

打包 JAR 实跑发现同一实现类连续生成两份 contract 时注入字段名冲突。修复为类型复用与确定性名字分配，增加组合生成、编译及重复 NOOP 回归。

最终验证：45 项 Java 测试、18 项 Python 测试、Skill 元数据与文档链接校验通过。打包 JAR 从空目录运行 demo，单 Handler 与多 Handler 在同一工程预览/应用、检查均完成，发现并检查 2 个方法，无 finding；示例 Maven Java 8 编译成功，重复生成 NOOP。readiness 仍为 SCAFFOLD，底层业务与 Builder 映射需由使用者完成。

## 2026-10-16：命令行安装与项目初始化

新增 atomic-api 用户命令（POSIX / Windows 启动器）、用户目录安装脚本、便携安装包；日常调用隐藏 Java 启动参数。所有命令默认当前目录。init 默认仅创建 .atomic-api/，归纳 profile、contract、schema、完整 Skill，可显式 --agent 安装到代理发现目录。旧 profile 原样迁移，既有文件保留。

Ruling：默认不修改代理规则文件、不猜测 .skills/ 是统一发现目录；Codex/Claude/Cursor 通过显式选项接入。安装器检查目标和父目录链接。重复 init 保留定制内容，不承诺自动升级用户 Skill。

独立审查复现了旧配置被新草稿遮蔽、安装目标链接覆盖外部文件两项问题；分别增加失败回归并修复。发现 Skill 引用了分发包外的说明，已改为自带字段规则及项目内 schema。回归测试中曾修正一处测试方法插入位置错误，不将测试代码错误算作产品红灯证据。

验证：Maven verify 成功，50 项 Java 测试通过；24 项 Python 测试通过（含 6 项实际启动器/安装测试）；Skill 校验与文档链接检查通过。便携包解压后实际安装到独立用户目录，通过 atomic-api -h、demo、init --agent codex、连续生成两条链路、check 和重复 NOOP。本地没有 Windows 执行环境，Windows 命令通过新增的 GitHub windows-latest smoke job 验证，结果以 CI 为准。
