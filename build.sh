#!/usr/bin/env bash
set -euo pipefail

# --- read project.json ----------------------------------------------------
json_str() { sed -n 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' project.json | head -1; }
json_int() { sed -n 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' project.json | head -1; }

PKG=$(json_str package)
APP_NAME=$(json_str name)
MIN_SDK=$(json_int min_sdk)
TARGET_SDK=$(json_int target_sdk)

VERSION=$(cat VERSION | tr -d '[:space:]')
IFS='.' read -r V_MAJOR V_MINOR V_PATCH <<< "$VERSION"
VERSION_CODE=$((V_MAJOR * 10000 + V_MINOR * 100 + V_PATCH))

APK_NAME=$(echo "$APP_NAME" | tr -d ' ' | tr -cd 'A-Za-z0-9')
[ -z "$APK_NAME" ] && APK_NAME="App"

# --- toolchain: Termux defaults, or auto-detected from an Android SDK. Override any via env.
ANDROID_JAR="${ANDROID_JAR:-${HOME}/.android/android.jar}"
FRAMEWORK_RES="${FRAMEWORK_RES:-${HOME}/.android/framework-res.apk}"
KEYSTORE="${KEYSTORE:-${HOME}/.android/debug.keystore}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-androiddebugkey}"
KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
KEY_PASS="${KEY_PASS:-android}"

# Desktop Android SDK: newest build-tools onto PATH, platform android.jar for both jars.
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Android/Sdk}}"
if [ -d "$SDK_ROOT" ]; then
    BT_DIR="$(ls -d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
    [ -n "$BT_DIR" ] && export PATH="${BT_DIR%/}:$PATH"
    PLATFORM_JAR="$SDK_ROOT/platforms/android-$TARGET_SDK/android.jar"
    [ -f "$PLATFORM_JAR" ] || PLATFORM_JAR="$(ls "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
    [ -f "$ANDROID_JAR" ]   || ANDROID_JAR="$PLATFORM_JAR"
    [ -f "$FRAMEWORK_RES" ] || FRAMEWORK_RES="$ANDROID_JAR"
fi

# Java compiler: ecj (Termux), else javac from PATH / JAVA_HOME / Android Studio's JBR.
JAVAC=""
if command -v ecj >/dev/null; then JAVAC=ecj
elif command -v javac >/dev/null; then JAVAC=javac
elif [ -x "${JAVA_HOME:-/nonexistent}/bin/javac" ]; then JAVAC="$JAVA_HOME/bin/javac"
else
    for cand in /snap/android-studio/current/jbr/bin/javac /snap/android-studio/*/jbr/bin/javac \
                /opt/android-studio/jbr/bin/javac \
                "$HOME"/.local/share/JetBrains/Toolbox/apps/android-studio/jbr/bin/javac; do
        [ -x "$cand" ] && { JAVAC="$cand"; break; }
    done
fi
# d8/apksigner exec `java`, so put the chosen JDK on PATH.
case "$JAVAC" in
    */bin/javac) export JAVA_HOME="$(dirname "$(dirname "$JAVAC")")"; export PATH="$(dirname "$JAVAC"):$PATH" ;;
esac

fail() { echo "ERROR: $1"; [ -n "${2:-}" ] && echo "  $2"; exit 1; }
command -v aapt2     >/dev/null || fail "aapt2 not found" "pkg install aapt2, or install Android SDK build-tools"
[ -n "$JAVAC" ]                 || fail "no Java compiler found" "pkg install ecj, or install a JDK / Android Studio"
command -v d8        >/dev/null || fail "d8 not found" "pkg install d8, or install Android SDK build-tools"
command -v apksigner >/dev/null || fail "apksigner not found" "pkg install apksigner, or install Android SDK build-tools"
command -v zip       >/dev/null || fail "zip not found" "pkg install zip"
[ -f "$ANDROID_JAR" ]   || fail "android.jar not found at: $ANDROID_JAR" "run setup.sh (Termux), set ANDROID_JAR=..., or install an SDK platform"
[ -f "$FRAMEWORK_RES" ] || fail "framework-res.apk not found at: $FRAMEWORK_RES" "run setup.sh (Termux) or set FRAMEWORK_RES=..."
[ -f "$KEYSTORE" ]      || fail "keystore not found at: $KEYSTORE"

