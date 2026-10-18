# 审查与构建清单

## 开始与实现

- 新增 API 必须运行 version 2 契约的 atomic-api verify；业务验收必须同时具备退出 0/STRUCTURE_READY 与实际项目编译、定向测试证据；可提前编译检查接线或补齐类型信息。check/v1 只用于历史接口，不替代验收。

- 保存任务约束和 git status；找到入口、公共契约、Facade 实现、Handler、原子接口/Latest/REST 和 Maven 接线。
- 比较修改前后的字段、默认值、null/空集合、顺序、错误文本、异常类型、JSON 与公开工具名。
- 核对 Converter 纯映射、Builder 业务汇总、Handler 实际生命周期职责和注册链。
- 确认 getErrMsg 与 cause 到达最终错误；既有 String 失败响应不改成异常。
- 使用真实版权头、项目日志 API、简短中文注释；只增加有业务含义的入口。

## Maven 失败定位

1. 取得失败任务实际命令与首个错误前后上下文，区分代码扫描任务、编译、测试和打包。不能用另一条流水线的命令推断失败任务。
2. 和修改前可构建提交对照，先检查本次新增/重命名类型及模块接线；没有证据不归因历史问题、缓存或扫描器错误。
3. 核对根版本、每个新增模块的 parent.version、dependencyManagement、classifier、实际 Reactor 和编译 classpath；无 Git 文本冲突不证明版本一致。
4. 包或类型缺失优先检查依赖来源。MapStruct 在输入类型已缺失时不生成实现，通常是后续结果，不先归因 processor。
5. 修复已证实的问题，先目标模块加 `-am`，再按影响验证 service 调用链。长任务续读日志，记录退出码，不重复构建。

本地父 POM 检查不计算完整 Maven effective model，属性、profile、外部父 POM及 classifier 仍需 Maven 证据；不要改 dependency 版本或清缓存碰运气。

## 验证与交付

- 先 git diff --check；读取本地构建说明和 IDE 配置，确认 mvn -v、JDK、settings、本地仓库。新 worktree 不假定带有旧机器配置。版本与命令来自目标项目，通用插件不固定 JDK 21 或私有仓库。
- 按任务授权做有意义的测试；JUnit 版本遵循业务仓库。从 Handler.run 验证生命周期，请求规范化及非法输入不调用底层；Delegate 分别验证 Facade 与响应适配抛错。
- MapStruct 检查实际生成类；validate 通过不是 compile 通过，compile 通过不是 test 通过，本地通过不是云端通过。报告跳过的项目。
- 用户要求先交付已确定修复时及时提交并推送，不以长验证阻塞；验证继续进行，单独报告结果。没有推送授权则不推送。
- 只交付本任务改动，不混入临时日志/凭据/私有业务源码；不为了全绿放宽断言、忽略失败或改变业务行为。
