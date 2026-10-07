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

# --- Commons Compress (ALL_IN_ONE_PLAN.md 2.2): optional codecs the app doesn't ship ---
# Zstandard, Brotli, LZ4-via-aircompressor, Pack200's ASM and OSGi are optional dependencies; only the
# formats the app opens (TAR, GZ, BZ2, XZ) are used, so the missing classes are never reached.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn io.airlift.compress.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**

# --- Tink (ALL_IN_ONE_PLAN.md 2.5): compile-only annotations it references ---
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# --- junrar (ALL_IN_ONE_PLAN.md 2.2): logs through slf4j, which has no binding in the app ---
-dontwarn org.slf4j.**

# --- sshj and BouncyCastle (ALL_IN_ONE_PLAN.md 3.1) ---
# BouncyCastle registers algorithms by class name and sshj builds its factories reflectively,
# so both are kept whole. sshj logs through slf4j (already -dontwarn above); the rest are optional.
-keep class org.bouncycastle.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn javax.naming.**
-dontwarn sun.security.x509.**
# GSSAPI (Kerberos) auth is never offered; Android has no org.ietf.jgss or JAAS login.
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.login.**
