pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "FutbolAnalisis"

// El módulo de análisis es Kotlin puro: compila y se prueba sin el SDK de Android.
include(":core")

// La app Android solo se incluye cuando hay un SDK de Android disponible
// (Android Studio crea local.properties; CI define ANDROID_HOME).
// Con -PcoreOnly=true se fuerza a omitirla.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"
if (hasAndroidSdk && !coreOnly) {
    include(":app")
}
