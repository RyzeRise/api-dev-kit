# API Dev Kit

让 Code Agent 按项目规范生成、检查 Java API。只需向 Agent 描述任务并指定 skill 文件；命名、目录和部分分层约束由工具执行。

首次使用请先读 [QUICKSTART.md](QUICKSTART.md)：以 L2EVPN 查询 EVPL 节点、修改 EVPL Service ID 为例，说明接入、任务提示、生成边界和验收。

## 仓库结构

| 位置 | 存在理由 |
|---|---|
| `skills/api-dev/` | 自包含 Skill：入口、两层手册、运行脚本和模板；发布包另包含配套 JAR。 |
| `tools/` | 维护者使用的 Java 实现、schema、测试和打包程序。 |

根目录不提供安装脚本、重复入口、历史计划或另一套设计说明。当前版本 0.8.0。

## 安装 Skill

获取维护者提供的 `api-dev.zip`，将包内完整 `api-dev/` 放到所用 Agent 的 Skill 目录，例如 `.skills/api-dev/`；也可放在仓库外任意位置。不要拆开目录，脚本、JAR、手册和模板都随 Skill 分发。

```text
api-dev/
  SKILL.md
  references/
  scripts/                 # 各平台入口
  lib/atomic-api.jar
  templates/               # 可读、可复用模板
```

在**业务仓库根目录**运行，以下变量替换为实际 Skill 绝对路径。Linux/macOS：

```bash
SKILL_DIR='/实际安装位置/api-dev'
sh "$SKILL_DIR/scripts/atomic-api.sh" init
```

Windows PowerShell：

```powershell
$skillDir = 'C:/实际安装位置/api-dev'
& "$skillDir/scripts/atomic-api.cmd" init
```

运行只需 Java 17+，无需全局安装、Maven、Python、管理员权限或修改 PATH。脚本从自身位置找 JAR，业务资料统一在 `.atomic-api/`；init 保留已有配置，不复制或更新 Skill。

然后直接提示 Agent（`.skills/` 仅为安装示例）：

> 请读取 `.skills/api-dev/SKILL.md`，开发 L2EVPN 的 EVPL 节点查询接入，先读已有实现并复用真实底层能力；已经存在则核对并增量修改。

