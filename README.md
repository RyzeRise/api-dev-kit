# Atomic API Pattern

中文开发 Skill + API contract + Java AST 骨架生成器/检查器。

面向 Delegate → 业务 Facade → Handler → 原子能力的分层架构，字段转换由 Converter 承担，确需业务汇总时使用 Builder。**当前版本 0.2.0**：通用设计 skill 与 IPRAN 故障业务 skill 分开，CLI 支持独立响应转换器和本地 Maven 父版本检查。默认预览、显式应用；生成的是业务骨架，不是已实现业务。

本次重构将专家反馈整理为职责、兼容性与验证规则：公共业务 DTO 不强制进入 SDK；单契约节点可作为响应内部类；内部 get 命名不改变已发布 query 工具或外部路径；JSON 注解按真实 wire 契约判断；构建失败先查本次变更、父版本、Reactor 与 classpath。可自动证明的规则进入 CLI，业务语义保留明确的人工审查边界。

## 快速试用

一次安装后，在业务项目根目录直接使用 `atomic-api`。源码安装需要 JDK 17、Maven 3.8+，安装脚本负责构建和命令注册；生成的业务代码使用 Java 8 兼容语法。

进入包含安装脚本的工具目录，按操作系统执行安装。

Windows PowerShell：

```powershell
./install.ps1
```

macOS / Linux：

```bash
sh install.sh
```

安装到用户目录，无需管理员权限；安装后打开新终端。安装脚本也接受已构建产物（PowerShell `-Jar <path>`，shell `--jar <path>`），此时无需 Maven。日常使用不需要输入 Java 命令。

进入业务项目根目录：

```bash
atomic-api -h
atomic-api init
atomic-api check --format json
```

`init` 默认只创建 `.atomic-api/`：配置为 `.atomic-api/profile.yaml`，示例为 `.atomic-api/contracts/example.yaml`，Skill 为 `.atomic-api/skills/` 下的 `atomic-api-design` 和 `ipran-fault-api-dev`。无法自动识别框架时生成明确标注的待配置草稿，使用前核对。再次 init 保留已有文件。

初始化还预留 `.atomic-api/work/`，临时计划、日志、调试脚本、参数文件和编译产物统一保存到 `.atomic-api/work/<operation>/`，不散落在 `.atomic-api/` 根目录。工作目录默认忽略版本管理；`generate`、`check` 默认输出到终端，不自动生成日志文件。已有文件不会被 init 移动或删除。

需要代码代理自动发现 Skill 时，可显式选择：

```bash
atomic-api init --agent codex
# 或 --agent claude / --agent cursor
```

此选项会额外复制完整 Skill 到对应项目目录（Codex `.agents/skills/`、Claude `.claude/skills/`、Cursor `.cursor/skills/`）。默认 init 不创建这些目录，不修改已有 AGENTS.md 或 CLAUDE.md。具体接入见 [CLI 使用约定](docs/cli.md)。

## 在独立目录试用

```bash
atomic-api demo --repo ../atomic-api-demo
```

demo 仅接受空目录，创建虚构框架、外部响应模型与三份 contract，不连接真实服务。

预览并生成单 Handler API：

```bash
atomic-api generate --repo ../atomic-api-demo --contract ../atomic-api-demo/contracts/single-handler.yaml
atomic-api generate --repo ../atomic-api-demo --contract ../atomic-api-demo/contracts/single-handler.yaml --apply
```

生成多 Handler API，再检查两条链路并编译示例：

```bash
atomic-api generate --repo ../atomic-api-demo --contract ../atomic-api-demo/contracts/multi-handler.yaml --apply
atomic-api check --repo ../atomic-api-demo --format json
mvn -f ../atomic-api-demo/pom.xml test
```

重复 generate 应显示 NOOP。`check` 不要求 contract，也不依赖 API 清单；传入 `--contract` 才增加设计对照。

