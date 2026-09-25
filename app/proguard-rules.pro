# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class com.xzo.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.xzo.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# Room
-keep class androidx.room.** { *; }
# Compose
-dontwarn androidx.compose.**
