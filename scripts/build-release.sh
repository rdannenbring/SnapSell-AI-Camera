#!/bin/bash
# SnapSell Production Release Build
# Builds an optimized, signed release APK

set -e
cd "$(dirname "$0")/.."

echo "🔧 Building production web assets..."
npx vite build

echo "📱 Syncing with Android..."
npx cap sync android

echo "🏗️ Building signed release APK..."
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
ANDROID_HOME=$HOME/Android/Sdk \
./gradlew assembleRelease

cd ..

APK="android/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo ""
    echo "✅ Release APK built successfully!"
    echo "📦 Location: $APK"
    echo "📊 Size: $SIZE"
    echo ""
    echo "To install on device:"
    echo "  ~/Android/Sdk/platform-tools/adb install -r $APK"
else
    echo "❌ Build failed - APK not found"
    exit 1
fi