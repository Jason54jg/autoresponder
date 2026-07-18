package pro.letrip.autoresponder

/**
 * Une reponse prete a envoyer, avec sa fenetre de delai aleatoire.
 * isCommand = true -> envoye via sendCommand (sans le "/").
 * delayResolved = true -> minDelayMs/maxDelayMs sont deja identiques et definitifs (cooldown de
 * base deja inclus), utilise quand le delai doit etre connu a l'avance (ex: texte de commande
 * qui affiche le timer). Sinon MessageScheduler tire aleatoirement et ajoute le cooldown de base.
 */
data class Response(
    val message: String,
    val minDelayMs: Long,
    val maxDelayMs: Long,
    val isCommand: Boolean = false,
    val delayResolved: Boolean = false
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
 * Distingue, dans le fichier unique autoresponder_questions.json, quel handler/mecanisme de
 * matching une entree alimente : /ar addtrigger (substring sur "Chat Games » ..."),
 * /ar addquestion (banque "Answer the following question: ...") ou /ar add (unscramble,
 * matching par anagramme, pas par texte litteral).
 */
enum class QuestionKind { CHATGAMES, QUESTION, UNSCRAMBLE }

/**
 * Entree unique du fichier de config, alimentee par /ar add, /ar addquestion et /ar addtrigger
 * (kind distingue le mecanisme). Pour CHATGAMES, trigger est cherche en sous-chaine du message
 * (apres le prefixe "Chat Games »"). mindelay/maxdelay en ms.
 */
data class ValidationQuestion(
    val trigger: String,
    val response: String,
    val mindelay: Long = 1000,
    val maxdelay: Long = 2000,
    val kind: QuestionKind = QuestionKind.CHATGAMES
)

/**
 * Fichier de config complet : gabarit de commande (flux Chat Games uniquement) + liste unique
 * des entrees apprises par /ar add / addquestion / addtrigger. commandTemplate accepte les
 * placeholders {response} et {timer}.
 */
data class ValidationConfigFile(
    val commandTemplate: String = "/cg valid {response} | {timer}",
    val questions: List<ValidationQuestion> = emptyList()
)