echo "=== Building $APP_NAME v$VERSION (code $VERSION_CODE) ==="
echo "    package: $PKG"

# --- prepare build dirs ---------------------------------------------------
rm -rf build
mkdir -p build/gen build/classes build/dex tmp

# --- sync manifest --------------------------------------------------------
sed -i "s/package=\"[^\"]*\"/package=\"$PKG\"/" AndroidManifest.xml
sed -i "s/android:versionCode=\"[^\"]*\"/android:versionCode=\"$VERSION_CODE\"/" AndroidManifest.xml
sed -i "s/android:versionName=\"[^\"]*\"/android:versionName=\"$VERSION\"/" AndroidManifest.xml
sed -i "s|android:authorities=\"[^\"]*\.fileprovider\"|android:authorities=\"$PKG.fileprovider\"|" AndroidManifest.xml

# --- generate BuildConfig.java --------------------------------------------
PKG_PATH=$(echo "$PKG" | tr '.' '/')
mkdir -p "build/gen/$PKG_PATH"
cat > "build/gen/$PKG_PATH/BuildConfig.java" <<JAVA
package $PKG;

public final class BuildConfig {
    public static final String VERSION_NAME = "$VERSION";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String PACKAGE_NAME = "$PKG";
    public static final String APP_NAME = "$APP_NAME";
    public static final String REPO = "$(json_str repo)";
    public static final String GITHUB_PROFILE = "$(json_str github_profile)";
}
JAVA

# --- 1. compile resources -------------------------------------------------
echo "[1/5] compiling resources ..."
aapt2 compile --dir res/ -o build/resources.zip

# --- 2. link resources ----------------------------------------------------
echo "[2/5] linking resources ..."
aapt2 link \
    -o build/app_res.apk \
    --manifest AndroidManifest.xml \
    -I "$FRAMEWORK_RES" \
    --java build/gen \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION" \
    build/resources.zip

# --- 3. compile java ------------------------------------------------------
echo "[3/5] compiling java ..."
find src build/gen -name '*.java' > build/sources.txt
CP="$ANDROID_JAR"
if [ -d libs ] && ls libs/*.jar 1>/dev/null 2>&1; then
    for jar in libs/*.jar; do CP="$CP:$jar"; done
fi
if [ "$JAVAC" = ecj ]; then
    ecj -cp "$CP" -d build/classes @build/sources.txt
else
    # Java 8 bytecode; -Xlint:-options mutes the "source 8 obsolete" notice.
    "$JAVAC" -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" -cp "$CP" -d build/classes @build/sources.txt
fi

# --- 4. dex ---------------------------------------------------------------
echo "[4/5] dexing ..."
CLASS_FILES=$(find build/classes -name '*.class')
D8_ARGS="--min-api $MIN_SDK --output build/dex --lib $ANDROID_JAR"
if [ -d libs ] && ls libs/*.jar 1>/dev/null 2>&1; then
    d8 $D8_ARGS $CLASS_FILES libs/*.jar
else
    d8 $D8_ARGS $CLASS_FILES
fi

# --- 5. package & sign ----------------------------------------------------
echo "[5/5] packaging & signing ..."
cp build/app_res.apk build/app_unsigned.apk
(cd build/dex && zip -j ../app_unsigned.apk classes.dex)

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEYSTORE_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "${APK_NAME}.apk" \
    build/app_unsigned.apk

rm -f "${APK_NAME}.apk.idsig"

SIZE=$(du -h "${APK_NAME}.apk" | cut -f1)
echo "=== done: ${APK_NAME}.apk ($SIZE) ==="
