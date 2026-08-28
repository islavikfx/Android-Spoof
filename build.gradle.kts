plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
}


ext {
    set("compileSdk", 37)
    set("minSdk", 27)
}


tasks.register("clean", Delete::class) {
    description = ""
    delete(rootProject.layout.buildDirectory)
}