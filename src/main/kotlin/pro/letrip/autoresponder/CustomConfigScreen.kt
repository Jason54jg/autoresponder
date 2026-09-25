package pro.letrip.autoresponder

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** Palette partagee par tous les widgets custom de cet ecran. */
private object Palette {
    const val BACKDROP = 0xB8000000.toInt()
    const val BG_BASE = 0xFF141419.toInt()
    const val BG_CARD = 0xFF1D1D24.toInt()
    const val BG_FIELD = 0xFF101014.toInt()
    const val ACCENT = 0xFF7C6FF0.toInt()
    const val ACCENT_HOVER = 0xFF9186F5.toInt()
    const val ACCENT_DIM = 0xFF4A4270.toInt()
    const val BORDER = 0xFF34343F.toInt()
    const val TRACK_OFF = 0xFF3A3A44.toInt()
    const val TEXT_PRIMARY = 0xFFF2F2F5.toInt()
    const val TEXT_SECONDARY = 0xFF8E8E9C.toInt()
    const val TAB_INACTIVE = 0xFF1A1A20.toInt()
    const val TAB_INACTIVE_HOVER = 0xFF232329.toInt()
}

/**
 * Coins arrondis via textures 9-slice (assets/autoresponder/textures/gui/sprites/) : forme blanche
 * pleine, teintee a la couleur voulue via le parametre color de blitSprite (meme technique que les
 * boutons vanilla -- verifie par decompilation de AbstractButton.extractDefaultSprite : pipeline
 * RenderPipelines.GUI_TEXTURED, blitSprite(pipeline, id, x, y, w, h, ARGB.white(alpha)), ici on
 * passe directement une couleur ARGB au lieu de blanc pour teinter la sprite).
 *
 * rounded_rect.png (32x32, bordure 9-slice 8px) : rectangle a coins arrondis, panneau/boutons/champs.
 * pill.png (22x22, bordure 9-slice 11px) : pilule totalement ronde, switch on/off uniquement.
 */
private val ROUNDED_RECT_SPRITE = Identifier.fromNamespaceAndPath("autoresponder", "rounded_rect")
private val PILL_SPRITE = Identifier.fromNamespaceAndPath("autoresponder", "pill")

private fun GuiGraphicsExtractor.fillRoundedRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
    blitSprite(RenderPipelines.GUI_TEXTURED, ROUNDED_RECT_SPRITE, x, y, w, h, color)
}

private fun GuiGraphicsExtractor.fillPill(x: Int, y: Int, w: Int, h: Int, color: Int) {
    blitSprite(RenderPipelines.GUI_TEXTURED, PILL_SPRITE, x, y, w, h, color)
}

/** Meme forme que fillRoundedRect, avec une bordure de 1px d'une autre couleur autour. */
private fun GuiGraphicsExtractor.fillRoundedRectBordered(x: Int, y: Int, w: Int, h: Int, bg: Int, border: Int) {
    fillRoundedRect(x, y, w, h, border)
    fillRoundedRect(x + 1, y + 1, w - 2, h - 2, bg)
}

private fun lerpColor(from: Int, to: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    fun chan(shift: Int): Int {
        val a = (from ushr shift) and 0xFF
        val b = (to ushr shift) and 0xFF
        return (a + (b - a) * tt).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (chan(16) shl 16) or (chan(8) shl 8) or chan(0)
}

/** Anime une transition 0..1 vers une cible booleenne, independante du framerate (delta reel). */
private class HoverAnim(private val durationSeconds: Float = 0.12f) {
    var value = 0f
        private set
    private var lastNanos = System.nanoTime()

    fun update(target: Boolean) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastNanos = now
        val t = if (target) 1f else 0f
        value += (t - value) * (dt / durationSeconds).coerceIn(0f, 1f)
    }
}

