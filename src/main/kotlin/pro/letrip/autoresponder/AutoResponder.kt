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
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object AutoResponder : ClientModInitializer {

    private val logger = LoggerFactory.getLogger("AutoResponder")

    private lateinit var toggleKey: KeyMapping

    override fun onInitializeClient() {
        Config.load()
        Notifications.registerHud()

        toggleKey = KeyMappingHelper.registerKeyMapping(
            // Pas de GLFW.*/Type.KEYSYM : 26.3 est passe a SDL (KEYSYM -> KEYBOARD, LWJGL glfw retire).
            // Ctor a 3 args (type clavier par defaut) + InputConstants.UNKNOWN valent sur 26.1 -> 26.3.
            KeyMapping(
                "key.autoresponder.toggle",
                InputConstants.UNKNOWN.value,
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

    /**
     * Prefixe qui declenche le flux de validation "Chat Games". Le separateur "»" apres
     * "Chat Games" varie selon le serveur/resource pack (glyph Unicode custom, pas forcement
     * U+00BB) : on ne matche que sur "Chat Games" pour eviter un decalage d'encodage silencieux.
     */
    private const val CHAT_GAMES_PREFIX = "Chat Games"

    /**
     * "X answered Y in Z.ZZZs!" : annonce de fin de round (quelqu'un a deja repondu), pas une
     * question -> on l'ignore silencieusement, pas de notice "aucune reponse connue".
     */
    private val ROUND_RESULT_PATTERN = Regex("""\banswered\b.*\d+(?:\.\d+)?s!?\s*$""", RegexOption.IGNORE_CASE)

    // GAME et CHAT sont tous deux enregistres (certains serveurs routent les broadcasts sur l'un
    // ou l'autre selon le contexte) ; si le meme texte arrive deux fois quasi simultanement, on
    // ignore le doublon pour ne pas soumettre 2x la commande /cg valid (qui a des effets de bord
    // cote serveur, ex. teams de scoreboard, et peut le desynchroniser -> crash client).
    private var lastMessageText: String? = null
    private var lastMessageAtMs: Long = 0

    private fun onMessage(message: Component) {
        if (!Config.enabled) return

        // trim() : certains serveurs entourent le message de lignes vides/espaces de padding
        // (ex. "\n\nChat Games » ..." avec des dizaines d'espaces avant), confirme par le log
        // brut du client -> startsWith(prefixe) echouait silencieusement sans le trim.
        val text = message.string.trim()

        val now = System.currentTimeMillis()
        if (text == lastMessageText && now - lastMessageAtMs < 200) return
        lastMessageText = text
        lastMessageAtMs = now

        // Messages "Chat Games »" : geres exclusivement par le flux de validation
        // (notice ephemere + commande /cg valid ou /chatgame valid apres delai).
        // Pas de fallback vers les autres solveurs pour eviter une reponse publique instantanee.
        if (text.startsWith(CHAT_GAMES_PREFIX, ignoreCase = true)) {
            if (ROUND_RESULT_PATTERN.containsMatchIn(text)) {
                // "X answered Y in Z.ZZZs!" : annonce, pas une question a repondre.
                return
            }
            logger.info("[ChatGames] message recu: '{}' (enabled={}, entrees={})", text, Config.enabled, Config.validationConfig.questions.size)
            val response = Config.validationHandler.handle(text)
            if (response == null) {
                logger.info("[ChatGames] aucun trigger ne matche ce message")
                // Aucun trigger connu : notice locale pour ne pas rater le round en silence.
                Notifications.show("autoresponder.msg.no_answer", isError = true)
                return
            }
            logger.info("[ChatGames] match trouve, commande programmee: '{}'", response.message)
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
        val state = Component.translatable(if (Config.enabled) "autoresponder.state.on" else "autoresponder.state.off")
            .withStyle(if (Config.enabled) ChatFormatting.GREEN else ChatFormatting.RED)
        client.player?.sendSystemMessage(prefixed(Component.translatable("autoresponder.msg.state", state)))
    }

    private fun buildCommand(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(name)
            // /ar seul ouvre directement l'ecran de config (le toggle actif/inactif y est deja,
            // plus besoin d'un sous-comande "config" separee).
            .executes { openConfigScreen(it.source); 1 }
            .then(ClientCommands.literal("on").executes { set(it.source, true); 1 })
            .then(ClientCommands.literal("off").executes { set(it.source, false); 1 })
            .then(ClientCommands.literal("reload").executes {
                Config.load()
                MessageScheduler.clear()
                feedback(it.source, "autoresponder.msg.reloaded", ChatFormatting.YELLOW)
                1
            })
            // Une seule commande "add", sans variable de type : ajoute toujours un trigger
            // "Chat Games »" (substring apres le prefixe).
            .then(ClientCommands.literal("add")
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                    .executes { ctx -> addEntry(ctx, 1000, 2000) }))

    private fun openConfigScreen(source: FabricClientCommandSource) {
        // execute { } : reporte l'ouverture au tick suivant. Appeler setScreenAndShow
        // directement ici perd la course contre la fermeture du ChatScreen (Minecraft ferme le
        // chat juste apres l'execution de la commande, sur le meme tick, ce qui ecrasait notre
        // ecran juste pose). Confirme par le fait que /ar config echouait alors que le meme
        // ecran ouvert depuis ModMenu (pas de chat en cours) marchait.
        Minecraft.getInstance().execute {
            try {
                // parent = null : evite de lire Minecraft.screen (a deja cause un NoSuchFieldError
                // au runtime entre versions). setScreenAndShow (pas setScreen, renomme en 26.2).
                Minecraft.getInstance().setScreenAndShow(CustomConfigScreen(null))
            } catch (e: Throwable) {
                feedback(source, "autoresponder.msg.open_failed", ChatFormatting.RED, e.toString())
                e.printStackTrace()
            }
        }
    }

    /**
     * Parse "trigger | reponse | [mindelay] [maxdelay]" (delais optionnels, "|" ou espace,
     * defautMin/defautMax si absents) et persiste dans autoresponder_questions.json.
     */
    private fun addEntry(ctx: CommandContext<FabricClientCommandSource>, defaultMin: Long, defaultMax: Long): Int {
        val parts = StringArgumentType.getString(ctx, "text").split("|")
        val trigger = parts.getOrNull(0)?.trim().orEmpty()
        val response = parts.getOrNull(1)?.trim().orEmpty()
        if (trigger.isEmpty() || response.isEmpty()) {
            feedback(ctx.source, "autoresponder.msg.add_format", ChatFormatting.RED)
            return 1
        }
        val remainder = parts.drop(2).joinToString(" ")
        val numbers = Regex("""\d+""").findAll(remainder).map { it.value.toLong() }.toList()
        val mindelay = numbers.getOrNull(0) ?: defaultMin
        val maxdelay = numbers.getOrNull(1) ?: defaultMax
        Config.addEntry(trigger, response, mindelay, maxdelay)
        feedback(ctx.source, "autoresponder.msg.learned", ChatFormatting.GREEN, trigger, response, mindelay, maxdelay)
        return 1
    }

    private fun set(source: FabricClientCommandSource, value: Boolean) {
        Config.setEnabled(value)
        if (value) feedback(source, "autoresponder.msg.enabled", ChatFormatting.GREEN)
        else feedback(source, "autoresponder.msg.disabled", ChatFormatting.RED)
    }

    private fun prefixed(message: Component): Component =
        Component.literal("[AutoResponder] ").withStyle(ChatFormatting.GRAY).append(message)

    /** [key] = cle de traduction : le retour suit la langue choisie dans Minecraft. */
    private fun feedback(source: FabricClientCommandSource, key: String, color: ChatFormatting = ChatFormatting.WHITE, vararg args: Any) {
        source.sendFeedback(prefixed(Component.translatable(key, *args).withStyle(color)))
    }
}
