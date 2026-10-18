package dev.atomic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.SourceVersion;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.AliasEvent;

public record Contract(
    int version,
    String operation,
    String description,
    Target target,
    Models models,
    String externalResponse,
    List<Step> steps,
    Assembly assembly,
    Map<String, String> responseBindings,
    String business,
    String errorMode) {
  static final ObjectMapper JSON = new ObjectMapper();
  static final Set<String> PRIMITIVES =
      Set.of("int", "long", "double", "float", "short", "byte", "boolean", "char");

  public record Target(
      String facade,
      String implementation,
      String delegate,
      String converter,
      String responseConverter) {
    public Target(String facade, String implementation, String delegate, String converter) {
      this(facade, implementation, delegate, converter, null);
    }
  }

  public record Field(
      String name, String type, String description, boolean required, boolean diagnostic) {}

  public record Model(
      @JsonProperty("class") String className, String sourceRoot, List<Field> fields) {}

  public record Models(Model request, Model response) {}

  public record Step(
      String id,
      String handler,
      String sourceRoot,
      String input,
      String inputType,
      String outputType) {}

  public record Assembly(String mode, String builder, String sourceRoot) {}

  boolean stringResponse() {
    return "java.lang.String".equals(externalResponse);
  }

  boolean returnsFailureMessage() {
    return stringResponse() && "message".equals(errorMode);
  }

  static String packageName(String fqn) {
    return fqn.substring(0, fqn.lastIndexOf('.'));
  }

  public static Object yaml(Path path) throws Exception {
    require(Files.size(path) <= 100_000, "YAML exceeds 100 KB");
    String text = Files.readString(path).replaceFirst("^\uFEFF", "");
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(0);
    options.setCodePointLimit(100_000);
    options.setNestingDepthLimit(30);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    for (var event : yaml.parse(new StringReader(text)))
      require(!(event instanceof AliasEvent), "YAML aliases forbidden");
    return yaml.load(text);
  }

  public static Contract load(Path path) throws Exception {
    try {
      Object data = yaml(path);
      try (var schema = Contract.class.getResourceAsStream("/schemas/api-contract.schema.json")) {
        require(schema != null, "Contract schema not packaged");
        var validator =
            com.networknt.schema.JsonSchemaFactory.getInstance(
                    com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(schema);
        var errors = validator.validate(JSON.valueToTree(data));
        require(errors.isEmpty(), "Contract schema violation: " + errors);
      }
      Contract c = JSON.convertValue(data, Contract.class);
      c.validate();
      return c;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid contract: " + e.getMessage(), e);
    }
  }

  public void validate() {
    require(version == 1 || version == 2, "version must be 1 or 2");
    if (version == 2) {
      id(business);
      require(
          target != null && target.responseConverter() != null,
          "STRICT-001: version 2 requires target.responseConverter; use atomic-api new");
      require(
          models != null
              && models.request().className().endsWith("ReqDto")
              && models.response().className().endsWith("RspDto"),
          "STRICT-001: version 2 models must end with ReqDto/RspDto");
      require(
          packageName(target.converter()).equals(packageName(target.responseConverter())),
          "STRICT-001: request/response converters must share a package");
    }
    id(operation);
    require(description != null && !description.isBlank(), "description missing");
    require(
        target != null && models != null && assembly != null && steps != null && !steps.isEmpty(),
        "Required contract section missing");
    List<String> targets =
        new ArrayList<>(
            Arrays.asList(
                target.facade,
                target.implementation,
                target.delegate,
                target.converter,
                externalResponse));
    if (target.responseConverter != null) targets.add(target.responseConverter);
    targets.forEach(Contract::fqn);
    require(new HashSet<>(targets).size() == targets.size(), "Target types must differ");
    model(models.request);
    model(models.response);
    require(
        !models.request.className.equals(models.response.className),
        "Request/response must differ");
    require(
        errorMode == null || Set.of("exception", "message").contains(errorMode),
        "errorMode must be exception or message");
    if (stringResponse()) {
      require(
          version == 2 && errorMode != null,
          "String response requires v2 with explicit errorMode: exception or message");
      require(
          responseBindings.size() == 1 && responseBindings.containsKey("message"),
          "String responseBindings must contain only message: <business string field>");
      String source = responseBindings.get("message");
      require(
          models.response.fields.stream()
              .anyMatch(f -> f.name.equals(source) && f.type.equals("java.lang.String")),
          "String message binding must refer to a java.lang.String response field");
    } else
      require(
          !"message".equals(errorMode), "message errorMode requires a String external response");
    Set<String> generated = new HashSet<>();
    generated.add(models.request.className);
    generated.add(models.response.className);
    Map<String, String> fields = new HashMap<>();
    models.request.fields.forEach(f -> fields.put(f.name, f.type));
    Map<String, String> prior = new HashMap<>();
    for (Step step : steps) {
      id(step.id);
      require(!prior.containsKey(step.id), "Duplicate step: " + step.id);
      fqn(step.handler);
      root(step.sourceRoot);
      javaType(step.inputType);
      javaType(step.outputType);
      require(
          !PRIMITIVES.contains(step.inputType) && !PRIMITIVES.contains(step.outputType),
          "Handler generic requires reference type");
      require(generated.add(step.handler), "Generated type collision");
      require(
          step.input != null
              && step.input.matches(
                  "request(?:\\.[A-Za-z_][A-Za-z0-9_]*)?|steps\\.[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?"),
          "Invalid input binding");
      String[] p = step.input.split("\\.");
      String expected = null;
      if (p[0].equals("request")) {
        expected = p.length == 1 ? models.request.className : fields.get(p[1]);
        require(expected != null, "Unknown request field");
      } else {
        require(prior.containsKey(p[1]), "Forward/unknown step reference");
        if (p.length == 2) expected = prior.get(p[1]);
      }
      require(expected == null || expected.equals(step.inputType), "Input type mismatch");
      prior.put(step.id, step.outputType);
    }
    if ("direct".equals(assembly.mode)) {
      require(
          assembly.builder == null && assembly.sourceRoot == null,
          "direct does not accept builder fields");
      require(
          steps.size() == 1 && steps.get(0).outputType.equals(models.response.className),
          "direct requires one matching business result");
    } else {
      require("builder".equals(assembly.mode), "Unknown assembly mode");
      fqn(assembly.builder);
      root(assembly.sourceRoot);
      require(generated.add(assembly.builder), "Generated builder collision");
    }
    require(Collections.disjoint(generated, targets), "Generated/existing type collision");
    require(responseBindings != null && !responseBindings.isEmpty(), "responseBindings missing");
    Set<String> resultFields = new HashSet<>();
    models.response.fields.forEach(f -> resultFields.add(f.name));
    responseBindings.forEach(
        (dest, src) -> {
          id(dest);
          require(resultFields.contains(src), "Unknown business result field: " + src);
        });
  }

  static void model(Model m) {
    require(m != null && m.fields != null && !m.fields.isEmpty(), "Model fields missing");
    fqn(m.className);
    root(m.sourceRoot);
    Set<String> names = new HashSet<>();
    for (Field f : m.fields) {
      id(f.name);
      javaType(f.type);
      require(f.description != null && !f.description.isBlank(), "Field description missing");
      require(names.add(f.name), "Duplicate field");
      require(!f.required || !PRIMITIVES.contains(f.type), "required only for reference fields");
    }
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  static void id(String s) {
    require(
        s != null
            && s.matches("[A-Za-z_][A-Za-z0-9_]*")
            && SourceVersion.isIdentifier(s)
            && !SourceVersion.isKeyword(s)
            && !Set.of("true", "false", "null", "_").contains(s),
        "Invalid Java identifier: " + s);
  }

  static void fqn(String s) {
    require(s != null && s.contains("."), "Fully qualified type required: " + s);
    for (String p : s.split("\\.", -1)) id(p);
  }

  static void javaType(String s) {
    require(s != null, "Missing Java type");
    try {
      var parsed = com.github.javaparser.StaticJavaParser.parseType(s);
      validateType(parsed, false);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid Java type: " + s, e);
    }
  }

  private static void validateType(com.github.javaparser.ast.type.Type type, boolean argument) {
    if (type.isArrayType()) {
      validateType(type.asArrayType().getComponentType(), false);
    } else if (type.isPrimitiveType()) {
      require(!argument, "Generic arguments require reference types");
    } else {
      require(type.isClassOrInterfaceType(), "Unsupported Java type");
      var reference = type.asClassOrInterfaceType();
      fqn(reference.getNameWithScope());
      reference.getTypeArguments().ifPresent(args -> args.forEach(t -> validateType(t, true)));
      reference
          .getScope()
          .ifPresent(
              scope ->
                  require(
                      scope
                          .findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class)
                          .stream()
                          .noneMatch(t -> t.getTypeArguments().isPresent()),
                      "Parameterized outer types unsupported"));
    }
  }

  static void root(String s) {
    require(s != null && s.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*"), "Invalid sourceRoot");
    for (String p : s.split("/")) require(!p.equals(".") && !p.equals(".."), "Unsafe sourceRoot");
  }

  static String simple(String type) {
    return type.substring(type.lastIndexOf('.') + 1);
  }

  static String cap(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
