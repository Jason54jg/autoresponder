package pro.letrip.autoresponder

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Charge la config depuis <configDir>/autochatgames/, en copiant les defauts
 * embarques au premier lancement. Reconstruit la chaine de handlers a chaque reload.
 */
object Config {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dir: Path = FabricLoader.getInstance().configDir.resolve("autoresponder")

    var enabled: Boolean = true
        private set

    lateinit var handlers: List<IChatHandler>
        private set

    /** Handler dedie au flux "Chat Games » ... " (notice ephemere + commande de validation). */
    lateinit var validationHandler: ChatGamesValidationHandler
        private set

    fun load() {
        Files.createDirectories(dir)

        val words = readLines("words.txt")
        val responders = gson.fromJson(read("responders.json"), Array<Responder>::class.java).toList()
        val rawQuestions: Map<String, String> =
            gson.fromJson(read("questions.json"), object : TypeToken<Map<String, String>>() {}.type)
        val questions = buildMap {
            for ((q, a) in rawQuestions) put(normalizeQuestion(q), a)
            for ((q, a) in readLearnedQuestions()) put(normalizeQuestion(q), a) // apprises priment
        }
        val validationConfig = gson.fromJson(read("autoresponder_questions.json"), ValidationConfigFile::class.java)
            ?: ValidationConfigFile()

        enabled = readSettings().enabled

        handlers = listOf(
            FirstToSayHandler(),
            UnscrambleHandler(words, readLearned()),
            MathHandler(),
            QuestionHandler(questions),
            StaticResponderHandler(responders)
        )
        validationHandler = ChatGamesValidationHandler(validationConfig.questions, validationConfig.commandTemplate)
    }

    /** Apprend une paire scramble -> reponse, la persiste et recharge. */
    fun addUnscramble(scrambled: String, answer: String) {
        val learned = readLearned().toMutableMap()
        learned[sortedLetters(scrambled)] = answer
        learnedFile.writeText(gson.toJson(learned))
        load()
    }

    /** Apprend une reponse a une question, la persiste et recharge. */
    fun addQuestion(question: String, answer: String) {
        val learned = readLearnedQuestions().toMutableMap()
        learned[question] = answer
        learnedQuestionsFile.writeText(gson.toJson(learned))
        load()
    }

    private val learnedFile: Path get() = dir.resolve("learned.json")
    private val learnedQuestionsFile: Path get() = dir.resolve("learned_questions.json")

    private fun readLearned(): Map<String, String> = readMap(learnedFile)
    private fun readLearnedQuestions(): Map<String, String> = readMap(learnedQuestionsFile)

    private fun readMap(file: Path): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return gson.fromJson(file.readText(), object : TypeToken<Map<String, String>>() {}.type)
            ?: emptyMap()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        saveSettings(Settings(value))
    }

    private data class Settings(val enabled: Boolean = true)

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
