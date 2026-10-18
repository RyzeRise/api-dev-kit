# CLI 使用约定

安装：源码仓库运行 `sh install.sh`（macOS/Linux）或 `./install.ps1`（Windows PowerShell）。安装脚本将 atomic-api 注册为用户命令；日常无需输入 Java 启动命令。

```bash
atomic-api -h
atomic-api init
atomic-api init --agent codex
atomic-api demo --repo <empty-directory>
atomic-api generate --repo <path> --contract <api.yaml> --profile <framework.yaml> --dry-run
atomic-api generate --repo <path> --contract <api.yaml> --profile <framework.yaml> --apply
atomic-api check --repo <path>
atomic-api check --repo <path> --contract <api.yaml> --format json
```

所有命令的 --repo 默认当前目录。generate 默认 dry-run；`--dry-run` 与 `--apply` 互斥。不支持隐式覆盖已有业务实现，不提供 force 覆盖开关。

## 项目初始化

init 默认只在 `.atomic-api/` 内创建文件：`profile.yaml`、`contracts/example.yaml`、`contracts/fault-response.yaml`、`schemas/api-contract.schema.json`、`skills/atomic-api-design/` 与 `skills/ipran-fault-api-dev/`（含完整 references）以及忽略 reports/cache 的内部 .gitignore。所有既有文件保留，写入前检查全部目标路径并拒绝符号链接。

已有根目录旧配置时，init 校验并原样复制到新目录，保留原文件；旧配置无效时停止，不用示例遮蔽。没有旧配置时尝试识别唯一的框架类型和单一源码根；无法确认时生成标注“待配置”的 profile 草稿并说明原因，不假定示例配置适合当前项目。示例 contract 也需要按需求修改。

运行时优先使用 `.atomic-api/profile.yaml`，没有时兼容旧的 `atomic-api-profile.yaml`；显式 --profile 优先级最高。相对 contract/profile 参数以当前工作目录解析，source-root 以 --repo 解析。

可选 `init --agent codex|claude|cursor` 额外复制 Skill 到 `.agents/skills/`、`.claude/skills/`、`.cursor/skills/`，以便相应代理发现。默认 none 仅在 `.atomic-api/skills/` 保存原件，需要在代理中明确读取此路径。重新 init 保留两处已有 Skill，不自动覆盖用户定制版本，也不创建代理规则文件。