检查会显示 `readiness=SCAFFOLD`，因为 Handler 底层调用和多结果 Builder 明确抛出 `ATOMIC_API_UNIMPLEMENTED` 异常，需要补充业务。退出 0 只表示当前结构检查没有 ERROR/INCOMPLETE，不能证明业务完成。

CI 的 `Build Java CLI` 工作流会上传 `atomic-api-jar` 和包含安装脚本的 `atomic-api-cli` 构建产物。以工作流实际成功状态为准。

## 独立响应转换器

新 contract 可指定 `target.responseConverter`：请求 Converter 只转请求，响应 Converter 校验并复制业务响应字段，Delegate 在原有 try 范围调用它。参考 [故障业务对象响应示例](examples/fault-response.yaml)，在另一个空 demo 目录单独试用；它与旧单 Handler 示例是同一操作的两种设计，不能同时应用到同一实现类。

省略新字段的旧 contract 继续使用 Delegate 私有映射方法。这个兼容路径不改变新业务推荐规范。生成器不自动实现 String 响应、嵌套节点 DTO、实际原子调用或 MapStruct 注解处理；这些按领域 skill 和真实项目实现。

## 用于实际项目

1. 复制一份示例 contract，填写真实操作、请求/响应、已有外部模型和 Handler 步骤。
2. 使用 init 创建的 `.atomic-api/profile.yaml` 配置框架类型和模块布局。profile 是项目级配置，不登记 API；仍兼容旧的根目录 `atomic-api-profile.yaml`。
3. 先 dry-run 审阅 diff。目标方法已有业务实现时报告 CONFLICT，不覆盖。
4. 需要依赖类型时通过多个 `--classpath <jar>` 提供；非标准源码根通过 `--source-root <目录>` 指定。
5. 应用后补充底层业务、字段汇总及测试，再按实际项目的构建流程验证。

已有 Delegate 实现外部接口时，生成器解析其准确方法签名，保留 context 等额外参数；请求字段必须能按名字、类型绑定。重载或复杂签名不明确时停止，不猜测。

**0.2.0 边界**：profile 支持 `facadeDefault: throw` 或显式 `return-null`；支持 Java 8/11/17/21 目标及完整限定的泛型集合字段；不改生成模型、不覆盖既有方法、不支持任意绑定表达式、复杂控制流或自动业务实现。Lombok 生成的方法若源码/依赖无法解析，需要补充编译后 classpath，或手工适配。详细规则及未覆盖项见 [使用约定](docs/cli.md)。

## 文档与 Skill

- [总体设计](docs/design.md)
- [CLI 与已实现范围](docs/cli.md)
- [Contract 规范](docs/contracts.md)
- [项目级 profile](docs/framework-profile.md)
- [通用设计 Skill](skills/atomic-api-design/SKILL.md)
- [IPRAN 故障业务 Skill](skills/ipran-fault-api-dev/SKILL.md)
- [Schema](schemas/api-contract.schema.json)
- [单 Handler](examples/single-handler.yaml)、[多 Handler](examples/multi-handler.yaml)、[独立响应 Converter](examples/fault-response.yaml)

两份 Skill 及其 references 均随 CLI 打包，`atomic-api init` 可直接安装到项目。升级时 init 仍保留本地已有文件；在临时目录初始化新版本，再逐文件对照合并已有 skill/profile，避免覆盖项目定制。Skill 使用 PATH 中的 atomic-api 命令，无需保留工具源码仓库的绝对路径。

## 测试

```bash
mvn -f cli/pom.xml verify
python -m pip install -r requirements-dev.txt
python scripts/validate_design.py
python -m unittest discover -s tests -v
```

Java 测试包含 AST 发现、异常传播、独立响应 Converter、Maven 父版本、contract 校验、安全写入、生成源码的 Java 8 编译及 CLI 示例。Python 脚本只验证设计文件与示例，不代替 Java AST。

本仓库仅包含虚构 `com.example` 示例和通用工具，不包含业务仓库源码或环境凭据。
