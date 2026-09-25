pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // Multi-version : genere un sous-projet par entree de versions()/version() ci-dessous.
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Bascule automatiquement la bonne variante de Fabric Loom selon la version MC du sous-projet
    // (resout le bug loom "disableObfuscation force a true" rencontre en appliquant loom nous-memes).
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "autoresponder"
