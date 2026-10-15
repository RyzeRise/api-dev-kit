# CLI 使用约定

构建：`mvn -f cli/pom.xml package`。下文 atomic-api 表示 `java -jar cli/target/atomic-api-0.1.0.jar`。

```bash
atomic-api demo --repo <empty-directory>
atomic-api generate --repo <path> --contract <api.yaml> --profile <framework.yaml> --dry-run
atomic-api generate --repo <path> --contract <api.yaml> --profile <framework.yaml> --apply
atomic-api check --repo <path>
atomic-api check --repo <path> --contract <api.yaml> --format json
```

generate 默认 dry-run；`--dry-run` 与 `--apply` 互斥。不支持隐式覆盖已有业务实现，不提供 force 覆盖开关。

profile 是项目级框架适配，不是 API 登记表。自动识别唯一时可省略；不唯一时报告候选并退出。

允许多次 `--source-root` 与 `--classpath`，不要求目标业务项目能先通过 Maven 编译。

## 输出

generate 输出 CREATE / UPDATE / NOOP / CONFLICT、逐文件 diff、阻塞项、待实现业务位置及运行前摘要。apply 后再次解析并验证输出。任何阻塞在写入前发现时，所有目标文件保持原样。

check 输出规则编号、文件、行号、发现依据和严重级别。机器报告含 schemaVersion、toolVersion、scope 与 report；report 含 discoveredMethods、checkedMethods、findings、manualChecks、readiness。

0.1.0 的 coverage 为 discoveredMethods/checkedMethods；未完成项保留 INCOMPLETE finding。readiness 仅输出 SCAFFOLD / UNKNOWN，不自动证明业务 COMPLETE。完整方法级覆盖、列号及 SARIF 作为后续增强。

## 退出码

| 退出码 | 含义 |
|---|---|
| 0 | 当前覆盖范围内无 ERROR、无 INCOMPLETE；仍需读取 WARN 和 readiness |
| 1 | 发现明确违规、contract drift 或生成冲突 |
| 2 | 参数/解析/环境错误、部分检查未完成、没有适用对象 |

INCOMPLETE 优先于 ERROR 决定退出码，完整 finding 列表仍保留。可选 `--warnings-as-errors`；不隐藏 WARN 来获得通过。

## 环境兼容

CLI 运行环境建议 JDK 17；目标生成代码遵守目标 source level（含 Java 8）。支持 Windows、Linux 与 macOS 路径。不依赖 shell 展开、平台专用命令或目录名等于 package 的假设。

第一版不提供 HTTP 服务、不引入远程执行、不自动上传源码、不自动创建提交。

## 当前自动检查范围

Facade 从 getFacadeImpl 类字面量和实现注解发现；Symbol Solver 解析接收者、重载、字段与继承。实现索引按源码真实 package 建立，支持物理目录不匹配。

提供 profile 时，框架异常、Handler 基类和 Facade 注解按解析后的完整类型名匹配；未提供 profile 时仅使用命名约定，实际项目建议配置 profile。延迟执行的 lambda 或匿名类内调用不计为当前 Facade 方法的直接 Handler 编排；响应 null 分支必须直接抛出内部异常，不能只在延迟代码中声明抛出。

自动检查请求 DTO 约定、异常声明、调用处 try/catch、getErrMsg 进入直接抛出的外部异常、响应转换的首个 null 失败检查、组合 OR 校验风险。可选 contract 检查 DTO 字段类型、Handler 数量/顺序及预期类型存在。

Converter 字段语义、Handler 生命周期职责、底层业务、注释含义与完整响应映射仍需人工审查；存在辅助变量、复杂分支、动态调用等无法证明的关系，应读取 INCOMPLETE/manualChecks，不能只看退出码。

generate 只支持顺序链路和受限字段绑定；不覆盖既有实现。既有 Converter 需要接口形态，映射使用可执行 default 方法；不自动重写 MapStruct 已有方法。

多个 API 可共享 Facade、实现类和 Delegate。注入字段按准确类型复用，名字被其他类型占用时分配新名字。已有实现类的其他接口保留，并补入缺失的目标 Facade。已有 DTO 使用解析出的 getX/isX/x 访问器；无法证明字段、访问器或 setter 类型一致时停止生成。
