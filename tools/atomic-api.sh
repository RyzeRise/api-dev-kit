#!/bin/sh
set -eu
entry=$0
# Resolve symlinks so the command can also be linked into a PATH directory.
while [ -L "$entry" ]; do
  parent=$(CDPATH= cd -- "$(dirname -- "$entry")" && pwd)
  entry=$(readlink "$entry")
  case "$entry" in /*) ;; *) entry=$parent/$entry ;; esac
done
tool_home=$(CDPATH= cd -- "$(dirname -- "$entry")" && pwd)
jar=${ATOMIC_API_JAR:-"$tool_home/lib/atomic-api/atomic-api.jar"}
if [ ! -f "$jar" ] && [ -z "${ATOMIC_API_JAR:-}" ]; then
  jar=$tool_home/cli/target/atomic-api-0.7.1.jar
fi
if [ ! -f "$jar" ]; then
  printf '%s\n' 'atomic-api 缺少配套 JAR。请使用完整便携包，或先构建源码；无需运行安装脚本。' >&2
  exit 2
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  runtime=$JAVA_HOME/bin/java
elif command -v java >/dev/null 2>&1; then
  runtime=$(command -v java)
else
  printf '%s\n' 'atomic-api 需要 Java 17 或更高版本，请安装运行环境后重试。' >&2
  exit 2
fi
exec "$runtime" -Dfile.encoding=UTF-8 -jar "$jar" "$@"
