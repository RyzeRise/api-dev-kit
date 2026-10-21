# 首次接入与配置核对

仅在首次接入或框架变化时阅读；日常按 [App](app-api.md) / [Atomic](api-atomic.md) 操作。
两种方式先按下表核对真实源码与依赖签名。

- **纯文档**：记录文件/签名与结论即可，不运行 init，不创建 profile 或 YAML；不满足生成器形状时按实际框架手工实现。
- **工具辅助**：使用入口中的 SKILL_DIR，在业务仓库根执行 `sh "$SKILL_DIR/scripts/atomic-api.sh" init`；Windows 使用对应命令。两份 profile 独立，init 保留既有文件；本页后半的 profile 和日志配置仅用于工具。

## 生成前适配检查表

从相邻已运行的代码记录“文件/方法签名 → 核对结果”；只填类名不算适配。下表区分真实职责与生成器形状：纯文档据此选择可借用片段，工具辅助据此决定能否生成，不把工具支持范围当作业务强制结构。

| 核对项 | App 模板要求 | Atomic 模板要求 |
|---|---|---|
| 命名与位置 | 契约 target/models/steps 的类名、包、源码根符合项目；复用类时预览只增所需成员 | 公共接口/DTO、版本实现/Latest 的命名及模块符合 roles |
| 调用签名 | Handler 为双泛型 I/O，BasicApiContext 可用；preHandler(I)→Context、invokeAtomicApi(Context)→Context、postHandler(Context)→O、run(I)→O 的参数/返回/可见性/throws 与模板兼容（Context 指 BasicApiContext） | 本操作 ReqDto→RspDto 签名与父接口兼容；Latest 的 class/interface 继承模式符合真实框架 |
| 边界与注册 | 核对内部异常 String 构造器/getErrMsg、外部异常 String 构造器/cause、getFacadeImpl(Class) 返回类型、组件/注入注解及已有对外方法参数 | 核对真实注册注解的目标/参数、支持版本顺序与 Latest 注册，不凭空补值 |
| DTO 与转换 | 顶层 DTO、访问器、REST 响应无参构造/setter 与类型匹配；内部节点、回填或复杂映射走增量路线 | 顶层 DTO 与实际字段/访问器相容；复杂节点手工保留原结构 |
| 依赖与日志 | 两层都须解析真实源码/JAR；日志工厂与 entry/ret 签名见下节。自动日志不支持时明确列出手工补齐项 | 同左，且公共模块不得依赖版本实现 |

信息缺失就列出具体文件/签名；确定不兼容则保留真实框架，走手工增量或交维护者适配工具。
工具辅助全部已确认才生成；日志若须手工补齐，交付前单独核对。dry-run 成功不是完整签名兼容证明，两种方式最终都须真实编译。

## 自动预检实际证明到哪里（工具辅助）

以下依据本工具源码，不是对私有 L2EVPN 签名的确认：

| 清单项 | 自动预检当前能力 | 仍需真实源码/编译证明 |
|---|---|---|
| Handler/Context | Generator.validateFramework 解析基类并查四个生命周期方法名 | 泛型个数、参数/返回、可见性、throws、BasicApiContext 是否存在/可继承；同名不能证明兼容 |
| 异常 | 解析内外异常类型，查 getErrMsg 方法名 | String 构造器、getErrMsg 返回类型、checked 异常关系与实际 cause 行为 |
| 组件管理器 | 解析配置类型；模板直接调用 getFacadeImpl(Class) | 方法完整泛型签名及运行时注册；解析类不代表方法正确 |
| 日志 | LoggingSupport.validate 检查工厂静态/Class 参数/返回类型及无参 entry/ret | 真实工厂是否采用此签名、可访问性、日志重载/载荷与运行效果 |
| Atomic 注册/Latest | AtomicTool 校验契约注解、已配置继承关系 | 实际首次支持版本、注册器发现、管理器选择、历史不支持行为 |

