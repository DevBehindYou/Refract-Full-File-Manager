// A macrobenchmark module, deliberately NOT using the atomic.android.application
// convention plugin — see settings.gradle.kts and PHASE_1_NOTES.md's addendum for why.
// This is the one module in the project not built on that shared baseline.
plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.devbehindyou.atomicfilemanager.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Macrobenchmarks run against :app's "benchmark" build type: release-like (R8, not
    // debuggable) but signed with the debug key so it installs next to the test APK.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(17)
}

// Only the benchmark variant is useful; skip building debug/release test APKs.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
