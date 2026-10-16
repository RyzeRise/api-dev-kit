package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ImportLayoutTest {
  @Test
  void groupsNormalAndStaticImportsAndKeepsCrlf() {
    String s="package test;\r\n\r\nimport java.util.Map;\r\nimport com.example.Z;\r\nimport static java.util.Collections.emptyMap;\r\nimport com.example.A;\r\n\r\nclass Test {}\r\n";
    String expected="package test;\r\n\r\nimport com.example.A;\r\nimport com.example.Z;\r\n\r\nimport java.util.Map;\r\n\r\nimport static java.util.Collections.emptyMap;\r\n\r\nclass Test {}\r\n";
    assertEquals(expected,ImportLayout.format(s));
    assertEquals(expected,ImportLayout.format(expected));
  }

  @Test
  void preservesCommentedImportsAndBodyVerbatim() {
    String s="package test;\nimport java.util.Map;\n// special group\nimport com.example.Z;\nclass Test { String s=\"import java.util.Set;\"; }";
    assertEquals(s,ImportLayout.format(s));
  }
}
