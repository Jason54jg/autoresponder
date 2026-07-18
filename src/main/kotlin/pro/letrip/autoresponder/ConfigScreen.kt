package pro.letrip.autoresponder

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.LongSliderControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Ecran de config YACL : activation, cooldown de base, template de commande et liste des
 * triggers/reponses du flux "Chat Games »" (autoresponder_questions.json).
 */
object AutoResponderConfigScreen {

    /** Une ligne editable du menu <-> une entree de autoresponder_questions.json (kind CHATGAMES). */
    private fun ValidationQuestion.toLine(): String = "$trigger|$response|$mindelay|$maxdelay"

    /** Parse "trigger|response|mindelay|maxdelay" ; mindelay/maxdelay optionnels (defaut 1000/2000). */
    private fun lineToQuestion(line: String): ValidationQuestion? {
        val parts = line.split("|")
        val trigger = parts.getOrNull(0)?.trim().orEmpty()
        val response = parts.getOrNull(1)?.trim().orEmpty()
        if (trigger.isEmpty() || response.isEmpty()) return null
        val mindelay = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: 1000
        val maxdelay = parts.getOrNull(3)?.trim()?.toLongOrNull() ?: 2000
        return ValidationQuestion(trigger, response, mindelay, maxdelay, QuestionKind.CHATGAMES)
    }

    fun create(parent: Screen?): Screen {
        var pendingEnabled = Config.enabled
        var pendingCooldown = Config.baseCooldownMs
        var pendingCommandTemplate = Config.validationConfig.commandTemplate
        // Stockage unifie : le menu n'edite que le kind CHATGAMES, les entrees /ar add et
        // /ar addquestion (autres kinds) restent intactes et sont reinjectees telles quelles au save.
        var pendingQuestionLines = Config.validationConfig.questions
            .filter { it.kind == QuestionKind.CHATGAMES }
            .map { it.toLine() }

        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("AutoResponder"))
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.literal("General"))
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(Component.literal("Actif"))
                            .binding(Config.enabled, { pendingEnabled }, { pendingEnabled = it })
                            .controller(TickBoxControllerBuilder::create)
                            .build()
                    )
                    .option(
                        Option.createBuilder<Long>()
                            .name(Component.literal("Cooldown de base (ms)"))
                            .binding(Config.baseCooldownMs, { pendingCooldown }, { pendingCooldown = it })
                            .controller { opt ->
                                // Bornes alignees sur Config.setBaseCooldownMs (coerceIn(2000, 10000)).
                                LongSliderControllerBuilder.create(opt)
                                    .range(2_000L, 10_000L)
                                    .step(100L)
                            }
                            .build()
                    )
                    .build()
            )
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.literal("Chat Games »"))
                    .option(
                        Option.createBuilder<String>()
                            .name(Component.literal("Template de commande"))
                            .description(
                                OptionDescription.of(
                                    Component.literal("Placeholders : {response} et {timer}. Ex: /cg valid {response} | {timer} ou /chatgame valid {response} | {timer}")
                                )
                            )
                            .binding(
                                Config.validationConfig.commandTemplate,
                                { pendingCommandTemplate },
                                { pendingCommandTemplate = it }
                            )
                            .controller(StringControllerBuilder::create)
                            .build()
                    )
                    .group(
                        ListOption.createBuilder<String>()
                            .name(Component.literal("Triggers » Chat Games (trigger|response|mindelay|maxdelay)"))
                            .binding(
                                Config.validationConfig.questions
                                    .filter { it.kind == QuestionKind.CHATGAMES }
                                    .map { it.toLine() },
                                { pendingQuestionLines },
                                { pendingQuestionLines = it }
                            )
                            .controller { opt -> StringControllerBuilder.create(opt) }
                            .initial("")
                            .build()
                    )
                    .build()
            )
            .save {
                Config.setEnabled(pendingEnabled)
                Config.setBaseCooldownMs(pendingCooldown)
                val chatGamesQuestions = pendingQuestionLines.mapNotNull { lineToQuestion(it) }
                val others = Config.validationConfig.questions.filter { it.kind != QuestionKind.CHATGAMES }
                Config.saveValidationConfig(pendingCommandTemplate, others + chatGamesQuestions)
            }
            .build()
            .generateScreen(parent)
    }
}
