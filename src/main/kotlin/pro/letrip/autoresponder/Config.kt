package pro.letrip.autoresponder

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Charge la config depuis <configDir>/autoresponder/, en copiant les defauts embarques au
 * premier lancement. Reconstruit la chaine de handlers a chaque reload.
 *
 * autoresponder_questions.json : une seule liste plate de triggers "Chat Games »", alimentee par
 * /ar add et par l'ecran de config.
 */
object Config {

    // Gson (standard, pas kotlinx-serialization) ignore les defauts de constructeur Kotlin pour
    // les champs absents du JSON (bypass du constructeur via Unsafe -> null/0 au lieu du defaut
    // declare) : deserializer explicite pour rester robuste aux vieux fichiers incomplets.
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ValidationQuestion::class.java, JsonDeserializer { json, _, _ ->
            val obj = json.asJsonObject
            ValidationQuestion(
                trigger = obj.get("trigger")?.asString.orEmpty(),
                response = obj.get("response")?.asString.orEmpty(),
                mindelay = obj.get("mindelay")?.asLong ?: 1000,
                maxdelay = obj.get("maxdelay")?.asLong ?: 2000
            )
        })
        .create()
    private val dir: Path = FabricLoader.getInstance().configDir.resolve("autoresponder")

    var enabled: Boolean = true
        private set

    /** Delai (ms) ajoute a chaque reponse programmee, en plus du mindelay/maxdelay propre a l'entree. */
    var baseCooldownMs: Long = 2000
        private set

    /** Style des notices locales (trouve / pas trouve) : chat, overlay, toast ou aucune. */
    var notificationStyle: NotificationStyle = NotificationStyle.CHAT
        private set

    /** Emplacement a l'ecran quand notificationStyle == OVERLAY. */
    var overlayPosition: OverlayPosition = OverlayPosition.TOP_CENTER
        private set

    lateinit var handlers: List<IChatHandler>
        private set

    /** Handler dedie au flux "Chat Games » ... " (notice ephemere + commande de validation). */
    lateinit var validationHandler: ChatGamesValidationHandler
        private set

    /** Dernier contenu charge de autoresponder_questions.json (source pour l'ecran de config). */
    lateinit var validationConfig: ValidationConfigFile
        private set

    fun load() {
        Files.createDirectories(dir)

        val words = readLines("words.txt")
        val responders = gson.fromJson(read("responders.json"), Array<Responder>::class.java).toList()
        val rawQuestions: Map<String, String> =
            gson.fromJson(read("questions.json"), object : TypeToken<Map<String, String>>() {}.type)

        var loaded = gson.fromJson(read("autoresponder_questions.json"), ValidationConfigFile::class.java)
            ?: ValidationConfigFile()
        loaded = migrateLegacyFilesIfPresent(loaded)
        validationConfig = loaded

        val settings = readSettings()
        enabled = settings.enabled
        baseCooldownMs = settings.baseCooldownMs
        notificationStyle = settings.notificationStyle ?: NotificationStyle.CHAT
        overlayPosition = settings.overlayPosition ?: OverlayPosition.TOP_CENTER

        val questions = buildMap {
            for ((q, a) in rawQuestions) put(normalizeQuestion(q), ValidationQuestion(q, a, 800, 1400))
        }

        handlers = listOf(
            FirstToSayHandler(),
            UnscrambleHandler(words),
            MathHandler(),
            QuestionHandler(questions),
            StaticResponderHandler(responders)
        )
        validationHandler = ChatGamesValidationHandler(validationConfig.questions, validationConfig.commandTemplate)
    }

    /** Ecrase autoresponder_questions.json (gabarit + liste complete) et recharge. Utilise par l'ecran de config. */
    fun saveValidationConfig(commandTemplate: String, questions: List<ValidationQuestion>) {
        val file = ValidationConfigFile(commandTemplate, questions)
        dir.resolve("autoresponder_questions.json").writeText(gson.toJson(file))
        load()
    }

    /** Point d'entree de /ar add : ajoute (ou remplace si meme trigger) et persiste. */
    fun addEntry(trigger: String, response: String, mindelay: Long, maxdelay: Long) =
        upsert(ValidationQuestion(trigger, response, mindelay, maxdelay))

    private fun upsert(entry: ValidationQuestion) {
        val key = entry.trigger.lowercase()
        val updated = validationConfig.questions.filterNot { it.trigger.lowercase() == key } + entry
        saveValidationConfig(validationConfig.commandTemplate, updated)
    }

    private val learnedFile: Path get() = dir.resolve("learned.json")
    private val learnedQuestionsFile: Path get() = dir.resolve("learned_questions.json")

    /**
     * Anciens fichiers separes (learned.json / learned_questions.json, d'avant le stockage
     * unifie) : import ponctuel dans autoresponder_questions.json puis renommage en .migrated
     * pour ne pas les reimporter au prochain load(). Couvre l'ancien format Map<String,String>
     * (lettres/question -> reponse) et le format liste intermediaire (avec mindelay/maxdelay).
     */
    private fun migrateLegacyFilesIfPresent(current: ValidationConfigFile): ValidationConfigFile {
        val legacyUnscramble = readLegacyEntries(learnedFile)
        val legacyQuestions = readLegacyEntries(learnedQuestionsFile)
        if (legacyUnscramble.isEmpty() && legacyQuestions.isEmpty()) return current

        val migrated = ValidationConfigFile(current.commandTemplate, current.questions + legacyUnscramble + legacyQuestions)
        dir.resolve("autoresponder_questions.json").writeText(gson.toJson(migrated))

        if (learnedFile.exists()) {
            Files.move(learnedFile, dir.resolve("learned.json.migrated"), StandardCopyOption.REPLACE_EXISTING)
        }
        if (learnedQuestionsFile.exists()) {
            Files.move(learnedQuestionsFile, dir.resolve("learned_questions.json.migrated"), StandardCopyOption.REPLACE_EXISTING)
        }
        return migrated
    }

    private fun readLegacyEntries(file: Path): List<ValidationQuestion> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        return try {
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val raw: List<Map<String, Any>> = gson.fromJson(text, listType) ?: emptyList()
            raw.mapNotNull { obj ->
                val trigger = (obj["question"] ?: obj["scrambled"])?.toString() ?: return@mapNotNull null
                val answer = obj["answer"]?.toString() ?: return@mapNotNull null
                val mindelay = (obj["mindelay"] as? Double)?.toLong() ?: 1000
                val maxdelay = (obj["maxdelay"] as? Double)?.toLong() ?: 2000
                ValidationQuestion(trigger, answer, mindelay, maxdelay)
            }
        } catch (e: Exception) {
            val map: Map<String, String> = gson.fromJson(text, object : TypeToken<Map<String, String>>() {}.type)
                ?: emptyMap()
            map.map { (k, v) -> ValidationQuestion(k, v) }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        saveSettings(currentSettings())
    }

    /** Change le cooldown de base (ms), borne a [0, 10000] pour eviter une config absurde. */
    fun setBaseCooldownMs(value: Long) {
        baseCooldownMs = value.coerceIn(2000, 10000)
        saveSettings(currentSettings())
    }

    fun setNotificationStyle(value: NotificationStyle) {
        notificationStyle = value
        saveSettings(currentSettings())
    }

    fun setOverlayPosition(value: OverlayPosition) {
        overlayPosition = value
        saveSettings(currentSettings())
    }

    private fun currentSettings() = Settings(enabled, baseCooldownMs, notificationStyle, overlayPosition)

    // notificationStyle/overlayPosition nullables : meme raison que le deserializer de
    // ValidationQuestion plus haut. Un settings.json ecrit avant l'ajout de ces champs n'aurait
    // pas la valeur, et Gson (bypass du constructeur Kotlin) le laisserait a null plutot que
    // d'appliquer le defaut.
    private data class Settings(
        val enabled: Boolean = true,
        val baseCooldownMs: Long = 500,
        val notificationStyle: NotificationStyle? = null,
        val overlayPosition: OverlayPosition? = null
    )

    private fun readSettings(): Settings {
        val file = dir.resolve("settings.json")
        if (!file.exists()) {
            saveSettings(Settings())
            return Settings()
        }
        return gson.fromJson(file.readText(), Settings::class.java) ?: Settings()
    }

    private fun saveSettings(settings: Settings) {
        dir.resolve("settings.json").writeText(gson.toJson(settings))
    }

    /** Contenu texte d'un fichier de config, copie depuis les ressources si absent. */
    private fun read(name: String): String {
        val file = dir.resolve(name)
        if (!file.exists()) file.writeText(defaultResource(name))
        return file.readText()
    }

    private fun readLines(name: String): List<String> =
        read(name).lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    private fun defaultResource(name: String): String =
        javaClass.getResourceAsStream("/assets/autoresponder/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Ressource par defaut manquante: $name")
}
