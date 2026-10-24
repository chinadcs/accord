import java.util.Properties

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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
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

    debugImplementation(libs.compose.ui.tooling)

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
