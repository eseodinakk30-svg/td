#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  Сборка APK «Резонанс TD» без Android Studio, без Gradle и без Android SDK.
#
#  Цепочка:  javac  ->  dx (dex)  ->  aapt2 (ресурсы + манифест)
#            ->  сборка zip  ->  jarsigner (подпись v1)
#
#  Использование:  ./build.sh          обычная сборка
#                  ./build.sh clean    удалить build/ и пересобрать
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TC="$ROOT/.toolchain"
LIB="$TC/libs"
BIN="$TC/bin"
BUILD="$ROOT/build"
DIST="$ROOT/dist"
APK_NAME="resonance-td.apk"

MIN_SDK=21
TARGET_SDK=28

[ "${1:-}" = "clean" ] && rm -rf "$BUILD"

echo "==> 1/7 Зависимости"
bash "$ROOT/tools/fetch_deps.sh"

echo "==> 2/7 Ресурсы (иконка + шрифт)"
if [ ! -s "$ROOT/android/res/mipmap-xxxhdpi/ic_launcher.png" ]; then
  python3 "$ROOT/tools/make_icons.py"
else
  echo "  · иконки на месте"
fi
if [ ! -s "$ROOT/android/assets/font.fnt" ]; then
  mkdir -p "$ROOT/android/assets"
  java -Djava.awt.headless=true "$ROOT/tools/FontGen.java" "$ROOT/android/assets"
else
  echo "  · шрифт на месте"
fi

CP="$LIB/android.jar:$LIB/gdx.jar:$LIB/gdx-backend-android.jar"

echo "==> 3/7 Компиляция Java"
rm -rf "$BUILD/classes" && mkdir -p "$BUILD/classes"
find "$ROOT/core/src" "$ROOT/android/src" -name '*.java' >"$BUILD/sources.txt"
echo "  · файлов: $(wc -l <"$BUILD/sources.txt")"
javac -nowarn -encoding UTF-8 -source 8 -target 8 \
      -classpath "$CP" -d "$BUILD/classes" \
      @"$BUILD/sources.txt" 2>&1 | grep -v "bootstrap class path\|source value 8\|target value 8\|deprecat" || true
[ -n "$(find "$BUILD/classes" -name '*.class' -print -quit)" ] || { echo "компиляция провалилась" >&2; exit 1; }

echo "==> 4/7 Dex"
rm -rf "$BUILD/dex" && mkdir -p "$BUILD/dex"
java -Xmx2g -cp "$LIB/dx.jar" com.android.dx.command.Main \
     --dex --multi-dex --min-sdk-version=$MIN_SDK \
     --output="$BUILD/dex" \
     "$BUILD/classes" "$LIB/gdx.jar" "$LIB/gdx-backend-android.jar" 2>&1 | grep -v "Picked up" || true
ls "$BUILD/dex"/*.dex >/dev/null || { echo "dex не собрался" >&2; exit 1; }
echo "  · $(ls "$BUILD"/dex/*.dex | wc -l) dex-файл(ов), $(du -sh "$BUILD/dex" | cut -f1)"

echo "==> 5/7 Ресурсы через aapt2"
rm -rf "$BUILD/res.zip" "$BUILD/base.apk"
"$BIN/aapt2" compile --dir "$ROOT/android/res" -o "$BUILD/res.zip"
"$BIN/aapt2" link \
    -I "$LIB/android.jar" \
    --manifest "$ROOT/android/AndroidManifest.xml" \
    -A "$ROOT/android/assets" \
    --min-sdk-version $MIN_SDK \
    --target-sdk-version $TARGET_SDK \
    -o "$BUILD/base.apk" \
    "$BUILD/res.zip"

echo "==> 6/7 Упаковка (dex + нативные библиотеки + ресурсы jar-ов)"
mkdir -p "$DIST"
python3 "$ROOT/tools/package_apk.py" \
    --base "$BUILD/base.apk" \
    --dex-dir "$BUILD/dex" \
    --libs "$LIB" \
    --out "$BUILD/unsigned.apk"

echo "==> 7/7 Подпись"
KS="$TC/debug.keystore"
if [ ! -s "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
          -alias resonance -keyalg RSA -keysize 2048 -validity 10950 \
          -dname "CN=Resonance TD, OU=Game, O=Indie, L=-, S=-, C=RU" >/dev/null 2>&1
  echo "  · создан отладочный keystore"
fi
cp "$BUILD/unsigned.apk" "$DIST/$APK_NAME"
jarsigner -keystore "$KS" -storepass android -keypass android \
          -digestalg SHA-256 -sigalg SHA256withRSA \
          "$DIST/$APK_NAME" resonance >/dev/null 2>&1
jarsigner -verify "$DIST/$APK_NAME" >/dev/null 2>&1 && echo "  · подпись v1 проверена"

echo
echo "APK готов: dist/$APK_NAME  ($(du -h "$DIST/$APK_NAME" | cut -f1))"
echo "Установка:  adb install -r dist/$APK_NAME"
