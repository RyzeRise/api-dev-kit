# 快速使用：以 L2EVPN 开发为例

你要做的是：让 Code Agent 按本项目的结构开发 Java API，再用工具检查容易写错的部分。

只想快速开始，先做第 1～3 步；第 4～5 步用来理解和验收 Agent 的产物。

这套工具包含一份 skill、两份开发手册和一个便携命令。**它不会自动完成真实底层调用、REST 路由定义、Maven 接线和业务测试。** 当前通用生成模板也不能原样重建已有的完整 L2EVPN 实现；下面会区分“照现有代码开发”和“适用模板时生成”。

## 1. 把工具放进业务仓库

使用维护者提供的 `atomic-api-cli.zip`。解压后，把其中的 `atomic-api` 目录作为业务仓库的 `.atomic-api/tool` 目录，完整保留配套 JAR。

最终应存在：

```text
业务仓库/.atomic-api/tool/atomic-api.cmd
业务仓库/.atomic-api/tool/atomic-api.sh
业务仓库/.atomic-api/tool/lib/atomic-api/atomic-api.jar
```

终端切到**业务仓库根目录**。Windows PowerShell：

```powershell
& ".atomic-api/tool/atomic-api.cmd" -h
& ".atomic-api/tool/atomic-api.cmd" init
```

Linux/macOS：

```bash
sh .atomic-api/tool/atomic-api.sh -h
sh .atomic-api/tool/atomic-api.sh init
```

预期：命令退出 0，出现 `.atomic-api/skills/api-dev/SKILL.md` 及其两份 references。init 保留已有文件，适合重复执行。运行工具只需 Java 17+，不需要 Maven、Python 或全局安装；**编译业务项目仍使用项目原有的 Maven 环境**。

只有源码、没有 ZIP？参见本文最后的“维护者怎样打包”。

## 2. 首次接入：让 Agent 核对一次项目配置

已经配置过的仓库可以跳过。第一次使用，把下面这段发给 Code Agent：

> 请读取 `.atomic-api/skills/api-dev/SKILL.md` 和 `.atomic-api/skills/api-dev/references/app-api.md`。先完成本仓库的 App 开发配置：以现有 L2EVPN 的 Delegate、Facade、Handler、DTO、Converter 为依据，核对 `.atomic-api/profile.yaml` 的真实框架类型、源码目录、八个 roles 和日志 API。列出配置依据及当前生成模板与已有代码不适配的地方。不要使用 com.example 草稿替代真实项目，不改业务架构来迁就工具。

Agent 应查本仓库源码，核对真实 package、模块、注解和异常类型。配置项逐项解释见 [App 手册](skills/api-dev/references/app-api.md)的“首次接入时怎样填写 profile”。profile 是“类放在哪里、用什么框架”的项目配置，通常只需维护者核对一次；contract 是“这次操作有什么字段和映射”，每个操作单独一份。

若发现模板的固定类名不符合现有 `OpsIpranFaultL2evpnFacade` 等结构，或者复杂 DTO 无法生成，应先说明限制，不能声称配置好了就能全自动生成。`init` 成功只代表文件准备好，不代表项目已适配。

## 3. 给 Agent 一个实际任务

以这次 L2EVPN 场景为例，可以直接发：

> 请读取 `.atomic-api/skills/api-dev/SKILL.md`，按 App 手册开发 L2EVPN 的“查询 EVPL 节点”和“修改 EVPL Service ID”接入。先检查当前分支已有实现：查询使用 `QueryL2evpnEvplNodesReqDto/RspDto`，修改使用 `ModifyL2evpnEvplServiceIdReqDto/RspDto`，并复用现有 EVPL 原子能力。保留内部节点 DTO、null/空列表、字段顺序和已有错误契约。方法已存在就核对并增量修改，不重复创建接口。先说明准备复用和修改的文件，再实现、检查、编译和运行定向测试；未验证项明确列出。

这是业务任务描述，**不用你先手写整份 YAML**。Agent 应从已有代码找到真实参数、字段和调用关系；缺少关键资料时问具体问题，不靠接口名称猜字段。

只开发查询时删掉修改部分；只开发修改时反过来。若本分支两项能力都已经完成，Agent 应报告核对结果，不为演示故意改代码。

若底层确实没有所需能力，再让 Agent 读取 `references/api-atomic.md`，先补 Atomic，再接 App。仅仅提到 EVPL，不等于需要同时开发两层。

## 4. 看 Agent 是否在做正确的事情

