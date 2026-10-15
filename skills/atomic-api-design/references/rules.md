# 开发规则与检查证据

| 编号 | 规则 | 检查证据与限制 |
|---|---|---|
| API-001 | Delegate 保持外部契约，转换 SDK 请求 DTO | 跟踪 Converter 与实际参数；字段语义人工审查 |
| API-002 | Facade 接收一个请求 DTO | 解析类型身份，ReqDto 命名仅为约定 |
| API-003 | 组件管理获取 Facade，不绕过调用链 | 解析 getFacadeImpl、接收者和调用关系 |
| API-004 | Facade 编排 Handler，必要时 Builder 汇总 | 解析实际类型与顺序，不以方法名 run 作为充分证据 |
| API-005 | Handler pre/invoke/post 分工 | 检查覆写，业务职责仍需人工判断 |
| API-006 | 内部使用 OpsApiException | 核对类型、声明、throw，保留 cause 和已有 addEntry |
| API-007 | Delegate 转 ServiceException 并保留错误 | 追踪 getErrMsg 实际进入异常、操作标识及 Locale.ROOT |
| API-008 | 不同失败原因分别校验 | OR+throw 仅 WARN，不能机械拆分 |
| API-009 | SDK 与外部响应分开，null 结果失败 | 跟踪结果到 builder，核对空值分支与字段来源 |
| API-010 | 英文步骤注释匹配实际编排 | 核对编号与步骤数量，描述语义人工确认 |
| API-011 | 签名变更同步调用链 | 完整签名关联实现、调用和测试，缺失依赖为 INCOMPLETE |

## 校验示例

以下组合校验不能指出具体失败项：

```java
if (isBlank(value.getName()) || isBlank(value.getAddress())) {
    throw new OpsApiException("Name or address is empty");
}
```

先检查 value，再分别校验 name/address，并给出不同错误信息。

以下过滤不抛模糊异常，可以使用 OR：

```java
if (item == null || item.getId() == null) {
    continue;
}
```

## 层次边界

Delegate：保持对外参数，转换请求 DTO，获取 Facade，调用并组装外部响应，转换异常。

Facade：请求 DTO + OpsApiException；按顺序编排；多结果需要汇总才用 Builder。

Handler：preHandler 校验与上下文；invokeAtomicApi 底层调用；postHandler 结果校验与输出。内部输入不强制 DTO 化。

Builder：汇总输出，不发起底层查询。异常定位沿用既有 addEntry 策略。

default 返回行为不是已统一范式，必须由项目明确，不自动改变现有默认方法。
