plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.snapsell.nativecamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.snapsell.nativecamera"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Read .env file for dev API key
    val envFile = file("../.env")
    val geminiApiKey = if (envFile.exists()) {
        envFile.readLines()
            .find { it.startsWith("GEMINI_API_KEY=") }
            ?.substringAfter("GEMINI_API_KEY=")
            ?.trim()
            ?.removeSurrounding("\"") ?: ""
    } else ""

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"$geminiApiKey\"")
        }
        release {
            // No default key in release builds — users must provide their own
            buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Networking (Gemini API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // EXIF for rotation handling
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Native image cropper
    implementation("com.github.yalantis:ucrop:2.2.8")

    // AppCompat + Fragment — required to host UCropFragment inline
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}

kapt {
    correctErrorTypes = true
}