package pro.letrip.autoresponder

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
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
            // "ar"/"autoresponder", pas "cg"/"chatgame" : ces deux-la sont les commandes du
            // PLUGIN SERVEUR (voir commandTemplate, ex. "/cg valid ..."). Si notre mod les
            // enregistrait aussi cote client, taper "/cg valid ..." serait intercepte par notre
            // propre arbre de commandes (aucun sous-commande "valid" chez nous) et rejete avant
            // meme d'atteindre le serveur.
            dispatcher.register(buildCommand("autoresponder"))
            dispatcher.register(buildCommand("ar"))
        }
    }

    /** Prefixe exact qui declenche le flux de validation "Chat Games". */
    private const val CHAT_GAMES_PREFIX = "Chat Games »"

    // GAME et CHAT sont tous deux enregistres (certains serveurs routent les broadcasts sur l'un
    // ou l'autre selon le contexte) ; si le meme texte arrive deux fois quasi simultanement, on
    // ignore le doublon pour ne pas soumettre 2x la commande /cg valid (qui a des effets de bord
    // cote serveur, ex. teams de scoreboard, et peut le desynchroniser -> crash client).
    private var lastMessageText: String? = null
    private var lastMessageAtMs: Long = 0

    private fun onMessage(message: Component) {
        if (!Config.enabled) return

        val text = message.string

        val now = System.currentTimeMillis()
        if (text == lastMessageText && now - lastMessageAtMs < 200) return
        lastMessageText = text
        lastMessageAtMs = now

        // Messages "Chat Games »" : geres exclusivement par le flux de validation
        // (notice ephemere + commande /cg valid ou /chatgame valid apres delai).
        // Pas de fallback vers les autres solveurs pour eviter une reponse publique instantanee.
        if (text.startsWith(CHAT_GAMES_PREFIX)) {
            val response = Config.validationHandler.handle(text)
            if (response == null) {
                // Aucun trigger connu : notice locale pour ne pas rater le round en silence.
                Minecraft.getInstance().player?.sendSystemMessage(
                    Component.literal("§7[AutoResponder] §caucune reponse connue pour ce Chat Games, reponds toi-meme !")
                )
                return
            }
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
            .then(ClientCommands.literal("config").executes {
                try {
                    // parent = null : evite de lire Minecraft.screen (nom/presence du champ a
                    // varie entre versions 26.x et a deja cause un NoSuchFieldError au runtime).
                    // YACL accepte un parent null (retour au jeu au lieu de l'ecran precedent).
                    // setScreenAndShow (pas setScreen, renomme en 26.2) : voir Minecraft.class.
                    Minecraft.getInstance().setScreenAndShow(AutoResponderConfigScreen.create(null))
                } catch (e: Throwable) {
                    feedback(it.source, "§cimpossible d'ouvrir l'ecran de config: ${e}")
                    e.printStackTrace()
                }
                1
            })
            // Une seule commande "add", le kind choisit le mecanisme de detection (meme fichier
            // autoresponder_questions.json pour les trois, distingues par le champ kind).
            .then(ClientCommands.literal("add")
                .then(ClientCommands.literal("unscramble")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes { ctx -> addEntry(ctx, QuestionKind.UNSCRAMBLE, 800, 1400) }))
                .then(ClientCommands.literal("question")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes { ctx -> addEntry(ctx, QuestionKind.QUESTION, 800, 1400) }))
                .then(ClientCommands.literal("chatgames")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes { ctx -> addEntry(ctx, QuestionKind.CHATGAMES, 1000, 2000) })))

    /**
     * Logique partagee des 3 sous-commandes de /ar add. Parse "trigger | reponse | [mindelay] [maxdelay]"
     * (delais optionnels, "|" ou espace, defauts par kind) et persiste dans autoresponder_questions.json.
     */
    private fun addEntry(
        ctx: CommandContext<FabricClientCommandSource>,
        kind: QuestionKind,
        defaultMin: Long,
        defaultMax: Long
    ): Int {
        val parts = StringArgumentType.getString(ctx, "text").split("|")
        val trigger = parts.getOrNull(0)?.trim().orEmpty()
        val response = parts.getOrNull(1)?.trim().orEmpty()
        if (trigger.isEmpty() || response.isEmpty()) {
            feedback(ctx.source, "§cformat: /ar add <unscramble|question|chatgames> <trigger> | <reponse> | [mindelay] [maxdelay]")
            return 1
        }
        val remainder = parts.drop(2).joinToString(" ")
        val numbers = Regex("""\d+""").findAll(remainder).map { it.value.toLong() }.toList()
        val mindelay = numbers.getOrNull(0) ?: defaultMin
        val maxdelay = numbers.getOrNull(1) ?: defaultMax
        Config.addEntry(trigger, response, mindelay, maxdelay, kind)
        feedback(ctx.source, "§aappris (${kind.name.lowercase()}): §f$trigger §7-> §f$response §7($mindelay-$maxdelay ms)")
        return 1
    }

    private fun flip(source: FabricClientCommandSource) = set(source, !Config.enabled)

    private fun set(source: FabricClientCommandSource, value: Boolean) {
        Config.setEnabled(value)
        feedback(source, if (value) "§aactive" else "§cdesactive")
    }

    private fun feedback(source: FabricClientCommandSource, msg: String) {
        source.sendFeedback(Component.literal("§7[AutoResponder] §f$msg"))
    }
}
