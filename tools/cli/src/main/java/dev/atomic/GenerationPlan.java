package dev.atomic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public record GenerationPlan(Path root, List<Change> changes, Map<Path, String> dependencies) {
  public record Change(Path path, String before, String after) {
    public String action() {
      return before == null ? "CREATE" : "UPDATE";
    }

    public String beforeSha256() {
      return digest(before);
    }

    public String afterSha256() {
      return digest(after);
    }
  }

  public static String digest(String text) {
    if (text == null) return null;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Path safe(Path root, Path target) throws IOException {
    Path base = root.toRealPath(), path = target.toAbsolutePath().normalize();
    Contract.require(
        path.startsWith(base) && !path.equals(base), "Path escapes repository: " + target);
    Path part = base;
    for (Path component : base.relativize(path)) {
      part = part.resolve(component);
      Contract.require(!Files.isSymbolicLink(part), "Symlink target forbidden: " + part);
    }
    return path;
  }

  private static String read(Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path) : null;
  }

  public void apply() throws Exception {
    for (var entry : dependencies.entrySet()) {
      safe(root, entry.getKey());
      Contract.require(
          Objects.equals(read(entry.getKey()), entry.getValue()),
          "Stale dependency: " + entry.getKey());
    }
    for (Change change : changes) {
      safe(root, change.path);
      Contract.require(
          Objects.equals(read(change.path), change.before), "Stale plan: " + change.path);
    }
    List<Change> written = new ArrayList<>();
    try {
      for (Change change : changes) {
        safe(root, change.path);
        Files.createDirectories(change.path.getParent());
        Contract.require(
            Objects.equals(read(change.path), change.before), "Concurrent edit: " + change.path);
        Path temp = Files.createTempFile(change.path.getParent(), ".atomic-api-", ".tmp");
        try {
          Files.writeString(temp, change.after);
          if (change.before != null
              && Files.getFileStore(change.path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(change.path));
          safe(root, change.path);
          Contract.require(
              Objects.equals(read(change.path), change.before), "Concurrent edit: " + change.path);
          try {
            Files.move(
                temp,
                change.path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
          } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, change.path, StandardCopyOption.REPLACE_EXISTING);
          }
          written.add(change);
        } finally {
          Files.deleteIfExists(temp);
        }
      }
    } catch (Exception failure) {
      Collections.reverse(written);
      for (Change change : written)
        try {
          safe(root, change.path);
          if (!Objects.equals(read(change.path), change.after))
            throw new IOException("Cannot restore concurrent edit: " + change.path);
          if (change.before == null) Files.delete(change.path);
          else Files.writeString(change.path, change.before);
        } catch (Exception recovery) {
          failure.addSuppressed(recovery);
        }
      throw failure;
    }
  }

  public String preview() {
    StringBuilder out = new StringBuilder();
    for (Change change : changes) {
      out.append(change.action()).append(" ").append(root.relativize(change.path)).append("\n");
      out.append("--- ")
          .append(change.before == null ? "/dev/null" : change.path)
          .append("\n+++ ")
          .append(change.path)
          .append("\n");
      // Whole-file unified hunk: explicit, deterministic, and reviewable.
      List<String> old = change.before == null ? List.of() : change.before.lines().toList(),
          now = change.after.lines().toList();
      out.append("@@ -")
          .append(old.isEmpty() ? 0 : 1)
          .append(',')
          .append(old.size())
          .append(" +")
          .append(now.isEmpty() ? 0 : 1)
          .append(',')
          .append(now.size())
          .append(" @@\n");
      for (String line : old) out.append('-').append(line).append('\n');
      if (change.before != null && !change.before.isEmpty() && !change.before.endsWith("\n"))
        out.append("\\ No newline at end of file\n");
      for (String line : now) out.append('+').append(line).append('\n');
      if (!change.after.isEmpty() && !change.after.endsWith("\n"))
        out.append("\\ No newline at end of file\n");
    }
    if (changes.isEmpty()) out.append("NOOP\n");
    return out.toString();
  }
}
