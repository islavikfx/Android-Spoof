pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}


@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.islavikfx")
                includeGroup("com.github.topjohnwu")
                includeGroup("com.github.topjohnwu.libsu")
                includeModule("com.github.topjohnwu.libsu", "core")
                includeModule("com.github.topjohnwu.libsu", "service")
                includeModule("com.github.topjohnwu.libsu", "nio")
            }
        }
    }
}


rootProject.name = "Android Spoof"
include(":app")