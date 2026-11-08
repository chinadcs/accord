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

// "true": piloto com interpretador fake (4 frases). "rules": sugestor de categorias com o interpretador por regras (ADR 0036).
val categoryPilotMode = providers.gradleProperty("accord.categoryPilot").orNull
val categoryPilotEnabled = categoryPilotMode == "true" || categoryPilotMode == "rules"
// "true": liga os recursos P2 do S7 (intenções, simulador, pistas) no build de debug. Sem a propriedade a fase é P1 e nenhum controle P2 aparece.
val phaseP2Enabled = providers.gradleProperty("accord.p2").orNull == "true"
val uiValidationIsolated = providers.gradleProperty("accord.uiValidation").orNull == "true"
val pilotAssets = layout.buildDirectory.dir("generated/categoryPilotAssets")
val pilotArtifact = rootProject.layout.projectDirectory.file("poc/results/pc/run-070-category-development-v3/learned/model.json")
val validateCategoryPilotArtifact = tasks.register("validateCategoryPilotArtifact") {
    val artifactFile = pilotArtifact.asFile
    inputs.file(artifactFile)
    doLast {
        val bytes = artifactFile.readBytes()
        check(bytes.size == 7_887_106 && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } ==
            "5d40a47adf7024c672e091eb50ffc4cd81e2c2d89106077614202ed4478fa016") { "Invalid frozen category pilot artifact" }
    }
}
val prepareCategoryPilotAssets = tasks.register<Copy>("prepareCategoryPilotAssets") {
    dependsOn(validateCategoryPilotArtifact)
    from(pilotArtifact) { into("category-pilot") }
    into(pilotAssets)
}

android {
    namespace = "app.accord"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.accord"
        // Android 10: o Galaxy S20 FE (referência mínima) sai de fábrica com ele.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
            buildConfigField("boolean", "CATEGORY_PILOT", categoryPilotEnabled.toString())
            buildConfigField("boolean", "CATEGORY_PILOT_RULES", (categoryPilotMode == "rules").toString())
            buildConfigField("boolean", "PHASE_P2", phaseP2Enabled.toString())
            // Physical UI tests get independent storage; never reinstall/uninstall the user's app.
            if (uiValidationIsolated) applicationIdSuffix = ".uivalidation"
        }
        getByName("release") {
            buildConfigField("String", "SUPABASE_URL", "\"$releaseServiceUrl\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$releaseServiceKey\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("boolean", "CATEGORY_PILOT", "false")
            buildConfigField("boolean", "CATEGORY_PILOT_RULES", "false")
            buildConfigField("boolean", "PHASE_P2", "false")
        }
    }
    if (categoryPilotEnabled) sourceSets.getByName("debug").assets.directories.add(pilotAssets.get().asFile.absolutePath)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }

if (categoryPilotEnabled) tasks.configureEach {
    if (name == "mergeDebugAssets") dependsOn(prepareCategoryPilotAssets)
}

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
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lucide.icons)
    implementation(libs.work.runtime)
    implementation(libs.serialization.json)
    // Texto de PDF no aparelho (S4.6): PdfBox-Android, Apache-2.0, versão fixada.
    implementation(libs.pdfbox.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(project(":categorybench"))

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
