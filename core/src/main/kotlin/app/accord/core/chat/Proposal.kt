package app.accord.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Expressão de data como o modelo a vê; quem a resolve é o app, com relógio e fuso (S3 §5, etapa 3). */
enum class DateKind { Today, Yesterday, DayBeforeYesterday, DayOfMonth, NextMonth, Explicit }

data class DateExpression(val kind: DateKind, val text: String)

enum class CorrectionField { Amount, Date, Description, Category, Payment, Account }

enum class UnsupportedType { Recurrence, Boleto, Refund, InvoicePayment, Cancellation }

/** Catálogo fechado das consultas do Chat (S6). Pedido fora dele é rejeitado, nunca respondido por aproximação. */
enum class ConsultType { CategorySpend, PurchaseSchedule, MonthCommitments, TodayAllowance, WeekAllowance }

/** Critério de data pedido: mês do orçamento ou data original da compra. Ausente, o app assume o orçamento e diz isso. */
enum class ConsultCriterion { Budget, PurchaseDate }

/**
 * Conjunto limitado de ações que o interpretador pode propor (S3 §4). Tudo que é texto é um
 * Trecho (parte literal da mensagem) e tudo que é referência é uma `ref` curta do contexto.
 * Não há campo para mês, fatura, parcela calculada, total derivado, saldo ou confiança.
 */
sealed interface Proposal {
    data class Register(
        val nature: Nature? = null,
        val description: String? = null,
        val amount: String? = null,
        val role: AmountRole? = null,
        val installments: String? = null,
        val date: DateExpression? = null,
        val category: String? = null,
        val payment: ChatPayment? = null,
        val account: String? = null,
        val person: String? = null,
    ) : Proposal

    data class Correct(
        val target: String? = null,
        val field: CorrectionField? = null,
        val text: String? = null,
        val date: DateExpression? = null,
        val category: String? = null,
        val payment: ChatPayment? = null,
        val account: String? = null,
    ) : Proposal

    data class Answer(
        val question: String? = null,
        val choice: String? = null,
        val text: String? = null,
        val date: DateExpression? = null,
    ) : Proposal

    /**
     * Pedido de esclarecimento sobre [field], com o que já foi entendido em [entry] (S3 §4). [options]
     * são referências sugeridas; o validador decide sozinho o que falta e o que perguntar.
     */
    data class Clarify(
        val field: QuestionField? = null,
        val options: List<String> = emptyList(),
        val entry: Register? = null,
    ) : Proposal

    /**
     * Consulta de leitura do catálogo fechado (S6 §"Consulta pelo Chat"). [month] e [target] são trechos literais
     * da mensagem, [category] é uma `ref` do contexto e [date] só desambigua a compra-alvo. O número vem da
     * fachada, nunca da proposta.
     */
    data class Consult(
        val type: ConsultType,
        val month: String? = null,
        val criterion: ConsultCriterion? = null,
        val category: String? = null,
        val target: String? = null,
        val date: DateExpression? = null,
    ) : Proposal

    data object Query : Proposal
    data object Intent : Proposal
    data class Unsupported(val type: UnsupportedType) : Proposal
    data object Uninterpretable : Proposal
}

sealed interface ProposalParse {
    data class Ok(val proposal: Proposal) : ProposalParse

    /** [reason] é categórico e nunca carrega o texto da saída (S3 §14). */
    data class Invalid(val reason: String) : ProposalParse
}

/**
 * Lê e escreve a saída do interpretador em JSON (S3 §5, etapa 1): ação conhecida, só os campos
 * permitidos para ela, tipos corretos e tamanhos máximos. Qualquer coisa fora do esquema é inválida,
 * inclusive texto que lembre SQL: não existe executor de texto livre.
 */
object ProposalCodec {
    const val MAX_OUTPUT = 4_000
    const val MAX_TEXT = 80
    const val MAX_OPTIONS = 12

    private val allowed = mapOf(
        "register" to setOf("nature", "description", "amount", "role", "installments", "date", "category", "payment", "account", "person"),
        "correct" to setOf("target", "field", "text", "date", "category", "payment", "account"),
        "answer" to setOf("question", "choice", "text", "date"),
        "clarify" to setOf("field", "options", "entry"),
        "consult" to setOf("type", "month", "criterion", "category", "target", "date"),
        "query" to emptySet(),
        "intent" to emptySet(),
        "unsupported" to setOf("type"),
        "none" to emptySet(),
    )

