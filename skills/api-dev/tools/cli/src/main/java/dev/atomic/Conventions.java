package dev.atomic;

import java.util.*;

/** Project-owned role locations; models fill business facts, not architecture. */
final class Conventions {
  static final Set<String> ROLES =
      Set.of(
          "facade",
          "implementation",
          "delegate",
          "request",
          "response",
          "handler",
          "requestConverter",
          "responseConverter");

  static void requireRoles(Profile profile) {
    Contract.require(
        profile != null && profile.roles() != null,
        "STRICT-001: 配置 .atomic-api/profile.yaml 的 roles 后再运行；由项目维护者确定各层包和模块，不要猜目录。参考"
            + " README.md 的项目配置部分");
  }

  static String name(Profile p, String role, String business, String simple) {
    return p.roles().get(role).packagePrefix() + "." + business + "." + simple;
  }

  static Map<String, String> targets(Contract c) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("facade", c.target().facade());
    result.put("implementation", c.target().implementation());
    result.put("delegate", c.target().delegate());
    result.put("request", c.models().request().className());
    result.put("response", c.models().response().className());
    result.put("requestConverter", c.target().converter());
    result.put("responseConverter", c.target().responseConverter());
    return result;
  }

  static Set<String> scope(Contract c) {
    Set<String> types = new HashSet<>(targets(c).values());
    c.steps().forEach(s -> types.add(s.handler()));
    if (c.assembly().builder() != null) types.add(c.assembly().builder());
    types.remove(null);
    return types;
  }

  static void location(Profile p, String role, String business, String type, String sourceRoot) {
    var rule = p.roles().get(role);
    String expectedPackage = rule.packagePrefix() + "." + business;
    Contract.require(
        Contract.packageName(type).equals(expectedPackage),
        "STRICT-001: " + role + " must be in " + expectedPackage + "; actual " + type);
    Contract.require(
        rule.sourceRoot().equals(sourceRoot),
        "STRICT-001: " + role + " must use " + rule.sourceRoot() + "; actual " + sourceRoot);
  }

  static void validate(Contract c, Profile p) {
    requireRoles(p);
    targets(c)
        .forEach(
            (role, type) ->
                location(
                    p,
                    role,
                    c.business(),
                    type,
                    role.equals("request")
                        ? c.models().request().sourceRoot()
                        : role.equals("response")
                            ? c.models().response().sourceRoot()
                            : p.rootFor(type)));
    for (var step : c.steps())
      location(p, "handler", c.business(), step.handler(), step.sourceRoot());
    if (c.assembly().builder() != null)
      location(
          p, "implementation", c.business(), c.assembly().builder(), c.assembly().sourceRoot());
  }
}
