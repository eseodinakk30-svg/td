#!/usr/bin/env bash
# Прогон симуляции без графики + карта урона. Полезно при правке баланса.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
bash tools/fetch_deps.sh >/dev/null
OUT=build/simtest
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -encoding UTF-8 -cp .toolchain/libs/gdx.jar -d "$OUT" \
      $(find core/src -name '*.java') tools/SimTest.java tools/MicroTest.java
export LC_ALL=C.UTF-8
java -Dstdout.encoding=UTF-8 -cp "$OUT:.toolchain/libs/gdx.jar" SimTest "${1:-2}"
java -Dstdout.encoding=UTF-8 -cp "$OUT:.toolchain/libs/gdx.jar" MicroTest
