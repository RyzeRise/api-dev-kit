# 首次接入与配置核对

仅在首次接入或框架变化时阅读；日常按 [App](app-api.md) / [Atomic](api-atomic.md) 操作。
执行 `sh .atomic-api/tool/atomic-api.sh init` 创建草稿；Windows 使用入口中对应命令。
两份 profile 独立，init 保留既有文件；配置必须由真实源码与依赖签名核对。

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

当前 App 生成模板按双泛型 Handler 基类和 BasicApiContext 生命周期编写，使用 preHandler、invokeAtomicApi、postHandler、run，以及内部异常 getErrMsg、管理器 getFacadeImpl、REST 响应 setter。同名不代表完整签名兼容；若实际框架不同，先适配工具，不能伪造项目框架。最终用真实编译证明可用。

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

logging 配置见下节；未配置仍需人工核对日志，不宣称通过。更多未知类型通过真实源码或 --classpath 的实际 JAR 解析，不改 profile 以掩盖源码违规。


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
