# 首次接入与配置核对

仅在首次接入或框架变化时阅读；日常按 [App](app-api.md) / [Atomic](api-atomic.md) 操作。
执行 `sh .atomic-api/tool/atomic-api.sh init` 创建草稿；Windows 使用入口中对应命令。
两份 profile 独立，init 保留既有文件；配置必须由真实源码与依赖签名核对。

## 生成前适配检查表

从相邻已运行的代码记录“文件/方法签名 → 核对结果”；只填类名不算适配。

| 核对项 | App 模板要求 | Atomic 模板要求 |
|---|---|---|
| 命名与位置 | 契约 target/models/steps 的类名、包、源码根符合项目；复用类时预览只增所需成员 | 公共接口/DTO、版本实现/Latest 的命名及模块符合 roles |
| 调用签名 | Handler 为双泛型 I/O，BasicApiContext 可用；preHandler(I)→Context、invokeAtomicApi(Context)→Context、postHandler(Context)→O、run(I)→O 的参数/返回/可见性/throws 与模板兼容（Context 指 BasicApiContext） | 本操作 ReqDto→RspDto 签名与父接口兼容；Latest 的 class/interface 继承模式符合真实框架 |
| 边界与注册 | 核对内部异常 String 构造器/getErrMsg、外部异常 String 构造器/cause、getFacadeImpl(Class) 返回类型、组件/注入注解及已有对外方法参数 | 核对真实注册注解的目标/参数、支持版本顺序与 Latest 注册，不凭空补值 |
| DTO 与转换 | 顶层 DTO、访问器、REST 响应无参构造/setter 与类型匹配；内部节点、回填或复杂映射走增量路线 | 顶层 DTO 与实际字段/访问器相容；复杂节点手工保留原结构 |
| 依赖与日志 | 两层都须解析真实源码/JAR；日志工厂与 entry/ret 签名见下节。自动日志不支持时明确列出手工补齐项 | 同左，且公共模块不得依赖版本实现 |

信息缺失就列出具体文件/签名；确定不兼容则保留真实框架，走手工增量或交维护者适配工具。
全部已确认才生成；日志若须手工补齐，交付前单独核对。dry-run 成功不是完整签名兼容证明，最终仍须真实编译。

## App profile

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

每个 role 都写真实包前缀与模块源码根目录；layout 配置相应 packagePrefix/sourceRoot。磁盘目录前缀与 package 不一致时用 directoryPrefix，不批量搬迁历史源码。这里只配置归属，不会改变生成器固定类名，也不能单靠正确 roles 消除已有命名或复杂模型的模板限制。

例如 request 的 sourceRoot 从当前公共 DTO 所在模块截到 src/main/java，packagePrefix 从其 package 声明归纳；不要从文件系统目录猜 package。targetJava 与业务编译版本一致。facadeDefault 按既有默认实现选 throw 或 return-null；已有禁用注册还需核对 disabledFacade.configuration/mapField。

未知类型通过真实源码或 --classpath 的实际 JAR 解析，不改 profile 以掩盖源码违规。


## Atomic profile

读取真实相邻 Atomic 能力的公共接口、版本实现、Latest、版本注解、管理器和底层 REST 封装。维护者核对独立的 `.atomic-api/atomic-profile.yaml`：

- targetJava：实际业务 Java 版本；工具自身仍需 Java 17+。
- layout 与 roles：apiInterface、request、response、implementation、latest。公共接口/DTO 在公共模块，实现放版本模块。directoryPrefix 支持物理目录与 package 不同的仓库。
- latestKind：按实际框架选 interface 或 class。interface 模式中，Latest extends 公共接口，各版本 implements Latest；class 模式中，各版本 implements 公共接口，Latest extends 契约最后一个版本实现。class 模式 Latest 必须放实现模块，不能让公共模块反向依赖实现。不符合这两种关系的实际框架须先适配工具，不能迁就生成器重构框架。
- baseInterfaces：实际公共接口父类型列表；interfaceAnnotations：实际接口注解表达式列表，两者即使为空也显式填写。保留类型参数，生成后用真实编译验证。
- logging：按下节配置真实日志 API，可缺省但日志规范保持 REVIEW_REQUIRED。

init 仅提供 com.example 草稿，不能代表真实项目注册方式。注册通过契约的完整注解表达式显式配置；例如 `@项目实际包.Version(项目实际版本常量)`，必须从已有源码复制并核对，不直接使用示例。工具不会根据类名自动发明管理器、注解或版本值。

类型含泛型逗号时按 YAML 字符串加引号，例如 `type: 'java.util.Map<java.lang.String,java.lang.String>'`。


## 日志配置

各自 profile 的 logging 包含 type（实际日志类）、factoryType（实际工厂）、factoryMethod（静态工厂方法）。
当前支持静态工厂接收 Class、返回日志类，以及无参 entry()/ret()。LoggingSupport 校验签名后，在模板渲染结果中添加日志字段、入口与每个显式出口调用，不额外制造 try-catch。
其他签名需要维护者适配工具；不伪造项目 API。未配置保留 REVIEW_REQUIRED。

## 升级与定制

新版本包内 JAR 的 skill、references、模板随 init 复制到 `.atomic-api/`；既有文件全部 KEEP。
升级时在另一个空目录 init，再逐文件比较、合并 skill/profile/模板定制；旧 atomic-api-dev/app-api-dev 不自动删除。
阅读模板的副本可定制，但生成器读取 JAR 内模板；要改变生成行为须修改本工具源码模板、验证并重打包，不能只改已安装副本。
