package pro.letrip.autoresponder

/** Cle normalisee d'une question (minuscule, sans ponctuation, espaces compresses). */
fun normalizeQuestion(question: String): String =
    question.lowercase().replace(Regex("""[^a-z0-9 ]"""), "").trim().replace(Regex("""\s+"""), " ")

/** Cle anagramme d'un mot : lettres minuscules triees. */
fun sortedLetters(word: String): String =
    word.lowercase().toCharArray().sorted().joinToString("")

/**
 * "First one to say X" / "first one to type X" -> renvoie X tel quel.
 */
class FirstToSayHandler : IChatHandler {

    private val regex = Regex("""first (?:one|player) to (?:say|type)[:\s]+(\S+)""", RegexOption.IGNORE_CASE)

    override fun handle(text: String): Response? {
        val token = regex.find(text)?.groupValues?.get(1) ?: return null
        return Response(token, 800, 1400)
    }
}

/**
 * "Unscramble abc" -> mot du dictionnaire dont les lettres triees correspondent. Delai fixe
 * 800/1400ms.
 */
class UnscrambleHandler(words: List<String>) : IChatHandler {

    private val regex = Regex("""unscramble[:\s]+([A-Za-z]+)""", RegexOption.IGNORE_CASE)
    private val byLetters: Map<String, String> = buildMap {
        for (word in words) {
            val key = sortedLetters(word)
            if (key.isNotEmpty()) putIfAbsent(key, word)
        }
    }

    override fun handle(text: String): Response? {
        val scrambled = regex.find(text)?.groupValues?.get(1) ?: return null
        val answer = byLetters[sortedLetters(scrambled)] ?: return null
        return Response(answer, 800, 1400)
    }
}

/**
 * "What is A op B" avec op dans + - x * / -> resultat calcule.
 */
class MathHandler : IChatHandler {

    private val regex = Regex("""what is\s+(-?\d+)\s*([x*+/\-])\s*(-?\d+)""", RegexOption.IGNORE_CASE)

    override fun handle(text: String): Response? {
        val match = regex.find(text) ?: return null
        val (rawA, op, rawB) = match.destructured
        val a = rawA.toLong()
        val b = rawB.toLong()

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "x", "*" -> a * b
            "/" -> if (b != 0L && a % b == 0L) a / b else return null
            else -> return null
        }
        return Response(result.toString(), 800, 1400)
    }
}

/**
 * "Answer the following question: ..." -> reponse issue de la banque configurable.
 * Delai par entree (mindelay/maxdelay), 800/1400ms par defaut.
 */
class QuestionHandler(private val answers: Map<String, ValidationQuestion>) : IChatHandler {

    private val regex = Regex("""answer the following question:\s*(.+)""", RegexOption.IGNORE_CASE)

    override fun handle(text: String): Response? {
        val question = regex.find(text)?.groupValues?.get(1) ?: return null
        val entry = answers[normalizeQuestion(question)] ?: return null
        return Response(entry.response, entry.mindelay, entry.maxdelay)
    }
}

/**
 * Liste statique d'origine (responder.List). Fallback quand aucun solveur ne matche.
 */
class StaticResponderHandler(private val responders: List<Responder>) : IChatHandler {

    override fun handle(text: String): Response? {
        val match = responders.firstOrNull { matches(it, text) } ?: return null
        return Response(match.message, match.minDelayInMilliseconds, match.maxDelayInMilliseconds)
    }

    private fun matches(responder: Responder, text: String): Boolean =
        if (responder.regex) {
            Regex(responder.pattern).containsMatchIn(text)
        } else {
            text.contains(responder.pattern, ignoreCase = false)
        }
}

/**
 * Flux de validation "Chat Games »" (spec AutoResponder) :
 * 1. Ne traite que les messages deja filtres sur le prefixe "Chat Games »" (fait par l'appelant).
 * 2. Cherche une entree dont le trigger est contenu dans le message.
 * 3. Notifie le joueur en local (actionbar, jamais envoye au serveur -> ephemere).
 * 4. Renvoie une Response "commande" a delai fixe qui embarque {response}/{timer} deja resolus,
 *    pour que le timer affiche dans la commande corresponde exactement au delai reellement attendu.
 */
class ChatGamesValidationHandler(
    private val questions: List<ValidationQuestion>,
    private val commandTemplate: String
) : IChatHandler {

    override fun handle(text: String): Response? {
        val entry = questions.firstOrNull { text.contains(it.trigger, ignoreCase = true) } ?: return null

        // Resolu ici (cooldown de base inclus) pour que {timer} corresponde exactement au delai reel.
        val delay = MessageScheduler.resolveDelay(entry.mindelay, entry.maxdelay)

        Notifications.show("autoresponder.msg.answer_found")

        val command = commandTemplate
            .replace("{response}", entry.response)
            .replace("{timer}", delay.toString())
            .removePrefix("/")

        return Response(command, delay, delay, isCommand = true, delayResolved = true)
    }
}
