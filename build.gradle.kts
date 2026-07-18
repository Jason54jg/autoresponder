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
    modImplementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")

    // ModMenu : optionnel. compileOnly pour compiler contre l'API, localRuntime pour le tester
    // avec `gradlew.bat runClient` ; absent de la liste "depends" du fabric.mod.json donc le mod
    // charge normalement sans ModMenu installe (l'entrypoint "modmenu" n'est alors jamais lu).
    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    modLocalRuntime("com.terraformersmc:modmenu:${property("modmenu_version")}")
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
