#!/usr/bin/env bash
# Скачивает всё, что нужно для сборки APK без Android Studio и без Android SDK.
# Всё складывается в .toolchain/ (в git не попадает).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
LIB="$TC/libs"
BIN="$TC/bin"
mkdir -p "$LIB" "$BIN"

GDX_VER="1.13.1"
DX_VER="14.0.0_r21"
APKTOOL_VER="2.9.3"
CENTRAL="https://repo1.maven.org/maven2"

get() { # url dest
  local url="$1" dest="$2"
  if [ -s "$dest" ]; then echo "  · $(basename "$dest") (уже есть)"; return 0; fi
  echo "  ↓ $(basename "$dest")"
  local n=0
  until curl -sSL --fail -o "$dest.part" "$url"; do
    n=$((n+1)); [ $n -ge 4 ] && { echo "не удалось скачать $url" >&2; return 1; }
    sleep $((2 ** n))
  done
  mv "$dest.part" "$dest"
}

echo "== libGDX $GDX_VER =="
get "$CENTRAL/com/badlogicgames/gdx/gdx/$GDX_VER/gdx-$GDX_VER.jar" "$LIB/gdx.jar"
get "$CENTRAL/com/badlogicgames/gdx/gdx-backend-android/$GDX_VER/gdx-backend-android-$GDX_VER.aar" "$LIB/gdx-backend-android.aar"
for ABI in armeabi-v7a arm64-v8a x86 x86_64; do
  get "$CENTRAL/com/badlogicgames/gdx/gdx-platform/$GDX_VER/gdx-platform-$GDX_VER-natives-$ABI.jar" "$LIB/natives-$ABI.jar"
done

# распаковываем classes.jar из aar
if [ ! -s "$LIB/gdx-backend-android.jar" ]; then
  ( cd "$TC" && rm -rf aar && mkdir aar && cd aar && unzip -oq "$LIB/gdx-backend-android.aar" && cp classes.jar "$LIB/gdx-backend-android.jar" )
fi

echo "== android.jar (API 28, зеркало Sable/android-platforms) =="
get "https://raw.githubusercontent.com/Sable/android-platforms/master/android-28/android.jar" "$LIB/android.jar"

echo "== dx (dexer, AOSP $DX_VER из Maven Central) =="
get "$CENTRAL/com/jakewharton/android/repackaged/dalvik-dx/$DX_VER/dalvik-dx-$DX_VER.jar" "$LIB/dx.jar"

echo "== aapt2 (prebuilt из Apktool $APKTOOL_VER) =="
if [ ! -x "$BIN/aapt2" ]; then
  get "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar" "$TC/apktool.jar"
  unzip -o -j "$TC/apktool.jar" "prebuilt/linux/aapt2_64" -d "$BIN" >/dev/null
  mv "$BIN/aapt2_64" "$BIN/aapt2"
  chmod +x "$BIN/aapt2"
fi

echo "Готово. Инструменты в $TC"