不要把“清单已写出要求”理解成“工具已自动证明全部要求”。本轮源码范围与红绿测试结果见 [校准记录](l2evpn-example.md#真实源码校准2026-10-19)。

## 本项目已确认的适配边界

L2EVPN 的 Handler 双泛型、protected BasicApiContext 生命周期及组件管理器泛型签名已通过源码核对，实际 Handler.run 测试也已接入。以下限制仍存在：

- `OpsApiException.getErrMsg()` 来自 Lombok @Getter；源码解析器没有执行注解处理，按 getAllMethods 查不到时不代表生产代码缺少 getter。保留 Lombok，采用手工增量和真实编译证明；不要手写重复 getter 或改异常类型来迎合预检。
- App 查询返回 List，生成器的外部响应 setter 模型不适用；Atomic 使用嵌套版本接口链，当前两种平面模型均不适用。
- 当前实际日志为 SLF4J，ApiHandler 使用 @Slf4j，Facade 使用 LoggerFactory.getLogger(Class)。没有 entry/ret 方法的 API 不能配置为自动日志方案。日志规范待适配须显式报告。
- 仓库 AGENTS/knowledge-spec 中部分旧模块位置（例如 Facade 仅 function-sdk、Atomic 公共类型在 api-common）与当前源码不一致。已有类增量保留真实位置，不自动搬迁；新增结构如与仓库硬规则冲突，明确列出文件和冲突后处理。

具体源码、字段和验收范围见 [真实源码校准](l2evpn-example.md#真实源码校准2026-10-19)。

## App profile（仅工具辅助）

本节只在首次接入或框架变化时使用。读取 `.atomic-api/profile.yaml` 的现有完整结构并修改真实值；没有配置时先运行 init。不要手工只建下面几行导致缺字段。

| types 下的键 | 从哪里找到真实完整类名 |
|---|---|
| componentManagement | Delegate 使用的组件管理器，核对 getFacadeImpl 等实际方法 |
| facadeAnnotation | 现有 Facade 接口的注册注解 |
| handlerBase | 现有 Query/Modify Handler 的框架基类 |
| internalException | Handler/Facade 的内部业务异常，核对构造器和 getErrMsg |
| externalException | service 对外包装异常，核对构造器及 cause 保留方式 |
| injectionAnnotation | 当前代码使用的注入注解 |
| componentAnnotation | 具体 Handler/Helper 使用的组件注解 |

| roles 下的键 | 应指向哪类代码的 packagePrefix 与 sourceRoot |
|---|---|
| facade | 公共模块的业务 Facade 接口 |
| request | 公共模块的 Query/Modify 请求 DTO |
| response | 公共模块的 Query/Modify 响应 DTO |
| implementation | 业务模块的 Facade 实现 |
| handler | 业务模块的 Query/Modify Handler |
| delegate | service 模块的 Delegate |
| requestConverter | service 模块的请求 Converter |
| responseConverter | service 模块的响应 Converter |

每个 role 都写真实包前缀与模块源码根目录；layout 配置相应 packagePrefix/sourceRoot。磁盘目录前缀与 package 不一致时用 directoryPrefix，不批量搬迁历史源码。roles 配置归属；new 采用默认类名，但 App 契约 target 可以指向真实已有 Facade/实现/Delegate/Converter，并非强制它们采用默认简单类名。Conventions.location 仍要求各角色 package 等于该角色 packagePrefix + business、sourceRoot 匹配；两个 Converter 还必须同包，DTO 后缀遵守 ReqDto/RspDto。现有包结构不符合这些条件，或存在复杂内部节点/回填时，不能仅改 roles 假装适配。

例如 request 的 sourceRoot 从当前公共 DTO 所在模块截到 src/main/java，packagePrefix 从其 package 声明归纳；不要从文件系统目录猜 package。targetJava 与业务编译版本一致。facadeDefault 按既有默认实现选 throw 或 return-null；已有禁用注册还需核对 disabledFacade.configuration/mapField。

未知类型通过真实源码或 --classpath 的实际 JAR 解析，不改 profile 以掩盖源码违规。


## Atomic profile（仅工具辅助）

读取真实相邻 Atomic 能力的公共接口、版本实现、Latest、版本注解、管理器和底层 REST 封装。维护者核对独立的 `.atomic-api/atomic-profile.yaml`：

- targetJava：实际业务 Java 版本；工具自身仍需 Java 17+。
- layout 与 roles：apiInterface、request、response、implementation、latest。公共接口/DTO 在公共模块，实现放版本模块。directoryPrefix 支持物理目录与 package 不同的仓库。
- latestKind：按实际框架选 interface 或 class。interface 模式中，Latest extends 公共接口，各版本 implements Latest；class 模式中，各版本 implements 公共接口，Latest extends 契约最后一个版本实现。class 模式 Latest 必须放实现模块，不能让公共模块反向依赖实现。不符合这两种关系的实际框架须先适配工具，不能迁就生成器重构框架。
- baseInterfaces：实际公共接口父类型列表；interfaceAnnotations：实际接口注解表达式列表，两者即使为空也显式填写。保留类型参数，生成后用真实编译验证。
- logging：按下节配置真实日志 API，可缺省但日志规范保持 REVIEW_REQUIRED。

init 仅提供 com.example 草稿，不能代表真实项目注册方式。注册通过契约的完整注解表达式显式配置；例如 `@项目实际包.Version(项目实际版本常量)`，必须从已有源码复制并核对，不直接使用示例。工具不会根据类名自动发明管理器、注解或版本值。

类型含泛型逗号时按 YAML 字符串加引号，例如 `type: 'java.util.Map<java.lang.String,java.lang.String>'`。


## 日志配置（仅工具辅助）

各自 profile 的 logging 包含 type（实际日志类）、factoryType（实际工厂）、factoryMethod（静态工厂方法）。
当前支持静态工厂接收 Class、返回日志类，以及无参 entry()/ret()。LoggingSupport 校验签名后，在模板渲染结果中添加日志字段、入口与每个显式出口调用，不额外制造 try-catch。
其他签名需要维护者适配工具；不伪造项目 API。未配置保留 REVIEW_REQUIRED。

## 升级与定制

升级以完整 `api-dev/` 目录为单位：先解压到新位置，比较合并旧 Skill 的脚本、references 和模板定制，再更新 Agent 引用路径。
init 只准备 `.atomic-api/` 的业务配置/契约/schema/工作目录，保留既有文件，不复制或升级 Skill，也不创建 Agent 专属目录。
旧 `.atomic-api/skills/`、`.atomic-api/tool/`、`.atomic-api/templates/` 会保留并提示迁移；确认定制已合并、业务配置未变后再手动移除旧安装，避免 Agent 继续读取旧入口。
纯文档修改可读模板不需要构建 JAR；工具辅助的生成器读 JAR 内模板，改变生成行为须重建 JAR 并重打包，不能只改可读文件。
