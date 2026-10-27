buildscript {
    repositories { google(); mavenCentral() }
    // Kotlin 2.4 metadata requires R8 9.1.29 or newer.
    dependencies { classpath("com.android.tools:r8:9.1.57") }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
