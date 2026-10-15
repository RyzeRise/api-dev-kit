package dev.atomic;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class SourceIndex {
  private final Path root;
  private final Map<String, ClassOrInterfaceDeclaration> types = new LinkedHashMap<>();
  private final Map<Path, CompilationUnit> units = new LinkedHashMap<>();
  private final Map<Path, String> originals = new LinkedHashMap<>();
  private final List<String> issues = new ArrayList<>();
  private com.github.javaparser.resolution.TypeSolver solver;

  private SourceIndex(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  public static SourceIndex scan(Path root) throws IOException {
    return scan(root, List.of(), List.of());
  }

  public static SourceIndex scan(Path root, List<Path> roots, List<Path> jars) throws IOException {
    SourceIndex index = new SourceIndex(root);
    Contract.require(Files.isDirectory(index.root), "Repository directory missing");
    var memory = new MemoryTypeSolver();
    var solver = new CombinedTypeSolver(memory, new ReflectionTypeSolver(false));
    index.solver = solver;
    for (Path jar : jars) solver.add(new JarTypeSolver(jar));
    var parser =
        new JavaParser(
            new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setSymbolResolver(new JavaSymbolSolver(solver)));
    try (Stream<Path> stream = Files.walk(index.root)) {
      for (Path path :
          stream
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".java"))
              .sorted()
              .toList()) {
        String relative = index.root.relativize(path).toString().replace('\\', '/');
        if (relative.startsWith(".git/")
            || relative.contains("/target/")
            || relative.contains("/build/")
            || relative.contains("/generated-sources/")) continue;
        boolean included =
            roots.isEmpty()
                ? relative.contains("src/main/java/") && !relative.contains("/src/main/resources/")
                : roots.stream().anyMatch(r -> path.startsWith(r.toAbsolutePath().normalize()));
        if (!included) continue;
        String text = Files.readString(path, StandardCharsets.UTF_8);
        index.originals.put(path, text);
        ParseResult<CompilationUnit> parsed =
            parser.parse(text.startsWith("\uFEFF") ? text.substring(1) : text);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
          index.issues.add(path + ": parse failure " + parsed.getProblems());
          continue;
        }
        CompilationUnit cu = parsed.getResult().get();
        cu.setStorage(path);
        index.units.put(path, cu);
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
          if (type.getFullyQualifiedName().isEmpty()) continue;
          String name = type.getFullyQualifiedName().get();
          if (index.types.putIfAbsent(name, type) != null) {
            index.issues.add("Duplicate type: " + name);
            continue;
          }
          if (type.isInterface())
            memory.addDeclaration(name, new JavaParserInterfaceDeclaration(type, solver));
          else memory.addDeclaration(name, new JavaParserClassDeclaration(type, solver));
        }
        for (AnnotationDeclaration type : cu.findAll(AnnotationDeclaration.class))
          type.getFullyQualifiedName()
              .ifPresent(
                  name ->
                      memory.addDeclaration(
                          name, new JavaParserAnnotationDeclaration(type, solver)));
      }
    }
    return index;
  }

  public Map<String, ClassOrInterfaceDeclaration> types() {
    return Collections.unmodifiableMap(types);
  }

  public ClassOrInterfaceDeclaration type(String name) {
    return types.get(name);
  }

  public List<String> issues() {
    return List.copyOf(issues);
  }

  public Collection<CompilationUnit> units() {
    return units.values();
  }

  public Map<Path, String> originals() {
    return Collections.unmodifiableMap(originals);
  }

  public Path root() {
    return root;
  }

  public com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration declaration(
      String name) {
    return solver.solveType(name);
  }

  public Path path(Node node) {
    return node.findCompilationUnit()
        .flatMap(c -> c.getStorage())
        .map(CompilationUnit.Storage::getPath)
        .orElse(root);
  }

  public String unique(String simple) {
    List<String> matches =
        types.keySet().stream().filter(s -> Contract.simple(s).equals(simple)).toList();
    Contract.require(
        matches.size() == 1, "INCOMPLETE: expected unique " + simple + ", found " + matches);
    return matches.get(0);
  }
}
