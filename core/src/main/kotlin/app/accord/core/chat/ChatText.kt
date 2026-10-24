package app.accord.core.chat

import app.accord.core.Money
import app.accord.core.MoneyParse
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

sealed interface AmountRead {
    data class Ok(val money: Money) : AmountRead

    /** "39.90", "1,200": o app não adivinha, pergunta. */
    data object Ambiguous : AmountRead

    /** Zero, negativo ou ilegível. */
    data object Invalid : AmountRead
}

sealed interface DateRead {
    data class Ok(val date: LocalDate) : DateRead

    /** Dia inexistente, formato ilegível ou data que o app não resolve. */
    data object Invalid : DateRead
}

/** Marcador explícito de parcelamento no texto: define o papel do valor sem depender do modelo. */
data class InstallmentMarker(val role: AmountRole, val installments: Int)

/**
 * Leitura determinística do texto digitado (S3 §5, etapa 3). Dinheiro, parcelas e datas são lidos
 * pelo app a partir de trechos literais da mensagem; o modelo só aponta onde estão.
 */
object ChatText {
    private const val MAX_INSTALLMENTS = 120

    /** Minúsculas, sem acentos e com espaços normalizados, para comparar sem diferenciar a grafia. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * O Trecho precisa existir na mensagem. Número só vale como número inteiro do texto: "150" não
     * está em "1500" nem em "150,50", e "200" não está em "1.200".
     */
    fun anchored(text: String, excerpt: String): Boolean {
        val haystack = fold(text)
        val needle = fold(excerpt)
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return false
            if (!needle.first().isDigit() && !needle.last().isDigit()) return true
            if (boundaryOk(haystack, at, needle.length, needle)) return true
            from = at + 1
        }
    }

    private fun boundaryOk(haystack: String, at: Int, length: Int, needle: String): Boolean {
        if (needle.first().isDigit()) {
            val before = haystack.getOrNull(at - 1)
            if (before != null && before.isDigit()) return false
            if (before != null && (before == '.' || before == ',') && haystack.getOrNull(at - 2)?.isDigit() == true) return false
        }
        if (needle.last().isDigit()) {
            val after = haystack.getOrNull(at + length)
            if (after != null && after.isDigit()) return false
            if (after != null && (after == '.' || after == ',') && haystack.getOrNull(at + length + 1)?.isDigit() == true) return false
        }
        return true
    }

    fun readAmount(excerpt: String): AmountRead {
        val cleaned = fold(excerpt).replace("r$", "").replace("reais", "").replace("real", "").replace(" ", "")
        return when (val parsed = Money.parse(cleaned)) {
            is MoneyParse.Ok -> if (parsed.money.cents > 0) AmountRead.Ok(parsed.money) else AmountRead.Invalid
            is MoneyParse.Error -> if (parsed.reason == MoneyParse.Reason.Ambiguous) AmountRead.Ambiguous else AmountRead.Invalid
        }
    }

    private val installmentsPattern = Regex("""^(\d{1,3})\s*(?:x|vezes|parcelas?)?$""")

    fun readInstallments(excerpt: String): Int? =
        installmentsPattern.find(fold(excerpt))?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..MAX_INSTALLMENTS }

    private val perInstallment = Regex("""(\d{1,3})\s*(?:parcelas?|vezes|x)\s+de\s+(?:r\$\s*)?\d""")
    private val totalInstallments = Regex("""\d(?:[\d.,]*)\s*(?:reais\s+)?(?:em|dividido em|parcelado em)\s+(\d{1,3})\s*(?:parcelas?|vezes|x)\b""")

    /**
     * "N parcelas de X" indica parcela; "X em Nx" indica total, sem pergunta (decisão de 23/10/2026).
     * Sem marcador explícito, o papel fica em aberto.
     */
    fun installmentMarker(text: String): InstallmentMarker? {
        val folded = fold(text)
        perInstallment.find(folded)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 2..MAX_INSTALLMENTS }?.let { return InstallmentMarker(AmountRole.PerInstallment, it) }
        }
        totalInstallments.find(folded)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 2..MAX_INSTALLMENTS }?.let { return InstallmentMarker(AmountRole.Total, it) }
        }
        return null
    }

    private val digits = Regex("""\d{1,2}""")
    private val explicitDate = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?""")

    /**
     * Resolve a expressão com a data local de America/Sao_Paulo. "Dia N" sem mês é o dia N passado
     * mais recente (hoje, se hoje é o dia N); dia que esse mês não tem é dúvida, não chute.
     */
    fun resolveDate(expression: DateExpression, today: LocalDate): DateRead = when (expression.kind) {
        DateKind.Today -> DateRead.Ok(today)
        DateKind.Yesterday -> DateRead.Ok(today.minusDays(1))
        DateKind.DayBeforeYesterday -> DateRead.Ok(today.minusDays(2))
        DateKind.NextMonth -> DateRead.Ok(today.plusMonths(1))
        DateKind.DayOfMonth -> dayOfMonth(expression.text, today)
        DateKind.Explicit -> explicit(expression.text, today)
    }

    private fun dayOfMonth(text: String, today: LocalDate): DateRead {
        val day = digits.find(fold(text))?.value?.toIntOrNull() ?: return DateRead.Invalid
        if (day !in 1..31) return DateRead.Invalid
        if (day <= today.dayOfMonth) return DateRead.Ok(today.withDayOfMonth(day))
        val previous = YearMonth.from(today).minusMonths(1)
        return if (day <= previous.lengthOfMonth()) DateRead.Ok(previous.atDay(day)) else DateRead.Invalid
    }

    private fun explicit(text: String, today: LocalDate): DateRead {
        val m = explicitDate.find(fold(text)) ?: return DateRead.Invalid
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val yearText = m.groupValues[3]
        return try {
            if (yearText.isNotEmpty()) {
                val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
                DateRead.Ok(LocalDate.of(year, month, day))
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                DateRead.Ok(if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear)
            }
        } catch (_: DateTimeException) {
            DateRead.Invalid
        }
    }

    private val marks = listOf(
        UnsupportedType.Recurrence to Regex("""todo mes|todos os meses|toda semana|mensal|recorrente|assinatura"""),
        UnsupportedType.Boleto to Regex("""boleto"""),
        UnsupportedType.Refund to Regex("""estorn|reembolso"""),
        UnsupportedType.InvoicePayment to Regex("""fatura"""),
        UnsupportedType.Cancellation to Regex("""cancel"""),
    )

    /** Marcas de ação fora do Chat no P0: com elas, nunca se registra como despesa comum (S3 §5). */
    fun unsupportedMark(text: String): UnsupportedType? {
        val folded = fold(text)
        return marks.firstOrNull { (_, pattern) -> pattern.containsMatchIn(folded) }?.first
    }

    private val undoByText = Regex("""^(?:desfaz(?:er)?|apaga(?:r)?|exclui(?:r)?|remove(?:r)?|cancela(?:r)?)(?: (?:esse|essa|isso|o ultimo|a ultima))?[.!]*$""")

    /** "desfaz", "apaga esse": o Chat não desfaz por texto (decisão de 24/10/2026). */
    fun isUndoByText(text: String): Boolean = undoByText.matches(fold(text))

    private val paymentWords = mapOf(
        ChatPayment.Card to Regex("""cart|credito|visa|master"""),
        ChatPayment.Pix to Regex("""pix"""),
        ChatPayment.Debit to Regex("""debito"""),
        ChatPayment.Cash to Regex("""dinheiro|especie|cash"""),
    )

    /** O meio de pagamento proposto precisa aparecer na frase: nunca se presume cartão. */
    fun mentionsPayment(text: String, payment: ChatPayment): Boolean =
        paymentWords.getValue(payment).containsMatchIn(fold(text))
}
