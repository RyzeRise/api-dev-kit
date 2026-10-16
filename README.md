# Atomic API Pattern

中文开发 Skill + API contract + Java AST 骨架生成器/检查器。

面向 Delegate → SDK Facade → ApiHandler → Builder 分层架构。**当前提供可运行的 0.1.0 版本**：支持单 Handler 与多 Handler 顺序编排，默认预览，显式应用，独立源码检查。生成的是可编译的业务骨架，不是已实现业务。

## 快速试用

一次安装后，在业务项目根目录直接使用 `atomic-api`。源码安装需要 JDK 17、Maven 3.8+，安装脚本负责构建和命令注册；生成的业务代码使用 Java 8 兼容语法。

Windows PowerShell：

```powershell
git clone https://github.com/RyzeRise/atomic-api-pattern.git
cd atomic-api-pattern
./install.ps1
```

macOS / Linux：

```bash
git clone https://github.com/RyzeRise/atomic-api-pattern.git
cd atomic-api-pattern
sh install.sh
```

安装到用户目录，无需管理员权限；安装后打开新终端。安装脚本也接受已构建产物（PowerShell `-Jar <path>`，shell `--jar <path>`），此时无需 Maven。日常使用不需要输入 Java 命令。

进入业务项目根目录：

```bash
atomic-api -h
atomic-api init
atomic-api check --format json
```

`init` 默认只创建 `.atomic-api/`：配置为 `.atomic-api/profile.yaml`，示例为 `.atomic-api/contracts/example.yaml`，Skill 为 `.atomic-api/skills/atomic-api-design/`。无法自动识别框架时生成明确标注的待配置草稿，使用前核对。再次 init 保留已有文件。

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

demo 仅接受空目录，创建虚构框架、外部响应模型与两份 contract，不连接真实服务。

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

## 用于实际项目

1. 复制一份示例 contract，填写真实操作、请求/响应、已有外部模型和 Handler 步骤。
2. 使用 init 创建的 `.atomic-api/profile.yaml` 配置框架类型和模块布局。profile 是项目级配置，不登记 API；仍兼容旧的根目录 `atomic-api-profile.yaml`。
3. 先 dry-run 审阅 diff。目标方法已有业务实现时报告 CONFLICT，不覆盖。
4. 需要依赖类型时通过多个 `--classpath <jar>` 提供；非标准源码根通过 `--source-root <目录>` 指定。
5. 应用后补充底层业务、字段汇总及测试，再按实际项目的构建流程验证。

已有 Delegate 实现外部接口时，生成器解析其准确方法签名，保留 context 等额外参数；请求字段必须能按名字、类型绑定。重载或复杂签名不明确时停止，不猜测。

**0.1.0 边界**：仅支持 profile 的 `facadeDefault: throw`；不改生成模型、不覆盖既有方法、不支持任意绑定表达式、复杂控制流或自动业务实现。Lombok 生成的方法若源码/依赖无法解析，需要补充编译后 classpath，或手工适配。详细规则及未覆盖项见 [使用约定](docs/cli.md)。

## 文档与 Skill

- [总体设计](docs/design.md)
- [CLI 与已实现范围](docs/cli.md)
- [Contract 规范](docs/contracts.md)
- [项目级 profile](docs/framework-profile.md)
- [中文 Skill](skills/atomic-api-design/SKILL.md)
- [Schema](schemas/api-contract.schema.json)
- [单 Handler](examples/single-handler.yaml)、[多 Handler](examples/multi-handler.yaml)

Skill 已随 CLI 打包，`atomic-api init` 可直接安装到项目。Skill 使用 PATH 中的 atomic-api 命令，无需保留工具源码仓库的绝对路径。

## 测试

```bash
mvn -f cli/pom.xml verify
python -m pip install -r requirements-dev.txt
python scripts/validate_design.py
python -m unittest discover -s tests -v
```

Java 测试包含 AST 发现、异常传播、contract 校验、安全写入、生成源码的 Java 8 编译及 CLI 示例。Python 脚本只验证设计文件与示例，不代替 Java AST。

本仓库仅包含虚构 `com.example` 示例和通用工具，不包含业务仓库源码或环境凭据。
