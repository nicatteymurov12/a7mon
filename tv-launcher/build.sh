#!/usr/bin/env bash
# Builds RahatTV.apk without Gradle / Android Studio.
# Needs (Ubuntu/Debian): aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 + a JDK.
#   sudo apt-get install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 default-jdk
set -euo pipefail
cd "$(dirname "$0")"

VERSION="1.1"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
KEYSTORE="${KEYSTORE:-keystore/rahattv.jks}"
KS_PASS="${KS_PASS:-rahattv}"
OUT=build

[ -f "$ANDROID_JAR" ] || { echo "android.jar tapılmadı: $ANDROID_JAR" >&2; exit 1; }
DX=$(command -v dalvik-exchange || command -v dx)

rm -rf "$OUT"
mkdir -p "$OUT/gen/az/rahattv" "$OUT/obj"

# build constants (BUILD_TIME is used to detect a TV clock that is wrong)
cat > "$OUT/gen/az/rahattv/BuildInfo.java" <<JAVA
package az.rahattv;
final class BuildInfo {
    private BuildInfo() {}
    static final String VERSION = "$VERSION";
    static final long BUILD_TIME = $(date +%s)000L;
}
JAVA

echo "> resources"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

echo "> javac"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/obj" $(find src "$OUT/gen" -name '*.java')

echo "> dex"
"$DX" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/obj"

echo "> package"
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "> new signing key: $KEYSTORE (keep it to be able to update the app later)"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -alias rahattv -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Rahat TV" >/dev/null 2>&1
fi

echo "> sign"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --min-sdk-version 21 \
  --out "$OUT/RahatTV.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/RahatTV.apk"

mkdir -p dist
cp "$OUT/RahatTV.apk" dist/RahatTV.apk

echo "> USB kit"
rm -rf "$OUT/usb" dist/RahatTV-USB.zip
cp -r usb "$OUT/usb"
cp "$OUT/RahatTV.apk" "$OUT/usb/RahatTV/RahatTV.apk"
(cd "$OUT/usb" && TZ=UTC find RahatTV -exec touch -t 202601010000 {} + && zip -q -X -r ../../dist/RahatTV-USB.zip RahatTV)

echo "OK -> dist/RahatTV.apk ($(du -k dist/RahatTV.apk | cut -f1) KB), dist/RahatTV-USB.zip"
