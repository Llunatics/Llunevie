plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.llunaticsys.llunevie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.llunaticsys.llunevie"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.1.3-beta.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/llunevie-release.jks")
            storePassword = "llunevie123"
            keyAlias = "llunevie"
            keyPassword = "llunevie123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.version"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.md"
            excludes += "META-INF/*.MD"
            excludes += "META-INF/com/android/build/gradle/*"
            excludes += "META-INF/version-control-info.textproto"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android & Lifecycle MVVM (StateFlow)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Jetpack Compose UI & Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room Database (Persistent Storage for Books, Chapters, Highlights/Notes, Daily Reading Logs, Shelves)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Jetpack DataStore Preferences (Persistent Storage for Theme, Glass, AMOLED, Language, Reader Settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Jsoup HTML/XML Parser for Real Native EPUB OPF/Manifest/Spine/TOC/Paragraph Parsing
    implementation("org.jsoup:jsoup:1.18.1")

    // Coil Compose for Async Book Covers & Local Extracted Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Palette for Extracting Dominant Spine Colors from Book Covers
    implementation("androidx.palette:palette-ktx:1.0.0")

    // CameraX & Google ML Kit Barcode Scanning & On-Device Text Recognition
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // PDFBox Android for robust, memory-safe PDF Outline, Link Annotations & Text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
