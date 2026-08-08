#!/usr/bin/env bash
# Compiles the game logic against tiny stub Android classes and plays every map
# headlessly. Pure JVM: no device, no emulator, no Android SDK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/build/harness"
rm -rf "$OUT"
mkdir -p "$OUT"

# GameView/MainActivity are the only classes that touch the real Android view
# stack, so they stay out of the headless build.
find "$ROOT/app/src/main/java" -name '*.java' \
     ! -name 'GameView.java' ! -name 'MainActivity.java' > "$OUT/sources.txt"
find "$ROOT/tools/harness/src" -name '*.java' >> "$OUT/sources.txt"

javac -nowarn -d "$OUT" @"$OUT/sources.txt"
java -cp "$OUT" com.claude.td.Harness
