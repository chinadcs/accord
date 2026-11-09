package app.accord.core.notification

import app.accord.core.Money
import app.accord.core.MoneyParse
import java.text.Normalizer

/**
 * Proposta extraída de uma notificação de banco (ADR 0069, accord-bced). Só estes campos sobrevivem à leitura: o texto
 * da notificação nunca é guardado. [description] vazia significa que o texto não deixou claro o estabelecimento ou a pessoa.
 */
data class NotificationProposal(
    val cents: Long,
    val description: String,
    val direction: Direction,
    val viaPix: Boolean,
) {
    enum class Direction { Expense, Income }
}

/**
 * Lê o título e o texto de uma notificação de app bancário e devolve uma proposta, ou nada. É deliberadamente conservador:
 * sem valor em reais, sem verbo que diga se o dinheiro saiu ou entrou, ou com palavra de estorno/cancelamento, não propõe
 * (créditos de estorno têm regra própria e não viram renda). Os padrões são hipóteses a validar com amostras reais de
 * cada banco; o produto só tem uma amostra do Itaú (extrato OFX e fatura PDF), nenhuma de notificação. Toda proposta passa
 * por revisão humana antes de virar lançamento.
 */
object BankNotificationParser {
    /** Palavras que qualificam a notificação como movimentação (ADR 0069). A pessoa não as edita nesta primeira entrega. */
    val KEYWORDS: List<String> = listOf("r$", "compra", "pix", "débito", "pagamento", "transferência")

    private const val MAX_TEXT = 600
    private const val MAX_DESCRIPTION = 40

    private val AMOUNT = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?""", RegexOption.IGNORE_CASE)
    private val NOT_A_MOVEMENT_AMOUNT = Regex("""(saldo|limite|dispon[ií]vel|fatura|total)\W{0,12}$""", RegexOption.IGNORE_CASE)
    private val INCOME = Regex("""\b(recebeu|recebido|recebida|recebemos|dep[oó]sito)\b""", RegexOption.IGNORE_CASE)
    private val EXPENSE = Regex("""\b(compra|comprou|pagou|pagamento|enviou|enviado|enviada|d[eé]bito|debitado|saque|transfer[eê]ncia)\b""", RegexOption.IGNORE_CASE)
    private val REFUND = Regex("""\b(estorno|estornado|estornada|reembolso|cancelad[ao]|cancelamento|devolu[cç][aã]o)\b""", RegexOption.IGNORE_CASE)
    private val PIX = Regex("""\bpix\b""", RegexOption.IGNORE_CASE)

    // Nome depois de preposição, até pontuação, horário, data ou outra preposição de contexto.
    private val SUBJECT = Regex(
        """\b(?:em|no|na|para|de|do|da)\s+((?:[\p{Lu}\d][^\s.,;!?]*)(?:\s+[^\s.,;!?]+){0,5}?)(?=\s*[.,;!?]|\s+(?:[àa]s|em|no|na|via|com|vlr|valor|final|saldo|dia)\b|\s+\d{2}/\d{2}|\s*$)""",
    )
    private val BLOCKED_SUBJECT_START = Regex("""^(R\$|Pix\b|Itaú|Itau|Nubank|Inter|Bradesco|Santander|Caixa|Banco|Cartão|Cartao|Conta|Sua|Seu|Você|Voce)""", RegexOption.IGNORE_CASE)

    /** O texto (título e corpo) contém alguma das [KEYWORDS], sem diferenciar maiúsculas nem acentos. */
    fun matchesKeywords(text: String): Boolean {
        val folded = fold(text)
        return KEYWORDS.any { folded.contains(fold(it)) }
    }

    fun parse(title: CharSequence?, body: CharSequence?): NotificationProposal? {
        val text = listOfNotNull(title, body).joinToString(" ") { it.toString() }
            .replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)
        if (text.isEmpty() || !matchesKeywords(text) || REFUND.containsMatchIn(text)) return null

        val amountMatch = AMOUNT.findAll(text).firstOrNull { match ->
            !NOT_A_MOVEMENT_AMOUNT.containsMatchIn(text.substring(0, match.range.first).takeLast(24))
        } ?: return null
        val cents = ((Money.parse(amountMatch.groupValues[1] + amountMatch.groupValues[2].let { if (it.isEmpty()) "" else ",$it" }) as? MoneyParse.Ok)
            ?.money?.cents ?: return null).takeIf { it > 0 } ?: return null

        val income = INCOME.containsMatchIn(text)
        val expense = EXPENSE.containsMatchIn(text)
        val direction = when {
            income && !expense -> NotificationProposal.Direction.Income
            expense && !income -> NotificationProposal.Direction.Expense
            else -> return null // sem verbo claro ou com os dois: não adivinhar o sentido do dinheiro
        }

        val withoutAmount = text.removeRange(amountMatch.range)
        val subject = SUBJECT.findAll(withoutAmount).map { it.groupValues[1].trim() }
            .firstOrNull { it.isNotEmpty() && !BLOCKED_SUBJECT_START.containsMatchIn(it) && it.any(Char::isLetter) }
            ?.take(MAX_DESCRIPTION)?.trimEnd().orEmpty()
        return NotificationProposal(cents, subject, direction, viaPix = PIX.containsMatchIn(text))
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
