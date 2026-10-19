plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; this plugin also pins the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}
