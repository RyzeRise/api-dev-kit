# 快速使用

目标：把简单任务交给 Code Agent，由它选 Atomic/App 手册、参考模板开发并运行检查器。
[L2EVPN 完整案例](skills/api-dev/references/l2evpn-example.md)保留查询 EVPL 节点、修改 EVPL Service ID 的定位、字段线索和验收流程。

## 1. 放入工具并初始化

解压维护者提供的 `api-dev.zip`，把完整 `api-dev/` 放入所用 Agent 的 Skill 目录；安装位置不限，下面仅以业务仓库 `.skills/api-dev/` 为例。不要只复制 SKILL.md。

以下检查与初始化可交给 Agent 按 Skill 执行，也可在**业务仓库根目录**手动执行（只需 Java 17+，无需全局安装、Maven 或 Python）：

```powershell
& ".skills/api-dev/scripts/atomic-api.cmd" -h
& ".skills/api-dev/scripts/atomic-api.cmd" init
```

Linux/macOS：

```bash
sh .skills/api-dev/scripts/atomic-api.sh -h
sh .skills/api-dev/scripts/atomic-api.sh init
```

若安装在其他位置，替换上述路径；脚本始终从自身位置找配套 JAR。init 只在目标业务仓库 `.atomic-api/` 准备两层 profile、契约、schema 和工作目录，保留已有文件，不复制 Skill。
[配置手册](skills/api-dev/references/configuration.md)说明真实框架核对与旧版迁移；init 成功不等于业务已适配。

## 2. 给 Agent 简单任务

> 请读取 `.skills/api-dev/SKILL.md`，开发 L2EVPN 的 EVPL 节点查询接入。先看已有实现，复用现有原子能力；方法已存在就增量修改。按手册选模板、实现和检查，并报告真实编译/测试和未验证项。

安装在其他目录时修改提示中的路径。修改 Service ID、补原子支持版本时直接说明实际任务即可，不需要用户先手写 YAML。
Agent 应先说明要复用和修改哪些文件；资料不足时指出具体文件或签名，不能从名称补造实现。

## 3. 看执行路线与交付

| 情况 | 正确路线 |
|---|---|
| 新操作，真实框架适配模板 | new → 填真实契约 → generate --dry-run → --apply → 实现业务与测试 → verify |
| 已有方法，或新方法复用已有类 | 默认增量修改；不因新增方法就新建整套类 |
| Atomic 补支持版本 | 只添加新版本并核对 Latest/注册；不全量重新生成历史实现 |
| 现有结构不适配 | 保留实际代码；按手册辅助检查，明确未完成工具验收 |

详见 [Skill 入口](skills/api-dev/SKILL.md)、[模板替换项](skills/api-dev/references/templates.md)、[规范对照](skills/api-dev/references/rules.md)。
TestPlan 需按 [测试接入步骤](skills/api-dev/references/templates.md#测试模板怎样使用)先验证“运行器发现且因占位失败”，再补真实调用和断言。
验收统一按 Skill“完成条件”：check 不等于 verify，自动结构通过不等于编译和业务测试通过。

## 维护者构建和打包

在本工具源码仓库根目录运行，需要 JDK 17+、Maven 和 Python 3：

```text
mvn -f skills/api-dev/tools/cli/pom.xml clean verify
python -m unittest discover -s skills/api-dev/tools/tests -v
python skills/api-dev/tools/package.py
```

产物 `skills/api-dev/tools/dist/api-dev.zip`，包含完整 Skill。源码 checkout 不含预编译 JAR，普通用户使用发布包；修改模板/Java 后重建，修改文档后重新打包。
Windows 便携检查在构建打包后运行 `& ./skills/api-dev/tools/tests/windows.ps1`，真实业务仍需本项目之外的私有源码和依赖验证。
