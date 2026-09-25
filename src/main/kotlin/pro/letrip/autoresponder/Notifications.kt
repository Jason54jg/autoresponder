package pro.letrip.autoresponder

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * Affiche une notice locale (jamais envoyee au serveur) selon le style choisi dans la config
 * (Config.notificationStyle, reglable via /ar config).
 */
object Notifications {

    private val logger = LoggerFactory.getLogger("AutoResponder")

    private const val OVERLAY_DURATION_MS = 3000L
    private const val OVERLAY_MARGIN = 8

    @Volatile private var overlayText: Component? = null
    @Volatile private var overlayColor: Int = -1
    @Volatile private var overlayExpiresAt: Long = 0

    /** A appeler une seule fois au demarrage du mod (voir AutoResponder.onInitializeClient). */
    fun registerHud() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("autoresponder", "overlay"),
            HudElement { extractor, _ -> renderOverlay(extractor) }
        )
    }

    private fun renderOverlay(extractor: GuiGraphicsExtractor) {
        val component = overlayText ?: return
        if (System.currentTimeMillis() > overlayExpiresAt) {
            overlayText = null
            return
        }
        val client = Minecraft.getInstance()
        val font = client.font
        val textWidth = font.width(component)
        val lineHeight = font.lineHeight

        val x = when (Config.overlayPosition) {
            OverlayPosition.TOP_LEFT, OverlayPosition.BOTTOM_LEFT -> OVERLAY_MARGIN
            OverlayPosition.TOP_CENTER, OverlayPosition.CENTER, OverlayPosition.BOTTOM_CENTER ->
                (extractor.guiWidth() - textWidth) / 2
            OverlayPosition.TOP_RIGHT, OverlayPosition.BOTTOM_RIGHT ->
                extractor.guiWidth() - textWidth - OVERLAY_MARGIN
        }
        val y = when (Config.overlayPosition) {
            OverlayPosition.TOP_LEFT, OverlayPosition.TOP_CENTER, OverlayPosition.TOP_RIGHT -> OVERLAY_MARGIN
            OverlayPosition.CENTER -> (extractor.guiHeight() - lineHeight) / 2
            OverlayPosition.BOTTOM_LEFT, OverlayPosition.BOTTOM_CENTER, OverlayPosition.BOTTOM_RIGHT ->
                extractor.guiHeight() - lineHeight - OVERLAY_MARGIN
        }

        extractor.text(font, component, x, y, overlayColor)
    }

    /**
     * [key] = cle de traduction (assets/autoresponder/lang/<langue>.json) : le texte suit la langue choisie
     * dans Minecraft. [args] remplissent les %s.
     */
    fun show(key: String, isError: Boolean = false, vararg args: Any) {
        val message = Component.translatable(key, *args)
        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            logger.warn("[Notify] player null, notice abandonnee")
            return
        }
        val color = if (isError) ChatFormatting.RED else ChatFormatting.WHITE
        val style = Config.notificationStyle
        logger.info("[Notify] style={} message='{}'", style, message.string)

        try {
            when (style) {
                NotificationStyle.CHAT ->
                    player.sendSystemMessage(
                        Component.literal("[AutoResponder] ").withStyle(ChatFormatting.GRAY)
                            .append(message.copy().withStyle(color))
                    )
                NotificationStyle.OVERLAY -> {
                    overlayText = message
                    overlayColor = if (isError) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
                    overlayExpiresAt = System.currentTimeMillis() + OVERLAY_DURATION_MS
                }
                NotificationStyle.TOAST -> {
                    SystemToast.add(
                        client.gui.toastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("AutoResponder"),
                        message
                    )
                    logger.info("[Notify] SystemToast.add() execute sans exception")
                }
                NotificationStyle.NONE -> {}
            }
        } catch (e: Throwable) {
            logger.error("[Notify] exception en affichant la notice (style={})", style, e)
        }
    }
}