    fun decode(raw: String): ProposalParse {
        if (raw.length > MAX_OUTPUT) return invalid("tamanho")
        val root = try {
            Json.parseToJsonElement(raw.trim())
        } catch (_: Exception) {
            return invalid("json")
        }
        val obj = root as? JsonObject ?: return invalid("objeto")
        val action = (obj["action"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return invalid("acao")
        val fields = allowed[action] ?: return invalid("acao")
        if (obj.keys.any { it != "action" && it !in fields }) return invalid("campo extra")
        return try {
            ProposalParse.Ok(build(action, obj))
        } catch (e: BadField) {
            invalid(e.field)
        }
    }

    private class BadField(val field: String) : Exception(field)

    private fun invalid(reason: String) = ProposalParse.Invalid(reason)

    private fun JsonObject.text(key: String): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: throw BadField(key)
        if (!primitive.isString) throw BadField(key)
        val content = primitive.content
        if (content.isBlank() || content.length > MAX_TEXT) throw BadField(key)
        return content
    }

    private fun <E : Enum<E>> JsonObject.enum(key: String, names: Map<String, E>): E? =
        text(key)?.let { names[it] ?: throw BadField(key) }

    private fun JsonObject.dateExpression(key: String): DateExpression? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        val inner = value as? JsonObject ?: throw BadField(key)
        if (inner.keys.any { it != "kind" && it != "text" }) throw BadField(key)
        val kind = inner.enum("kind", DATE_KINDS) ?: throw BadField(key)
        val text = inner.text("text") ?: throw BadField(key)
        return DateExpression(kind, text)
    }

    private fun build(action: String, obj: JsonObject): Proposal = when (action) {
        "register" -> Proposal.Register(
            nature = obj.enum("nature", NATURES),
            description = obj.text("description"),
            amount = obj.text("amount"),
            role = obj.enum("role", ROLES),
            installments = obj.text("installments"),
            date = obj.dateExpression("date"),
            category = obj.text("category"),
            payment = obj.enum("payment", PAYMENTS),
            account = obj.text("account"),
            person = obj.text("person"),
        )
        "correct" -> Proposal.Correct(
            target = obj.text("target"),
            field = obj.enum("field", CORRECTION_FIELDS),
            text = obj.text("text"),
            date = obj.dateExpression("date"),
            category = obj.text("category"),
            payment = obj.enum("payment", PAYMENTS),
            account = obj.text("account"),
        )
        "answer" -> Proposal.Answer(
            question = obj.text("question"),
            choice = obj.text("choice"),
            text = obj.text("text"),
            date = obj.dateExpression("date"),
        )
        "clarify" -> Proposal.Clarify(field = obj.enum("field", QUESTION_FIELDS), options = options(obj), entry = nestedEntry(obj))
        "consult" -> Proposal.Consult(
            type = obj.enum("type", CONSULT_TYPES) ?: throw BadField("type"),
            month = obj.text("month"),
            criterion = obj.enum("criterion", CONSULT_CRITERIA),
            category = obj.text("category"),
            target = obj.text("target"),
            date = obj.dateExpression("date"),
        )
        "query" -> Proposal.Query
        "intent" -> Proposal.Intent
        "unsupported" -> Proposal.Unsupported(obj.enum("type", UNSUPPORTED) ?: throw BadField("type"))
        else -> Proposal.Uninterpretable
    }

    private fun nestedEntry(obj: JsonObject): Proposal.Register? {
        val value = obj["entry"] ?: return null
        if (value is JsonNull) return null
        val inner = value as? JsonObject ?: throw BadField("entry")
        if (inner.keys.any { it !in allowed.getValue("register") }) throw BadField("entry")
        return build("register", inner) as Proposal.Register
    }

    private fun options(obj: JsonObject): List<String> {
        val value = obj["options"] ?: return emptyList()
        if (value is JsonNull) return emptyList()
        val array = value as? JsonArray ?: throw BadField("options")
        if (array.size > MAX_OPTIONS) throw BadField("options")
        return array.map { item ->
            val primitive = item as? JsonPrimitive ?: throw BadField("options")
            if (!primitive.isString || primitive.content.isBlank() || primitive.content.length > MAX_TEXT) throw BadField("options")
            primitive.content
        }
    }

    /** Saída no formato do contrato; usada pelos fakes e pela bateria. */
    fun encode(proposal: Proposal): String = encodeElement(proposal).toString()

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRegister(proposal: Proposal.Register) {
        proposal.nature?.let { put("nature", NATURES.keyOf(it)) }
        proposal.description?.let { put("description", it) }
        proposal.amount?.let { put("amount", it) }
        proposal.role?.let { put("role", ROLES.keyOf(it)) }
        proposal.installments?.let { put("installments", it) }
        proposal.date?.let { put("date", dateElement(it)) }
        proposal.category?.let { put("category", it) }
        proposal.payment?.let { put("payment", PAYMENTS.keyOf(it)) }
        proposal.account?.let { put("account", it) }
        proposal.person?.let { put("person", it) }
    }

