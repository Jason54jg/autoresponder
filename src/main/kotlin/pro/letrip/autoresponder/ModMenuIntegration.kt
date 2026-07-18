package pro.letrip.autoresponder

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/**
 * Point d'entree "modmenu" (fabric.mod.json). N'est charge par le loader que si ModMenu
 * est installe et lit cette entree -> aucune dependance dure sur ModMenu.
 */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> AutoResponderConfigScreen.create(parent) }
}
