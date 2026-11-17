#!/bin/bash
# 相册滑选 · 无 Gradle 原生构建脚本（aapt2 + javac + d8 + apksigner）
# 用法：bash 应用工程/build.sh
# 产物：安装包/相册滑选-v<版本号>.apk
set -e

# ---- 版本信息：改这里即可，包的版本号与文件名随之变化 ----
# v1.x 单机；v2.x 留给未来联网功能。versionCode 只递增以支持覆盖安装。
VERSION_CODE=24
VERSION_NAME=1.9.14

SDK="$HOME/android-dev/sdk"
JDK="$HOME/android-dev/jdk/Contents/Home"
BT="$SDK/build-tools/34.0.0"
PLAT="$SDK/platforms/android-34/android.jar"
PROJ="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$PROJ/.." && pwd)"
OUT="$ROOT/安装包/相册滑选-v$VERSION_NAME.apk"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

rm -rf "$PROJ/build"
mkdir -p "$PROJ/build/gen" "$PROJ/build/classes" "$PROJ/build/dex" "$ROOT/安装包"

echo "[1/6] aapt2 compile"
"$BT/aapt2" compile --dir "$PROJ/res" -o "$PROJ/build/res.zip"

echo "[2/6] aapt2 link"
"$BT/aapt2" link -o "$PROJ/build/base.apk" \
  -I "$PLAT" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -R "$PROJ/build/res.zip" \
  --java "$PROJ/build/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "[3/6] javac"
find "$PROJ/src" "$PROJ/build/gen" -name "*.java" > "$PROJ/build/sources.txt"
javac -encoding UTF-8 -nowarn -source 8 -target 8 \
  -classpath "$PLAT" \
  -d "$PROJ/build/classes" \
  @"$PROJ/build/sources.txt"

echo "[4/6] d8"
find "$PROJ/build/classes" -name "*.class" > "$PROJ/build/classes.txt"
"$BT/d8" --release --min-api 26 --lib "$PLAT" \
  --output "$PROJ/build/dex" \
  @"$PROJ/build/classes.txt"

echo "[5/6] package + zipalign"
cp "$PROJ/build/base.apk" "$PROJ/build/app-unsigned.apk"
( cd "$PROJ/build/dex" && zip -q -X "$PROJ/build/app-unsigned.apk" classes.dex )
"$BT/zipalign" -f -p 4 "$PROJ/build/app-unsigned.apk" "$PROJ/build/app-aligned.apk"

echo "[6/6] sign"
KS="$HOME/android-dev/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$PROJ/build/app-aligned.apk"

"$BT/apksigner" verify "$OUT"
ls -lh "$OUT"
echo "OK: $OUT"
