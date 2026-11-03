import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Núcleo sem dependência de Android: motor financeiro e contrato da fachada, testável na JVM.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Só a árvore JSON: a saída do modelo é lida com formato estrito, campo a campo (S3 §5, etapa 1).
    implementation(libs.serialization.json)
    api(libs.coroutines.core)

    testImplementation(libs.coroutines.test)
    // Extrator de texto de PDF só dos testes (o do aplicativo é o PdfBox-Android): valida a leitura por layout na JVM.
    testImplementation(libs.pdfbox.jvm)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
