import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The beta module is Kotlin-only; AGP still creates an empty javac task.
// JDK 11 on this managed Windows image fails while closing its empty archive.
tasks.withType<JavaCompile>().configureEach { enabled = false }

android {
    namespace = "com.baptiste.dicta.beta"
    compileSdk = 33
    buildToolsVersion = "30.0.3"

    defaultConfig {
        applicationId = "com.baptiste.dicta.beta"
        minSdk = 26
        targetSdk = 33
        versionCode = 7
        versionName = "0.1.0-beta.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("beta") {
            dimension = "channel"
            buildConfigField("String", "RELEASE_CHANNEL", "\"beta\"")
        }
    }

    signingConfigs {
        create("betaDebugLocal") {
            storeFile = rootProject.file("beta-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes.getByName("debug") {
        signingConfig = signingConfigs.getByName("betaDebugLocal")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.4.8" }
    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "**/*.proto"
    }
}

// Compose 1.5 pulls emoji2 1.4, whose metadata requires API 34. The beta
// toolchain intentionally compiles with API 33, so keep the compatible line.
configurations.all {
    resolutionStrategy.force("androidx.emoji2:emoji2:1.3.0")
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.camera:camera-camera2:1.2.3")
    implementation("androidx.camera:camera-lifecycle:1.2.3")
    implementation("androidx.camera:camera-view:1.2.3")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
