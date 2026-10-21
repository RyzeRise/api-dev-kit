# 快速使用

目标：把简单任务交给 Code Agent，沿同一流程选 Atomic/App 手册、参考模板开发并验证。[L2EVPN 完整案例](skills/api-dev/references/l2evpn-example.md)保留查询 EVPL 节点、修改 EVPL Service ID 的定位、字段线索和验收。

## 1. 放入 Skill，选择方式

将完整 `api-dev/` 放入 Agent 的 Skill 目录，下面仅以业务仓 `.skills/api-dev/` 为例，其他安装位置也可。

| 方式 | 准备 |
|---|---|
| 纯文档（默认） | 复制源码仓的 `skills/api-dev/` 即可，无需 JAR、工具编译或 init |
| 工具辅助 | 解压维护者的 `api-dev.zip`，使用内含 JAR 的完整 Skill；工具需要 Java 17+ |

纯文档直接进入第 2 步。工具辅助由 Agent 在业务仓根目录初始化（也可手动执行）：

```bash
sh .skills/api-dev/scripts/atomic-api.sh -h
sh .skills/api-dev/scripts/atomic-api.sh init
```

Windows PowerShell 将前缀替换为 `& ".skills/api-dev/scripts/atomic-api.cmd"`。其他安装位置替换路径。
init 只在业务仓 `.atomic-api/` 准备 profile/契约/schema/工作目录，保留已有文件，不复制 Skill；成功不等于框架已适配。

## 2. 给 Agent 简单任务（两种方式相同）

> 请读取 `.skills/api-dev/SKILL.md`，用纯文档方式开发 L2EVPN 的 EVPL 节点查询接入。先看已有实现、复用现有能力，方法已存在就增量修改。按手册选模板、实现和验收，报告真实编译测试及未验证项。

要启用工具，将“纯文档”改为“工具辅助”；未指定时默认纯文档。修改 Service ID 或补原子版本时直接描述任务，无需先手写 YAML。
Agent 应先说明要复用和修改哪些文件；资料不足时指出具体文件或签名，不能从名称补造实现。

## 3. 核对开发路线

| 情况 | 共同要求 | 纯文档 | 工具辅助 |
|---|---|---|---|
| 新能力/整套新骨架 | 核对真实职责、字段和签名 | 借用模板实现 | 适配后 new → 填契约 → generate --dry-run → --apply → 实现 |
| 已有方法，或新方法复用已有类 | 保留已有实现，只改所需成员 | 手工增量 | 适配时可预览增量计划，不适配则手工 |
| Atomic 补支持版本 | 只新增版本，核对 Latest/注册及消费者 | 按手工清单实施 | 同样手工实施；平面契约适配时额外同步契约 |

详见 [Skill 入口](skills/api-dev/SKILL.md)、[App](skills/api-dev/references/app-api.md)、[Atomic](skills/api-dev/references/api-atomic.md)；每一步只在执行方式不同处拆分说明。

## 4. 看验收与交付

两种方式都按 [测试接入步骤](skills/api-dev/references/templates.md#测试模板怎样使用)确认用例实际运行，核对 [规范对照](skills/api-dev/references/rules.md)，完成项目真实编译测试。

- **纯文档**：交人工审查和业务测试证据，注明未运行工具检查；不要求 profile/contract/check/verify。
- **工具辅助**：另交 check/verify 结果；结构不适配时报告工具验收未完成。自动结构通过不等于业务通过。

## 维护者构建和打包

在本工具源码仓库根目录运行，需要 JDK 17+、Maven 和 Python 3：

```text
mvn -f skills/api-dev/tools/cli/pom.xml clean verify
python -m unittest discover -s skills/api-dev/tools/tests -v
python skills/api-dev/tools/package.py
```

产物 `skills/api-dev/tools/dist/api-dev.zip`，包含完整 Skill。源码 checkout 不含预编译 JAR；纯文档用户直接复制源码目录，工具辅助用户使用发布包；修改模板/Java 后重建，修改文档后重新打包。
Windows 便携检查在构建打包后运行 `& ./skills/api-dev/tools/tests/windows.ps1`，真实业务仍需本项目之外的私有源码和依赖验证。
