#!/bin/sh
set -eu
tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
prefix=${HOME:?}/.local
jar=
while [ "$#" -gt 0 ]; do
  case "$1" in
    --prefix|--jar)
      [ "$#" -ge 2 ] || { printf '%s\n' 'Missing install argument' >&2; exit 2; }
      case "$1" in --prefix) prefix=$2 ;; --jar) jar=$2 ;; esac
      shift 2 ;;
    -h|--help) printf '%s\n' 'install.sh [--prefix <directory>] [--jar <built-jar>]'; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done
case "$prefix" in /*) ;; *) prefix=$PWD/$prefix ;; esac
if [ -z "$jar" ] && [ -f "$tool_root/lib/atomic-api/atomic-api.jar" ]; then
  jar=$tool_root/lib/atomic-api/atomic-api.jar
elif [ -z "$jar" ]; then
  mvn -q -f "$tool_root/cli/pom.xml" package
  jar=$tool_root/cli/target/atomic-api-0.2.0.jar
fi
[ -f "$jar" ] || { printf '%s\n' 'Built CLI artifact is missing' >&2; exit 2; }
case "${SHELL:-}" in
  */zsh) rc=$HOME/.zshrc ;;
  */bash) rc=$HOME/.bashrc ;;
  *) rc=$HOME/.profile ;;
esac
assert_plain_path() {
  checked_path=$1
  while [ "$checked_path" != / ] && [ "$checked_path" != . ]; do
    if [ -L "$checked_path" ]; then
      printf 'Refusing symbolic link destination: %s\n' "$checked_path" >&2
      exit 2
    fi
    checked_path=$(dirname -- "$checked_path")
  done
}
assert_plain_path "$prefix/lib/atomic-api/atomic-api.jar"
assert_plain_path "$prefix/bin/atomic-api"
assert_plain_path "$rc"
mkdir -p "$prefix/bin" "$prefix/lib/atomic-api"
cp "$jar" "$prefix/lib/atomic-api/atomic-api.jar"
cp "$tool_root/bin/atomic-api" "$prefix/bin/atomic-api"
chmod +x "$prefix/bin/atomic-api"
# Shell-quote the path, including spaces and literal single quotes.
escaped=$(printf '%s' "$prefix/bin" | sed "s/'/'\\\\''/g")
line="export PATH='$escaped':\"\$PATH\""
if ! [ -f "$rc" ] || ! grep -F -x -- "$line" "$rc" >/dev/null 2>&1; then
  printf '\n# atomic-api command\n%s\n' "$line" >> "$rc"
fi
printf 'Installed atomic-api. Open a new terminal, then run: atomic-api -h\n'
printf 'Current terminal: . "%s"\n' "$rc"
