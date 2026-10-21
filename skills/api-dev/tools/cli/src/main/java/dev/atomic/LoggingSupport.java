package dev.atomic;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;

/** Add intent comments while leaving the project's logging and unified coverage untouched. */
final class LoggingSupport {
  static boolean owned(Node n, MethodDeclaration m) {
    for (Node p = n; p != null; p = p.getParentNode().orElse(null)) {
      if (p == m) return true;
      if (p instanceof LambdaExpr
          || p instanceof ClassOrInterfaceDeclaration
          || p instanceof MethodDeclaration) return false;
    }
    return false;
  }

  static String documentation(MethodDeclaration method) {
    return switch (method.getNameAsString()) {
      case "preHandler" -> "先校验输入，再组装请求上下文。";
      case "invokeAtomicApi" -> "获取并调用真实底层能力；未实现时明确失败。";
      case "postHandler" -> "先校验底层结果，再转换业务响应。";
      default -> "按业务契约处理请求或转换结果。";
    };
  }

  static void decorate(MethodDeclaration method) {
    if (method.getBody().isEmpty()) return;
    if (method.getComment().isEmpty() && !method.getBody().orElseThrow().getStatements().isEmpty())
      method.getBody().orElseThrow().getStatement(0).setLineComment(documentation(method));
  }
}
