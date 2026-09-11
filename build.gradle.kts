// Intentionally minimal. Per architecture/MODULES.md §6, shared build configuration
// lives in build-logic/ convention plugins, not in root-project `subprojects {}` blocks
// or `allprojects {}` blocks — those hide configuration from the module that uses it.
// This file exists only so Gradle has a root project.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

