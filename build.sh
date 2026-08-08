#!/usr/bin/env bash
#
# Builds dist/TowerDefense.apk without Android Studio or the Google SDK
# downloader, using only the Android build tools packaged by Debian/Ubuntu:
#
#   sudo apt-get install -y aapt dalvik-exchange apksigner zipalign \
#                           android-sdk-platform-23 openjdk-21-jdk-headless
#
# If you do have a normal Android SDK installed, set ANDROID_JAR / AAPT / DEXER
# to point at it instead.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build"
DIST="$ROOT/dist"
APK_NAME="${APK_NAME:-TowerDefense.apk}"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
AAPT="${AAPT:-aapt}"
DEXER="${DEXER:-dalvik-exchange}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"

KEYSTORE="${KEYSTORE:-$ROOT/build/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KS_ALIAS="${KS_ALIAS:-towerdefense}"

MIN_SDK=21
TARGET_SDK=34

say() { printf '\033[1;36m==>\033[0m %s\n' "$1"; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$1" >&2; exit 1; }

for tool in "$AAPT" "$DEXER" "$ZIPALIGN" "$APKSIGNER" javac keytool; do
    command -v "$tool" >/dev/null 2>&1 || die "missing tool: $tool"
done
[ -f "$ANDROID_JAR" ] || die "android.jar not found at $ANDROID_JAR"

rm -rf "$OUT/classes" "$OUT/apk"
mkdir -p "$OUT/classes" "$OUT/apk" "$DIST"

say "compiling java sources"
find "$SRC/java" -name '*.java' > "$OUT/sources.txt"
javac -Xlint:-options -nowarn -source 8 -target 8 \
      -bootclasspath "$ANDROID_JAR" \
      -d "$OUT/classes" @"$OUT/sources.txt"

say "dexing"
"$DEXER" --dex --min-sdk-version=$MIN_SDK \
         --output="$OUT/apk/classes.dex" "$OUT/classes"

say "packaging resources"
"$AAPT" package -f \
    -M "$SRC/AndroidManifest.xml" \
    -S "$SRC/res" \
    -I "$ANDROID_JAR" \
    --min-sdk-version $MIN_SDK \
    --target-sdk-version $TARGET_SDK \
    -F "$OUT/apk/unsigned.apk"

say "adding classes.dex"
( cd "$OUT/apk" && "$AAPT" add -f unsigned.apk classes.dex >/dev/null )

if [ ! -f "$KEYSTORE" ]; then
    say "generating a debug keystore"
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$KS_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Tower Defense, OU=Game, O=Tower Defense, L=-, ST=-, C=US" \
        >/dev/null 2>&1
fi

say "aligning"
"$ZIPALIGN" -f -p 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

say "signing"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$DIST/$APK_NAME" "$OUT/apk/aligned.apk"

"$APKSIGNER" verify --print-certs "$DIST/$APK_NAME" | head -3

say "done: dist/$APK_NAME ($(du -h "$DIST/$APK_NAME" | cut -f1))"
