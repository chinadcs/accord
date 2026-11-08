package app.accord.core.chat

import app.accord.core.Money
import java.time.LocalDate

/**
 * Campos normalizados do rascunho local e do registro manual. Só o que o app já leu e validou:
 * o texto da mensagem não entra (S3 §12).
 */
object DraftCodec {
    fun toFields(entry: PartialEntry): Map<String, String> = buildMap {
        put("nature", entry.nature.name)
        entry.description?.let { put("description", it) }
        entry.amount?.let { put("amountCents", it.cents.toString()) }
        entry.role?.let { put("role", it.name) }
        put("installments", entry.installments.toString())
        entry.date?.let { put("date", it.toString()) }
        entry.categoryId?.let { put("categoryId", it) }
        entry.payment?.let { put("payment", it.name) }
        entry.cardId?.let { put("cardId", it) }
        entry.accountId?.let { put("accountId", it) }
        entry.spentBy?.let { put("spentBy", it) }
        if (entry.issues.isNotEmpty()) put("issues", entry.issues.entries.joinToString(",") { "${it.key.name}:${it.value.name}" })
    }

    fun fromFields(fields: Map<String, String>): PartialEntry = PartialEntry(
        nature = fields["nature"]?.let { runCatching { Nature.valueOf(it) }.getOrNull() } ?: Nature.Expense,
        description = fields["description"],
        amount = fields["amountCents"]?.toLongOrNull()?.let(::Money),
        role = fields["role"]?.let { runCatching { AmountRole.valueOf(it) }.getOrNull() },
        installments = fields["installments"]?.toIntOrNull() ?: 1,
        date = fields["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        categoryId = fields["categoryId"],
        payment = fields["payment"]?.let { runCatching { ChatPayment.valueOf(it) }.getOrNull() },
        accountId = fields["accountId"],
        cardId = fields["cardId"],
        spentBy = fields["spentBy"],
        issues = fields["issues"]?.split(",")?.mapNotNull { pair ->
            val field = runCatching { QuestionField.valueOf(pair.substringBefore(':')) }.getOrNull()
            val reason = runCatching { ChatQuestion.Reason.valueOf(pair.substringAfter(':')) }.getOrNull()
            if (field != null && reason != null) field to reason else null
        }?.toMap().orEmpty(),
    )

    /** Campos que ainda faltam para efetivar, para o rascunho da fachada. */
    fun missing(question: ChatQuestion): List<String> = listOf(question.field.name)
}

/** Opções de uma pergunta como texto, para guardar no histórico local. */
object OptionCodec {
    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(options: List<QuestionOption>): String =
        options.joinToString(RECORD.toString()) { listOf(it.ref, it.label, encodeValue(it.value)).joinToString(UNIT.toString()) }

    fun decode(text: String): List<QuestionOption> =
        if (text.isEmpty()) {
            emptyList()
        } else {
            text.split(RECORD).mapNotNull { record ->
                val parts = record.split(UNIT)
                if (parts.size != 3) null else decodeValue(parts[2])?.let { QuestionOption(parts[0], parts[1], it) }
            }
        }

    fun encodeValue(value: OptionValue): String = when (value) {
        is OptionValue.Category -> "cat:${value.id}"
        OptionValue.Card -> "card"
        is OptionValue.Account -> "acc:${value.id}"
        OptionValue.Cash -> "cash"
        is OptionValue.Role -> "role:${value.role.name}"
        is OptionValue.Day -> "day:${value.date}"
        is OptionValue.Receipt -> "rec:${value.receiptId}"
    }

    fun decodeValue(text: String): OptionValue? = when {
        text.startsWith("cat:") -> OptionValue.Category(text.removePrefix("cat:"))
        text == "card" -> OptionValue.Card
        text.startsWith("acc:") -> OptionValue.Account(text.removePrefix("acc:"))
        text == "cash" -> OptionValue.Cash
        text.startsWith("role:") -> runCatching { OptionValue.Role(AmountRole.valueOf(text.removePrefix("role:"))) }.getOrNull()
        text.startsWith("day:") -> runCatching { OptionValue.Day(LocalDate.parse(text.removePrefix("day:"))) }.getOrNull()
        text.startsWith("rec:") -> OptionValue.Receipt(text.removePrefix("rec:"))
        else -> null
    }
}

/** Correção pendente de uma resposta (alvo ou conta), como texto, para guardar junto da pergunta. */
object CorrectionCodec {
    fun encode(request: CorrectionRequest): String = listOf(
        request.receiptId.orEmpty(),
        request.entryId.orEmpty(),
        when (val change = request.change) {
            is EntryChange.Amount -> "amount:${change.amount.cents}"
            is EntryChange.Date -> "date:${change.date}"
            is EntryChange.Description -> "description:${change.text}"
            is EntryChange.Category -> "category:${change.categoryId}"
            is EntryChange.Payment -> "payment:${change.payment.name}:${change.accountId.orEmpty()}"
        },
    ).joinToString("\u001F")

    fun decode(text: String): CorrectionRequest? {
        val parts = text.split('\u001F')
        if (parts.size != 3) return null
        val change = parts[2]
        val value = change.substringAfter(':')
        val parsed: EntryChange = when (change.substringBefore(':')) {
            "amount" -> EntryChange.Amount(Money(value.toLongOrNull() ?: return null))
            "date" -> EntryChange.Date(runCatching { LocalDate.parse(value) }.getOrNull() ?: return null)
            "description" -> EntryChange.Description(value)
            "category" -> EntryChange.Category(value)
            "payment" -> EntryChange.Payment(
                runCatching { ChatPayment.valueOf(value.substringBefore(':')) }.getOrNull() ?: return null,
                value.substringAfter(':').ifEmpty { null },
            )
            else -> return null
        }
        return CorrectionRequest(parts[0].ifEmpty { null }, parts[1].ifEmpty { null }, parsed)
    }
}
