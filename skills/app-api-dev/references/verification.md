# 验证、Maven 定位与交付

## 版本接线

新模块接入父 modules、根 dependencyManagement 和实际消费模块依赖，核对 classifier 和版本来源。合并主线升级版本后，逐个检查分支新增模块 parent.version 是否与本地父模块一致。Git 没有文本冲突不代表 Maven 模型正确。

例如根和公共模块已升级为 `2.4.2`，新增 fault 模块仍继承 `2.4.1`：它可能从仓库解析旧父 POM 和旧公共包，即使当前源码已有新 DTO，编译仍报 package/type 不存在。本地旧父 POM 未缓存时也可能更早在模型解析失败。先对齐本地父坐标，再验证 Reactor/classpath，不硬编码示例版本或添加重复类。

## 构建失败的排查顺序

1. 读取失败任务实际 Maven 命令、首个错误上下文、Reactor 和项目版本；代码扫描使用 Maven 编译失败不等于扫描器误报，也不等于业务测试失败。
2. 和用户确认可构建的提交做 diff，优先定位本次新增、移动和重命名类型、模块 POM 与依赖版本。没有基线证据不能宣称历史失败。
3. 查类型真实定义、打到哪个模块、编译 classpath 取当前 Reactor 还是旧仓库 JAR；同时看 parent.version、dependencyManagement 和 classifier。
4. 先修首因；缺失类型后的 MapStruct “未生成实现”是可能的连带错误，不先删 Converter 或放宽映射策略。
5. 已证实的版本错配直接修正。按用户授权提交推送；用户要求先推时不以漫长验证阻塞，随后继续验证并回报。

## 验证范围与证据

- 先 git diff --check。读取本地 Maven 说明及 IDE 配置，核对 JDK、mvn -v、settings、本地仓库和实际命令；新 worktree 不假设继承机器配置。JDK 版本由业务项目决定，不让插件 JDK 17 要求覆盖业务 JDK。
- 先相关模块 `-am`，再按影响范围编译 service 调用链。跨模块请求/响应和转换器需同时验证，检查 MapStruct 生成的实现类。
- 测试遵循仓库 JUnit 版本。Facade mock Handler/Builder；Handler 从 run 入口验证三段职责、规范化请求和非法输入不调用原子；Builder 验证多来源匹配和方向；Delegate mock 真正 Facade，分别覆盖调用失败、响应转换失败及请求回填。
- 保留 null、空集合、null 节点、缺失消息、默认值、字段顺序与英文错误断言。不要为通过测试改变业务行为或放宽断言。
- 长构建写 `.atomic-api/work/<operation>/`，记录进程、日志及退出码；超时继续读原日志，只取必要上下文，不因输出截断重复执行。
- validate 仅模型校验，compile 不代表测试运行，本地通过不代表云端通过。交付明确测试统计、跳过项及待验证的云端结果。用户暂停业务测试时遵守，不自行恢复已删除测试。

## MR 与提交

Atomic API 和 App API 分两个 MR：前者包含原子契约、版本实现及底层调用，后者包含业务公共契约、Facade/Handler/Builder、service 与工具定义。按实际归属处理 Maven 接线。

开始时规划分支和提交边界，只拆 commit 但仍在同个 MR 不算完成拆分。App MR 关联依赖的 Atomic MR；先合入 Atomic，再基于更新主线更新 App MR。用户明确豁免的现有 MR 不追溯拆分，不擅自改写历史。

反馈修改按用户要求小步提交；只提交本任务文件。已有提交/推送授权继续执行，不重复确认；未授权时不自动推送。不要自动部署、真实网元操作或发送外部消息。公开插件只保存通用规则与虚构示例，不携带业务源码、内部地址、凭据和机器路径。
