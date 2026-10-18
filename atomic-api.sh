#!/bin/sh
set -eu
# Preserve the caller's directory: commands default to the business project.
tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec sh "$tool_root/bin/atomic-api" "$@"
