package pro.letrip.autoresponder

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object AutoResponder : ClientModInitializer {

    private lateinit var toggleKey: KeyMapping

    override fun onInitializeClient() {
        Config.load()

        toggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.autoresponder.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
            )
        )

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) onMessage(message)
        }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            onMessage(message)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (toggleKey.consumeClick()) toggle(client)
            MessageScheduler.tick(client)
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(buildCommand("chatgame"))
            dispatcher.register(buildCommand("cg"))
        }
    }

    /** Prefixe exact qui declenche le flux de validation "Chat Games". */
    private const val CHAT_GAMES_PREFIX = "Chat Games »"

    private fun onMessage(message: Component) {
        if (!Config.enabled) return

        val text = message.string

        // Messages "Chat Games »" : geres exclusivement par le flux de validation
        // (notice ephemere + commande /cg valid ou /chatgame valid apres delai).
        // Pas de fallback vers les autres solveurs pour eviter une reponse publique instantanee.
        if (text.startsWith(CHAT_GAMES_PREFIX)) {
            val response = Config.validationHandler.handle(text) ?: return
            MessageScheduler.submit(response)
            return
        }

        for (handler in Config.handlers) {
            val response = handler.handle(text) ?: continue
            MessageScheduler.submit(response)
            return
        }
    }

    private fun toggle(client: Minecraft) {
        Config.setEnabled(!Config.enabled)
        val state = if (Config.enabled) "§aON" else "§cOFF"
        client.player?.sendSystemMessage(Component.literal("§7[AutoResponder] §fetat: $state"))
    }

    private fun buildCommand(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(name)
            .executes { flip(it.source); 1 }
            .then(ClientCommands.literal("toggle").executes { flip(it.source); 1 })
            .then(ClientCommands.literal("on").executes { set(it.source, true); 1 })
            .then(ClientCommands.literal("off").executes { set(it.source, false); 1 })
            .then(ClientCommands.literal("reload").executes {
                Config.load()
                MessageScheduler.clear()
                feedback(it.source, "§econfig rechargee")
                1
            })
            .then(ClientCommands.literal("add")
                .then(ClientCommands.argument("scrambled", StringArgumentType.word())
                    .then(ClientCommands.argument("answer", StringArgumentType.word())
                        .executes {
                            val scrambled = StringArgumentType.getString(it, "scrambled")
                            val answer = StringArgumentType.getString(it, "answer")
                            Config.addUnscramble(scrambled, answer)
                            feedback(it.source, "§aappris: §f$scrambled §7-> §f$answer")
                            1
                        })))
            .then(ClientCommands.literal("addquestion")
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val parts = StringArgumentType.getString(ctx, "text").split("|", limit = 2)
                        if (parts.size < 2) {
                            feedback(ctx.source, "§cformat: /cg addquestion <question> | <reponse>")
                        } else {
                            val question = parts[0].trim()
                            val answer = parts[1].trim()
                            Config.addQuestion(question, answer)
                            feedback(ctx.source, "§aappris Q: §f$question §7-> §f$answer")
                        }
                        1
                    }))

    private fun flip(source: FabricClientCommandSource) = set(source, !Config.enabled)

    private fun set(source: FabricClientCommandSource, value: Boolean) {
        Config.setEnabled(value)
        feedback(source, if (value) "§aactive" else "§cdesactive")
    }

    private fun feedback(source: FabricClientCommandSource, msg: String) {
        source.sendFeedback(Component.literal("§7[AutoResponder] §f$msg"))
    }
}
