---
name: api-dev
description: 开发或修改本项目 Java API。先判断是 api-atomic 原子能力还是 ipran-app-api 业务接口，再读取对应操作手册，使用仓库内生成/检查工具，并实现真实业务与测试。
---

# API 开发

把用户的需求落实为本项目的代码。工具负责生成部分结构和检查规范；你负责读懂已有接口、实现底层调用、接入现有入口并测试。不要把生成的占位代码当成交付。

## 收到任务，先这样做

1. 在业务仓库根目录读 AGENTS.md（若有）、git status，查找用户提到的 DTO、Facade、Handler、Delegate 和原子接口。已有代码优先复用；用户要新增一个方法，不代表需要新建整套类。
2. 用下表选手册。**写代码前读完该手册的开发步骤与规范**，不要只读入口文件就开始生成。

| 用户要做的事 | 读哪份手册 | 使用哪套工具 |
|---|---|---|
| 封装设备/控制器接口，新增原子接口、版本实现或 Latest | [api-atomic.md](references/api-atomic.md) | `--layer atomic`，配置 `.atomic-api/atomic-profile.yaml` |
| 在现有原子能力上增加业务查询/修改，接 Facade、Handler 和 service | [app-api.md](references/app-api.md) | `--layer app`，配置 `.atomic-api/profile.yaml` |
| 两层都缺 | 两份都读；先原子能力，再业务接入 | 分别生成契约、检查和交付 |

例如“查询 L2EVPN 的 EVPL 节点、修改 EVPL Service ID”：先读 App 手册并查现有 Atomic 能力；只有底层能力确实缺失时才进入 Atomic 手册。不要根据“EVPL”这个词直接决定开发层。

3. 检查配套工具：Linux/macOS 执行 `sh .atomic-api/tool/atomic-api.sh -h`；Windows PowerShell 执行 `& ".atomic-api/tool/atomic-api.cmd" -h`。工具需 Java 17+，免全局安装。
4. 读所选 profile，按相邻已运行的代码核对真实框架类型、包、目录和日志 API。profile 是项目一次性配置；每个操作的字段和映射另存在 contract YAML。不要把 init 产生的 com.example 草稿用于真实项目。

## 先告诉用户你找到了什么

用几句话列出：对外入口、业务模块、可复用的原子接口/版本、需要改的文件。若缺少会影响实现的资料，指出具体缺失；能从源码找到的先找，不要求用户再解释整个项目。

已有实现（包括本例 L2EVPN）先走手册的增量开发步骤；不要默认执行 new/generate。只有新操作且模板适用时，才准备契约 → 预览 → 生成 → 实现真实业务。两条路径最终都要检查、编译和测试；不能为了工具通过改项目架构。

## 写代码时始终遵守

- Helper 仅一个 public 业务入口（含继承，排除 Object 方法和构造器）。辅助方法私有化。已有类保持框架继承；新类可按项目约定用单类 Component。
- 中文注释解释业务意图、判断优先级；缩写在说明中展开。不要用“处理请求”代替真正的业务说明。
- 按项目日志 API 写 entry()/ret()，每个显式 return/throw 前记录 ret，不为日志强加 try-catch。异常用可诊断英文，保留原错误与 cause；格式化用 Locale.ROOT。不记录凭据。
- 不擅自改已有公开接口、JSON 字段、默认值、集合顺序、null/空集合或错误语义。遵循实际版权头。
- 不覆盖用户修改。不能通过删占位、返回空成功、修改规则/profile 或放宽断言伪造完成。

## 怎么判断做完了

1. 模板与真实契约已适配时，用原 layer 和原契约运行 verify，按文件、规则和 fix 修复。若当前结构不适配，按手册执行只读辅助检查，并明确“未完成工具验收”；仍须真实编译和定向测试。不要伪造契约、降低版本，或把 check 说成 verify。
2. `git diff --check` 后，按项目实际 JDK/settings/本地仓库编译并运行定向测试。构建失败先看真实命令、首错和本次 diff；缺 DTO 可能导致后续 MapStruct 连带失败。
3. 输出改动文件、实际调用链、编译/测试命令与退出码，以及仍未验证的项目。日志放 `.atomic-api/work/<operation>/`，长任务续读原日志，不重复启动。

| 检查结果 | 你下一步做什么 |
|---|---|
| FAIL / 退出 1 | 修源码；若仅交骨架则保留占位，明确说明业务未实现 |
| INCOMPLETE / 退出 2 | 补真实配置、源码或依赖 JAR；不当作通过 |
| REVIEW_REQUIRED | 自动结构未证明这些语义；结合实际源码、基线、测试逐项说明 |
| 退出 0 / STRUCTURE_READY | 只是自动结构检查无阻塞，继续确认编译、测试和待审项 |
| SCAFFOLD | 只有结构，真实底层尚未接通，不能报告接口已可用 |

只交任务范围，按已有授权提交推送，不自动部署或操作网元。Atomic/App 分别规划 MR；App 依赖新 Atomic 时先合 Atomic，再更新 App 基线，已有明确豁免不追溯拆分。

## 共用规范与检查器对照

这里保留逐条映射，供审查和定位错误，不是需要填写的表单。N 编号的代码入口属于 App 检查器；Atomic 的对应覆盖见 api-atomic.md 的 A01～A08。`rules --layer app --format json` 或 `rules --layer atomic --format json` 可查看完整清单。

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

日志生成的可选配置在各自 profile 的 logging 下：type（实际日志类）、factoryType（实际工厂）、factoryMethod（静态工厂方法）。当前支持工厂接收 Class、返回日志类，以及无参 entry()/ret()。其他签名先适配工具，不能伪造项目 API。缺配置时保留待审状态。
