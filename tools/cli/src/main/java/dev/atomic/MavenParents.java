package dev.atomic;

import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Read-only local parent-coordinate checks; never resolves or downloads a POM. */
final class MavenParents {
  private record Pom(Path path, Element project) {}

  static List<Checker.Finding> check(Path directory) {
    Path root = directory.toAbsolutePath().normalize();
    List<Checker.Finding> findings = new ArrayList<>();
    Map<Path, Pom> poms = new LinkedHashMap<>();
    try (var files = Files.walk(root)) {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      for (Path path :
          files
              .filter(p -> p.getFileName().toString().equals("pom.xml"))
              .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
              .sorted()
              .toList()) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        if (Arrays.stream(relative.split("/"))
            .anyMatch(s -> Set.of(".git", ".atomic-api", "target", "build").contains(s))) continue;
        try {
          var builder = factory.newDocumentBuilder();
          builder.setErrorHandler(
              new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException e)
                    throws org.xml.sax.SAXException {
                  throw e;
                }
              });
          poms.put(path, new Pom(path, builder.parse(path.toFile()).getDocumentElement()));
        } catch (Exception e) {
          findings.add(
              new Checker.Finding(
                  "INCOMPLETE",
                  "MAVEN-PARSE",
                  relative,
                  0,
                  "Cannot safely parse POM: " + e.getClass().getSimpleName()));
        }
      }
    } catch (Exception e) {
      findings.add(
          new Checker.Finding(
              "INCOMPLETE",
              "MAVEN-PARSE",
              root.toString(),
              0,
              "Cannot inspect local POMs: " + e.getClass().getSimpleName()));
    }
    for (Pom pom : poms.values()) {
      Element parent = child(pom.project, "parent");
      if (parent == null) continue;
      Element relative = child(parent, "relativePath");
      // An explicit empty relativePath deliberately selects a repository parent.
      if (relative != null && relative.getTextContent().isBlank()) continue;
      Path candidate =
          pom.path
              .getParent()
              .resolve(relative == null ? "../pom.xml" : relative.getTextContent().trim())
              .normalize();
      if (Files.isDirectory(candidate)) candidate = candidate.resolve("pom.xml");
      Pom local = poms.get(candidate);
      if (local == null) continue; // External/nonexistent parent: Maven must resolve it.
      Map<String, String> localProperties = properties(local, poms, new HashSet<>());
      Map<String, String> properties = properties(pom, poms, new HashSet<>());
      String expectedGroup = expand(text(parent, "groupId"), properties);
      String expectedArtifact = expand(text(parent, "artifactId"), properties);
      String expectedVersion = expand(text(parent, "version"), properties);
      String group = expand(coordinate(local.project, "groupId"), localProperties);
      String artifact = expand(text(local.project, "artifactId"), localProperties);
      String version = expand(coordinate(local.project, "version"), localProperties);
      if (!Objects.equals(expectedGroup, group) || !Objects.equals(expectedArtifact, artifact))
        continue;
      if (expectedVersion == null
          || version == null
          || expectedVersion.contains("${")
          || version.contains("${")) {
        findings.add(
            new Checker.Finding(
                "WARN",
                "MAVEN-001",
                root.relativize(pom.path).toString(),
                0,
                "Parent version expression needs Maven effective-model verification"));
      } else if (!expectedVersion.equals(version)) {
        findings.add(
            new Checker.Finding(
                "ERROR",
                "MAVEN-001",
                root.relativize(pom.path).toString(),
                0,
                "parent.version="
                    + expectedVersion
                    + " differs from local parent "
                    + expectedGroup
                    + ":"
                    + artifact
                    + ":"
                    + version
                    + "; align intentionally and verify Reactor/classpath."
                    + " A repository parent can select older shared dependencies."));
      }
    }
    return List.copyOf(findings);
  }

  private static Map<String, String> properties(Pom pom, Map<Path, Pom> poms, Set<Path> seen) {
    Map<String, String> result = new HashMap<>();
    if (!seen.add(pom.path)) return result;
    Element parent = child(pom.project, "parent");
    if (parent != null) {
      Element relative = child(parent, "relativePath");
      if (relative == null || !relative.getTextContent().isBlank()) {
        Path path =
            pom.path
                .getParent()
                .resolve(relative == null ? "../pom.xml" : relative.getTextContent().trim())
                .normalize();
        if (Files.isDirectory(path)) path = path.resolve("pom.xml");
        Pom local = poms.get(path);
        if (local != null) result.putAll(properties(local, poms, seen));
      }
    }
    Element props = child(pom.project, "properties");
    if (props != null)
      for (var node = props.getFirstChild(); node != null; node = node.getNextSibling())
        if (node instanceof Element e) result.put(e.getLocalName(), e.getTextContent().trim());
    return result;
  }

  private static String expand(String value, Map<String, String> properties) {
    if (value == null) return null;
    for (int i = 0; i < 10; i++) {
      String previous = value;
      for (var entry : properties.entrySet())
        value = value.replace("${" + entry.getKey() + "}", entry.getValue());
      if (value.equals(previous)) break;
    }
    return value;
  }

  private static String coordinate(Element project, String name) {
    String own = text(project, name);
    return own == null ? text(child(project, "parent"), name) : own;
  }

  private static Element child(Element element, String name) {
    if (element == null) return null;
    for (var node = element.getFirstChild(); node != null; node = node.getNextSibling())
      if (node instanceof Element e && e.getLocalName().equals(name)) return e;
    return null;
  }

  private static String text(Element element, String name) {
    Element found = child(element, name);
    return found == null ? null : found.getTextContent().trim();
  }
}
