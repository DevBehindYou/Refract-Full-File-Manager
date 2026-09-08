import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion

// Shared Android application baseline. Per architecture/MODULES.md §6, this is the one
// place compileSdk/minSdk/targetSdk are set — an app-level build.gradle.kts should never
// set these directly, so a version bump is a one-line change here.
//
// AGP 9.x note: com.android.application now ships built-in Kotlin support (no separate
// org.jetbrains.kotlin.android plugin needed/applied here) — see PHASE_1_NOTES.md for
// why this differs from pre-2026 Android build setups.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        targetSdk = 36
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// TECH_STACK.md §11 specifies `kotlin { jvmToolchain(17) }` verbatim. Kept here as
// specified; under AGP 9's built-in-Kotlin the exact extension surface for this may
// have shifted (see PHASE_1_NOTES.md) — verify this block resolves as written.
kotlin {
    jvmToolchain(17)
}
