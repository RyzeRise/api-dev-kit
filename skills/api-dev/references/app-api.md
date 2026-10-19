# App API 开发手册

共用规范与完成条件见 [入口](../SKILL.md)，本手册只补 App 路线。
模板选择见 [模板说明](templates.md#app)，字段/类名示例见 [L2EVPN 案例](l2evpn-example.md)。

## 1. 先判断已有还是新增

找到本次业务的入口、DTO、相关实现和测试，读方法体及调用者后选择：

| 现状 | 默认路线 |
|---|---|
| 方法已存在 | 在原文件增量修改，不运行 new/generate |
| 方法不存在，但应复用已有 Facade/Delegate 等 | 只增加所需成员及缺失文件，不另建一套类 |
| 确需一套新骨架，命名与框架兼容 | 通过适配检查表后走新增生成流程 |

复用已有类时可先核对新操作草稿与 dry-run；仅当目标与原类一致、计划只增所需成员且保留已有业务体时 apply，否则手工增量。已验证的复用范围和真实限制见 [校准记录](l2evpn-example.md#本轮工具源码校准与验证)。

写下真实请求/响应字段、默认值、异常以及调用链：

`Delegate（转请求 → 调 Facade → 转响应） → Facade → Handler.run（pre → invoke → post） → Atomic`

没有真实底层能力时转 Atomic 手册；不要按业务缩写猜开发层。

## 2. 选择实施路径

### 修改已有实现或向已有类加方法

按现有 DTO/方法和框架增量修改，参照 `app/` 模板的职责结构，保留已有命名和外部签名。
有适配过的 v2 契约则同步本次实际变更并 verify；不重新生成已有业务体。
已有契约不适配时走下方“模板不适用”，不能为了生成改公开契约。

### 新增且模板适配

先通过 [适配检查表](configuration.md#生成前适配检查表)，核对 `.atomic-api/profile.yaml`。
下面命令的 operation/business 替换为本次真实值，L2EVPN 仅是命令示例；已有该操作不运行 new。
Linux/macOS：

```text
sh .atomic-api/tool/atomic-api.sh new --layer app --operation queryL2evpnEvplNodes --business l2evpn
```

填写 `.atomic-api/contracts/queryL2evpnEvplNodes.yaml`：

| 项 | 依据 |
|---|---|
| description | 本次真实用途 |
| models.request/response.fields | 完整 Java 类型、字段名、中文含义、required/diagnostic |
| externalResponse | service 实际 REST 响应完整类名 |
| responseBindings | 外部响应字段名 → 业务响应字段名，只填字段名，不写 Java 表达式 |
| errorMode | 外部响应为 String 时选择 exception/message，遵守历史契约 |

字段列表必须从当前请求/响应 DTO 和调用者填写：name 是真实字段，type 是完整 Java 类型，description 写中文含义，required/diagnostic 按真实校验和安全约定选择。L2EVPN 的 neId 类型尚未在本工具仓核实，不在此示例中预填 String 或 Long。不要把格式示例当作业务契约。

String 外部响应的 responseBindings 只有保留键 message，其值为业务响应字段名；若字段也叫 message，则写 `message: message`。

class/sourceRoot、target、steps 按真实源码及 profile 的角色约束填写；复用已有类时显式核对 target，不能只沿用默认名称。泛型逗号类型使用 YAML 引号。

```text
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --dry-run
sh .atomic-api/tool/atomic-api.sh generate --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --apply
```

预览检查命名、路径、类型和既有文件。生成 DTO、Facade、Handler、两个 Converter、Delegate；真实多来源汇总才有 Builder。
同时生成 `.atomic-api/work/app-<operation>/tests/<Operation>TestPlan.java`；按 [测试接入步骤](templates.md#测试模板怎样使用)迁入相关模块。

### 模板不适用

内部节点 DTO、已有特殊类名、请求字段回填、复杂映射或框架签名与模板不符时，记录具体差异，保留原结构直接增量开发。
生成器只支持顶层 DTO 与普通映射，不生成 REST 路由、Maven 模块接线或真实 Atomic 调用。
适配检查契约前，只做辅助扫描（读取真实 profile，不要求虚构契约）：

```text
sh .atomic-api/tool/atomic-api.sh check --layer app --format json
```

扫描不保证只覆盖本次业务；无 v2 契约的规则可能 NOT_EVALUATED。缺类型时补项目真实源码/依赖或重复 `--classpath <真实JAR>`。

## 3. 实现和验证

按入口规范落实三阶段与 Context、Facade 编排、纯映射和 Delegate 异常适配。
复用已有 MapStruct 时保留 INSTANCE/严格目标策略，实际编译确认 processor/binding 与生成实现。
按真实字段完善测试：从 Handler.run 断言生命周期及非法输入不调用底层；转换断言完整字段、回填、集合边界；Delegate 的请求转换、Facade、响应转换分别失败时验证消息与 cause。
核对 Facade 注解、默认实现及配置的禁用注册；默认 return-null 若为框架禁用约定须保留，不作为真实业务成功实现。

真实结构已适配的 v2 契约：

```text
sh .atomic-api/tool/atomic-api.sh verify --layer app --contract .atomic-api/contracts/queryL2evpnEvplNodes.yaml --format json
```

App v1 只用于存量 generate/check，不能代替 v2 verify。完成条件统一按入口执行。
Windows 将所有命令前缀换为 `& ".atomic-api/tool/atomic-api.cmd"`。
