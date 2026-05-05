#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
APK_PATH="native-android/app/build/outputs/apk/debug/app-debug.apk"

echo "🧹 Stopping stale Gradle daemons..."
(cd native-android && JAVA_HOME="$JAVA_HOME" ./gradlew --stop 2>/dev/null || true)

echo "🏗️ Building native Android APK..."
(cd native-android && JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" ./gradlew --no-daemon assembleDebug)

echo "📱 Installing APK via adb..."
"$ANDROID_HOME/platform-tools/adb" push "$APK_PATH" /data/local/tmp/app.apk
"$ANDROID_HOME/platform-tools/adb" shell pm install -r -t /data/local/tmp/app.apk

SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "✅ Native deploy complete! ($SIZE)"echo "🕒 $(date)"
