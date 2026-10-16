package dev.atomic;

import java.nio.file.Path;
import java.util.*;

public record Profile(
    int version,
    int targetJava,
    Map<String, String> types,
    String facadeDefault,
    List<Layout> layout,
    DisabledFacade disabledFacade) {
  public record DisabledFacade(String configuration, String mapField) {}

  public Profile(int version, int targetJava, Map<String,String> types,
      String facadeDefault, List<Layout> layout) {
    this(version, targetJava, types, facadeDefault, layout, null);
  }
  public record Layout(String packagePrefix, String sourceRoot, String directoryPrefix) {}

  static final List<String> KEYS =
      List.of(
          "componentManagement",
          "facadeAnnotation",
          "handlerBase",
          "internalException",
          "externalException",
          "injectionAnnotation",
          "componentAnnotation");

  public static Profile load(Path path) throws Exception {
    Profile p = Contract.JSON.convertValue(Contract.yaml(path), Profile.class);
    p.validate();
    return p;
  }

  void validate() {
    Contract.require(
        version == 1 && Set.of(8, 11, 17, 21).contains(targetJava),
        "Unsupported profile version/targetJava");
    Contract.require(
        types != null && types.keySet().equals(new HashSet<>(KEYS)),
        "Profile types missing or unknown");
    types.values().forEach(Contract::fqn);
    Contract.require(
        Set.of("throw", "return-null").contains(facadeDefault == null ? "" : facadeDefault),
        "Unsupported facadeDefault: use throw or return-null");
    if (disabledFacade != null) {
      Contract.fqn(disabledFacade.configuration());
      Contract.id(disabledFacade.mapField());
    }
    Contract.require(layout != null && !layout.isEmpty(), "Profile layout missing");
    for (Layout l : layout) {
      Contract.fqn(l.packagePrefix);
      Contract.root(l.sourceRoot);
      if (l.directoryPrefix != null) Contract.root(l.directoryPrefix);
    }
  }

  public String type(String key) {
    return types.get(key);
  }

  public String rootFor(String type) {
    var matches =
        layout.stream()
            .filter(l -> type.startsWith(l.packagePrefix + "."))
            .sorted(Comparator.comparingInt((Layout l) -> l.packagePrefix.length()).reversed())
            .toList();
    Contract.require(!matches.isEmpty(), "No layout mapping for " + type);
    Contract.require(
        matches.size() == 1
            || matches.get(0).packagePrefix.length() != matches.get(1).packagePrefix.length(),
        "Ambiguous layout for " + type);
    return matches.get(0).sourceRoot;
  }

  public String relativeClassPath(String type, String sourceRoot) {
    var matches =
        layout.stream()
            .filter(l -> l.sourceRoot.equals(sourceRoot) && type.startsWith(l.packagePrefix + "."))
            .sorted(Comparator.comparingInt((Layout l) -> l.packagePrefix.length()).reversed())
            .toList();
    Contract.require(
        matches.size() < 2
            || matches.get(0).packagePrefix.length() != matches.get(1).packagePrefix.length(),
        "Ambiguous physical package mapping");
    if (!matches.isEmpty() && matches.get(0).directoryPrefix != null) {
      var l = matches.get(0);
      return l.directoryPrefix
          + "/"
          + type.substring(l.packagePrefix.length() + 1).replace('.', '/')
          + ".java";
    }
    return type.replace('.', '/') + ".java";
  }

  public static Profile discover(SourceIndex index) {
    Map<String, String> types = new LinkedHashMap<>();
    var names =
        Map.of(
            "componentManagement",
            "OpsComponentManagement",
            "facadeAnnotation",
            "OpsFacadeInterface",
            "handlerBase",
            "ApiHandler",
            "internalException",
            "OpsApiException",
            "externalException",
            "ServiceException",
            "injectionAnnotation",
            "Autowired",
            "componentAnnotation",
            "Component");
    // Annotation declarations are separate AST nodes from classes/interfaces.
    for (String key : KEYS) {
      String simple = names.get(key);
      var found =
          index.units().stream()
              .flatMap(c -> c.getTypes().stream())
              .filter(t -> t.getNameAsString().equals(simple))
              .map(t -> t.getFullyQualifiedName().orElseThrow())
              .distinct()
              .toList();
      Contract.require(
          found.size() == 1, "INCOMPLETE: specify --profile; cannot uniquely discover " + simple);
      types.put(key, found.get(0));
    }
    var roots =
        index.originals().keySet().stream()
            .map(p -> index.root().relativize(p).toString().replace('\\', '/'))
            .filter(s -> s.contains("src/main/java/"))
            .map(s -> s.substring(0, s.indexOf("src/main/java/") + 13))
            .distinct()
            .toList();
    Contract.require(
        roots.size() == 1, "INCOMPLETE: multiple source roots; specify profile layout");
    String prefix =
        index.types().keySet().stream()
            .map(s -> s.substring(0, s.lastIndexOf('.')))
            .reduce(
                (a, b) -> {
                  while (!b.equals(a) && !b.startsWith(a + ".")) {
                    int dot = a.lastIndexOf('.');
                    if (dot < 0) return "";
                    a = a.substring(0, dot);
                  }
                  return a;
                })
            .orElse("");
    Contract.require(prefix.contains("."), "INCOMPLETE: specify profile package layout");
    return new Profile(1, 8, types, "throw", List.of(new Layout(prefix, roots.get(0), null)));
  }
}