    private fun encodeElement(proposal: Proposal): JsonElement = buildJsonObject {
        when (proposal) {
            is Proposal.Register -> {
                put("action", "register")
                putRegister(proposal)
            }
            is Proposal.Correct -> {
                put("action", "correct")
                proposal.target?.let { put("target", it) }
                proposal.field?.let { put("field", CORRECTION_FIELDS.keyOf(it)) }
                proposal.text?.let { put("text", it) }
                proposal.date?.let { put("date", dateElement(it)) }
                proposal.category?.let { put("category", it) }
                proposal.payment?.let { put("payment", PAYMENTS.keyOf(it)) }
                proposal.account?.let { put("account", it) }
            }
            is Proposal.Answer -> {
                put("action", "answer")
                proposal.question?.let { put("question", it) }
                proposal.choice?.let { put("choice", it) }
                proposal.text?.let { put("text", it) }
                proposal.date?.let { put("date", dateElement(it)) }
            }
            is Proposal.Clarify -> {
                put("action", "clarify")
                proposal.field?.let { put("field", QUESTION_FIELDS.keyOf(it)) }
                put("options", buildJsonArray { proposal.options.forEach { add(JsonPrimitive(it)) } })
                proposal.entry?.let { entry -> put("entry", buildJsonObject { putRegister(entry) }) }
            }
            is Proposal.Consult -> {
                put("action", "consult")
                put("type", CONSULT_TYPES.keyOf(proposal.type))
                proposal.month?.let { put("month", it) }
                proposal.criterion?.let { put("criterion", CONSULT_CRITERIA.keyOf(it)) }
                proposal.category?.let { put("category", it) }
                proposal.target?.let { put("target", it) }
                proposal.date?.let { put("date", dateElement(it)) }
            }
            Proposal.Query -> put("action", "query")
            Proposal.Intent -> put("action", "intent")
            is Proposal.Unsupported -> {
                put("action", "unsupported")
                put("type", UNSUPPORTED.keyOf(proposal.type))
            }
            Proposal.Uninterpretable -> put("action", "none")
        }
    }

    private fun dateElement(expression: DateExpression) = buildJsonObject {
        put("kind", DATE_KINDS.keyOf(expression.kind))
        put("text", expression.text)
    }

    private fun <E : Enum<E>> Map<String, E>.keyOf(value: E) = entries.first { it.value == value }.key

    private val CONSULT_TYPES = mapOf(
        "category_spend" to ConsultType.CategorySpend,
        "purchase_schedule" to ConsultType.PurchaseSchedule,
        "month_commitments" to ConsultType.MonthCommitments,
        "today_allowance" to ConsultType.TodayAllowance,
        "week_allowance" to ConsultType.WeekAllowance,
    )
    private val CONSULT_CRITERIA = mapOf("budget" to ConsultCriterion.Budget, "purchase_date" to ConsultCriterion.PurchaseDate)
    private val NATURES = mapOf("expense" to Nature.Expense, "income" to Nature.Income)
    private val ROLES = mapOf("single" to AmountRole.Single, "total" to AmountRole.Total, "installment" to AmountRole.PerInstallment)
    private val PAYMENTS = mapOf("card" to ChatPayment.Card, "pix" to ChatPayment.Pix, "debit" to ChatPayment.Debit, "cash" to ChatPayment.Cash)
    private val DATE_KINDS = mapOf(
        "today" to DateKind.Today,
        "yesterday" to DateKind.Yesterday,
        "day_before_yesterday" to DateKind.DayBeforeYesterday,
        "day_of_month" to DateKind.DayOfMonth,
        "next_month" to DateKind.NextMonth,
        "explicit" to DateKind.Explicit,
    )
    private val CORRECTION_FIELDS = mapOf(
        "amount" to CorrectionField.Amount,
        "date" to CorrectionField.Date,
        "description" to CorrectionField.Description,
        "category" to CorrectionField.Category,
        "payment" to CorrectionField.Payment,
        "account" to CorrectionField.Account,
    )
    private val QUESTION_FIELDS = mapOf(
        "amount" to QuestionField.Amount,
        "role" to QuestionField.AmountRole,
        "date" to QuestionField.Date,
        "payment" to QuestionField.Payment,
        "account" to QuestionField.Account,
        "category" to QuestionField.Category,
        "target" to QuestionField.Target,
    )
    private val UNSUPPORTED = mapOf(
        "recurrence" to UnsupportedType.Recurrence,
        "boleto" to UnsupportedType.Boleto,
        "refund" to UnsupportedType.Refund,
        "invoice_payment" to UnsupportedType.InvoicePayment,
        "cancellation" to UnsupportedType.Cancellation,
    )
}
