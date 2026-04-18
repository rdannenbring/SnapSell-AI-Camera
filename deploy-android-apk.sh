#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"

echo "🔨 Building web app..."
npm run build

echo "📦 Syncing to Android..."
npx cap sync android

echo "🤖 Compiling debug APK..."
cd android && JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" ./gradlew assembleDebug && cd ..

echo "📱 Installing APK via adb..."
"$ANDROID_HOME/platform-tools/adb" install -r "$APK_PATH"

echo "✅ Deploy complete!"