升级时在新目录展开完整 Skill，比较合并定制后更新引用路径。旧 `.atomic-api/skills/`、tool/、templates/ 保留并提示迁移，不自动覆盖或删除；业务 profile/contracts 继续沿用。详见 [配置与迁移](skills/api-dev/references/configuration.md#升级与定制)。

## 项目配置（维护者一次核对）

两条路线独立配置，不把 Atomic 套进 App 框架。`.atomic-api/profile.yaml` 配置 App；`.atomic-api/atomic-profile.yaml` 配置 Atomic，均不能直接使用虚构 com.example 草稿。

App 配置：

- `types`：组件管理、Facade 注解、Handler 基类、内部/外部异常、注入及组件注解的真实完整类名；方法签名必须可解析。
- `targetJava`：业务源码版本，可选 8/11/17/21，与运行工具的 Java 17+ 分开。
- `facadeDefault`：throw 或 return-null，遵守当前项目默认实现约定。
- `layout`：packagePrefix 与仓库内 sourceRoot 对应，最长前缀匹配。目录与 package 不同时可加 directoryPrefix。
- `roles`：必须包含 facade、request、response、implementation、handler、delegate、requestConverter、responseConverter 八个角色，每个都有 packagePrefix/sourceRoot，且落在对应 layout 内。工具据此生成类名和目录，不由模型每次决定。

例如角色配置 `request: {packagePrefix: 项目公共DTO包, sourceRoot: 公共模块/src/main/java}`。init 的示例提供完整结构；替换为项目真实值，不填 operation 清单。可选 disabledFacade 使用现有 configuration 完整类名及 mapField，不能凭空创建注册机制。

Atomic 配置的 roles 为 apiInterface/request/response/implementation/latest；latestKind 明确 class 或 interface，baseInterfaces/interfaceAnnotations 显式保留实际框架父接口和注解。版本注册注解由操作契约提供，不能猜测。具体继承关系和未覆盖边界见 `skills/api-dev/references/api-atomic.md`。

## 规范、检查器、生成器逐条对应

人类先读 [Skill 入口](skills/api-dev/SKILL.md) 的共用规范和分流表，再读对应路线文档；[逐条规范对照](skills/api-dev/references/rules.md)单独供审查。App 保留 N01～N24，Atomic 使用 A01～A08；编号分别对应真实检查代码、生成支持和未覆盖项，不能用另一层的结果验收。App 清单在 RuleCatalog.java，Atomic 清单在 AtomicTool.java；测试要求文档与代码一致。

```bash
sh "$SKILL_DIR/scripts/atomic-api.sh" rules --layer app
sh "$SKILL_DIR/scripts/atomic-api.sh" rules --layer atomic --format json
```

verify 的 text/JSON 报告包含每条规范的 PASS、FAIL、INCOMPLETE、REVIEW_REQUIRED 或 NOT_EVALUATED。JSON 分开报告 structureStatus 与规范 status：自动结构通过但仍需审查时，status 为 REVIEW_REQUIRED。只有自动检查范围内无问题才记 PASS；JSON、版本、兼容性和测试执行等依赖业务证据的规则，会列出检查位置、未覆盖项和所需证据，不会假装静态证明。

Helper 单一公开入口、组件注解、显式日志出口、异常文本语言、内部节点修饰符及 MapStruct 配置已补检查。生成器提供中文骨架注释；配置真实 `logging.type/factoryType/factoryMethod` 后生成 entry()/ret()，不为日志额外加 try-catch。字段和日志 API 必须来自实际项目，缺日志配置会报告 N13 待验证。

## 开发流程

以下仅为**模板已适配的新操作**示例；已有 L2EVPN 命名、内部节点和响应回填不能直接套通用模板，先按 App 手册核对。命令在业务仓库根执行，Windows 使用上文 $skillDir，将前缀换为 `& "$skillDir/scripts/atomic-api.cmd"`：

```bash
sh "$SKILL_DIR/scripts/atomic-api.sh" new --layer app --operation queryL2evpnEvplNodes --business l2evpn
# Agent 填写输出契约中的真实业务字段、外部响应与映射
sh "$SKILL_DIR/scripts/atomic-api.sh" generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --dry-run
sh "$SKILL_DIR/scripts/atomic-api.sh" generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --apply
# 实现真实业务后检查并按 FIX 修复
sh "$SKILL_DIR/scripts/atomic-api.sh" verify --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml
```

Atomic 示例：

```bash
sh "$SKILL_DIR/scripts/atomic-api.sh" new --layer atomic --operation getEvplService --business evpl --api-version R26C10
# 填写 Atomic 契约的真实字段、用途、releases 注册注解及 latestAnnotations
sh "$SKILL_DIR/scripts/atomic-api.sh" generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --dry-run
sh "$SKILL_DIR/scripts/atomic-api.sh" generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --apply
sh "$SKILL_DIR/scripts/atomic-api.sh" verify --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml
```

Atomic 单独生成接口、公共 DTO、版本实现及配置模式的 Latest 骨架；不生成 Facade/Handler/Delegate，不自动发明注册表或真实 REST 调用。releases 仅列真实支持版本，顺序和首支持证据需审查。两种已支持的 Latest 结构若不适合实际项目，先适配工具，不能改框架凑模板。

App 工具检查角色位置、转换器实际调用和纯映射范围、Facade 编排、Handler 常见职责错位、公共 DTO 反向依赖、明显占位、异常 cause 及本地父 POM 版本错配。无法解析时输出 INCOMPLETE，不能当通过。

verify 退出 0/STRUCTURE_READY 只表示覆盖范围内结构可接受，业务交付还须有实际项目编译和定向测试。退出 1 表示违规/占位，2 表示配置或解析不完整；生成冲突也会阻塞。CI 可执行同一 verify 命令作为门槛。

仅需骨架或缺底层资料时保留占位并报告 SCAFFOLD；新生成的骨架 verify 失败是预期行为。App 只读旧接口可用 check，App v1 只保留存量兼容，不能替代新增 v2 的 verify；Atomic check/verify 均须 Atomic v1 真实契约。嵌套 DTO、复杂映射、版本注册运行及 JSON 语义仍需真实框架资料和人工验证，不能改外部契约迎合工具。

## 实际模板与增量开发

两套实际模板位于 `skills/api-dev/templates/atomic/`、`skills/api-dev/templates/app/`，DTO 共用 `common/`。
它们由 AtomicTool/Generator 读取并装配，Skill 同时携带 `templates/` 可读文件；[模板说明](skills/api-dev/references/templates.md)列出替换项和代码对应关系。
生成计划附带明确失败的业务测试待办，不假定项目 JUnit 版本；迁入真实测试目录、补调用和断言后才能验收。
Atomic 全量生成不支持覆盖历史真实实现，补版本采用[只新增版本的增量路线](skills/api-dev/references/api-atomic.md#4-为已有能力补一个支持版本)。
[L2EVPN 案例](skills/api-dev/references/l2evpn-example.md)是待私有源码校准的上下文线索，不是权威源码快照；具体待核对文件列在案例末尾。

## 工具维护与试用

从任意 Git 托管平台克隆或下载的源码不含 JAR。维护工具需要 JDK 17、Maven 和 Python 3；在本仓库根目录执行以下命令，构建后可直接运行 Skill 的 scripts/ 入口，无需安装：

```bash
mvn -f tools/cli/pom.xml clean verify
sh skills/api-dev/scripts/atomic-api.sh -h
sh skills/api-dev/scripts/atomic-api.sh demo --repo ../atomic-api-demo
python -m unittest discover -s tools/tests -v
python tools/package.py
```

Python 仅用于维护测试/打包，使用标准库，无 pip 依赖。产物为 `tools/dist/api-dev.zip`，可通过团队文件共享或所用托管平台的制品服务分发。

Windows 用 `skills/api-dev/scripts/atomic-api.cmd`。在 Windows PowerShell 中，从本仓库根目录执行以下命令验证便携包（先完成上述构建和打包）：

```powershell
& ./tools/tests/windows.ps1
```

自动构建系统可执行相同的构建、测试和打包命令；Windows 检查需在 Windows 执行环境中运行。demo 使用虚构框架，不连接真实系统；包中的模板和旧契约用于生成及回归测试，不是另外一套开发规范。

仓库不包含私有业务源码或凭据。各业务仓库保留自己的 profile/contracts；普通复制不会随工具仓库 git pull 自动同步，当前版本没有自动同步/自动重编译机制。
