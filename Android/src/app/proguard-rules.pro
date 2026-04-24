# ============================================================================
# OlliteRT ProGuard / R8 Rules
# ============================================================================

# Open-source project — keep class/method names readable in stack traces and APK.
# R8 still removes unused code (tree-shaking) and optimizes, just skips renaming.
-dontobfuscate

# --- kotlinx.serialization ---
# Keep serialization-related metadata and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ollitert.llm.server.**$$serializer { *; }
-keepclassmembers class com.ollitert.llm.server.** {
    *** Companion;
}
-keepclasseswithmembers class com.ollitert.llm.server.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Kotlin Reflect ---
# Used for runtime type inspection
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# --- NanoHTTPD ---
# HTTP server — must keep the serve() entry point and response classes
-keep class org.nanohttpd.** { *; }

# --- Protobuf Lite ---
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# --- Hilt / Dagger ---
# Hilt generates components at compile time; R8 handles most of it,
# but keep the entry points
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- LiteRT LM ---
# Keep the LiteRT LM SDK classes (native bridge)
-keep class com.google.ai.edge.litertlm.** { *; }

# --- AppAuth (OpenID) ---
-keep class net.openid.appauth.** { *; }

# --- Compose ---
# Compose compiler handles most optimizations; keep runtime stability annotations
-dontwarn androidx.compose.**

# --- General Android ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep BuildConfig fields accessible at runtime
-keep class com.ollitert.llm.server.BuildConfig { *; }
