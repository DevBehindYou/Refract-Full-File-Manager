plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // These are `implementation`, not `compileOnly` — the precompiled script plugins in
    // src/main/kotlin/ apply these plugins by id() at *runtime* (from this module's own
    // perspective), so their implementations must be on this module's compile classpath,
    // not just their API surface.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.composeCompilerGradlePlugin)
}
