---
name: api-dev
description: 按项目规范开发或审查 api-atomic 与 ipran-app-api，使用独立契约、生成器和检查路线。适用于原子接口/DTO/版本实现/Latest，以及业务 Facade/Handler/Builder、service 入口与转换器。
---

# API Dev

先选择开发层，再读对应文档；不要把 Atomic 和 App 当成同一套模板。

| 任务范围 | 必读文档 | 工具路线与配置 |
|---|---|---|
| api-atomic-common、api-atomic：原子接口、DTO、版本实现、底层封装和 Latest | [api-atomic.md](references/api-atomic.md) | `--layer atomic`；`.atomic-api/atomic-profile.yaml` |
| ipran-app-api-common、ipran-app-api-*：公共契约、Facade/Handler/Builder，以及 service 接入 | [app-api.md](references/app-api.md) | `--layer app`；`.atomic-api/profile.yaml` |
| 同时新增底层能力和业务接入 | 两份都读，先 Atomic 再 App | 两份契约、两次 verify，分别交付与规划 MR |

业务模块和 package 以实际配置为准；上表模块是本项目的职责边界。App 的现有契约保持兼容；省略 layer 仍为 app，新任务始终显式选择。Atomic 契约含 `layer: atomic`，App 契约含 `version: 2`，两者不得互换或降级绕过。

## 共用规范

以下保留历史 N 编号。表中 N 检查入口属于既有 **App 检查器**；Atomic 对应检查及尚未覆盖项见 api-atomic.md 的 A01～A08，不能拿 App 的通过结果验收 Atomic。

| 编号 | 代码规范 | 检查入口与覆盖范围 | 生成器支持 |
|---|---|---|---|
| N07 | 真实调用未实现时保留明确占位，不以 null、空 DTO 或删标记伪装完成。 | `StrictChecks.lifecycle / Checker.check`；部分自动，仍需审查。未覆盖：无法证明调用有真实业务效果。 | 只生成骨架（`SCAFFOLD`） |
| N11 | 每个具体 Helper 仅有一个 public 业务方法（含继承，排除继承自 Object 的方法和构造器）；其他辅助方法私有化。 | `CodeRules.helpers`；自动（限定范围）。未覆盖：只检查本业务包或从目标类可追踪的 Helper；继承关系无法解析即 INCOMPLETE。 | 不自动生成（`NOT_GENERATED`） |
| N12 | 新增具体 Handler/Helper 可用单类 Component，不强造父子类；已有继承保持框架约定。 | `CodeRules.components`；部分自动，仍需审查。未覆盖：检查组件标识和抽象 Handler；是否新增及继承兼容需基线。 | 生成 Handler 组件（`HANDLER_FIXED`） |
| N13 | 业务方法入口 entry，每个显式 return/throw 前 ret；不为 ret 强加 try-catch。 | `CodeRules.logging / LoggingSupport.validate`；部分自动，仍需审查。未覆盖：须配置真实 logging API；不证明隐式异常路径，缺配置报告待验证。 | 配置 logging 后生成（`WHEN_LOGGING_CONFIGURED`） |
| N14 | 注释用精炼中文解释业务意图，函数注释说明优先级；英文缩写在说明中给出全拼。 | `CodeRules.comments`；部分自动，仍需审查。未覆盖：仅检查有无中文说明；意义、精炼程度、优先级和缩写准确性待审查。 | 生成中文骨架注释（`CHINESE_SKELETON_DOCS`） |
| N15 | 异常文本用可诊断的英文，包含操作及底层原因；格式化使用 Locale.ROOT。 | `CodeRules.messages / Checker.boundary`；部分自动，仍需审查。未覆盖：只检查字面量语言和 format 的 Locale，诊断充分性待审查。 | 生成器固定（`FIXED`） |
| N23 | 生成前预览，冲突拒绝覆盖，写入不越界；不改配置、断言或规则掩盖失败。 | `GenerationPlan.apply / CodeRules.paths`；部分自动，仍需审查。未覆盖：写入边界由生成器执行；修改规则的意图、任务范围和基线需审查。 | 生成器固定（`FIXED`） |
| N24 | 不在日志或 diagnostic 字段输出密码、令牌等敏感信息，沿用实际版权头和日志 API。 | `CodeRules.privacy`；部分自动，仍需审查。未覆盖：拦截明显敏感字段/日志；隐式内容和版权归属需项目证据。 | 安全默认值，项目细节待补（`SAFE_DEFAULTS`） |

中文注释解释业务意图与优先级，缩写注明全拼；仅有中文不代表说明正确。旧类继承保持框架约定，新类可单类 Component。Helper 单一 public 业务入口包含继承方法，但排除继承自 Object 的方法和构造器。遵循真实版权头与日志 API；不记录凭据。

## 固定执行顺序

1. 在业务仓库根目录读取 AGENTS.md、git status、相关真实源码和所选路线 profile。核对实际模块、字段、版本、注册及日志，不用 com.example 配置充当项目资料。
2. 先执行 `sh .atomic-api/tool/atomic-api.sh -h`。Windows PowerShell 使用 `& ".atomic-api/tool/atomic-api.cmd"` 替换命令前缀。工具免安装，需 Java 17+ 及完整配套 JAR；缺依赖报告位置，不安装全局命令、不改 PATH。
3. 按对应文档执行 `new --layer ...`，填写真实契约，`generate --dry-run` 审阅后再 `--apply`。有冲突就保留业务文件并审查，不能删文件迫使覆盖。重复生成应 NOOP；实现后有冲突属保护行为，不回写已有业务。
4. 按同一 layer 执行 `verify`。FAIL 修源码，INCOMPLETE 补实际类型/配置，REVIEW_REQUIRED 补业务证据；旧契约 NOT_EVALUATED 不能当通过。结构无阻塞不代表业务交付完成。
5. 先 git diff --check，再按实际 JDK/settings/本地仓库执行受影响模块与 service 链路编译、定向测试。先查本次 diff、真实命令和首错；缺输入类型后的 MapStruct 错误可能是连带结果。
6. 日志存 `.atomic-api/work/<operation>/`；长任务续读，不重复启动。交付列出路线、命令、退出码、测试统计及逐条未验证项。只交任务改动，依已有授权提交推送，不自动部署或操作网元。

缺底层资料时保留明确占位，报告 SCAFFOLD 和 verify 非零；不能返回空成功、删标记、放宽断言或改规则/profile 制造通过。

## 日志配置

两条路线的各自 profile 均可配置真实 `logging.type`、`logging.factoryType`、`logging.factoryMethod`。工厂为静态方法，接收 Class 并返回配置日志类型；日志类型提供无参 entry()/ret()。生成器为新业务方法生成入口和显式出口日志，不额外构造 try-catch。签名不同须按实际框架适配工具；缺配置明确待审，不伪造 API。

## 查看规范覆盖

`rules --layer app|atomic --format json` 列出该路线的规范、检查代码、生成支持和证明边界。`verify` 的结构结果与规范结果分开：退出 0 仅自动结构检查无阻塞，部分自动/审查规则仍为 REVIEW_REQUIRED；退出 1 为违规或占位，2 为配置/解析不完整。真实业务效果、历史兼容、动态注册和测试执行必须另有证据。
