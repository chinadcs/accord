import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Classificador do sugestor de categorias: usado pelas bancadas e pelo app (debug e release, ADR 0032 — release aprovado em 09/11/2026).
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17); allWarningsAsErrors.set(true) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    api(project(":core"))
    implementation(libs.serialization.json)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
