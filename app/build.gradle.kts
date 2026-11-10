import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Só a URL do projeto e a chave PÚBLICA (publishable) do serviço entram no aplicativo. A chave secreta
// (service role) nunca: ela existe só no ambiente do responsável pelo piloto. Os valores vêm de
// local.properties (fora do repositório), de propriedade do Gradle ou do ambiente.
fun serviceSetting(name: String, env: String): String {
    val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
    return (local.getProperty(name) ?: providers.gradleProperty(name).orNull ?: System.getenv(env) ?: "")
}

// Release: serviço HOSPEDADO e assinatura próprios, lidos de chaves `accord.release.*` (local.properties ou ambiente
// ACCORD_RELEASE_*), nunca das chaves do debug, que apontam para o Supabase local. Sem elas o release não é gerado.
val releaseServiceUrl = serviceSetting("accord.release.supabase.url", "ACCORD_RELEASE_SUPABASE_URL")
val releaseServiceKey = serviceSetting("accord.release.supabase.publishableKey", "ACCORD_RELEASE_SUPABASE_PUBLISHABLE_KEY")
val releaseStoreFile = serviceSetting("accord.release.storeFile", "ACCORD_RELEASE_STORE_FILE")
val releaseStorePassword = serviceSetting("accord.release.storePassword", "ACCORD_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = serviceSetting("accord.release.keyAlias", "ACCORD_RELEASE_KEY_ALIAS")
val releaseKeyPassword = serviceSetting("accord.release.keyPassword", "ACCORD_RELEASE_KEY_PASSWORD")
val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    val values = listOf(releaseServiceUrl, releaseServiceKey, releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    val url = releaseServiceUrl
    doLast {
        check(values.none { it.isBlank() }) { "Release exige accord.release.supabase.url/publishableKey e storeFile/storePassword/keyAlias/keyPassword (local.properties ou ACCORD_RELEASE_*)." }
        check(url.startsWith("https://")) { "A URL do release deve ser HTTPS (não o Supabase local)." }
    }
}

// O sugestor de categorias (ADR 0032) vai em todas as variantes, com o Chat por regras (ADR 0036).
// "true": só no debug, troca o interpretador do Chat pelo fake de 4 frases ("rules" é o comportamento padrão).
val categoryPilotMode = providers.gradleProperty("accord.categoryPilot").orNull
val categoryFakeInterpreter = categoryPilotMode == "true"
// "true": liga os recursos P2 do S7 (intenções, simulador, pistas) no build de debug. Sem a propriedade a fase é P1 e nenhum controle P2 aparece.
val phaseP2Enabled = providers.gradleProperty("accord.p2").orNull == "true"
val uiValidationIsolated = providers.gradleProperty("accord.uiValidation").orNull == "true"
// Artefato do sugestor, versionado em assets (o original e a prova vivem em poc/, ignorado pelo git).
val suggesterArtifact = layout.projectDirectory.file("src/main/assets/category-suggester/model.json")
val validateCategorySuggesterArtifact = tasks.register("validateCategorySuggesterArtifact") {
    val artifactFile = suggesterArtifact.asFile
    inputs.file(artifactFile)
    doLast {
        val bytes = artifactFile.readBytes()
        check(bytes.size == 7_887_106 && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } ==
            "5d40a47adf7024c672e091eb50ffc4cd81e2c2d89106077614202ed4478fa016") { "Artefato do sugestor de categorias diverge do congelado" }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(validateCategorySuggesterArtifact) }

// Releases usam vMAJOR.MINOR.PATCH; a propriedade permite compilar antes de criar a tag.
// Reservar dois dígitos para minor/patch torna a ordem SemVer crescente no Android.
val appVersionName = providers.gradleProperty("accord.version").orElse("0.1.0").get()
val appVersionParts = Regex("(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)")
    .matchEntire(appVersionName)?.groupValues?.drop(1)?.map(String::toInt)
    ?: error("accord.version deve ser MAJOR.MINOR.PATCH (major 0..9999, minor/patch 0..99).")
val appVersionCode = appVersionParts[0] * 10_000 + appVersionParts[1] * 100 + appVersionParts[2]
check(appVersionCode > 1) { "A versão deve superar o versionCode 1 já instalado no piloto." }

android {
    namespace = "app.accord"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.accord"
        // Android 10: o Galaxy S20 FE (referência mínima) sai de fábrica com ele.
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "app.accord.AccordTestRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${serviceSetting("accord.supabase.url", "ACCORD_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${serviceSetting("accord.supabase.publishableKey", "ACCORD_SUPABASE_PUBLISHABLE_KEY")}\"")
    }

    signingConfigs {
        if (releaseStoreFile.isNotBlank()) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "CATEGORY_PILOT", categoryFakeInterpreter.toString())
            buildConfigField("boolean", "PHASE_P2", phaseP2Enabled.toString())
            // Physical UI tests get independent storage; never reinstall/uninstall the user's app.
            if (uiValidationIsolated) applicationIdSuffix = ".uivalidation"
        }
        getByName("release") {
            buildConfigField("String", "SUPABASE_URL", "\"$releaseServiceUrl\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$releaseServiceKey\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("boolean", "CATEGORY_PILOT", "false")
            buildConfigField("boolean", "PHASE_P2", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":remote"))
    implementation(libs.sqlite.bundled)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.charty)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lucide.icons)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime)
    implementation(libs.serialization.json)
    // Texto de PDF no aparelho (S4.6): PdfBox-Android, Apache-2.0, versão fixada.
    implementation(libs.pdfbox.android)

    debugImplementation(libs.compose.ui.tooling)
    // Sugestor de categorias (ADR 0032): classificador local e guarda, no debug e no release.
    implementation(project(":categorybench"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.core)
}
