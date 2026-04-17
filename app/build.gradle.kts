plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}


android {
    namespace = "com.islavikfx.spoof"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.islavikfx.spoof"
        minSdk = 28
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        androidResources {
            localeFilters.add("en")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.androidx.activity)
    testImplementation(libs.gsonLibrary)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}