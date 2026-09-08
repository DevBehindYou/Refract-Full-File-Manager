pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "refract"

// Convention plugins (compileSdk/minSdk/targetSdk/Compose baseline). See
// architecture/MODULES.md §6 — "Convention plugins live in build-logic/ from day one,
// even with one module." :app is the only consumer today — :benchmark deliberately does
// NOT use this convention plugin; it applies com.android.test directly, since it's a
// different plugin type with different (and less certain) built-in-Kotlin support. See
// PHASE_1_NOTES.md's addendum on the :benchmark module for the reasoning.
includeBuild("build-logic")

include(":app")
include(":lint-rules")
include(":benchmark")
