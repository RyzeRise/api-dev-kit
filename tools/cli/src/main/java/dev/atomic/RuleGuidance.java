package dev.atomic;

/** Stable, executable-error vocabulary shared by text and JSON reports. */
final class RuleGuidance {
  static String fix(String rule) {
    if (rule.matches("N[0-9]{2}"))
      return RuleCatalog.all().stream()
          .filter(r -> r.id().equals(rule))
          .map(r -> r.requirement() + " 检查边界：" + r.limitation())
          .findFirst()
          .orElse("核对规范编号。");
    return switch (rule) {
      case "STRICT-001" -> "按 profile.roles 恢复目标包/sourceRoot；使用 atomic-api new 生成 v2 契约，不修改规则迁就代码。";
      case "STRICT-002" ->
          "Delegate 调用 RequestConverter.INSTANCE 创建请求、ResponseConverter.INSTANCE 适配响应；将字段组装移出"
              + " Delegate。";
      case "STRICT-003" -> "Converter 只保留 DTO 映射和必要校验；把管理器、网络和业务调用移回 Handler。";
      case "STRICT-004" -> "Facade 仅按 contract.steps 调用 Handler.run；单步直接返回，多步交由业务 Builder 汇总。";
      case "STRICT-005" -> "preHandler 构造原子请求；invokeAtomicApi 仅获取能力并调用；postHandler 校验并转换结果。";
      case "STRICT-006" -> "公共 DTO 删除对 service/Handler/实现层的依赖；跨层字段通过业务 Converter 转换。";
      case "STRICT-007" ->
          "实现报告位置的真实业务逻辑，保留失败校验；缺少真实能力资料时保留骨架并报告未验收，不得删除占位文本、返回 null/空 DTO 或更改 contract 伪装完成。";
      case "STRICT-008" -> "保留原 caught 异常为 cause：使用支持 cause 的构造器，或对实际抛出的包装异常调用 initCause(caught)。";
      case "MAVEN-001" -> "核对该模块 parent.version 与本地父 POM、Reactor 和 classpath；只对齐已证实错配，不清缓存碰运气。";
      case "API-007" -> "Facade 和响应适配置于原有 try；对外保留 caught.getErrMsg() 及 cause；String 失败契约需人工核对。";
      case "API-009" -> "使用 ResponseConverter 适配业务响应；转换前检查 null，保留空集合、消息与顺序语义。";
      case "API-004" -> "Facade 使用框架 Handler.run，不直接调底层，也不使用无关对象的 run 方法。";
      case "API-002", "API-006", "CONTRACT" ->
          "对照本操作 contract 恢复请求/响应字段、方法签名、Handler 次序与框架异常；不要降低契约要求。";
      case "API-008" -> "人工区分独立失败原因和同一原因的短路检查；不要机械拆分所有 OR。";
      case "PARSE", "RESOLUTION", "DISCOVERY", "MAVEN-PARSE" ->
          "补齐实际源码、profile 或 --classpath 后重查；未解析对象不能算通过。";
      default -> "按本条发现的位置核对实际调用链；修复源码后重跑同一命令，不隐藏告警。";
    };
  }
}
