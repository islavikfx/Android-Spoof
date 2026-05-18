plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}


ext {
    set("compileSdk", 37)
    set("minSdk", 29)
}


tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}