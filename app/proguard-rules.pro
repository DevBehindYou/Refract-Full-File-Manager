# Atomic File Manager R8 rules. Release builds are minified and resource-shrunk (ALL_IN_ONE_PLAN.md 0.5).
# Room, Compose, AndroidX and coroutines ship their own consumer rules; only app-specific needs are here.
# Persistence: Room (operation journal, trash, favourites, recents, search index) plus two SQLiteOpenHelpers.

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Hilt / Dagger generated components ---
-keep class * extends dagger.hilt.internal.GeneratedComponent {}

# --- Domain models (SafeParcelable-style, serialized to SQLite via reflection-free helpers) ---
-keep class com.devbehindyou.atomicfilemanager.domain.model.** { *; }

# --- Hide / Obscure data models serialized to SQLite ---
-keep class com.devbehindyou.atomicfilemanager.data.database.** { *; }

# --- Network backend enums and sealed classes (BackendType, FileResult, FileError) ---
-keep class com.devbehindyou.atomicfilemanager.domain.repository.** { *; }

# --- Enums stored by name (journal states, operation types, settings, hide modes) ---
-keepclassmembers enum com.devbehindyou.atomicfilemanager.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Crash reports stay readable: keep file names and line numbers, rename the source file attribute ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
