plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

// Voir https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    // 26.1 -> 26.2 a deplace setScreen()/toastManager() de Minecraft vers Gui (verifie par javap sur
    // les deux jars). Meme famille d'API par ailleurs (extractRenderState/GuiGraphicsExtractor deja
    // presents en 26.1). Un simple echange de texte suffit, pas besoin de deux implementations.
    replacements {
        string(current.parsed < "26.2") {
            replace("Minecraft.getInstance().gui.setScreen(parent)", "Minecraft.getInstance().setScreen(parent)")
            replace("client.gui.toastManager()", "client.toastManager")
        }
    }
}
