buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 内置 Kotlin 支持（不要再应用 org.jetbrains.kotlin.android）。
        // 这里通过 buildscript classpath 把内置的 KGP 提升到与 Compose 插件一致的 2.3.21。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
