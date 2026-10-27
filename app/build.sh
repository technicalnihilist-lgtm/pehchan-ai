#!/bin/bash
# Pehchan AI — manual Android build. No Gradle, no AndroidX.
#
#   SDK=/path/to/android-sdk bash app/build.sh
#   → out/PehchanAI-v1.1.apk  (signed, installable)
#
# The two model files are attached to the GitHub release rather than committed
# (the GitHub API refuses binaries that large), so this script fetches whichever
# one is missing. Everything else is in the tree.
set -e
ulimit -v unlimited 2>/dev/null || true

# ---------------------------------------------------------------- locate SDK
SDK="${SDK:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  echo "ERROR: set SDK=/path/to/android-sdk (or ANDROID_HOME)."
  echo "       Needs build-tools 34.0.0 and platform android-35."
  exit 1
fi
BUILD_TOOLS="${BUILD_TOOLS:-34.0.0}"
PLATFORM="${PLATFORM:-android-35}"
BT="$SDK/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"

[ -x "$BT/aapt2" ]    || { echo "ERROR: missing $BT/aapt2"; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "ERROR: missing $ANDROID_JAR"; exit 1; }
if [ -n "$JAVA_HOME" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null || { echo "ERROR: javac not on PATH (set JAVA_HOME)"; exit 1; }

# ---------------------------------------------------------------- paths
APP="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$APP")"
BUILD="$ROOT/build"
OUT="$ROOT/out"
DEPS="$BUILD/deps"
TFLITE_VER=2.14.0
REL="https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/download/v1.1"

rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT" "$DEPS"

fetch() { # fetch <url> <dest>
  if [ ! -s "$2" ]; then
    echo "   downloading $(basename "$2")"
    curl -L --fail --silent --show-error -o "$2" "$1"
  fi
}

echo "[0/7] dependencies"
if [ ! -s "$APP/assets/model.tflite" ]; then
  echo "   fetching classifier model.tflite (5.2 MB)"
  fetch "$REL/model.tflite" "$APP/assets/model.tflite"
fi
if [ ! -s "$APP/assets/detect.tflite" ]; then
  echo "   fetching detector detect.tflite (4.0 MB)"
  fetch "$REL/detect.tflite" "$APP/assets/detect.tflite"
fi
# TensorFlow Lite runtime: API classes + native libraries.
fetch "https://repo1.maven.org/maven2/org/tensorflow/tensorflow-lite/$TFLITE_VER/tensorflow-lite-$TFLITE_VER.aar" \
      "$DEPS/tensorflow-lite.aar"
fetch "https://repo1.maven.org/maven2/org/tensorflow/tensorflow-lite-api/$TFLITE_VER/tensorflow-lite-api-$TFLITE_VER.aar" \
      "$DEPS/tensorflow-lite-api.aar"
for name in tensorflow-lite tensorflow-lite-api; do
  if [ ! -f "$DEPS/$name/classes.jar" ]; then
    mkdir -p "$DEPS/$name"
    (cd "$DEPS/$name" && unzip -o -q "../$name.aar" classes.jar)
  fi
done
API_JAR="$DEPS/tensorflow-lite-api/classes.jar"
IMPL_JAR="$DEPS/tensorflow-lite/classes.jar"

echo "[1/7] compiling resources"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "[2/7] linking resources + manifest + assets"
"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 2 \
  --version-name 1.1 \
  "$BUILD/res.zip"

echo "[3/7] compiling java"
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$API_JAR:$IMPL_JAR" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

if [ ! -f "$BUILD/classes/com/rajok/pehchanai/MainActivity.class" ]; then
  echo "ERROR: java compilation failed"
  exit 1
fi

echo "[4/7] dexing"
CLASSES=$(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')
"$BT/d8" --release --min-api 21 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  $CLASSES "$API_JAR" "$IMPL_JAR"

echo "[5/7] packaging"
python3 - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$DEPS/tensorflow-lite.aar" \
          "$OUT/pehchan-ai-unsigned.apk" <<'PY'
import sys, zipfile, shutil, os
base, dex, aar, out = sys.argv[1:5]
shutil.copyfile(base, out)
libs = {}
with zipfile.ZipFile(aar) as z:
    for n in z.namelist():
        if n.startswith('jni/') and n.endswith('.so'):
            libs['lib/%s/%s' % (n.split('/')[1], os.path.basename(n))] = z.read(n)
with zipfile.ZipFile(out, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    for name, data in libs.items():
        z.writestr(name, data)
    print("   native libs:", ", ".join(sorted(libs)))
PY

echo "[6/7] zipalign"
"$BT/zipalign" -f 4 "$OUT/pehchan-ai-unsigned.apk" "$OUT/pehchan-ai-aligned.apk"

echo "[7/7] signing"
if [ ! -f "$APP/keystore.jks" ]; then
  echo "   generating app/keystore.jks (keep this file for future updates)"
  keytool -genkeypair -v -keystore "$APP/keystore.jks" -alias pehchan \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass pehchan123 -keypass pehchan123 \
    -dname "CN=Pehchan AI, OU=Mobile, O=Pehchan AI, L=Bengaluru, ST=Karnataka, C=IN" >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks "$APP/keystore.jks" --ks-key-alias pehchan \
  --ks-pass pass:pehchan123 --key-pass pass:pehchan123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/PehchanAI-v1.1.apk" "$OUT/pehchan-ai-aligned.apk"

echo
echo "=== done: $OUT/PehchanAI-v1.1.apk ==="
ls -lh "$OUT/PehchanAI-v1.1.apk"