/** Bouton plat custom (coins arrondis en pixel-art, survol anime) -- pas le Button vanilla. */
private open class FlatButton(
    x: Int, y: Int, w: Int, h: Int,
    label: Component,
    private val font: net.minecraft.client.gui.Font,
    private val bg: Int = Palette.BG_CARD,
    private val bgHover: Int = Palette.ACCENT_DIM,
    private val border: Int = Palette.BORDER,
    private val textColor: Int = Palette.TEXT_PRIMARY,
    private val onClick: () -> Unit
) : AbstractWidget(x, y, w, h, label) {

    private val anim = HoverAnim()

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        anim.update(isHovered && active)
        val bgColor = lerpColor(bg, bgHover, anim.value)
        extractor.fillRoundedRectBordered(x, y, width, height, bgColor, border)
        extractor.centeredText(font, message, x + width / 2, y + (height - 8) / 2, if (active) textColor else Palette.TEXT_SECONDARY)
    }

    override fun onClick(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean) {
        if (active) onClick.invoke()
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

/** Interrupteur on/off style "switch" moderne (pilule + curseur rond en pixel-art). */
private class ToggleSwitch(
    x: Int, y: Int,
    private var checked: Boolean,
    private val onToggle: (Boolean) -> Unit
) : AbstractWidget(x, y, 44, 22, Component.literal("")) {

    private var progress = if (checked) 1f else 0f
    private var lastNanos = System.nanoTime()

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastNanos = now
        val target = if (checked) 1f else 0f
        progress += (target - progress) * (dt / 0.15f).coerceIn(0f, 1f)

        val trackColor = lerpColor(Palette.TRACK_OFF, Palette.ACCENT, progress)
        extractor.fillPill(x, y, width, height, trackColor)
        val thumbSize = height - 6
        val thumbX = x + 3 + ((width - thumbSize - 6) * progress).toInt()
        extractor.fillRoundedRect(thumbX, y + 3, thumbSize, thumbSize, Palette.TEXT_PRIMARY)
    }

    override fun onClick(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean) {
        checked = !checked
        onToggle(checked)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.translatable(if (checked) "autoresponder.state.on" else "autoresponder.state.off"))
    }
}

/** Champ "cycle" custom (valeur d'enum + chevrons), clic = valeur suivante. Meme habillage que FlatButton. */
private class FlatCycleField<T>(
    x: Int, y: Int, w: Int, h: Int,
    private val font: net.minecraft.client.gui.Font,
    private val values: List<T>,
    initial: T,
    private val labelOf: (T) -> String,
    private val onChange: (T) -> Unit
) : AbstractWidget(x, y, w, h, Component.literal("")) {

    var value: T = initial
        private set
    private val anim = HoverAnim()

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        anim.update(isHovered)
        val bgColor = lerpColor(Palette.BG_FIELD, Palette.ACCENT_DIM, anim.value)
        extractor.fillRoundedRectBordered(x, y, width, height, bgColor, Palette.BORDER)
        val textY = y + (height - 8) / 2
        // Chevrons ecartes aux deux bords (pas colles l'un a l'autre) : le cote clique correspond
        // vraiment au chevron affiche a cet endroit -- avant ca cyclait toujours en avant meme en
        // cliquant sur "‹".
        extractor.text(font, Component.literal("‹"), x + 8, textY, Palette.ACCENT)
        extractor.centeredText(font, Component.literal(labelOf(value)), x + width / 2, textY, Palette.TEXT_PRIMARY)
        extractor.text(font, Component.literal("›"), x + width - 14, textY, Palette.ACCENT)
    }

    override fun onClick(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean) {
        // Moitie gauche (cote "‹") = valeur precedente, moitie droite (cote "›") = suivante.
        val i = values.indexOf(value)
        val goingBack = event.x() < x + width / 2.0
        value = if (goingBack) values[(i - 1 + values.size) % values.size] else values[(i + 1) % values.size]
        onChange(value)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal(labelOf(value)))
    }
}

/**
 * Ecran de config custom (remplace l'ecran YACL retire) : palette sombre indigo, coins arrondis
 * pixel-art partout, deux onglets (General / Chat Games), widgets entierement custom pour les
 * boutons/toggle/cycle. EditBox vanilla non bordee habillee d'un cadre maison assorti pour
 * garder l'edition de texte native (curseur, selection...) sans le style Minecraft par defaut.
 */
class CustomConfigScreen(private val parent: Screen?) : Screen(Component.literal("AutoResponder")) {

    private enum class Tab { GENERAL, CHATGAMES }
    private var activeTab = Tab.GENERAL

    private var pendingEnabled = Config.enabled
    private var pendingCooldown = Config.baseCooldownMs
    private var pendingNotificationStyle = Config.notificationStyle
    private var pendingOverlayPosition = Config.overlayPosition
    private var pendingCommandTemplate = Config.validationConfig.commandTemplate
    private var pendingTriggersText = Config.validationConfig.questions.joinToString("\n") { it.toLine() }

    private var panelX = 0
    private var panelY = 0
    private var panelW = 520
    private var panelH = 380
    private val margin = 28
    private val rowGap = 42
    private val fieldH = 24

    private lateinit var tabGeneralBtn: FlatButton
    private lateinit var tabChatGamesBtn: FlatButton
    private val generalWidgets = mutableListOf<AbstractWidget>()
    private val chatGamesWidgets = mutableListOf<AbstractWidget>()
    /** Cadres dessines derriere les EditBox non bordees (x,y,w,h), separes par onglet pour ne
     * dessiner que ceux de l'onglet actif (sinon les cadres Chat Games se dessinaient par-dessus
     * l'onglet General et inversement). */
    private val generalFieldFrames = mutableListOf<IntArray>()
    private val chatGamesFieldFrames = mutableListOf<IntArray>()

