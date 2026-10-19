package dev.atomic;

import com.github.javaparser.StaticJavaParser;
import java.nio.file.*;
import java.util.*;

/** Atomic has its own model: no Facade, Delegate or App Handler. */
record AtomicContract(
    int version,
    String layer,
    String operation,
    String business,
    String description,
    String apiInterface,
    String latest,
    Contract.Models models,
    List<Release> releases,
    List<String> latestAnnotations) {
  record Release(String id, String implementation, List<String> annotations) {}

  record Config(
      int version,
      int targetJava,
      List<Profile.Layout> layout,
      Map<String, Profile.Layout> roles,
      String latestKind,
      List<String> baseInterfaces,
      List<String> interfaceAnnotations,
      Profile.Logging logging) {
    static final Set<String> ROLES =
        Set.of("apiInterface", "request", "response", "implementation", "latest");

    static Config load(Path path) throws Exception {
      Config c = Contract.JSON.convertValue(Contract.yaml(path), Config.class);
      Contract.require(
          c.version == 1 && Set.of(8, 11, 17, 21).contains(c.targetJava),
          "Invalid atomic profile version/targetJava");
      Contract.require(
          c.layout != null
              && !c.layout.isEmpty()
              && c.roles != null
              && c.roles.keySet().equals(ROLES),
          "Atomic roles must define " + ROLES);
      Contract.require(
          Set.of("class", "interface").contains(c.latestKind == null ? "" : c.latestKind),
          "latestKind must follow actual framework: class/interface");
      for (var l : c.layout) {
        Contract.fqn(l.packagePrefix());
        Contract.root(l.sourceRoot());
        if (l.directoryPrefix() != null) Contract.root(l.directoryPrefix());
      }
      for (var role : c.roles.values()) {
        Contract.fqn(role.packagePrefix());
        Contract.root(role.sourceRoot());
        Contract.require(
            c.paths().rootFor(role.packagePrefix() + ".Example").equals(role.sourceRoot()),
            "Atomic role differs from layout");
      }
      Contract.require(
          c.baseInterfaces != null && c.interfaceAnnotations != null,
          "Explicit baseInterfaces/interfaceAnnotations required");
      c.baseInterfaces.forEach(Contract::javaType);
      c.interfaceAnnotations.forEach(AtomicContract::annotation);
      if (c.logging != null) {
        Contract.fqn(c.logging.type());
        Contract.fqn(c.logging.factoryType());
        Contract.id(c.logging.factoryMethod());
      }
      return c;
    }

    Profile paths() {
      return new Profile(1, targetJava, Map.of(), "throw", layout, null, null, logging);
    }

    String name(String role, String business, String simple) {
      return roles.get(role).packagePrefix() + "." + business + "." + simple;
    }
  }

  static void annotation(String text) {
    Contract.require(
        text != null && text.startsWith("@") && !text.contains("\n"),
        "Expected single annotation expression");
    var expr = StaticJavaParser.parseAnnotation(text);
    Contract.fqn(expr.getNameAsString());
  }

  static AtomicContract load(Path path, Config p) throws Exception {
    AtomicContract c = Contract.JSON.convertValue(Contract.yaml(path), AtomicContract.class);
    Contract.require(
        c.version == 1 && "atomic".equals(c.layer),
        "Atomic route requires layer: atomic, version: 1; App contracts cannot be used here");
    Contract.id(c.operation);
    Contract.id(c.business);
    Contract.require(
        c.description != null && !c.description.isBlank() && !c.description.equals("填写原子能力用途"),
        "Atomic description missing");
    Contract.require(
        c.operation.matches("(?:get|modify|create|delete)[A-Z].*"),
        "Atomic operation must use get/modify/create/deleteXxx; keep public App query names"
            + " unchanged");
    if (p.latestKind.equals("class"))
      Contract.require(
          p.roles.get("latest").sourceRoot().equals(p.roles.get("implementation").sourceRoot()),
          "Latest class must stay in implementation module, not common module");
    String cap = Contract.cap(c.operation);
    Contract.require(
        p.name("apiInterface", c.business, "AtomicApi" + cap + "Interface").equals(c.apiInterface),
        "Atomic interface name differs from roles");
    Contract.require(
        p.name("latest", c.business, "AtomicApi" + cap + "Latest").equals(c.latest),
        "Atomic Latest name differs from roles");
    Contract.require(c.models != null, "Atomic models missing");
    Contract.model(c.models.request());
    Contract.model(c.models.response());
    var names = new HashSet<String>();
    names.add(c.apiInterface);
    Contract.require(names.add(c.latest), "Atomic type collision");
    for (var pair :
        List.of(
            Map.entry("request", c.models.request()), Map.entry("response", c.models.response()))) {
      var m = pair.getValue();
      String suffix = pair.getKey().equals("request") ? "ReqDto" : "RspDto";
      Contract.require(
          m.className().equals(p.name(pair.getKey(), c.business, cap + suffix))
              && m.sourceRoot().equals(p.roles.get(pair.getKey()).sourceRoot()),
          "Atomic DTO differs from roles");
      Contract.require(names.add(m.className()), "Atomic type collision");
      for (var f : m.fields())
        Contract.require(
            !f.diagnostic()
                || !f.name()
                    .toLowerCase(Locale.ROOT)
                    .matches(".*(password|secret|token|credential|authorization).*"),
            "Sensitive diagnostic field");
    }
    Contract.require(
        c.releases != null && !c.releases.isEmpty(),
        "Provide real supported versions in chronological order");
    var ids = new HashSet<String>();
    for (var v : c.releases) {
      Contract.require(
          v.id != null && v.id.matches("R[0-9]{2}C[0-9]{2}[A-Za-z0-9_]*") && ids.add(v.id),
          "Invalid/duplicate atomic version");
      Contract.require(
          v.implementation.equals(p.name("implementation", c.business, "AtomicApi" + cap + v.id))
              && names.add(v.implementation),
          "Atomic version type differs from roles/collides");
      Contract.require(
          v.annotations != null && !v.annotations.isEmpty(),
          "Provide actual version registration annotations; do not invent a registry");
      v.annotations.forEach(AtomicContract::annotation);
    }
    Contract.require(c.latestAnnotations != null, "Explicit latestAnnotations required");
    c.latestAnnotations.forEach(AtomicContract::annotation);
    // Ordering and first support require release-history evidence, never lexical inference.
    return c;
  }
}
