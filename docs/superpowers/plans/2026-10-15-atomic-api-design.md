# Atomic API Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现基于 contract 的安全骨架生成与无需 API 清单的 Java AST 检查。

**Architecture:** 独立 Maven CLI 共用 AST 索引和框架 profile；生成计划与写入分离，规则检查独立读取源码。contract 是可选设计对照，不是自动发现的前提。

**Tech Stack:** JDK 17、JavaParser + Symbol Solver、受限 YAML 解析、JSON Schema、JUnit；版本在构建验证后固定。

**Spec:** docs/design.md、docs/contracts.md、docs/cli.md。

## Global Constraints

- 通用中文 Skill，不绑定业务项目或具体 API。
- check 无需 contract，无全局 API 登记表。
- 不直接编辑生成模型、不覆盖业务实现、不部署。
- 默认 dry-run，写入前校验所有目标和原始摘要。
- 未实现代码明确抛异常并显示 SCAFFOLD。
- 解析不确定为 INCOMPLETE，不能当作通过。
- CLI JDK 17，生成源码服从目标 Java source level。

## Review Focus

- 同名类型、重载、继承和缺失 classpath：精确解析或 INCOMPLETE。
- 脏工作区、并发修改与重复执行：保留修改、拒绝陈旧计划、重复 NOOP。
- 路径穿越、符号链接、UTF-8 BOM/CRLF：阻止越界、保留格式。
- 嵌套 catch/lambda/匿名类：不把不同执行边界的代码混为一体。
- 未实现业务与合法 OR：不伪装完成，不因逻辑 OR 本身报违规。

## Task 1：Contract 与框架适配

Files: cli/pom.xml、cli/src/main/java/design/atomic/contract/*、对应 src/test。

Interfaces: `ContractLoader.load(Path): ApiContract`，`ProfileResolver.resolve(SourceIndex, Optional<Path>): FrameworkProfile`。

- [ ] 先写失败测试：两个示例通过，重复字段/id、未知字段、前向引用、路径越界、表达式注入、direct 类型不匹配被拒绝。
- [ ] 实现 YAML 安全加载、schema 与语义校验、项目级 profile。限制输入大小/别名，禁止任意对象反序列化。
- [ ] 测试 profile 候选不唯一时返回明确诊断。
- [ ] 固定经验证依赖版本，运行 `mvn -f cli/pom.xml test` 后提交该任务。

## Task 2：AST 源码图

Files: cli/src/main/java/design/atomic/index/*、对应 fixtures 和 tests。

Interface: `SourceIndexer.scan(ScanOptions): SourceIndex`；方法标识为完整声明类型+方法名+参数类型。

- [ ] 先写失败测试：局部获取、字段、构造器注入、有限别名、重载及继承能识别；未知动态关系报告 INCOMPLETE。
- [ ] 实现源码根发现、显式 roots/classpath、类型索引与调用图。
- [ ] 增加 package 与物理目录不一致、Windows 路径、同名类和缺失依赖测试。
- [ ] 独立 fixtures 不依赖私有父 POM，运行相关测试后提交。

## Task 3：独立检查器

Files: cli/src/main/java/design/atomic/check/*、report/*、对应 tests。

Interface: `PatternChecker.check(SourceIndex, Optional<ApiContract>): CheckReport`。

- [ ] 每条规则先写符合、违规、允许例外及不完整测试。
- [ ] 实现规则中的异常边界、DTO、Facade 编排、空结果和组合校验。
- [ ] 证明 getErrMsg 实际进入新异常参数；仅在 catch 中出现不算通过。
- [ ] 增加嵌套 try/lambda、合法 OR、SCAFFOLD 状态、无对象和退出码测试。
- [ ] 运行检查器测试；不把启发式判断输出为确定 ERROR。

## Task 4：生成计划与输出

Files: cli/src/main/java/design/atomic/generate/*、templates/*、对应 tests。

Interface: `GenerationPlanner.plan(ApiContract, SourceIndex, FrameworkProfile): GenerationPlan`。

- [ ] 先写失败测试：single/multi 的预期文件清单、方法签名、绑定及顺序。
- [ ] 新文件生成 AST，已有文件 AST 插入并保留格式；输出统一 diff。
- [ ] 生成 Delegate、Converter、Facade、Handler、DTO、必要 Builder；底层业务明确抛异常。
- [ ] 使用 fixtures 中的 Java 8 框架桩编译生成结果；SDK 检查结构通过但 readiness=SCAFFOLD。
- [ ] 验证生成代码新增后无需更新 API 清单即可被检查器发现。

## Task 5：安全应用与 CLI

Files: cli/src/main/java/design/atomic/apply/*、Cli.java、对应 tests。

Interface: `PlanApplier.apply(GenerationPlan): ApplyReport`，支持 docs/cli.md 全部命令约定。

- [ ] 先写失败测试：dry-run 无写入；冲突/越界在所有写入前被拒绝；并发修改阻止 apply。
- [ ] 实现摘要检查、临时文件、快照和有限恢复，不触碰无关文件。
- [ ] 注入中途 I/O 失败，断言恢复结果与未恢复原因如实报告。
- [ ] 验证重复运行 NOOP、CRLF/BOM 保留及路径带空格。
- [ ] 最终运行全部测试、生成 fixtures 编译与 git diff --check；更新 README 的实现状态后交付。

## 当前验收

- [x] 中文设计、Skill、schema 与单/多 Handler 示例完成。
- [ ] Java CLI 实现完成。
- [ ] Java AST 检查与生成代码编译完成。
- [ ] 真实业务仓库适配验证完成。

以上未勾选项不能描述为已经实现。
