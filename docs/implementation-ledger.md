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
