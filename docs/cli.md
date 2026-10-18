# CLI：固定开发路径

0.3.0 的新增入口是 new，验收入口是 verify。项目维护者完成一次 init/profile/roles 配置后，代码代理只填写业务事实、实现占位并依据 FIX 修复。

```text
atomic-api init --agent codex
atomic-api new --operation queryCircuit --business circuit
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
atomic-api verify --contract .atomic-api/contracts/queryCircuit.yaml --format json
```

## new 与生成

new 要求实际 profile.roles，不自动发现或猜测角色。operation/business 必须是 Java 标识符；名称来自需求而不是全局 API 表。类名和目录确定后，只填写 description、models.request.fields、models.response.fields、externalResponse、responseBindings。空草稿不通过 schema 校验；再次 new 同名契约报冲突且不覆盖。

v2 约束请求 ReqDto、响应 RspDto、同目录且独立的 RequestConverter/ResponseConverter，以及所有目标与角色配置的位置一致。generate 写前执行角色校验；已有业务体不同则 CONFLICT，不提供 force。目标 REST 模型已存在，缺少或签名不明确则停止；不修改生成模型。

多步确有汇总需要时配置 steps 和 assembly.builder，Builder 属于 implementation 角色。复杂控制流、内部节点 DTO、SimpleApiHandler、特定原子版本及 MapStruct 处理仍需维护者适配，不能改外部契约满足生成器。

String 外部响应使用 java.lang.String、`responseBindings: {message: <业务字符串字段>}`，显式 errorMode=exception 或 message。工具生成非空/非空白消息检查；分别生成抛包装异常（保留 cause）或返回带 getErrMsg 的英文失败消息。未指定错误形式即阻塞，不由模型猜测。对象响应 errorMode 为 null/exception，不能选 message。

## verify 与结果

verify 必须提供 v2 contract 和已配置角色的 profile。按契约限定本操作：旧操作的业务占位不会使本操作变成 SCAFFOLD；共享 Facade 仅检查指定方法。源码解析问题与本地 Maven 模型问题仍报告，不隐藏。

text/JSON 均包含规则、文件、行号、message 和 **fix**。JSON schemaVersion=2，report 保留发现数、检查数、manualChecks 和 readiness，顶层 status 为 BLOCKED 或 STRUCTURE_READY（普通 check 为 CHECKED），nextAction 指出下一步。

| 规则 | 自动检查边界 |
|---|---|
| STRICT-001 | 角色 package/sourceRoot、ReqDto/RspDto、两个 Converter、真实文件位置 |
| STRICT-002 | 实际请求绑定到 RequestConverter.INSTANCE，响应直接返回 ResponseConverter.INSTANCE；拒绝 Delegate 内构造/set REST 响应 |
| STRICT-003 | Converter 非注入/可变状态、类型方向、允许的 DTO/集合映射调用、构造与 MapStruct 表达式 |
| STRICT-004 | Facade 只编排 Handler/业务 Builder，不包含业务控制流或对象构造 |
| STRICT-005 | invoke 中原子 ReqDto 构造/赋值、pre/post 中明显能力查找，以及 Handler 外层保存请求 DTO 状态 |
| STRICT-006 | 公共 DTO 引入实现层/service 类型 |
| STRICT-007 | 本操作占位标记、明显空调用阶段、生命周期直接 null 返回 |
| STRICT-008 | 捕获业务异常后，实际抛出的包装异常保留 cause |
| API-*、CONTRACT | 既有 Facade/异常/响应校验、字段及 Handler 次序检查 |
| MAVEN-001 | 相同 GA 本地父 POM 的版本错配；外部父 POM不推断，无法展开属性只提示复核 |

Converter 纯映射检查只覆盖可解析的具体 DTO、Java 常用集合/字符串方法及本类辅助方法，不证明全部程序副作用；不支持的外部映射需要维护者扩展规则或明确接管。嵌套 DTO、共享 DTO、继承方法、复杂类型/别名、请求字段回填等关系不能假装已证明。无法解析时 INCOMPLETE。

只交骨架/缺少能力资料时可以保留 SCAFFOLD 并报告未验收；可以先编译检查接线。类型无法解析时也可以提前构建依赖、提供 classpath 后复查。业务交付必须同时完成结构验收和项目编译测试。验收成功只表示覆盖范围内结构无阻塞。原子能力确实执行、返回值语义、首次支持版本、JSON wire 名称、错误文本与测试充分性仍需业务验证；不得把 STRUCTURE_READY 写成业务完成。verify 不运行外部构建命令，继续执行实际项目编译与定向测试。

## 退出码

| 码 | 含义 |
|---|---|
| 0 | 当前结构检查无 ERROR/INCOMPLETE；仍看 WARN、readiness 和 nextAction |
| 1 | 确定违规、v2 业务占位或生成冲突 |
| 2 | 配置/解析/类型信息不完整、无适用对象、verify 使用 v1 |

INCOMPLETE 优先于 ERROR；完整 finding 仍输出。`--warnings-as-errors` 可按项目策略使用，不为了通过删告警或改规则。

## 存量兼容与安全边界

`atomic-api check [--contract <file>]` 继续支持无需契约的存量审查；v1 generate 继续保持旧路径，但不能替代新 API 的 verify。

所有命令默认当前目录，--repo 指定项目。显式 --profile 优先，否则读取 .atomic-api/profile.yaml，再兼容根目录 atomic-api-profile.yaml。相对 contract/profile 以当前工作目录解析，--source-root 相对 repo，可多次指定 source-root/classpath。

init 仅写项目工具目录，可显式 --agent codex/claude/cursor 安装两份完整 skill。已有文件不覆盖，旧 profile 原样迁移，符号链接与越界写入拒绝；不修改 AGENTS.md/CLAUDE.md。升级需审阅本地定制差异。

源码和 POM 检查跳过构建产物与 .atomic-api 临时副本。Maven XML 禁止 DTD/外部实体；检查不运行 Maven、不下载依赖、不计算完整 effective model。真实 Reactor、profile、BOM、classifier 和 classpath 由实际构建验证。

生成默认预览，apply 前核对依赖和内容摘要。相同结构 NOOP；不覆盖已有实现、并发修改或路径边界。多文件写入不是文件系统事务，恢复为有限尽力恢复。不自动上传源码、提交、部署或真实系统调用。

安装与平台：运行 sh install.sh 或 ./install.ps1；用户目录安装，无需管理员。支持已构建 --jar/-Jar；启动器优先 JAVA_HOME、再用 PATH。日常无需输入 Java 命令，运行需 Java 17+，目标生成代码可为 Java 8。
