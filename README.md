# Atomic API Pattern

**0.4.0：让代码代理按固定步骤开发规范 Java API。** Skill 负责简短操作指令；CLI 固定包、目录和结构，检查源码并给出逐项 FIX。新开发使用 `new → generate → 实现业务 → verify → 项目编译测试`。

## 解压即用（推荐）

从 [GitHub Actions](https://github.com/RyzeRise/atomic-api-pattern/actions/workflows/build-java.yml) 的成功构建下载 `atomic-api-cli` 产物，解开其中的 `atomic-api-cli.zip`。便携包包含入口脚本和预编译 JAR；保留整个 `atomic-api/` 目录。

只需 Java 17+，无需安装本工具、Maven、Python、管理员权限或修改 PATH。在**业务项目根目录**调用：

Windows PowerShell / CMD：

```powershell
C:/tools/atomic-api/atomic-api.cmd init
```

Linux / macOS：

```bash
sh /tools/atomic-api/atomic-api.sh init
```

后文 `atomic-api ...` 是命令简写；Agent 按 skill 使用 `.atomic-api/tool/` 中的实际脚本路径。PowerShell 路径含空格时写 `& "C:/my tools/atomic-api/atomic-api.cmd" -h`。工具不改变当前目录，默认处理调用时的业务项目，也支持 `--repo <path>`。详细见 [便携使用说明](PORTABLE.md)。

## 从源码使用或安装（可选）

GitHub 源码 ZIP 不含 JAR。开发者需要 JDK 17、Maven 3.8+，先运行 `mvn -f cli/pom.xml package`，然后直接调用根目录脚本。

如希望日常使用短命令 `atomic-api`，可选择运行 `./install.ps1`（Windows）或 `sh install.sh`（Linux/macOS）并打开新终端；安装脚本也支持已构建 JAR。生成业务源码的语言版本由业务 profile 决定。

## 放进业务仓库，提示 Agent 即可使用

将便携包内 `atomic-api/` 整个目录放到业务仓库的 `.atomic-api/tool/`。在业务仓库根目录执行一次：

```bash
sh .atomic-api/tool/atomic-api.sh init
```

Windows PowerShell 用 `& ".atomic-api/tool/atomic-api.cmd" init`。之后统一得到：

```text
.atomic-api/
  tool/                         # 脚本及配套 JAR
  profile.yaml                  # 项目框架、模块与角色配置
  skills/atomic-api-dev/SKILL.md
  skills/app-api-dev/SKILL.md
  contracts/                    # 每次任务的业务契约
```

初始化并核对 profile 后，对任意能读文件、执行命令的 Code Agent 提示：

> 请读取 `.atomic-api/skills/app-api-dev/SKILL.md`，新增一个根据地址查询电路的接口，复用项目已有底层查询能力。按 skill 调用仓库内脚本生成、检查并运行相关编译测试。

原子能力任务改为引用 `.atomic-api/skills/atomic-api-dev/SKILL.md`。不依赖 Codex、Claude Code、DeepSeek Harness 的自动发现目录；Agent 是否自动识别名称取决于其自身，明确文件路径始终是本项目推荐入口。资料不足时应说明缺少什么并保留骨架，不能编造成功实现。

## 项目维护者只配置一次

在业务项目根目录运行 `atomic-api init`。核对 `.atomic-api/profile.yaml` 中实际框架类型、layout 和 **roles**；[配置示例](docs/framework-profile.md) 说明各层的固定包与模块。

配置由熟悉项目的人确定，后续新增操作不需要反复设计目录。不能自动识别时工具会明确提示；com.example 只是虚构示例。init 保留已有文件与定制，不修改 AGENTS.md。

两份完整 skill 随 CLI 打包：`atomic-api-dev`（Atomic API Dev）和 `app-api-dev`（App API Dev）。前者提供共同工具流程及原子能力开发边界，后者补充 App API 的 DTO、Facade/Handler、转换与兼容性规范；模块和包名来自实际 profile，IPRAN fault 仅是参考场景。CLI 当前严格生成的是契约描述的 Facade/Handler 调用链，原子版本族接入仍需按真实框架人工实现。

## 代码代理的日常流程

```text
atomic-api new --operation queryCircuit --business circuit
```

工具从 roles 确定所有类名、包和源码根，生成 `.atomic-api/contracts/queryCircuit.yaml`。模型只填写用途、请求字段、响应字段、已有外部响应类型、字段映射，不从零设计调用链。空业务草稿故意不能生成，避免工具猜测需求。

```text
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
atomic-api generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
```

审阅计划后应用，补充报告中的 Handler/必要 Builder 业务。已有实现冲突会阻塞，不覆盖业务体；重复相同生成应 NOOP。

```text
atomic-api verify --contract .atomic-api/contracts/queryCircuit.yaml
```

按每条诊断的 FIX 修复源码并重跑。**业务交付必须同时具备 verify 退出 0/STRUCTURE_READY 和项目编译、定向测试证据。** 可以提前编译验证接线或生成类型；类型缺失时先构建依赖补齐 classpath，再重查。 未实现占位、明显空实现、解析不完整与确定违规都不能通过。verify 不会自动执行项目命令，也不证明业务结果、全部 wire 兼容性或云端构建正确。

项目 CI 可以执行同一条 verify 命令，以非零退出码阻止违规代码合入；不应让代理用旧版 check 替代。固定仓库配置和 CI 由维护者管理，不能通过修改规则、降低 contract 版本或删除测试来消除失败。

String 返回也支持：externalResponse 为 java.lang.String，responseBindings 只绑定 message；errorMode 必须按真实契约选择 exception 或 message。前者保留异常 cause，后者返回带底层原因的英文失败消息。示例见 [String 响应契约](examples/strict-message.yaml)。

## 哪些由代码约束

| 自动检查 | 失败时的处理 |
|---|---|
| 各层包/sourceRoot 与实际文件位置 | 按 roles 恢复，不凭模型习惯放 SDK 或新建目录 |
| 两个 Converter 的实际请求绑定、响应返回 | 拒绝只调用一个无用 Converter 充数或在 Delegate new/set REST 响应 |
| Converter 映射职责 | 拒绝可识别的服务状态、外部服务调用、可执行 MapStruct 表达式或反向模型转换 |
| Facade 编排 | 拒绝控制流、对象构造及超出 Handler/Builder 的业务调用 |
| Handler 常见生命周期错位 | 原子请求构造/赋值移回 pre，能力获取移回 invoke |
| 公共 DTO 反向依赖 | 移出实现层/service 依赖 |
| 未实现占位、明显空调用阶段/null 返回 | 补真实业务，不只删占位文字 |
| 异常适配与 Maven 父版本 | 保留 getErrMsg/cause，对齐已证实的本地父版本错配 |

这些是受限 AST 规则，并非完整语义证明。解析失败输出 INCOMPLETE；复杂映射、原子版本族、JSON wire 语义和测试充分性仍有人工边界。工具不把这些默认为通过，详细能力见 [CLI 说明](docs/cli.md)。

## 在空目录练习

```text
atomic-api demo --repo ../atomic-api-demo
atomic-api generate --repo ../atomic-api-demo --contract ../atomic-api-demo/contracts/strict-handler.yaml --apply
atomic-api verify --repo ../atomic-api-demo --contract ../atomic-api-demo/contracts/strict-handler.yaml
```

最后一步预期失败并指向业务占位。实现真实逻辑后重试，而不是把这个失败当作工具问题。demo 包含虚构框架及已配置 roles，不连接真实系统。只交骨架或缺少真实能力资料时，保留占位并报告 SCAFFOLD/未验收，可先编译检查接线。旧单/多 Handler contract 留作兼容测试，不与同操作新示例同时生成。

## 升级和文件

所有命令默认当前目录，可用 `--repo <path>` 指定。配置、contract、schema、skill 归入 `.atomic-api/`；日志、参数文件和临时产物放 `.atomic-api/work/<operation>/`。

已有 v1 contract 仍可 generate/check，防止破坏存量项目；**verify 拒绝 v1，新增必须使用 new 创建 v2。** init 不覆盖本地旧文件，升级时在临时目录初始化新版本并对照合并定制 profile/skill；主项目升级后要配置 roles。0.3.1 将 atomic-api-design 改名为 atomic-api-dev，将 ipran-fault-api-dev 改名为 app-api-dev。已有 skill 不会被删除：对照保留定制、更新代理引用，再手动移除旧目录，避免同时触发两份旧新指令。0.4.0 取消 --agent 和 Agent 专用目录分发，统一引用 .atomic-api/skills/ 中的文件。

## 文档

- [固定开发 Skill](skills/atomic-api-dev/SKILL.md)、[App API 开发 Skill](skills/app-api-dev/SKILL.md)
- [CLI 与诊断](docs/cli.md)、[框架与角色配置](docs/framework-profile.md)、[Contract](docs/contracts.md)
- [新规范完整示例](examples/strict-handler.yaml)、[Schema](schemas/api-contract.schema.json)、[设计](docs/design.md)

## 验证插件

```text
mvn -f cli/pom.xml verify
python -m pip install -r requirements-dev.txt
python scripts/validate_design.py
python -m unittest discover -s tests -v
python scripts/package_cli.py
```

CLI 运行需要 Java 17+，安装后日常只使用 atomic-api 命令。生成源码遵守目标 profile 的 Java 8/11/17/21；业务 JDK 由业务项目决定。

本仓库仅提供通用工具与虚构 com.example 示例，不包含私有业务源码或凭据。GitHub Actions 构建产物包含 JAR 与便携安装包；Windows 验证以实际 CI 结果为准。
