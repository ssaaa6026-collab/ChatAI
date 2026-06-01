# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.chat.ai.data.api.** { *; }

# Room
-keep class com.chat.ai.data.model.** { *; }
-keep class com.chat.ai.data.db.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
