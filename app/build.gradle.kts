plugins {
    id("atomic.android.application")
    alias(libs.plugins.androidJunit5)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.devbehindyou.atomicfilemanager"

    defaultConfig {
        applicationId = "com.devbehindyou.atomicfilemanager"
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val rootDebugKeystore = file("$rootDir/debug.keystore")
        if (rootDebugKeystore.exists()) {
            getByName("debug") {
                storeFile = rootDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // R8 shrinking and obfuscation (ALL_IN_ONE_PLAN.md 0.5, hotspot H3). Keep rules are in
            // proguard-rules.pro; the emulator CI job launches the shrunk APK to catch missing ones.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Atomic design rules warn while screens migrate; they become errors at U8 (ATOMIC_UI_PLAN.md §14.2).
        warning += "NoHardcodedDp"
        abortOnError = true
        checkTestSources = false
        checkDependencies = false
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    // Thumbnails in file lists (ALL_IN_ONE_PLAN.md hotspot H7): images and video frames, memory + disk cache.
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.junrar)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.hilt.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room (ALL_IN_ONE_PLAN.md Phase 0.2): the operation journal first, other tables move over later.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)

    // The one Robolectric smoke test uses JUnit4 + RobolectricTestRunner deliberately
    // (see PHASE_1_NOTES.md) — the vintage engine bridges it into the same JUnit
    // Platform test task as everything else.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

ksp {
    // Exported schemas are committed so every migration can be tested against the real history.
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
}

// AGP's built-in Kotlin does not trigger ktlint 12's legacy Android source-set hook.
// Include production and test Kotlin explicitly in both the check and formatter tasks.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    if (name.endsWith("OverKotlinScripts")) {
        source(fileTree("src") { include("**/*.kt") })
    }
}
