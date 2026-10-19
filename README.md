# Atomic API Pattern

让 Code Agent 按项目规范生成、检查 Java API。只需向 Agent 描述任务并指定 skill 文件；命名、目录和部分分层约束由工具执行。

## 仓库只保留三部分

| 位置 | 存在理由 |
|---|---|
| `skills/` | 两份开发指令，各一个 SKILL.md，没有平台元数据或附属说明文件。 |
| `tools/` | 代码生成/检查工具的入口、Java 实现、模板、schema，以及保证其正确性的测试和打包程序。 |
| `.github/workflows/` | 在 Linux/Windows 验证工具，并产出可直接运行的 ZIP。 |

根目录不提供安装脚本、重复入口、历史计划或另一套设计说明。当前版本 0.5.0。

## 放进业务仓库

从 [成功的 GitHub Actions 构建](https://github.com/RyzeRise/atomic-api-pattern/actions/workflows/build-java.yml) 下载 `atomic-api-cli` 产物，解开其中的 ZIP，把包内 `atomic-api/` 整个目录放到业务仓库的 `.atomic-api/tool/`。不能只复制入口脚本，配套 JAR 也必须保留。

在**业务仓库根目录**执行一次：

```bash
sh .atomic-api/tool/atomic-api.sh init
```

Windows PowerShell：

```powershell
& ".atomic-api/tool/atomic-api.cmd" init
```

只需 Java 17+，无需全局安装、Maven、Python、管理员权限或修改 PATH。init 只生成 `.atomic-api/` 内的配置、契约示例和两份 skill，保留已有文件。维护者核对 profile 后，对支持读取文件及执行命令的任意 Agent 提示：

> 请读取 `.atomic-api/skills/app-api-dev/SKILL.md`，新增根据地址查询电路的接口，复用已有底层能力。

原子能力任务引用 `.atomic-api/skills/atomic-api-dev/SKILL.md`。不配置各 Agent 专用目录。升级时需对照更新已有 skill/profile 定制，init 不覆盖；旧的 Agent 专用目录由维护者合并定制后移除。

## 项目配置（维护者一次核对）

`.atomic-api/profile.yaml` 决定真实框架和模块，不能直接使用虚构 com.example 配置：

- `types`：组件管理、Facade 注解、Handler 基类、内部/外部异常、注入及组件注解的真实完整类名；方法签名必须可解析。
- `targetJava`：业务源码版本，可选 8/11/17/21，与运行工具的 Java 17+ 分开。
- `facadeDefault`：throw 或 return-null，遵守当前项目默认实现约定。
- `layout`：packagePrefix 与仓库内 sourceRoot 对应，最长前缀匹配。目录与 package 不同时可加 directoryPrefix。
- `roles`：必须包含 facade、request、response、implementation、handler、delegate、requestConverter、responseConverter 八个角色，每个都有 packagePrefix/sourceRoot，且落在对应 layout 内。工具据此生成类名和目录，不由模型每次决定。

例如角色配置 `request: {packagePrefix: 项目公共DTO包, sourceRoot: 公共模块/src/main/java}`。init 的示例提供完整结构；替换为项目真实值，不填 operation 清单。可选 disabledFacade 使用现有 configuration 完整类名及 mapField，不能凭空创建注册机制。

## 开发流程

以下命令在业务仓库根执行，Windows 将前缀换为 `& ".atomic-api/tool/atomic-api.cmd"`：

```bash
sh .atomic-api/tool/atomic-api.sh new --operation queryCircuit --business circuit
# Agent 填写输出契约中的真实业务字段、外部响应与映射
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --contract .atomic-api/contracts/queryCircuit.yaml --apply
# 实现真实业务后检查并按 FIX 修复
sh .atomic-api/tool/atomic-api.sh verify --contract .atomic-api/contracts/queryCircuit.yaml
```

工具检查角色位置、转换器实际调用和纯映射范围、Facade 编排、Handler 常见职责错位、公共 DTO 反向依赖、明显占位、异常 cause 及本地父 POM 版本错配。无法解析时输出 INCOMPLETE，不能当通过。

verify 退出 0/STRUCTURE_READY 只表示覆盖范围内结构可接受，业务交付还须有实际项目编译和定向测试。退出 1 表示违规/占位，2 表示配置或解析不完整；生成冲突也会阻塞。CI 可执行同一 verify 命令作为门槛。

仅需骨架或缺底层资料时保留占位并报告 SCAFFOLD；新生成的骨架 verify 失败是预期行为。只读旧接口用 check；v1 契约只保留存量兼容，不能替代新增 v2 的 verify。嵌套 DTO、复杂映射、原子版本族及 JSON 语义仍需真实框架资料和人工实现，不能改外部契约迎合工具。

## 工具维护与试用

源码 clone 不含 JAR；维护工具需要 JDK 17、Maven，构建后直接运行 tools 内入口，无需安装：

```bash
mvn -f tools/cli/pom.xml clean verify
sh tools/atomic-api.sh -h
sh tools/atomic-api.sh demo --repo ../atomic-api-demo
python -m unittest discover -s tools/tests -v
python tools/package.py
```

Python 仅用于维护测试/打包，使用标准库，无 pip 依赖。Windows 用 `tools/atomic-api.cmd`，CI 运行 `tools/tests/windows.ps1`。产物为 `tools/dist/atomic-api-cli.zip`。demo 使用虚构框架，不连接真实系统；包中的模板和旧契约用于生成及回归测试，不是另外一套开发规范。

仓库不包含私有业务源码或凭据。各业务仓库保留自己的 profile/contracts；普通复制不会随工具仓库 git pull 自动同步，当前版本没有自动同步/自动重编译机制。