以查询为例，应找到这条实际调用关系：

| 位置 | 要完成的工作 |
|---|---|
| `NetChatOpsApiFaultL2EvpnIpranDelegateImpl` | 请求转换、调用 Facade、响应转换及异常适配 |
| `IpranFaultL2evpnRequestConverter` | REST 请求转业务请求 |
| `OpsIpranFaultL2evpnFacadeImpl` | 编排 `IpranFaultL2evpnQueryEvplNodesApiHandler.run` |
| Query Handler | pre 校验/构建请求，invoke 调真实 Atomic，post 校验/转换结果 |
| `IpranFaultL2evpnEvplNodeConverter` | 原子节点转业务节点，沿用现有转换约定 |
| `IpranFaultL2evpnResponseConverter` | 业务响应转 REST 响应，保留校验及字段回填 |

这些是本次实际类名，完整包名和方法签名以当前源码为准。修改 Service ID 同理使用 Modify Handler 及 `AtomicApiModifyEVPLInterface`，具体字段从真实请求读取，不在本文猜测。

你不应看到：Facade 自己查外部资源、Delegate 堆业务逻辑、把 App DTO 放回 SDK，或把真实底层失败改为 null/空成功。

### 什么时候使用生成器

新操作适合模板且项目已经适配时，Agent 才执行以下流程。Windows PowerShell 示例：

```powershell
& ".atomic-api/tool/atomic-api.cmd" new --layer app --operation queryL2evpnEvplNodes --business l2evpn
```

预期创建 `.atomic-api/contracts/queryL2evpnEvplNodes.yaml` 草稿。**已有该操作时不要重复运行，也不要删除已有文件让命令通过。** Agent 按真实 DTO/REST 类型填写草稿，然后预览：

```powershell
& ".atomic-api/tool/atomic-api.cmd" generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --dry-run
```

只有路径、命名和类型都适配，审阅预览后才生成：

```powershell
& ".atomic-api/tool/atomic-api.cmd" generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --apply
```

这只是生成结构，真实 Atomic 调用仍需 Agent 实现。现有 L2EVPN 的内部节点 DTO、既有类名或响应回填若不适配模板，应按 App 手册直接做增量开发；适配工具前明确报告“未完成工具验收”，不能强行 apply 或宣称 verify 已通过。

## 5. 怎样验收

契约已按实际结构适配时：

```powershell
& ".atomic-api/tool/atomic-api.cmd" verify --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --format json
```

| 看到的结果 | 怎么理解 |
|---|---|
| 缺少配套 JAR / Java | 工具尚不能运行，先补完整工具包或 Java 17+ |
| INCOMPLETE / 退出 2 | 类型、配置或依赖不完整，还没完成检查 |
| ATOMIC_API_UNIMPLEMENTED、SCAFFOLD / 退出 1 | 只有骨架，尚未接真实底层 |
| 其他 FAIL / 退出 1 | 按报告中的文件、message、fix 修源码后重查 |
| REVIEW_REQUIRED | 工具不能证明的业务语义仍需核对和测试 |
| 退出 0 / STRUCTURE_READY | 自动结构检查无阻塞；不等于完整业务验收通过 |

最终要求 Agent 提供：实际改动文件、复用的 Atomic、编译和测试命令/退出码、未验证项。L2EVPN 重点看：非法输入不调用底层、节点字段/顺序和 null/空集合不变、修改请求正确、底层失败保留原因，以及新增模块父版本与主线一致。

没有适配的真实契约时，不强行套用示例 YAML。先在业务仓库根目录执行辅助检查：

```powershell
& ".atomic-api/tool/atomic-api.cmd" check --layer app --format json
```

它读取实际 profile，扫描可发现的 App 入口，不保证只检查 L2EVPN。没有 v2 契约时 N 规则可能 NOT_EVALUATED；即使命令退出 0，也不能替代 v2 verify、真实项目编译和业务测试。报告“未完成工具验收”，不伪造通过结果。

## 维护者怎样打包

在 **API Dev Kit 源码仓库根目录**执行，需 JDK 17+、Maven 和 Python 3：

```text
mvn -f tools/cli/pom.xml clean verify
python -m unittest discover -s tools/tests -v
python tools/package.py
```

分发 `tools/dist/atomic-api-cli.zip` 即可。普通使用者不需要这些构建工具。升级 skill 内容后需要重新打包 JAR；旧业务仓库执行 init 不会覆盖定制文件，应先比较并合并新 skill，不能只更新仓库文字就认为安装处已更新。
