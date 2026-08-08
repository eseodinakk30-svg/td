#!/usr/bin/env bash
# Собирает и запускает игру на десктопе (для разработки).
# Зависимости десктопного бэкенда качаются в .toolchain/desktop.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
D="$TC/desktop"
mkdir -p "$D"
C=https://repo1.maven.org/maven2
GDX=1.13.1
LW=3.3.3

get() {
  local f="$D/$(basename "$1")"
  [ -s "$f" ] || curl -sSL --fail -o "$f" "$1"
}

get $C/com/badlogicgames/gdx/gdx-backend-lwjgl3/$GDX/gdx-backend-lwjgl3-$GDX.jar
get $C/com/badlogicgames/gdx/gdx-platform/$GDX/gdx-platform-$GDX-natives-desktop.jar
for a in lwjgl lwjgl-glfw lwjgl-opengl lwjgl-openal lwjgl-stb lwjgl-jemalloc; do
  get $C/org/lwjgl/$a/$LW/$a-$LW.jar
  get $C/org/lwjgl/$a/$LW/$a-$LW-natives-linux.jar
done

CP="$TC/libs/gdx.jar:$(ls "$D"/*.jar | tr '\n' ':')"
OUT="$ROOT/build/desktop"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT" \
      $(find "$ROOT/core/src" "$ROOT/desktop/src" -name '*.java')

cd "$ROOT/android/assets"   # чтобы Gdx.files.internal видел font.fnt
exec java -cp "$OUT:$CP" -Dfile.encoding=UTF-8 \
     com.resonance.td.desktop.DesktopLauncher "$@"
