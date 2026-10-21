---
name: api-dev
description: 用于本项目 Java API 的新增、修改、支持版本扩展和代码审查，涵盖 Atomic 原子能力与 App 业务接口。
---

# API 开发

把任务落实为真实代码与测试。用户指定方式时遵从；未指定时默认**纯文档**。两种方式沿同一流程、遵守同一规范，只在执行手段上有差异。

| 方式 | 如何使用 |
|---|---|
| 纯文档 | 直接读手册和 templates/，按真实项目编写代码、编译测试；无需工具 JAR、init、profile 或 YAML 契约 |
| 工具辅助 | 使用相同手册和模板，额外用生成器、check/verify；需要 Java 17+ 和配套 JAR，优先使用完整发布包 |

## 1. 选层、选操作（两种方式相同）

| 任务 | 手册与模板 | 实施范围 |
|---|---|---|
| 封装设备/控制器，新增原子能力 | [Atomic 手册](references/api-atomic.md)，`atomic/` 模板 | 核对真实契约后新建所需接口/DTO/版本实现/Latest |
| 修改已有 Atomic 方法 | 同上 | 原文件增量修改，不重新生成历史实现 |
| 给已有 Atomic 补支持版本 | 同上“补版本” | 只新增真实版本实现，核对 Latest 与注册 |
| 新增或修改 App 方法 | [App 手册](references/app-api.md)，`app/` 模板 | 先判断复用已有类还是新建骨架 |
| 两层都缺 | 两份手册 | 先 Atomic，再接 App，分别检查和交付 |

先查方法及所属类：已有方法直接增量修改；新方法若复用已有类也优先增量，不因方法是新的就生成整套类。
提到 EVPL 不代表两层都要开发；先查底层能力是否已经存在。
写代码前读所选手册及其链接的 [模板及替换项](references/templates.md)。[L2EVPN 案例](references/l2evpn-example.md)用于校准查询 EVPL 节点与修改 EVPL Service ID，不规定其他业务的命名、字段或版本。

## 2. 准备开发