    override fun init() {
        // S'adapte a toute resolution/GUI scale : le panneau garde sa taille ideale tant qu'elle
        // rentre a l'ecran avec une marge de 40px, sinon il se reduit pour tenir.
        panelW = (width - 40).coerceIn(240, 520)
        panelH = (height - 40).coerceIn(260, 380)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        generalFieldFrames.clear()
        chatGamesFieldFrames.clear()
        generalWidgets.clear()
        chatGamesWidgets.clear()

        val contentX = panelX + margin
        val contentW = panelW - margin * 2

        tabGeneralBtn = addRenderableWidget(
            FlatButton(panelX + margin, panelY + 20, 130, 26, Component.translatable("autoresponder.tab.general"), font,
                bg = Palette.TAB_INACTIVE, bgHover = Palette.TAB_INACTIVE_HOVER) {
                activeTab = Tab.GENERAL; refreshTabVisibility()
            }
        )
        tabChatGamesBtn = addRenderableWidget(
            FlatButton(panelX + margin + 138, panelY + 20, 150, 26, Component.literal("Chat Games »"), font,
                bg = Palette.TAB_INACTIVE, bgHover = Palette.TAB_INACTIVE_HOVER) {
                activeTab = Tab.CHATGAMES; refreshTabVisibility()
            }
        )

        // --- General ---
        var y = panelY + 74
        val toggle = addRenderableWidget(
            ToggleSwitch(contentX + contentW - 44, y - 2, pendingEnabled) { pendingEnabled = it }
        )
        generalWidgets += toggle
        y += rowGap

        // EditBox non bordee (setBordered(false)) ne centre PAS son texte verticalement -- verifie
        // par decompilation : textY = getY() brut sans bordure, alors qu'avec bordure il fait
        // getY() + (height-8)/2. On compense a la main en decalant l'EditBox dans le cadre dessine.
        val textInset = ((fieldH - font.lineHeight) / 2).coerceAtLeast(0)
        val textIndent = 8
        val cooldownBox = EditBox(font, contentX + textIndent, y + textInset, contentW - textIndent, fieldH - textInset, Component.translatable("autoresponder.option.cooldown"))
        cooldownBox.setBordered(false)
        cooldownBox.setValue(pendingCooldown.toString())
        cooldownBox.setResponder { text -> text.toLongOrNull()?.let { pendingCooldown = it.coerceIn(2000, 10000) } }
        addRenderableWidget(cooldownBox)
        generalWidgets += cooldownBox
        generalFieldFrames += intArrayOf(contentX, y, contentW, fieldH)
        y += rowGap

        val styleField = addRenderableWidget(
            FlatCycleField(contentX, y, contentW, fieldH, font, NotificationStyle.entries, pendingNotificationStyle, { Component.translatable("autoresponder.style.${it.name.lowercase()}").string }) {
                pendingNotificationStyle = it
            }
        )
        generalWidgets += styleField
        y += rowGap

        val posField = addRenderableWidget(
            FlatCycleField(contentX, y, contentW, fieldH, font, OverlayPosition.entries, pendingOverlayPosition, { Component.translatable("autoresponder.position.${it.name.lowercase()}").string }) {
                pendingOverlayPosition = it
            }
        )
        generalWidgets += posField

        // --- Chat Games » ---
        var yc = panelY + 74
        val templateBox = EditBox(font, contentX + textIndent, yc + textInset, contentW - textIndent, fieldH - textInset, Component.translatable("autoresponder.option.template"))
        templateBox.setBordered(false)
        templateBox.setMaxLength(256)
        templateBox.setValue(pendingCommandTemplate)
        templateBox.setResponder { pendingCommandTemplate = it }
        addRenderableWidget(templateBox)
        chatGamesWidgets += templateBox
        chatGamesFieldFrames += intArrayOf(contentX, yc, contentW, fieldH)
        yc += rowGap + 12

        val triggersH = panelY + panelH - 56 - yc
        val triggersBox = MultiLineEditBox.builder()
            .setX(contentX)
            .setY(yc)
            .setShowBackground(false)
            .setPlaceholder(Component.translatable("autoresponder.option.triggers.placeholder"))
            .build(font, contentW, triggersH, Component.translatable("autoresponder.option.triggers"))
        triggersBox.setValue(pendingTriggersText)
        triggersBox.setValueListener { pendingTriggersText = it }
        addRenderableWidget(triggersBox)
        chatGamesWidgets += triggersBox
        chatGamesFieldFrames += intArrayOf(contentX, yc, contentW, triggersH)

        addRenderableWidget(
            FlatButton(panelX + panelW - margin - 220, panelY + panelH - 44, 100, 26, Component.translatable("autoresponder.button.cancel"), font) {
                onClose()
            }
        )
        addRenderableWidget(
            FlatButton(panelX + panelW - margin - 110, panelY + panelH - 44, 110, 26, Component.translatable("autoresponder.button.save"), font,
                bg = Palette.ACCENT, bgHover = Palette.ACCENT_HOVER, border = Palette.ACCENT) {
                save()
            }
        )

        refreshTabVisibility()
    }

