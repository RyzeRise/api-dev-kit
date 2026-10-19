# 实际生成模板

`atomic/` 与 `app/` 分别为原子能力和业务接口模板，共用 DTO/文件结构位于 `common/`。
它们通过 Maven resources 打包到 JAR 的 `/templates/`；`Templates.render` 实际读取并替换 `{{名称}}`。
生成器使用模板装配 Java，再通过现有 AST 与写入保护应用；没有单独的展示模板。

用法、替换项、模板与生成器对应关系见 `skills/api-dev/references/templates.md`（安装后 `.atomic-api/skills/api-dev/references/templates.md`）。
新模板需加入 manifest.txt；init 按清单安装供阅读的副本。生成器始终读取 JAR 内模板，副本不是动态覆盖入口。

测试模板无第三方框架依赖，每项抛 AssertionError 明确待实现，按业务项目真实测试框架迁移后补真实断言。
