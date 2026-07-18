plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    kotlin("jvm") version "2.4.0"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.isxander.dev/releases") { name = "isxander" }
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
    // 26.x est dés-obfusque : aucune ligne `mappings`, le jar est deja en noms Mojang.
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Menu de config : requis (l'ecran /cg config et le menu ModMenu en dependent directement).
    // Ce setup Loom saute le remapping (26.x deja en noms Mojang) donc pas de configurations
    // "mod*" (elles servent a marquer ce qui doit etre remappe) : implementation classique,
    // comme fabric-api/fabric-language-kotlin ci-dessus.
    implementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")

    // ModMenu : optionnel, compileOnly seulement -> pas requis au runtime pour les joueurs qui
    // n'ont pas ModMenu (l'entrypoint "modmenu" du fabric.mod.json n'est alors jamais lu).
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}
