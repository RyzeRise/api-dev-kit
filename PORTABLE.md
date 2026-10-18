# 解压即用

需要 Java 17+。便携包自带 CLI JAR，不需要 Maven、Python、安装脚本、管理员权限或 PATH 配置；不下载依赖、不修改终端配置。请保留整个目录，不能只复制入口脚本。

把压缩包解压到任意目录，然后在**业务项目根目录**调用工具的完整路径：

Windows（PowerShell 或 CMD）：

```powershell
C:/tools/atomic-api/atomic-api.cmd init
C:/tools/atomic-api/atomic-api.cmd new --operation queryCircuit --business circuit
```

PowerShell 中路径含空格时用 `& "C:/my tools/atomic-api/atomic-api.cmd" -h`。

Linux / macOS：

```bash
sh /tools/atomic-api/atomic-api.sh init
sh /tools/atomic-api/atomic-api.sh new --operation queryCircuit --business circuit
```

命令默认作用于调用时的当前目录；也可以加 `--repo <业务项目路径>`。init 后由维护者核对真实 profile/roles，再按生成的 skill 操作。两份 skill 名称是 atomic-api-dev 与 app-api-dev。

仅试用：运行 `demo --repo <新的空目录>`，再使用 demo 中的 contracts/strict-handler.yaml 生成。verify 会报告未实现的业务占位，这是预期行为。

退出码原样返回；缺少 JAR 或 Java 时退出 2。更新工具时换用完整新便携包。已有项目的配置和 skill 不会被 init 覆盖，需对照合并；旧 skill 名称分别迁移为 atomic-api-dev 和 app-api-dev，确认定制已保留后再移除旧目录。

源码 ZIP 不含编译产物；普通使用者请下载 GitHub Actions 的 atomic-api-cli 构建产物，解开产物中的 atomic-api-cli.zip。开发者从源码构建一次 `mvn -f cli/pom.xml package` 后也能直接运行入口。

## 统一仓库目录

推荐把包内 atomic-api/ 整个目录放到业务仓库的 `.atomic-api/tool/`，在业务仓库根运行 `sh .atomic-api/tool/atomic-api.sh init`（Windows PowerShell：`& ".atomic-api/tool/atomic-api.cmd" init`）。初始化只分发到 `.atomic-api/skills/`，不再接受 --agent。

维护者核对 `.atomic-api/profile.yaml` 后，直接提示任意支持文件和终端操作的 Agent：

> 请读取 `.atomic-api/skills/app-api-dev/SKILL.md`，完成这个开发任务：……

Skill 包含真实脚本位置及操作系统调用方法。原子能力开发改用 atomic-api-dev/SKILL.md。旧 Agent 专用目录不会自动删除，升级时由维护者合并定制后移除，避免重复指令。
