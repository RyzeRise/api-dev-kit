# 总体设计

当前实现为 0.4.0。新增流程通过 new 与 verify 降低模型自由度，CLI 负责可证明的结构、受限生成和安全写入；skill 负责分层决策、兼容性、人工审查与验证流程。实际范围以 [CLI 使用约定](cli.md) 和报告为准。

## 组件

| 组件 | 职责 |
|---|---|
| Contract / Schema | 单操作设计，安全 YAML、类型和绑定校验；不维护 API 总表。 |
| Profile | 框架类型身份、targetJava、源码与包布局、既有 default 和禁用态注册。 |
| SourceIndex | JavaParser AST + Symbol Solver，按真实 package 索引，不假设物理目录匹配。 |
| Generator / GenerationPlan | 新文件 AST、已有类定点插入、全量计划、摘要校验和安全应用。 |
| Checker | 发现 Facade、有限调用关系与异常边界，对照可选 contract，报告覆盖和未完成项。 |
| MavenParents | 只读本地父坐标检查；不执行项目插件、不下载依赖、不冒充 Maven effective model。 |
| Initializer / Demo | 安装项目级配置、两份完整 skill 和虚构示例，保留已有文件。 |

## 调用链与边界

service Delegate 通过 RequestConverter 转业务请求、组件管理查找 Facade；Facade 编排有意义的 Handler；Handler 调用原子能力并用业务 Converter 或必要的 Builder 形成业务响应；service ResponseConverter 适配外部模型或既有 String 契约。

公共业务契约放业务公共模块，只有 SDK 专属类型放 SDK。新生成设计指定 responseConverter 后请求与响应分开；旧 contract 省略字段时保留私有映射以保证兼容。当前只自动生成对象响应的 default 映射，不假装提供 MapStruct 或真实原子业务实现。

Facade 单步直接返回，多来源计算或匹配才用 Builder。Handler 三阶段真实职责、节点内部类、支持版本、JSON 由领域 skill 引导人工核对；String 的成功消息映射和 exception/message 错误形式由显式契约生成，不靠命名扫描自动推断。

## 发现、生成与检查

Facade 从 getFacadeImpl 类字面量和实际注解/继承关系发现。解析完整类型与方法签名，动态目标和复杂数据流无法证明时报告 INCOMPLETE。不依赖生成标记判定结构；标记只用于说明 SCAFFOLD。

profile 只登记框架身份与布局。sourceRoot 与 package 分离；旧类定位真实路径，新类需要明确布局。默认扫描 main Java 源码，允许显式 source-root/classpath，排除构建和 `.atomic-api/` 临时目录。

新 DTO 生成显式访问方法，避免强加 Lombok。生成 Handler 和业务 Builder 在未实现处抛明确占位异常，不返回空对象伪装完成。既有外部 REST 类型不生成、不修改；签名、setter、getter 不能可靠解析时停止。

Maven 父坐标检查覆盖本地相同 GA 的版本错配，忽略有意使用的外部父模型。不能静态展开的属性只提示复核，仍需检查真实 Reactor、classifier 和 classpath。不能把后续 MapStruct 失败当作独立首因。

## 安全写入

默认 dry-run，先验证全部 AST、类型、输出与冲突再应用。相同生成结构 NOOP，已有业务实现 CONFLICT；不提供 force 覆盖。计划携带原始内容与摘要，apply 前核对依赖、目标与父路径，拒绝越界和符号链接。

LexicalPreservingPrinter 保留无关代码，支持 CRLF/BOM 与已有文件权限。多文件写入不是文件系统事务；失败后只在文件仍匹配本次内容时尽力恢复，不覆盖并发用户修改。不自动 stash/reset、递归清理、提交或部署。

## 可维护的规范

通用 skill 提供工作流、分层规则和工具边界；故障业务 skill 提供公共契约归属、转换职责、版本/JSON/错误兼容性、Maven 排查和 MR 边界。详细资料按需读取，避免在主 skill 重复长清单或保留互相冲突的旧建议。

两份 skill 的全部资源通过 manifest 打包并回归验证；init 保留本地定制，不自动覆盖升级。自动测试只用虚构 com.example 类型，不依赖私有父 POM或上传业务源码。插件运行需 JDK 17，业务目标版本由 profile 和业务项目决定。

## 0.4.0 的默认路径

项目维护者一次配置 roles，模型只提供 operation/business 和五类业务事实；new 生成 v2 草稿，不猜字段。generate 在写前校验角色约束，verify 结合 StrictChecks 验证实际架构，输出逐项 fix 和 BLOCKED/STRUCTURE_READY。

v1/check 只用于兼容存量，新验收不允许降级。检查范围按 v2 操作限定；预期的未实现业务会阻塞，旧无关占位不混入本次 readiness。代码规则、破坏性变体测试和最终业务编译测试分别提供证据，不把 AST 通过当成完整行为证明。
