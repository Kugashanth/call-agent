plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "lk.kathir.callagent"
    compileSdk = 34

    defaultConfig {
        applicationId = "lk.kathir.callagent"
        minSdk = 28          // TelecomManager.endCall() needs API 28 (Android 9)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// No extra libraries: only Android framework APIs + Kotlin stdlib.
