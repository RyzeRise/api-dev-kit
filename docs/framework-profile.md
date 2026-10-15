# 项目级 Framework Profile

profile 解决“这个项目采用哪些框架类型、源码放在哪里”，不登记 API。generate 自动读取目标根的 atomic-api-profile.yaml，或通过 --profile 指定；明确指定的文件不存在时失败，不静默改用自动识别。

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
  - packagePrefix: com.example.sdk
    sourceRoot: function-sdk/src/main/java
  - packagePrefix: com.example.app
    sourceRoot: app/src/main/java
  - packagePrefix: com.example.service
    sourceRoot: service/src/main/java
```

profile 的标识必须与实际源码/依赖解析身份一致。ComponentManagement 的 getFacadeImpl 签名、Handler 生命周期及异常构造器必须通过 AST/依赖校验，不能因为名字一致就套用模板。

0.1.0 仅支持 facadeDefault: throw（新增 default 显式抛内部异常），不改变既有默认方法。project-existing 尚未实现，输入时明确拒绝。

自动发现可输出候选 profile；候选不唯一为 INCOMPLETE。不修改用户现有配置。layout 按最长匹配 packagePrefix 定位新文件，匹配并列或没有匹配时停止。existing 源码修改直接使用索引中的真实路径。

sourceRoot 必须是仓库内相对目录，apply 前校验真实路径与符号链接。目标 Java 级别与 CLI 的 JDK 17 运行环境分别管理。

物理目录与 package 不同时，在对应 layout 中增加可选 directoryPrefix。例如 packagePrefix=com.example、sourceRoot=src/main/java、directoryPrefix=physical/shared 会把 com.example.sdk.Request 放到 src/main/java/physical/shared/sdk/Request.java。现有类型始终使用索引中的真实路径。

新增 API 无需修改 profile，除非引入新的模块或框架；不得向 profile 添加 operation 列表。
