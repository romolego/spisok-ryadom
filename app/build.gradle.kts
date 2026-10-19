plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.spisokryadom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spisokryadom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.17.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions { annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" } }
    }

    flavorDimensions += "installation"
    productFlavors {
        create("standard") { dimension = "installation" }
        create("parallel") { dimension = "installation"; applicationIdSuffix = ".next" }
    }
    signingConfigs { create("localRelease") {
        storeFile = rootProject.file(".local/signing/local-development.jks")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    } }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if(rootProject.file(".local/signing/local-development.jks").exists())"localRelease" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
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

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
}

// Native Robolectric SQLite handles must not outlive a sandbox on Windows.
// A fresh worker for each class avoids cross-sandbox native finalizer crashes.
tasks.withType<Test>().configureEach {
    forkEvery = 1
    maxParallelForks = 1
    maxHeapSize = "768m"
}
