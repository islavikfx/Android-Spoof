plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}


android {
    namespace = "com.islavikfx.spoof"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        //noinspection EditedTargetSdkVersion
        applicationId = "com.islavikfx.spoof"
        minSdk = 27
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0"
        androidResources {
            @Suppress("UnstableApiUsage")
            this.localeFilters.add("en")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        sourceSets {
            packaging {
            resources { excludes += "**/*.nim" }
        }
    }
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-custom-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation(dependencyNotation = "com.github.topjohnwu.libsu:core:6.0.0")
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}