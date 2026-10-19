# Atomic API 开发手册

共用规范与完成条件见 [入口](../SKILL.md)，模板与替换项见 [Atomic 模板](templates.md#atomic)，首次配置见 [配置手册](configuration.md)。
本路线管理公共接口/DTO/Latest、版本实现与底层调用封装，不生成 App 编排类。

## 1. 查真实能力和版本证据

读公共接口、请求/响应 DTO、全部受影响版本、Latest、注册注解、管理器、底层 REST 封装和已有测试。
分清新增能力、修改已有方法、补支持版本三种任务；operation 命令示例、business 和 R26C10 均替换为真实值，版本不能由需求号或字符串顺序猜测。
配置 `.atomic-api/atomic-profile.yaml`，独立于 App。接口模式 Latest extends 公共接口、各版本 implements Latest；类模式各版本 implements 公共接口、Latest extends 最后支持实现并留在实现模块。
真实框架不符合这两种模型时，保留原框架并先适配工具。

## 2. 新增原子能力

确认没有同一能力和契约，通过 [适配检查表](configuration.md#生成前适配检查表)后运行：

```text
sh .atomic-api/tool/atomic-api.sh new --layer atomic --operation getEvplService --business evpl --api-version R26C10
```

输出 `.atomic-api/contracts/atomic-getEvplService.yaml`，Atomic 专属 `layer: atomic, version: 1`，与 App v1 不同。
填写 description、models.request/response.fields（完整类型、中文含义、required/diagnostic）；复杂内部节点需手工实现，模板只生成顶层 DTO。
releases 按真实支持先后列 id、固定 implementation 类名、完整 annotations 表达式；版本注解不可空。
latestAnnotations 按框架填写列表，无需注解时显式 `[]`。从已有源码核对注解类型和值；生成器不发明注册机制。

```text
sh .atomic-api/tool/atomic-api.sh generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --apply
```

预览接口/DTO/版本实现/Latest 与测试待办；实现真实底层调用后验收。测试待办位于 `.atomic-api/work/atomic-<operation>/tests/<Operation>TestPlan.java`。
固定命名为 AtomicApi<Operation>Interface、<Operation>ReqDto/RspDto、AtomicApi<Operation><Version>、AtomicApi<Operation>Latest；与实际命名不符时不重命名现有公开接口凑模板。
内部读取沿用实际 get/GetXxx/invokeGet 约定，已有公开 App query 名称与路径不变。

## 3. 修改已有方法

保留原接口、DTO、版本实现和 Latest，直接增量修改受影响方法，参照 `atomic/implementation.java.tpl` 的校验结构；手工日志按下表补齐。
同步已适配的真实 Atomic 契约字段/签名；检查全部受影响调用者和历史版本。
不要运行 new：它只创建新草稿，不用于迁移；不要全量 generate 已实现业务：生成器仅接受未改骨架的 NOOP，差异会 CONFLICT。

## 4. 为已有能力补一个支持版本

当前没有“只生成新版本”命令，全量 generate 会对历史真实实现报冲突。安全路径是：

| 顺序 | 必做项 |
|---|---|
| 1. 固定基线 | 记录历史文件和原契约；核实新版本的支持证据、接口/DTO、注册及底层差异，不改历史业务体 |
| 2. 只建新版本 | 参考 implementation 模板替换包/类/接口、操作/DTO、apiVersion 和实际注解；展开 requiredFields 为真实类型的必填校验，文件中不留 `{{...}}` |
| 3. 补真实逻辑与日志 | 实现本版本调用；模板中没有完整日志，手工沿用真实工厂/字段，补 entry() 和每个显式 return/throw 前的 ret()，不强加 try-catch |
| 4. 接版本与依赖 | 原契约保留历史 releases，按真实顺序加入新项；interface Latest 通常不改继承，class Latest 仅在新项确为最新时改 extends；保留自定义成员并核对注册/POM |
| 5. 测试并核对差异 | 按 [测试接入步骤](templates.md#测试模板怎样使用)覆盖新版本/Latest/历史行为；对照 diff 确认历史业务体及公共契约未被意外修改，再以完整契约 verify、实际编译测试 |

仅借用模板片段，不直接把 `.tpl` 改后缀当源码；生成器的字段展开和日志装饰不会在手工路径自动运行。

没有原契约时，按实际结构整理 Atomic YAML（字段见新增路线），经维护者核对后检查；只有真实命名与 roles 兼容才可由当前检查器验收。
现有结构不兼容时保留代码，列出不适配点与具体缺失签名，按实际项目编译测试并报告“未完成工具验收”。Atomic check 和 verify 都要求契约，不存在无契约自动发现全部版本的命令。

## 5. 验证版本和接线

```text
sh .atomic-api/tool/atomic-api.sh verify --layer atomic --contract .atomic-api/contracts/atomic-getEvplService.yaml --format json
```

核对实际注册发现、管理器选择和 Latest；注解存在不能证明动态接线成功。
测试应覆盖各支持版本、首支持、历史不支持、真实成功、非法参数、查无数据与底层失败，捕获底层请求并断言字段规范化及原因保留。
新模块还需核对父 modules、dependencyManagement、消费者与 classifier，合主线后复核 parent.version。
完成条件统一按入口执行。Windows 将命令前缀换为 `& ".atomic-api/tool/atomic-api.cmd"`。