目录约定核对于 2026-10-16：[Codex](https://developers.openai.com/blog/skills-agents-sdk)、[Claude Code](https://code.claude.com/docs/en/skills)、[Cursor](https://cursor.com/docs/skills)。`.skills/` 不是这些工具统一采用的项目目录。

安装脚本默认使用用户目录；shell 版把 PATH 写入当前 shell 对应的 .zshrc/.bashrc/.profile，PowerShell 版更新用户 PATH。--prefix（PowerShell -Prefix）可以指定安装位置，--jar（PowerShell -Jar）可使用现有构建产物。启动器优先 JAVA_HOME，其次 PATH；保留参数边界和退出码。运行仍需要 Java 17+，该依赖由命令入口封装。

profile 是项目级框架适配，不是 API 登记表。自动识别唯一时可省略；不唯一时报告候选并退出。

允许多次 `--source-root` 与 `--classpath`，不要求目标业务项目能先通过 Maven 编译。

## 输出

generate 输出 CREATE / UPDATE / NOOP / CONFLICT、逐文件 diff、阻塞项、待实现业务位置及运行前摘要。apply 后再次解析并验证输出。任何阻塞在写入前发现时，所有目标文件保持原样。

check 输出规则编号、文件、行号、发现依据和严重级别。机器报告含 schemaVersion、toolVersion、scope 与 report；report 含 discoveredMethods、checkedMethods、findings、manualChecks、readiness。

0.2.0 的 coverage 为 discoveredMethods/checkedMethods；未完成项保留 INCOMPLETE finding。readiness 仅输出 SCAFFOLD / UNKNOWN，不自动证明业务 COMPLETE。完整方法级覆盖、列号及 SARIF 作为后续增强。

## 退出码

| 退出码 | 含义 |
|---|---|
| 0 | 当前覆盖范围内无 ERROR、无 INCOMPLETE；仍需读取 WARN 和 readiness |
| 1 | 发现明确违规、contract drift 或生成冲突 |
| 2 | 参数/解析/环境错误、部分检查未完成、没有适用对象 |

INCOMPLETE 优先于 ERROR 决定退出码，完整 finding 列表仍保留。可选 `--warnings-as-errors`；不隐藏 WARN 来获得通过。

## 环境兼容

CLI 运行环境建议 JDK 17；目标生成代码遵守目标 source level（含 Java 8）。支持 Windows、Linux 与 macOS 路径。不依赖 shell 展开、平台专用命令或目录名等于 package 的假设。

当前不提供 HTTP 服务、不引入远程执行、不自动上传源码、不自动创建提交。

## 当前自动检查范围

Facade 从 getFacadeImpl 类字面量和实现注解发现；Symbol Solver 解析接收者、重载、字段与继承。实现索引按源码真实 package 建立，支持物理目录不匹配。

提供 profile 时，框架异常、Handler 基类和 Facade 注解按解析后的完整类型名匹配；未提供 profile 时仅使用命名约定，实际项目建议配置 profile。延迟执行的 lambda 或匿名类内调用不计为当前 Facade 方法的直接 Handler 编排；响应 null 分支必须直接抛出内部异常，不能只在延迟代码中声明抛出。

自动检查请求 DTO 约定、异常声明、调用处 try/catch、getErrMsg 进入直接构造或直接初始化后抛出的外部异常、响应转换的首个 null 失败检查、组合 OR 校验风险。可选 contract 检查 DTO 字段类型、Handler 数量/顺序及预期类型存在。

Converter 字段语义、Handler 生命周期职责、底层业务、注释含义与完整响应映射仍需人工审查；存在复杂别名、复杂分支、动态调用等无法证明的关系，应读取 INCOMPLETE/manualChecks，不能只看退出码。

generate 只支持顺序链路和受限字段绑定；不覆盖既有实现。既有 Converter 需要接口形态，映射使用可执行 default 方法；不自动重写 MapStruct 已有方法。

多个 API 可共享 Facade、实现类和 Delegate。注入字段按准确类型复用，名字被其他类型占用时分配新名字。已有实现类的其他接口保留，并补入缺失的目标 Facade。已有 DTO 使用解析出的 getX/isX/x 访问器；无法证明字段、访问器或 setter 类型一致时停止生成。

## 0.2.0 新增与边界

- 可选 `target.responseConverter` 生成独立 default 映射接口，Delegate 的响应转换位于 Facade 调用的同一异常适配范围。缺省字段保留旧行为，不迁移已有方法体。生成异常适配保留 getErrMsg 和 cause。
- `MAVEN-001` 读取仓库内 POM，检查相同 GA 的本地父 POM 版本错配。支持默认/自定义 relativePath 和可展开的 POM 属性；显式空 relativePath、外部或不存在的父文件不强行匹配。无法静态展开的版本只 WARN。XML 禁止 DTD/外部实体；解析失败 INCOMPLETE。
- 检查不调用 Maven、不下载依赖、不计算完整 effective model。属性激活、外部父链、profile、BOM、classifier 与实际 classpath 仍要通过失败任务命令核对。无 Facade 仍退出 2，Maven finding 继续完整输出。
- 源码与 POM 检查跳过 `.atomic-api/` 内的临时副本和构建目录。OR 短路中左侧先检查响应 null 可识别；独立失败原因仍人工审查。
- catch 中直接初始化且未重新赋值的异常局部变量可追踪 getErrMsg。String 失败返回不能机械改为异常，报告 INCOMPLETE 并按已有契约人工审查。cause 是否保留与业务字段语义仍需审查。
- 仅调用 String.format 时才要求 Locale.ROOT，不对拼接消息误报警。
- `init` 安装两份 skill，包内 manifest 与仓库实际文件集合一起验证。保留已有文件的升级原则不变；在临时目录初始化新版本后审阅差异并合并本地定制。