    private fun refreshTabVisibility() {
        val showGeneral = activeTab == Tab.GENERAL
        for (w in generalWidgets) w.visible = showGeneral
        for (w in chatGamesWidgets) w.visible = !showGeneral
        tabGeneralBtn.active = !showGeneral
        tabChatGamesBtn.active = showGeneral
    }

    private fun save() {
        Config.setEnabled(pendingEnabled)
        Config.setBaseCooldownMs(pendingCooldown)
        Config.setNotificationStyle(pendingNotificationStyle)
        Config.setOverlayPosition(pendingOverlayPosition)
        val questions = pendingTriggersText.lineSequence().mapNotNull { lineToQuestion(it) }.toList()
        Config.saveValidationConfig(pendingCommandTemplate, questions)
        onClose()
    }

    private fun lineToQuestion(line: String): ValidationQuestion? {
        val parts = line.split("|")
        val trigger = parts.getOrNull(0)?.trim().orEmpty()
        val response = parts.getOrNull(1)?.trim().orEmpty()
        if (trigger.isEmpty() || response.isEmpty()) return null
        val mindelay = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: 1000
        val maxdelay = parts.getOrNull(3)?.trim()?.toLongOrNull() ?: 2000
        return ValidationQuestion(trigger, response, mindelay, maxdelay)
    }

    private fun ValidationQuestion.toLine(): String = "$trigger|$response|$mindelay|$maxdelay"

    override fun onClose() {
        // Gui.setScreen accepte null (Minecraft.setScreenAndShow non, reserve a l'ouverture) :
        // c'est ce que Screen.onClose() par defaut appelle en interne (verifie par decompilation).
        Minecraft.getInstance().gui.setScreen(parent)
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractor.fill(0, 0, width, height, Palette.BACKDROP)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractor.fillRoundedRectBordered(panelX, panelY, panelW, panelH, Palette.BG_BASE, Palette.BORDER)
        extractor.fill(panelX + 8, panelY, panelX + panelW - 8, panelY + 3, Palette.ACCENT)

        drawTitle(extractor)

        // Cadres des EditBox non bordees, dans le style des autres champs -- seulement ceux de
        // l'onglet actif (sinon les cadres Chat Games se dessinaient par-dessus l'onglet General).
        val activeFrames = if (activeTab == Tab.GENERAL) generalFieldFrames else chatGamesFieldFrames
        for (frame in activeFrames) {
            val (fx, fy, fw, fh) = frame
            extractor.fillRoundedRectBordered(fx, fy, fw, fh, Palette.BG_FIELD, Palette.BORDER)
        }

        val labels = if (activeTab == Tab.GENERAL)
            listOf(
                "autoresponder.option.enabled" to (panelY + 74),
                "autoresponder.option.cooldown" to (panelY + 74 + rowGap),
                "autoresponder.option.style" to (panelY + 74 + rowGap * 2),
                "autoresponder.option.position" to (panelY + 74 + rowGap * 3)
            )
        else
            listOf(
                "autoresponder.option.template" to (panelY + 74),
                "autoresponder.option.triggers" to (panelY + 74 + rowGap + 12)
            )

        for ((labelKey, ly) in labels) {
            extractor.text(font, Component.translatable(labelKey), panelX + margin, ly - 16, Palette.TEXT_SECONDARY)
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick)
    }

    /** Titre gras, ombre portee, centre au-dessus du panneau -- avant c'etait un simple texte 8px
     * colle en haut a gauche du panneau. */
    private fun drawTitle(extractor: GuiGraphicsExtractor) {
        val title = Component.literal("AutoResponder").withStyle { it.withBold(true) }
        val centerX = panelX + panelW / 2
        val y = panelY - 30
        val textWidth = font.width(title)
        extractor.text(font, title, centerX - textWidth / 2, y, Palette.TEXT_PRIMARY, true)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}

private operator fun IntArray.component1() = this[0]
private operator fun IntArray.component2() = this[1]
private operator fun IntArray.component3() = this[2]
private operator fun IntArray.component4() = this[3]
