plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val pixnetKeystorePath = System.getenv("PIXNET_KEYSTORE_PATH")
val pixnetKeystorePassword = System.getenv("PIXNET_KEYSTORE_PASSWORD")
val pixnetKeyAlias = System.getenv("PIXNET_KEY_ALIAS")
val pixnetKeyPassword = System.getenv("PIXNET_KEY_PASSWORD")

android {
    namespace = "com.pixnet.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixnet.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.3"
    }

    signingConfigs {
        create("release") {
            if (!pixnetKeystorePath.isNullOrBlank()) {
                storeFile = file(pixnetKeystorePath)
                storePassword = pixnetKeystorePassword
                keyAlias = pixnetKeyAlias
                keyPassword = pixnetKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
