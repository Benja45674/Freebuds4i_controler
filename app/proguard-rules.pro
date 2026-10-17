# Project specific Proguard rules.
# These rules tell R8 which parts of your code to keep during code shrinking/obfuscation.

# Keep line numbers and source file names for cleaner release stack traces
-keepattributes SourceFile,LineNumberTable

# Kotlin Coroutines optimization rules
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.AndroidDispatcherFactory {
    public <init>();
}

# Keep the domain and protocol classes from being renamed, to avoid issues
# and ensure clean debugging/logs of raw bytes and models
-keep class com.example.audifonos.data.protocol.** { *; }
-keep class com.example.audifonos.domain.model.** { *; }
