#!/usr/bin/env bash
# Renders the game's own draw calls to PNGs via Java2D (build/shots/).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/build/harness"
rm -rf "$OUT"; mkdir -p "$OUT"
find "$ROOT/app/src/main/java" -name '*.java' \
     ! -name 'GameView.java' ! -name 'MainActivity.java' > "$OUT/sources.txt"
find "$ROOT/tools/harness/src" -name '*.java' >> "$OUT/sources.txt"
javac -nowarn -d "$OUT" @"$OUT/sources.txt"
cd "$ROOT" && java -Djava.awt.headless=true -cp "$OUT" com.claude.td.Shot
