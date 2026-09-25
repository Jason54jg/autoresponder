plugins {
    // Applique automatiquement la bonne variante de Fabric Loom selon la version MC du noeud actif.
    id("dev.kikugie.loom-back-compat")
    kotlin("jvm") version "2.4.0"
}

// group vient de stonecutter (mod.group), NE PAS le fixer ici.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName.set(property("mod.id") as String)

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mappings Mojang officielles (deja deobfusque pour 26.x, sinon applique le mapping obfuscation->named).
    loomx.applyMojangMappings()

    // modXxx fonctionne meme sur 26.1+ : loom-back-compat convertit vers l'equivalent moderne.
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_kotlin")}")

    // ModMenu : optionnel, compileOnly seulement -> pas requis au runtime pour qui ne l'a pas.
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")
}

loom {
    runConfigs.all {
        runDirectory = rootProject.file("run") // Un seul dossier run/ partage entre versions.
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

kotlin {
    jvmToolchain(requiredJava.majorVersion.toInt())
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Compile le jar de cette version et le copie dans build/libs/<version mod>/"

    inputs.property("version", project.property("mod.version"))
    // loomx.mod(Sources)Jar renvoie la tache jar de la variante loom appliquee pour ce noeud.
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${property("mod.version")}"))
}
