package dev.atomic;

import java.util.*;
import java.util.regex.*;

/** Formats only a plain import block; comments and the rest of the file remain untouched. */
final class ImportLayout {
  private static final Pattern IMPORT = Pattern.compile(
      "(?m)^import\\s+(static\\s+)?([\\w.$]+(?:\\.\\*)?)\\s*;[\\t ]*\\r?$");

  static String format(String source) {
    Matcher matcher = IMPORT.matcher(source);
    List<String[]> entries = new ArrayList<>();
    int start = -1, end = -1;
    while (matcher.find()) {
      if (start < 0) start = matcher.start();
      else if (!source.substring(end, matcher.start()).isBlank()) return source;
      end = matcher.end();
      String name = matcher.group(2);
      String group = (matcher.group(1) == null ? "0" : "1")
          + (name.startsWith("java.") ? "zjava" : name.substring(0, name.indexOf('.')));
      entries.add(new String[] {group, name, matcher.group(1) == null ? "" : "static "});
    }
    if (entries.isEmpty()) return source;
    entries.sort(Comparator.<String[],String>comparing(e -> e[0]).thenComparing(e -> e[1]));
    String newline = source.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder block = new StringBuilder();
    String lastGroup = null;
    for (String[] entry : entries) {
      if (lastGroup != null) block.append(newline);
      if (lastGroup != null && !lastGroup.equals(entry[0])) block.append(newline);
      block.append("import ").append(entry[2]).append(entry[1]).append(';');
      lastGroup = entry[0];
    }
    return source.substring(0,start) + block + source.substring(end);
  }
}
