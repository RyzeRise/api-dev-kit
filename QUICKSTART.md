# 快速使用

目标：把简单任务交给 Code Agent，由它选 Atomic/App 手册、参考模板开发并运行检查器。
[L2EVPN 完整案例](skills/api-dev/references/l2evpn-example.md)保留查询 EVPL 节点、修改 EVPL Service ID 的定位、字段线索和验收流程。

## 1. 放入工具并初始化

使用维护者提供的 atomic-api-cli.zip，把包内 atomic-api 整个目录放到业务仓库 `.atomic-api/tool/`，保留 JAR。
在业务仓库根目录执行（运行只需 Java 17+，不用全局安装、Maven 或 Python）：

```powershell
& ".atomic-api/tool/atomic-api.cmd" -h
& ".atomic-api/tool/atomic-api.cmd" init
```

Linux/macOS：

```bash
sh .atomic-api/tool/atomic-api.sh -h
sh .atomic-api/tool/atomic-api.sh init
```

init 准备 `.atomic-api/` 下的 skill、references、模板、两层独立 profile 和契约草稿。
[配置手册](skills/api-dev/references/configuration.md)说明真实框架签名如何核对以及旧版本定制如何合并；init 保留已有文件，不等于已适配业务框架。

## 2. 给 Agent 简单任务

> 请读取 `.atomic-api/skills/api-dev/SKILL.md`，开发 L2EVPN 的 EVPL 节点查询接入。先看已有实现，复用现有原子能力；方法已存在就增量修改。按手册选模板、实现和检查，并报告真实编译/测试和未验证项。

修改 Service ID、补原子支持版本时直接说明实际任务即可，不需要用户先手写 YAML。
Agent 应先说明要复用和修改哪些文件；资料不足时指出具体文件或签名，不能从名称补造实现。

## 3. 看执行路线与交付

| 情况 | 正确路线 |
|---|---|
| 新操作，真实框架适配模板 | new → 填真实契约 → generate --dry-run → --apply → 实现业务与测试 → verify |
| 已有方法 | 参照模板增量修改，用适配后的真实契约检查 |
| Atomic 补支持版本 | 只添加新版本并核对 Latest/注册；不全量重新生成历史实现 |
| 现有结构不适配 | 保留实际代码；按手册辅助检查，明确未完成工具验收 |

详见 [Skill 入口](skills/api-dev/SKILL.md)、[模板替换项](skills/api-dev/references/templates.md)、[规范对照](skills/api-dev/references/rules.md)。
生成器附带的 TestPlan 各项明确失败；须用项目真实框架、调用和断言补齐，不能当成已通过测试。
验收统一按 Skill“完成条件”：check 不等于 verify，自动结构通过不等于编译和业务测试通过。

## 维护者构建和打包

在本工具源码仓库根目录运行，需要 JDK 17+、Maven 和 Python 3：

```text
mvn -f tools/cli/pom.xml clean verify
python -m unittest discover -s tools/tests -v
python tools/package.py
```

产物 `tools/dist/atomic-api-cli.zip`。模板、skill 和 references 都在 JAR 内；修改后需重新构建打包。
Windows 便携检查在构建打包后运行 `& ./tools/tests/windows.ps1`，真实业务仍需本项目之外的私有源码和依赖验证。
