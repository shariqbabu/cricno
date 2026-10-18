# Keep JavaScript Interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Models for Gson serialization
-keep class com.livestream.app.data.model.** { *; }

# Keep AndroidX Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep OkHttp & Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
