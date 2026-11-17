plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.zikr.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.zikr.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Har safar bir xil imzo bo'lishi uchun (yangilanish ustiga o'rnatilsin)
    signingConfigs {
        create("shared") {
            storeFile = file("zikr.keystore")
            storePassword = "zikr123"
            keyAlias = "zikr"
            keyPassword = "zikr123"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Ataylab hech qanday tashqi kutubxona yo'q: faqat Android'ning o'zi.
dependencies {
}
