# Refract File Manager ProGuard / R8 Rules
# Keep data models serialized / Room entities
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Hilt / Dagger
-keep class * extends dagger.hilt.internal.GeneratedComponent {}

# Keep FileNode and domain models
-keep class com.devbehindyou.refract.domain.model.** { *; }

# Coil image loader
-keepclassmembers class coil3.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