在业务仓库根目录读 AGENTS.md（若有）、git status、相关接口与测试，向用户简述所选方式、入口、复用能力、拟改文件和具体缺失资料。
先划分 MR 边界：只要本次需要修改 api-atomic（包括新增能力、修改已有方法、补版本及相关原子公共契约），必须先单独提交 Atomic MR；不能与 App 改动混入同一 MR，也不能只拆 commit 代替拆 MR。
两种方式都按 [适配检查表](references/configuration.md#生成前适配检查表)核对真实类型、签名和项目约定；表中生成器限制只决定能否自动生成，不能据此改造业务框架。

- **纯文档**：记录真实字段、调用链和兼容要求，直接进入所选手册；不创建工具配置。需要留存接入记录或日志时放业务仓 `.atomic-api/work/<operation>/`。
- **工具辅助**：把当前 SKILL.md 所在目录绝对路径记为 SKILL_DIR，在业务仓库根执行：

```bash
SKILL_DIR='/实际安装位置/api-dev'
sh "$SKILL_DIR/scripts/atomic-api.sh" -h
sh "$SKILL_DIR/scripts/atomic-api.sh" init
```

Windows 设置 `$skillDir = 'C:/实际安装位置/api-dev'`，使用 `& "$skillDir/scripts/atomic-api.cmd"` 加同样参数。
脚本从自身位置找 JAR，默认处理当前业务仓库，也可加 `--repo <业务仓库>`。App 配置 `.atomic-api/profile.yaml`，Atomic 配置 `.atomic-api/atomic-profile.yaml`；按 [配置手册](references/configuration.md)替换 com.example 草稿。init 保留已有文件，不复制或更新 Skill。
缺 JAR 不要求用户编译工具才能开发：可使用发布包；若改用纯文档，明确说明。用户要求的工具检查仍未执行时须报告，不能默认为已完成。业务编译 JDK 始终沿用项目配置。

## 3. 按所选手册开发（两种方式共同遵守）

- 公共接口与实现分层；用于公共接口、跨模块共享或已明确后续会跨模块使用的 DTO 提前放公共模块，不反向依赖 service/Handler/实现。仅模块内部使用的 DTO 可放业务模块。类名、包和源码根遵循真实项目约定（工具辅助将其记录为 roles）。
- Java 文件名与顶层公开类或接口名一致。新增 Facade/Converter 按业务域命名，Handler/请求响应 DTO 按具体操作命名，请求与响应 DTO 主体一致；已有类增量保留原命名，详见 [命名规则](references/templates.md#文件名与角色命名)。
- App Facade 只编排 Handler；pre 校验并构建请求，invoke 获取能力并调用，post 校验转换响应；请求状态放 Context。请求/响应 Converter 分开且只做映射，Delegate 连接转换和调用，并保持同一异常边界。Builder 仅用于真实汇总。
- 每个具体 Helper 只有一个 public 业务入口（含继承，排除 Object 方法与构造器），辅助方法私有。已有继承保持框架约定；新增具体 Handler/Helper 可按项目约定用单类 Component。
- 中文注释说明业务意图、判断优先级，缩写展开。遵循项目现有日志框架与约定，不强制 entry()/ret() 等方法名。先核对基类、切面或拦截器的统一日志覆盖，已覆盖时不重复添加；仅补充缺失的业务诊断信息，不发明日志包装器或为日志强加 try-catch，不记录凭据，保留实际版权头。
- 异常消息用可诊断英文，保留底层原因、getErrMsg/cause；格式化用 Locale.ROOT。不同失败原因分别表达，同因 null/空判断可短路合并。
- 保持公开接口、JSON 名/别名/未知字段策略、默认值、字段类型、集合顺序、null/空集合和错误语义。专属节点用响应 DTO 的 public static 内部类；跨契约共享才用顶层 DTO。已有 MapStruct 遵守 INSTANCE、严格目标策略及项目处理器配置。
- Atomic 的注册、Latest、版本顺序和首支持版本取自实际框架与历史证据。新模块接好 modules/dependencyManagement/消费依赖/classifier，合主线后核对 parent.version。
- 保留用户修改；生成前预览，冲突不覆盖。真实调用未实现时保留明确失败占位，不能返回 null/空成功、删标记、修改规则/profile 或放宽断言伪造通过。测试模板每项明确失败，须补真实调用和断言。

## 4. 检查、测试与完成条件

| 环节 | 共同要求 | 纯文档 | 工具辅助 |
|---|---|---|---|
| 规范审查 | 按 [规范对照](references/rules.md)核对本次涉及项，提供源码/历史基线证据，未满足项明确列出 | 人工核对，不标记工具 PASS | 适配时以原 layer/contract 执行 verify；check 仅辅助扫描。结构不适配时保留实现，报告未完成工具验收 |
| 编译与测试 | `git diff --check`，用项目真实 JDK/settings/依赖编译，按 [测试接入步骤](references/templates.md#测试模板怎样使用)确认用例实际执行；覆盖成功、非法输入、查无数据、底层失败和兼容边界 | 两种方式相同，工具结构通过不能替代真实测试 | 同左 |
| 报告 | 改动文件、真实调用链、命令/退出码、证据、未验证项；开发日志放 `.atomic-api/work/<operation>/`（生成待办保留工具输出路径），长任务续读原日志 | 写明“纯文档；未运行工具检查”，无需为此创建 YAML 或补跑工具 | 额外报告工具版本、layer/contract、检查状态及人工待审项 |

编译失败先核对首错、命令和本次 diff；缺 DTO 可能引发 MapStruct 连带失败。测试文件存在、通用工具测试通过都不代表私有业务集成通过。
工具辅助结果按下表处理；纯文档不使用这些状态冒充工具结果：

| 结果 | 下一步 |
|---|---|
| FAIL/退出 1，或 SCAFFOLD | 修违规或补真实实现；仅交骨架则明确未完成业务 |
| INCOMPLETE/退出 2 | 补真实配置、源码或依赖 JAR |
| REVIEW_REQUIRED | 用实际源码、历史基线和运行测试逐项提供证据 |
| 退出 0/STRUCTURE_READY | 自动结构无阻塞，仍需编译、业务测试和待审项证据 |

只交任务范围，按已有授权提交推送，不自动部署或操作网元。Atomic MR 只包含原子接口/DTO、版本实现、底层封装及必要的测试和 Maven 接线；App 的 Facade、Handler、Delegate、业务 DTO、Converter 和工具接入另开 MR。先提交 Atomic MR，App MR 关联其依赖；依赖 Atomic 改动时先合入 Atomic，再基于更新后的主线提交或更新 App MR，核对 App 差异不夹带 Atomic 改动。即使只修改 Atomic，也单独提交 Atomic MR。无远端提交权限时准备独立分支、提交和 MR 描述并报告待提交，不声称 MR 已创建。仅保留用户已明确豁免的既有 MR，不追溯改写其历史。
逐条规范、检查器实现、生成支持及覆盖边界集中在 [规范对照](references/rules.md)，按本次改动查阅，不必开发前逐行阅读。
