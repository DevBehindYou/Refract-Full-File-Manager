plugins {
    id("refract.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidJunit5)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.devbehindyou.refract"

    defaultConfig {
        applicationId = "com.devbehindyou.refract"
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    // Two distribution flavours, no separate application ID — they are alternate
    // builds of the same app for different release channels, not meant to coexist
    // side-by-side on one device (see PRIVACY.md §4, roadmap Phase 1).
    flavorDimensions += "distribution"
    productFlavors {
        create("base") {
            dimension = "distribution"
            // No source-set override here on purpose: the absence of a manifest that
            // declares INTERNET *is* the "no network access" guarantee for this
            // flavour — there is nothing to structurally add.
        }
        create("reporting") {
            dimension = "distribution"
            // INTERNET is added only by src/reporting/AndroidManifest.xml. No crash
            // reporter is wired yet in Phase 1 — this flavour exists so the manifest
            // split is correct from day one (roadmap Phase 1 deliverables).
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

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

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
}
