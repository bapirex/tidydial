plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Release signing comes only from the environment (the release workflow, or a maintainer's
// shell). Without all four variables the release APK/AAB are left unsigned, which is what
// F-Droid's build server needs: it builds from source without secrets and compares the result
// with the signed APK from the GitHub Release.
private val releaseSigningEnv: Map<String, String>? = listOf(
    "TIDYDIAL_KEYSTORE",
    "TIDYDIAL_KEYSTORE_PASSWORD",
    "TIDYDIAL_KEY_ALIAS",
    "TIDYDIAL_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull.orEmpty() }
    .takeIf { env -> env.values.all { it.isNotEmpty() } }

android {
    namespace = "net.evopoint.tidydial"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.evopoint.tidydial"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (releaseSigningEnv != null) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("TIDYDIAL_KEYSTORE"))
                storePassword = releaseSigningEnv.getValue("TIDYDIAL_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("TIDYDIAL_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("TIDYDIAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningEnv != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // META-INF/version-control-info.textproto depends on the checkout (git or not,
            // which commit), so it would break reproducible-build verification.
            vcsInfo { include = false }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Keep the Google-encrypted dependency metadata blob out of the APK/AAB (F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's API 36+ sandbox reflects into FileDescriptor internals.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

kotlin {
    jvmToolchain(21)
}

// Reproducible builds: the ART baseline profile (assets/dexopt/baseline.prof and .profm) is
// not byte-for-byte stable between builds, so F-Droid's verification of the release APK would
// fail. Leave it out of release builds (R8 then also stops using it to lay out classes.dex);
// debug builds keep it. A tree that built release before this needs `./gradlew clean` once,
// because disabled tasks leave their old outputs behind.
tasks.configureEach {
    if (name.contains("ArtProfile") && name.contains("Release")) {
        enabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libphonenumber)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
