# 项目级 Framework Profile

profile 解决“这个项目采用哪些框架类型、源码放在哪里”，不登记 API。init 在项目的 `.atomic-api/profile.yaml` 创建配置；new/generate/check/verify 优先读取它，没有时兼容根目录旧的 atomic-api-profile.yaml，也可通过 --profile 指定。明确指定的文件不存在时失败，不静默改用自动识别。

```yaml
version: 1
targetJava: 8
types:
  componentManagement: com.example.framework.OpsComponentManagement
  facadeAnnotation: com.example.framework.OpsFacadeInterface
  handlerBase: com.example.framework.ApiHandler
  internalException: com.example.framework.OpsApiException
  externalException: com.example.framework.ServiceException
  injectionAnnotation: org.springframework.beans.factory.annotation.Autowired
  componentAnnotation: org.springframework.stereotype.Component
facadeDefault: throw
layout:
  - packagePrefix: com.example.common
    sourceRoot: ipran-app-api-common/src/main/java
  - packagePrefix: com.example.sdk
    sourceRoot: function-sdk/src/main/java
  - packagePrefix: com.example.app
    sourceRoot: app/src/main/java
  - packagePrefix: com.example.service
    sourceRoot: service/src/main/java
```

profile 的标识必须与实际源码/依赖解析身份一致。ComponentManagement 的 getFacadeImpl 签名、Handler 生命周期及异常构造器必须通过 AST/依赖校验，不能因为名字一致就套用模板。

facadeDefault 支持 throw（新增 default 显式抛内部异常）和 return-null（新增 default 返回 null）。项目须显式选择；不改变既有默认方法。targetJava 支持 8、11、17、21。project-existing 尚未实现，输入时明确拒绝。

自动发现可输出候选 profile；候选不唯一为 INCOMPLETE。不修改用户现有配置。layout 按最长匹配 packagePrefix 定位新文件，匹配并列或没有匹配时停止。existing 源码修改直接使用索引中的真实路径。

sourceRoot 必须是仓库内相对目录，apply 前校验真实路径与符号链接。目标 Java 级别与 CLI 的 JDK 17 运行环境分别管理。

物理目录与 package 不同时，在对应 layout 中增加可选 directoryPrefix。例如 packagePrefix=com.example、sourceRoot=src/main/java、directoryPrefix=physical/shared 会把 com.example.sdk.Request 放到 src/main/java/physical/shared/sdk/Request.java。现有类型始终使用索引中的真实路径。

新增 API 无需修改 profile，除非引入新的模块或框架；不得向 profile 添加 operation 列表。

## 禁用 Facade 注册（可选）

需要在项目现有的禁用实现配置中注册 Facade 时，在 profile 中显式声明：

```yaml
disabledFacade:
  configuration: com.example.sdk.FacadeInterfaceDisableImplConfiguration
  mapField: disableMap
```

`configuration` 是现有配置类的完整类名；`mapField` 是该类的注册 Map 字段。生成器在显式无参构造函数中追加当前 contract 的 `Facade.class -> new Facade() {}` 注册；已有等价注册只简化类型引用，不重复添加。自定义实现或重复注册报冲突，不覆盖。未配置时不查找或修改注册类。生成的 import 按包分组排序（Java 标准库放在最后，static import 单独分组）；包含注释的既有 import 区块保持原样，避免丢失注释。

## 新开发的角色配置（维护者配置一次）

在现有 profile 添加 roles，每个角色明确 packagePrefix 和 sourceRoot。目录映射必须与 layout 一致；不要登记操作清单。示例与 demo 配套：

```yaml
roles:
  facade: {packagePrefix: com.example.common.fault.facadeinterface, sourceRoot: ipran-app-api-common/src/main/java}
  request: {packagePrefix: com.example.common.fault.dto, sourceRoot: ipran-app-api-common/src/main/java}
  response: {packagePrefix: com.example.common.fault.dto, sourceRoot: ipran-app-api-common/src/main/java}
  implementation: {packagePrefix: com.example.app, sourceRoot: app/src/main/java}
  handler: {packagePrefix: com.example.app, sourceRoot: app/src/main/java}
  delegate: {packagePrefix: com.example.service, sourceRoot: service/src/main/java}
  requestConverter: {packagePrefix: com.example.service.converter, sourceRoot: service/src/main/java}
  responseConverter: {packagePrefix: com.example.service.converter, sourceRoot: service/src/main/java}
```

对应 layout 还需包含 com.example.common → ipran-app-api-common/src/main/java、com.example.app → app/src/main/java、com.example.service → service/src/main/java。

new 在角色 packagePrefix 下加 business 包，按 operation/domain 生成 ReqDto、RspDto、Facade、Delegate、Handler 和两个 Converter。sourceRoot 由角色固定；verify 核对实际文件路径。Builder 使用 implementation 角色。

旧 profile 没有 roles 仍可 generate/check v1；new/verify 不猜测角色，缺少配置就退出 2。初次无法识别配置时 init 仍创建明确标注的示例草稿，必须确认真实项目路径后使用。后续新增不改配置；不要由代理修改角色绕过失败。
