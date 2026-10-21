# API Dev Kit

让 Code Agent 按项目规范开发、审查 Java API。支持**纯文档**和**工具辅助**：共用一份 Skill、规范和模板，每个步骤说明执行差异，未指定时默认纯文档。

首次使用见 [QUICKSTART.md](QUICKSTART.md)。[L2EVPN 案例](skills/api-dev/references/l2evpn-example.md)保留查询 EVPL 节点、修改 EVPL Service ID 的完整校准流程，不代替当前业务源码。

## 安装与选择方式

将完整 `api-dev/` 放入所用 Agent 的 Skill 目录，例如业务仓 `.skills/api-dev/`，也可放在仓库外。位置不限，不要只复制 SKILL.md。

| 方式 | 获取与依赖 | 开始方式 |
|---|---|---|
| 纯文档（默认） | 从源码仓复制 `skills/api-dev/` 即可；不需要工具 JAR/Java 17，业务编译仍用项目自己的环境 | 让 Agent 读 SKILL.md，按手册和可读模板开发、人工审查、编译测试 |
| 工具辅助 | 解压维护者提供的 `api-dev.zip`，其中含配套 JAR；运行工具只需 Java 17+，无需用户编译或全局安装 | 同一流程中额外执行 init、适配 profile/contract、生成与检查 |

源码目录的 `tools/` 包含维护源码、schema、测试和打包程序；发布包包含 `SKILL.md`、`references/`、`templates/`、`scripts/` 和 `lib/atomic-api.jar`，不带维护源码。源码 checkout 不含预编译 JAR；需要工具辅助时使用发布包或由维护者构建。
当前工具版本 0.8.1。根目录不提供另一套 Skill 或安装入口。

提示示例（安装路径按实际替换，可将“纯文档”改为“工具辅助”）：

> 请读取 `.skills/api-dev/SKILL.md`，用纯文档方式开发 L2EVPN 的 EVPL 节点查询接入。先读已有实现并复用真实底层能力；方法已存在就增量修改。报告规范审查、实际编译测试及未验证项。

## 按共同步骤开发

从 [Skill 入口](skills/api-dev/SKILL.md)选 [Atomic](skills/api-dev/references/api-atomic.md) 或 [App](skills/api-dev/references/app-api.md)，两份手册在每一步整合两种方式：

| 步骤 | 共同要求 | 纯文档 | 工具辅助 |
|---|---|---|---|
| 定位与适配 | 先读真实源码，区分新增能力、修改方法、补版本；核对签名和兼容边界 | 记录源码依据 | 另配置两层独立 profile |
| 明确开发内容 | 确定真实字段、调用链、注册与版本 | 不需要 YAML | 将同一事实填入真实 contract |
| 开发 | 新方法优先复用已有类；不覆盖已有业务体 | 借用模板片段手工实现 | 支持场景先预览、生成；不支持时仍手工增量 |
| 测试与交付 | 真实测试、编译、人工证据和未验证项 | 不要求运行工具检查 | 额外报告 check/verify 结果与覆盖限制 |

[适配检查表](skills/api-dev/references/configuration.md#生成前适配检查表)区分真实约定与工具形状；[模板说明](skills/api-dev/references/templates.md)给出手工装配、生成对应关系及测试接入步骤；[规范对照](skills/api-dev/references/rules.md)保留 App N01～N24、Atomic A01～A08 与代码检查的逐条映射。
工具限制不能变成修改公开接口、JSON、默认值、集合顺序或错误语义的理由。Atomic 补版本保留历史业务体；嵌套版本模型按 [增量清单](skills/api-dev/references/api-atomic.md#本项目嵌套版本接口的增量路线)接线。

check 是辅助扫描，verify 的退出 0/STRUCTURE_READY 只表示自动结构无阻塞，不能替代真实编译、业务测试及人工审查。纯文档按相同规范交付，报告未运行工具，不需要为此补建 profile/YAML。

## 升级与业务资料

业务配置、契约、接入记录与日志统一放业务仓 `.atomic-api/`，不创建 Agent 适配目录。纯文档无需运行 init，也无需创建工具配置。
升级在新位置展开完整 Skill，比较合并定制后更新引用路径；旧 `.atomic-api/skills/`、tool/、templates/ 保留，init 不静默覆盖或删除。生成器读取 JAR 内模板，修改可读文件不会自动改变生成行为。详见 [配置与迁移](skills/api-dev/references/configuration.md#升级与定制)。

## 工具维护与试用

以下仅供工具维护者使用，纯文档用户无需构建。维护工具需要 JDK 17、Maven 和 Python 3；在本仓库根目录执行以下命令，构建后可直接运行 Skill 的 scripts/ 入口，无需安装：

```bash
mvn -f skills/api-dev/tools/cli/pom.xml clean verify
sh skills/api-dev/scripts/atomic-api.sh -h
sh skills/api-dev/scripts/atomic-api.sh demo --repo ../atomic-api-demo
python -m unittest discover -s skills/api-dev/tools/tests -v
python skills/api-dev/tools/package.py
```

Python 仅用于维护测试/打包，使用标准库，无 pip 依赖。产物为 `skills/api-dev/tools/dist/api-dev.zip`，可通过团队文件共享或所用托管平台的制品服务分发。

Windows 用 `skills/api-dev/scripts/atomic-api.cmd`。在 Windows PowerShell 中，从本仓库根目录执行以下命令验证便携包（先完成上述构建和打包）：

```powershell
& ./skills/api-dev/tools/tests/windows.ps1
```

自动构建系统可执行相同的构建、测试和打包命令；Windows 检查需在 Windows 执行环境中运行。demo 使用虚构框架，不连接真实系统；包中的模板和旧契约用于生成及回归测试，不是另外一套开发规范。

仓库不包含私有业务源码或凭据。各业务仓库保留自己的 profile/contracts；普通复制不会随工具仓库 git pull 自动同步，当前版本没有自动同步/自动重编译机制。
