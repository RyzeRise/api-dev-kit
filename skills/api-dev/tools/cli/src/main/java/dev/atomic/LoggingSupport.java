package dev.atomic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.util.*;

/** Generate explicit entry/exit calls without synthesizing try/catch. */
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

  static boolean logStatement(Statement s, String name) {
    if (!s.isExpressionStmt() || !s.asExpressionStmt().getExpression().isMethodCallExpr())
      return false;
    var call = s.asExpressionStmt().getExpression().asMethodCallExpr();
    return call.getArguments().isEmpty()
        && call.getNameAsString().equals(name)
        && call.getScope().map(Object::toString).orElse("").equals("LOGGER");
  }

  static void validate(SourceIndex index, Profile.Logging log) {
    if (log == null) return;
    var logger = index.declaration(log.type());
    for (String name : List.of("entry", "ret"))
      Contract.require(
          logger.getAllMethods().stream()
              .anyMatch(m -> m.getName().equals(name) && m.getNoParams() == 0),
          "N13: configured logger must expose " + name + "()");
    Contract.require(
        index.declaration(log.factoryType()).getAllMethods().stream()
            .anyMatch(
                m ->
                    m.getName().equals(log.factoryMethod())
                        && m.getDeclaration().isStatic()
                        && m.getNoParams() == 1
                        && (m.getParamType(0).describe().equals("java.lang.Class")
                            || m.getParamType(0).describe().startsWith("java.lang.Class<"))
                        && m.returnType().describe().equals(log.type())),
        "N13: logger factory must be static, take Class, and return configured logger");
  }

  static String documentation(MethodDeclaration method) {
    return switch (method.getNameAsString()) {
      case "preHandler" -> "先校验输入，再组装请求上下文。";
      case "invokeAtomicApi" -> "获取并调用真实底层能力；未实现时明确失败。";
      case "postHandler" -> "先校验底层结果，再转换业务响应。";
      default -> "按业务契约处理请求或转换结果。";
    };
  }

  static void decorate(
      ClassOrInterfaceDeclaration owner, MethodDeclaration method, Profile profile) {
    if (method.getBody().isEmpty()) return;
    if (method.getComment().isEmpty() && !method.getBody().orElseThrow().getStatements().isEmpty())
      method.getBody().orElseThrow().getStatement(0).setLineComment(documentation(method));
    var log = profile.logging();
    if (log == null) return;
    if (owner.getFieldByName("LOGGER").isEmpty()) {
      String visibility = owner.isInterface() ? "" : "private ";
      owner.addMember(
          StaticJavaParser.parseBodyDeclaration(
              visibility
                  + "static final "
                  + log.type()
                  + " LOGGER="
                  + log.factoryType()
                  + "."
                  + log.factoryMethod()
                  + "("
                  + owner.getNameAsString()
                  + ".class);"));
    } else {
      String spelling =
          owner.getFieldByName("LOGGER").orElseThrow().getVariable(0).getType().asString();
      Contract.require(
          spelling.equals(log.type()) || spelling.equals(Contract.simple(log.type())),
          "N13: existing LOGGER has a different type");
    }
    var body = method.getBody().orElseThrow();
    for (var exit : List.copyOf(method.findAll(Statement.class)))
      if ((exit.isReturnStmt() || exit.isThrowStmt()) && owned(exit, method)) {
        var parent = exit.getParentNode().orElseThrow();
        if (parent instanceof BlockStmt block) {
          int pos = block.getStatements().indexOf(exit);
          block.addStatement(pos, StaticJavaParser.parseStatement("LOGGER.ret();"));
        } else
          exit.replace(
              new BlockStmt(
                  new NodeList<>(StaticJavaParser.parseStatement("LOGGER.ret();"), exit.clone())));
      }
    body.addStatement(0, StaticJavaParser.parseStatement("LOGGER.entry();"));
    if (method.getType().isVoidType())
      body.addStatement(StaticJavaParser.parseStatement("LOGGER.ret();"));
  }
}
