plugins {
    id("refract.android.application")
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
            isMinifyEnabled = false
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
        disable += "NoHardcodedDp"
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

    implementation(libs.hilt.android)

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
