# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Capacitor plugin classes
-keep class com.capacitorjs.** { *; }
-keep class io.capawesome.** { *; }
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.* class * { *; }
-keepclassmembers class * {
    @com.getcapacitor.annotation.* <methods>;
}

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve line numbers for crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile