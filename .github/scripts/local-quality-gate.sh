#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'Usage: %s <module-path>\n' "$0" >&2
  printf 'Example: %s feature/entry/domain\n' "$0" >&2
  exit 2
fi

module_directory="${1#[:/]}"
module_directory="${module_directory%/}"
module_directory="${module_directory//:/\/}"
module=":${module_directory//\//:}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [[ -z "$module_directory" ]]; then
  printf 'Unknown module: %s\n' "$1" >&2
  exit 2
fi

if [[ ! -f "$repository_root/$module_directory/build.gradle.kts" ]]; then
  printf 'Unknown module: %s\n' "$1" >&2
  exit 2
fi

cd "$repository_root"

./gradlew "$module:ktlintFormat" --no-daemon
./gradlew "$module:test" --no-daemon
./gradlew "$module:ktlintMainSourceSetCheck" "$module:ktlintTestSourceSetCheck" --no-daemon
./gradlew "$module:detektMain" "$module:detektTest" detektVerify --no-daemon --console=plain
