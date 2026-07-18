package pro.letrip.autoresponder

/**
 * Une reponse prete a envoyer, avec sa fenetre de delai aleatoire.
 * isCommand = true -> envoye via sendCommand (sans le "/").
 */
data class Response(
    val message: String,
    val minDelayMs: Long,
    val maxDelayMs: Long,
    val isCommand: Boolean = false
)

/**
 * Entree de la liste statique (format du responder.List d'origine).
 */
data class Responder(
    val address: String = "*",
    val pattern: String,
    val message: String,
    val minDelayInMilliseconds: Long = 100,
    val maxDelayInMilliseconds: Long = 200,
    val forceDisableFormatter: Boolean = false,
    val regex: Boolean = false
)

/**
 * Un handler regarde le message entrant et decide (ou non) d'une reponse.
 */
interface IChatHandler {
    fun handle(text: String): Response?
}

/**
 * Entree du fichier de config du flux de validation "Chat Games »" (spec AutoResponder).
 * trigger est cherche en sous-chaine du message (apres le prefixe), mindelay/maxdelay en ms.
 */
data class ValidationQuestion(
    val trigger: String,
    val response: String,
    val mindelay: Long = 1000,
    val maxdelay: Long = 2000
)

/**
 * Fichier de config complet : gabarit de commande + liste des questions.
 * commandTemplate accepte les placeholders {response} et {timer}.
 */
data class ValidationConfigFile(
    val commandTemplate: String = "/cg valid {response} | {timer}",
    val questions: List<ValidationQuestion> = emptyList()
)
