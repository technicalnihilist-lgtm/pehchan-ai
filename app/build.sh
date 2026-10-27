#!/bin/bash
# Pehchan AI — manual Android build (no Gradle needed)
set -e
ulimit -v unlimited 2>/dev/null || true

export JAVA_HOME=/scratch/work/sdk/jdk
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Xmx768m -Xms64m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -XX:-UsePerfData"

SDK=/scratch/work/sdk
BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-35/android.jar
APP=/scratch/work/app
BUILD=/scratch/work/build
OUT=/scratch/work/out
DL=/scratch/work/dl

rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"

# The 5.4 MB model lives on the GitHub release instead of in the repo tree
# (the GitHub API rejects binaries that large). Fetch it once if it is missing.
if [ ! -f "$APP/assets/model.tflite" ]; then
  echo "[0/7] fetching model.tflite from the release"
  curl -L --fail -o "$APP/assets/model.tflite" \
    "https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/download/v1.0/model.tflite"
fi

echo "[1/7] compiling resources"
$BT/aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "[2/7] linking resources + manifest + assets"
$BT/aapt2 link \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  "$BUILD/res.zip"

echo "[3/7] compiling java"
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$DL/jars/api/classes.jar:$DL/jars/impl/classes.jar" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

if [ ! -f "$BUILD/classes/com/rajok/pehchanai/MainActivity.class" ]; then
  echo "ERROR: java compilation failed"; exit 1
fi

echo "[4/7] dexing"
CLASSES=$(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')
$BT/d8 --release --min-api 21 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  $CLASSES \
  "$DL/jars/api/classes.jar" \
  "$DL/jars/impl/classes.jar"

echo "[5/7] packaging"
python3 - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$DL/tflite.aar" "$OUT/pehchan-ai-unsigned.apk" <<'PY'
import sys, zipfile, shutil, io, os
base, dex, aar, out = sys.argv[1:5]
shutil.copyfile(base, out)
libs = {}
with zipfile.ZipFile(aar) as z:
    for n in z.namelist():
        if n.startswith('jni/') and n.endswith('.so'):
            abi = n.split('/')[1]
            libs['lib/%s/%s' % (abi, os.path.basename(n))] = z.read(n)
with zipfile.ZipFile(out, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    for name, data in libs.items():
        z.writestr(name, data)
    print("   native libs:", ", ".join(sorted(libs)))
PY

echo "[6/7] zipalign"
$BT/zipalign -f 4 "$OUT/pehchan-ai-unsigned.apk" "$OUT/pehchan-ai-aligned.apk"

echo "[7/7] signing"
if [ ! -f "$APP/keystore.jks" ]; then
  keytool -genkeypair -v -keystore "$APP/keystore.jks" -alias pehchan \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass pehchan123 -keypass pehchan123 \
    -dname "CN=Pehchan AI, OU=Mobile, O=Pehchan AI, L=Bengaluru, ST=Karnataka, C=IN" >/dev/null 2>&1
fi
$BT/apksigner sign \
  --ks "$APP/keystore.jks" --ks-key-alias pehchan \
  --ks-pass pass:pehchan123 --key-pass pass:pehchan123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/PehchanAI-v1.0.apk" "$OUT/pehchan-ai-aligned.apk"

echo "=== done ==="
ls -lh "$OUT"
