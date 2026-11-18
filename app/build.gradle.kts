plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.limpacache.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.limpacache.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Chave fixa: assim cada APK novo atualiza por cima do anterior
    signingConfigs {
        create("fixa") {
            storeFile = file("limpacache.keystore")
            storePassword = "limpacache"
            keyAlias = "limpacache"
            keyPassword = "limpacache"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixa")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
