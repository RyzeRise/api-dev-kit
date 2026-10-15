# 交付前审查

- [ ] 只修改授权范围，保留用户修改，不编辑生成模型。
- [ ] 外部签名、DTO 转换、null 输入行为正确。
- [ ] 组件管理获取 Facade，未绕过至 Handler 或底层服务。
- [ ] 请求 DTO、Handler 顺序与输入依赖正确。
- [ ] 单 Handler 不增加无意义 Builder，多 Handler 汇总不漏输入。
- [ ] pre/invoke/post 分工合理，对象检查先于字段访问。
- [ ] 异常区分失败原因，合法 OR 没有被机械拆分。
- [ ] cause、addEntry、getErrMsg 传播未丢失。
- [ ] getErrMsg 实际进入 ServiceException，而非仅出现于 catch。
- [ ] null SDK 结果明确失败，响应字段来源与类型正确。
- [ ] 英文编排注释、编号及汇总数量与实际一致。
- [ ] 签名、实现、调用、Javadoc、import、Mock/Captor 同步。
- [ ] 未恢复用户删除的业务测试，未调用真实底层能力。
- [ ] contract drift 已说明，未据设计覆盖业务实现。
- [ ] 自动发现不依赖 API 清单，未知关系输出 INCOMPLETE。
- [ ] 骨架显示 SCAFFOLD，未宣称业务完成。
- [ ] dry-run 无写入，冲突前置，重复运行无重复结构。
- [ ] AST、编译、测试、diff 检查分别报告。
