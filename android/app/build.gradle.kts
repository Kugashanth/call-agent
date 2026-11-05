plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from environment variables (GitHub Actions secrets) — never commit a keystore.
val ksPath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "lk.kathir.callagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "lk.kathir.callagent"
        minSdk = 28          // TelecomManager.endCall() needs API 28 (Android 9)
        targetSdk = 36       // Google Play requirement for new apps from 31 Aug 2026
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    flavorDimensions += "dist"
    productFlavors {
        // Google Play: dashboard only (calls answered in the cloud). No accessibility / mic / call permissions.
        create("play") {
            dimension = "dist"
        }
        // GitHub Releases only: phone answers calls itself via an accessibility service. NOT allowed on Play.
        create("sideload") {
            dimension = "dist"
            applicationIdSuffix = ".sideload"
            versionNameSuffix = "-sideload"
        }
    }

    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
// No extra libraries: only Android framework APIs + Kotlin stdlib.
