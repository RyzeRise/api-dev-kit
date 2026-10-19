---
name: api-dev
description: 用于本项目 Java API 的新增、修改、支持版本扩展和代码审查，涵盖 Atomic 原子能力与 App 业务接口。
---

# API 开发

把任务落实为真实代码与测试。先读已有实现，再选择路线；模板负责结构，业务调用和项目接线由你完成。

## 1. 选层、选操作

| 任务 | 手册与模板 | 实施方式 |
|---|---|---|
| 封装设备/控制器，新增原子能力 | [Atomic 手册](references/api-atomic.md)，`atomic/` 模板 | 新契约 → 预览 → 生成 |
| 修改已有 Atomic 方法 | 同上 | 原文件增量修改，不重新生成历史实现 |
| 给已有 Atomic 补支持版本 | 同上“补版本” | 只新增真实版本实现，核对 Latest 与注册 |
| 新增或修改 App 方法 | [App 手册](references/app-api.md)，`app/` 模板 | 先判断复用已有类还是新建骨架 |
| 两层都缺 | 两份手册 | 先 Atomic，再接 App，分别检查和交付 |

先查方法及所属类：已有方法直接增量修改；新方法若复用已有类也优先增量，不因方法是新的就生成整套类。
提到 EVPL 不代表两层都要开发；先查底层能力是否已经存在。
写代码前读所选手册。手册链接 [模板及替换项](references/templates.md)；初始化后模板位于 `.atomic-api/templates/`。
[L2EVPN 完整案例](references/l2evpn-example.md)用于校准查询 EVPL 节点与修改 EVPL Service ID，不规定其他业务的命名、字段或版本。

## 2. 开始开发

在业务仓库根目录读 AGENTS.md（若有）、git status、相关接口与测试，向用户简述入口、复用能力、拟改文件和具体缺失资料。
先划分 MR 边界：只要本次需要修改 api-atomic（包括新增能力、修改已有方法、补版本及相关原子公共契约），必须先单独提交 Atomic MR；不能与 App 改动混入同一 MR，也不能只拆 commit 代替拆 MR。
检查工具：Linux/macOS 执行 `sh .atomic-api/tool/atomic-api.sh -h`；Windows PowerShell 执行 `& ".atomic-api/tool/atomic-api.cmd" -h`。
工具只需 Java 17+；业务编译 JDK 沿用项目配置。App 用 `.atomic-api/profile.yaml`，Atomic 用 `.atomic-api/atomic-profile.yaml`。
生成前逐项核对 [适配检查表](references/configuration.md#生成前适配检查表)；首次配置与迁移也在该文档，不将 com.example 草稿当真实框架。

## 3. 必须遵守的代码规范

- 公共接口/DTO 与实现分层；App 与 Atomic 分别归属公共模块，公共 DTO 不反向依赖 service/Handler/实现。类名、包和源码根遵循真实项目及 roles。
- App Facade 只编排 Handler；pre 校验并构建请求，invoke 获取能力并调用，post 校验转换响应；请求状态放 Context。请求/响应 Converter 分开且只做映射，Delegate 连接转换和调用，并保持同一异常边界。Builder 仅用于真实汇总。
- 每个具体 Helper 只有一个 public 业务入口（含继承，排除 Object 方法与构造器），辅助方法私有。已有继承保持框架约定；新增具体 Handler/Helper 可按项目约定用单类 Component。
- 中文注释说明业务意图、判断优先级，缩写展开。先核对真实日志 API；支持 entry()/ret() 时在入口及每个显式 return/throw 前成对调用。不支持时保留既有日志方式，列出 N13（Atomic 为 A06）未满足项、日志类型/签名、受影响方法及待适配项，不发明日志方法/包装器，不为日志强加 try-catch，不记录凭据，保留实际版权头。
- 异常消息用可诊断英文，保留底层原因、getErrMsg/cause；格式化用 Locale.ROOT。不同失败原因分别表达，同因 null/空判断可短路合并。
- 保持公开接口、JSON 名/别名/未知字段策略、默认值、字段类型、集合顺序、null/空集合和错误语义。专属节点用响应 DTO 的 public static 内部类；跨契约共享才用顶层 DTO。已有 MapStruct 遵守 INSTANCE、严格目标策略及项目处理器配置。
- Atomic 的注册、Latest、版本顺序和首支持版本取自实际框架与历史证据。新模块接好 modules/dependencyManagement/消费依赖/classifier，合主线后核对 parent.version。
- 保留用户修改；生成前预览，冲突不覆盖。真实调用未实现时保留明确失败占位，不能返回 null/空成功、删标记、修改规则/profile 或放宽断言伪造通过。测试模板每项明确失败，须补真实调用和断言。

## 4. 完成条件

1. 真实契约与结构适配时，以原 layer/contract 执行 verify，按文件、message、fix 修复。未适配时按手册保留既有实现并报告“未完成工具验收”；check 是辅助扫描，不能替代 verify。
2. `git diff --check`，按实际项目 JDK/settings/本地依赖编译，并按 [测试接入步骤](references/templates.md#测试模板怎样使用)确认用例被执行；覆盖成功、非法输入、查无数据、底层失败和兼容边界。失败先核对首错、命令和本次 diff；缺 DTO 可能引发 MapStruct 连带失败。
3. 报告改动文件、真实调用链、命令/退出码、待审证据和未验证项。日志放 `.atomic-api/work/<operation>/`，长任务续读原日志。测试文件存在、通用工具测试通过都不代表私有业务集成通过。

| 结果 | 下一步 |
|---|---|
| FAIL/退出 1，或 SCAFFOLD | 修违规或补真实实现；仅交骨架则明确未完成业务 |
| INCOMPLETE/退出 2 | 补真实配置、源码或依赖 JAR |
| REVIEW_REQUIRED | 用实际源码、历史基线和运行测试逐项提供证据 |
| 退出 0/STRUCTURE_READY | 自动结构无阻塞，仍需编译、业务测试和待审项证据 |

只交任务范围，按已有授权提交推送，不自动部署或操作网元。Atomic MR 只包含原子接口/DTO、版本实现、底层封装及必要的测试和 Maven 接线；App 的 Facade、Handler、Delegate、业务 DTO、Converter 和工具接入另开 MR。先提交 Atomic MR，App MR 关联其依赖；依赖 Atomic 改动时先合入 Atomic，再基于更新后的主线提交或更新 App MR，核对 App 差异不夹带 Atomic 改动。即使只修改 Atomic，也单独提交 Atomic MR。无远端提交权限时准备独立分支、提交和 MR 描述并报告待提交，不声称 MR 已创建。仅保留用户已明确豁免的既有 MR，不追溯改写其历史。
逐条规范、检查器实现、生成支持及覆盖边界见 [规范对照](references/rules.md)，无需开发时逐行阅读。
