# ProGuard rules for PrivteLLM

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.privtellm.nativebridge.** { *; }

# Keep Ktor engine and serialization
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep OpenAI data transfer models
-keep class com.privtellm.server.** { *; }
