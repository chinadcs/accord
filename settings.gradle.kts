rootProject.name = "accord"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":core")
include(":persistence")
include(":remote")
include(":app")
include(":categorybench")

// Bancadas opcionais mantidas localmente; um clone do produto compila sem esses acervos.
listOf("poc", "pocpc").forEach { bench ->
    if (file("$bench/build.gradle.kts").isFile) include(":$bench")
}
