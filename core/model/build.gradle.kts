plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Módulo Kotlin/JVM puro: não pode depender de Android nem de Room (README §3).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
