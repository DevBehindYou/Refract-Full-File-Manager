# Refract File Manager ProGuard / R8 Rules
# NOTE: Release build uses isMinifyEnabled = false (Phase 1 constraint).
# These rules are pre-populated for when minification is enabled.
# Current persistence layer: SQLiteOpenHelper (NOT Room). No @Entity/@Dao annotations exist.

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Hilt / Dagger generated components ---
-keep class * extends dagger.hilt.internal.GeneratedComponent {}

# --- Domain models (SafeParcelable-style, serialized to SQLite via reflection-free helpers) ---
-keep class com.devbehindyou.refract.domain.model.** { *; }

# --- Hide / Obscure data models serialized to SQLite ---
-keep class com.devbehindyou.refract.data.database.** { *; }

# --- Network backend enums and sealed classes (BackendType, FileResult, FileError) ---
-keep class com.devbehindyou.refract.domain.repository.** { *; }

# NOTE: When Coil or Media3 dependencies are added to build.gradle.kts, uncomment:
# -keepclassmembers class coil3.** { *; }
# -keep class androidx.media3.** { *; }
