package app.accord.core.chat

/**
 * Validator 20 / profile 17: "o último foi 52 não 45" and "errei, não foi 40 foi 45" say which value is right. The negated alternative
 * is removed before reading the evidence, so the affirmed value is the only candidate. Only a correction (it needs a correction cue),
 * only when both sides are the same kind of value (amount, payment or date), and never for installments, which the Chat cannot correct.
 */
internal object NegatedCorrection {
    private val cue = Regex("\\b(?:foi|era|na verdade|na vdd|errei|corrig\\w*|ajust\\w*|troc\\w*|mud\\w*|o certo|o correto|quis dizer)\\b")
    private val installments = Regex("\\d\\s*x\\b|\\bparcela|\\bvezes\\b")
    private val suffix = Regex("^(.+?)[,;]?\\s+n[aã]o\\s+(?:(?:foi|era|é)\\s+)?([^,;]{1,40})$", RegexOption.IGNORE_CASE)
    private val prefix = Regex("^(.*?)\\bn[aã]o\\s+(?:foi|era|é)\\s+([^,;]{1,40}?)[,;]?\\s+(?:foi|era|é)\\s+(.+)$", RegexOption.IGNORE_CASE)

    fun resolve(text: String): String {
        prefix.find(text.trim())?.let { m ->
            val (before, negated, affirmed) = m.destructured
            if (compatible(negated, affirmed) && cue.containsMatchIn(ChatText.fold(before + "foi "))) return before + "foi " + affirmed
        }
        suffix.find(text.trim())?.let { m ->
            val (head, negated) = m.destructured
            if (cue.containsMatchIn(ChatText.fold(head)) && compatible(head, negated)) return head
        }
        return text
    }

    private fun kinds(raw: String): Set<String> {
        val s = ChatText.fold(raw)
        if (installments.containsMatchIn(s)) return setOf("installments")
        return buildSet {
            if (Regex("\\d").containsMatchIn(s) && !Regex("\\bdia\\s+\\d").containsMatchIn(s)) add("amount")
            if (ChatPayment.entries.any { ChatText.mentionsPayment(raw, it) }) add("payment")
            if (Regex("\\b(?:ontem|anteontem|hoje)\\b|\\bdia\\s+\\d|\\d{1,2}/\\d{1,2}").containsMatchIn(s)) add("date")
        }
    }

    private fun compatible(a: String, b: String): Boolean {
        val ka = kinds(a); val kb = kinds(b)
        return "installments" !in ka && "installments" !in kb && ka.intersect(kb).isNotEmpty()
    }
}
