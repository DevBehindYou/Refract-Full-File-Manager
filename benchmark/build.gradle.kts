// A macrobenchmark module, deliberately NOT using the refract.android.application
// convention plugin — see settings.gradle.kts and PHASE_1_NOTES.md's addendum for why.
// This is the one module in the project not built on that shared baseline.
plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.devbehindyou.refract.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Mirrors :app's flavours exactly so Gradle can match variants between this module
    // and its target (androidx.benchmark's variant-matching needs this on both sides).
    flavorDimensions += "distribution"
    productFlavors {
        create("base") { dimension = "distribution" }
        create("reporting") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The androidx.benchmark plugin (applied above) adds a "benchmark" build type
    // derived from release automatically — non-debuggable, non-minified-by-default,
    // matching real-world startup conditions. See PHASE_1_NOTES.md for what this
    // scaffold does and doesn't verify.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
