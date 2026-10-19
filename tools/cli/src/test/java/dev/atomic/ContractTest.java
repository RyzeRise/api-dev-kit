package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractTest {
  @TempDir Path temp;

  String example() throws Exception {
    return Files.readString(Path.of("src/main/resources/demo/contracts/single-handler.yaml"));
  }

  Path input(String text) throws Exception {
    Path p = temp.resolve("api.yaml");
    Files.writeString(p, text);
    return p;
  }

  @Test
  void loadsExamples() throws Exception {
    assertEquals("queryResourceByAddress", Contract.load(input(example())).operation());
    assertEquals(2, Contract.load(Path.of("src/main/resources/demo/contracts/multi-handler.yaml")).steps().size());
  }

  @Test
  void rejectsInputInjection() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> Contract.load(input(example().replace("input: request", "input: request.exit()"))));
  }

  @Test
  void rejectsDuplicateYamlKey() throws Exception {
    assertThrows(Exception.class, () -> Contract.load(input(example() + "\nversion: 1\n")));
  }

  @Test
  void rejectsUnknownProperty() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> Contract.load(input(example() + "\nunknown: true\n")));
  }

  @Test
  void rejectsEscapeRoot() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> Contract.load(input(example().replace("function-sdk/src/main/java", "../outside"))));
  }

  @Test
  void rejectsWrongDirectResult() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Contract.load(
                input(
                    example()
                        .replace(
                            "outputType: com.example.sdk.QueryResourceByAddressResultDto",
                            "outputType: java.lang.String"))));
  }

  @Test
  void rejectsForwardReference() throws Exception {
    String multi = Files.readString(Path.of("src/main/resources/demo/contracts/multi-handler.yaml"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Contract.load(input(multi.replace("input: request.address", "input: steps.resource"))));
  }

  @Test
  void rejectsYamlScalarCoercion() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> Contract.load(input(example().replace("version: 1", "version: '1'"))));
  }
  @Test
  void acceptsNestedFullyQualifiedCollectionTypes() throws Exception {
    Contract.javaType("java.util.Map<java.lang.String, java.util.List<java.lang.String>>");
    assertThrows(IllegalArgumentException.class, () -> Contract.javaType("java.util.List<int>"));
    assertThrows(IllegalArgumentException.class, () -> Contract.javaType("java.util.List<String>"));
    assertThrows(IllegalArgumentException.class, () -> Contract.javaType("java.lang.String;System.exit(0)"));
  }
}
