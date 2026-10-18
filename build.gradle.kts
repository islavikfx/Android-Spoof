plugins {
    id("com.android.application") version "9.3.3" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}


ext {
    set("compileSdk", 37)
    set("minSdk", 27)
}


tasks.register("clean", Delete::class) {
    description = buildString { }
    delete(rootProject.layout.buildDirectory)
}
