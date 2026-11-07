package app.accord.core.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Caso da bateria com gabarito para medir um modelo (S3 §2). [sensitive] marca as frases reais do
 * casal: seu texto nunca é registrado, só o identificador e categorias de erro (S3, Privacidade).
 */
class GradedCase(
    val id: String,
    val group: String,
    val text: String,
    val expected: ChatBattery.Expected,
    val sensitive: Boolean = false,
) {
    /** Frase curta da meta de 5 s: até 60 caracteres (decisão de 24/10/2026). */
    val short: Boolean get() = text.length <= ChatGrading.SHORT_PHRASE_MAX

    /**
     * Frase clara: o gabarito é salvar, corrigir ou responder sem dúvida. É nelas que o critério de
     * "aceita pelo validador porém errada" precisa ser zero.
     */
    val clear: Boolean get() = expected !is ChatBattery.Expected.Asks && expected !is ChatBattery.Expected.Refuses
}

/** Nota de um caso: o que acertou, campo a campo. `null` = o campo não se aplica ao caso. */
data class CaseGrade(
    val outcome: ChatBattery.Outcome,
    val decisionKind: String,
    val intent: Boolean,
    val amountRole: Boolean?,
    val date: Boolean?,
    val category: Boolean?,
    val correction: Boolean?,
    val unnecessaryQuestion: Boolean?,
    val refusedFormat: Boolean,
    val criticalWrong: Boolean = false,
)

data class Rate(val ok: Int, val total: Int) {
    val value: Double? get() = if (total == 0) null else ok.toDouble() / total

    fun percent(): String = value?.let { "%.1f%%".format(java.util.Locale.ROOT, it * 100) } ?: "n/a"
}

/** Um critério mínimo de qualidade (S3 §2, decisão de 24/10/2026) e se foi atendido. */
data class Criterion(val name: String, val required: String, val actual: String, val met: Boolean?)

data class QualitySummary(
    val total: Int,
    val clearTotal: Int,
    val intent: Rate,
    val amountRole: Rate,
    val date: Rate,
    val category: Rate,
    val correction: Rate,
    val unnecessaryQuestions: Rate,
    val acceptedWrongClear: Int,
    val acceptedWrongOther: Int,
    val refusedFormat: Int,
    val acceptedWrongAllClear: Int = acceptedWrongClear,
) {
    /** Critérios mínimos do usuário, definidos antes de ver o resultado (24/10/2026). */
    val criteria: List<Criterion>
        get() = listOf(
            atLeast("Intenção", intent, ChatGrading.MIN_INTENT),
            atLeast("Valor e papel", amountRole, ChatGrading.MIN_AMOUNT_ROLE),
            atLeast("Categoria", category, ChatGrading.MIN_CATEGORY),
            atLeast("Data", date, ChatGrading.MIN_DATE),
            Criterion(
                "Perguntas desnecessárias",
                "até ${percentOf(ChatGrading.MAX_UNNECESSARY)}",
                unnecessaryQuestions.percent(),
                unnecessaryQuestions.value?.let { it <= ChatGrading.MAX_UNNECESSARY },
            ),
            Criterion("Erros financeiros ou de alvo aceitos (frases claras)", "0", acceptedWrongClear.toString(), acceptedWrongClear == 0),
        )

    val allMet: Boolean get() = criteria.all { it.met == true }

    private fun atLeast(name: String, rate: Rate, minimum: Double) =
        Criterion(name, "mínimo ${percentOf(minimum)}", rate.percent(), rate.value?.let { it >= minimum })

    private fun percentOf(value: Double) = "%.0f%%".format(java.util.Locale.ROOT, value * 100)

    fun toJson(): JsonObject = buildJsonObject {
        put("total", total)
        put("criteriaVersion", ChatGrading.CRITERIA_VERSION)
        put("clearTotal", clearTotal)
        for ((name, rate) in listOf(
            "intent" to intent, "amountRole" to amountRole, "date" to date, "category" to category,
            "correction" to correction, "unnecessaryQuestions" to unnecessaryQuestions,
        )) {
            put(name, buildJsonObject {
                put("ok", rate.ok)
                put("total", rate.total)
            })
        }
        put("acceptedWrongClear", acceptedWrongClear)
        put("acceptedWrongAllClear", acceptedWrongAllClear)
        put("acceptedWrongOther", acceptedWrongOther)
        put("refusedFormat", refusedFormat)
        put("allMet", allMet)
        put("criteria", buildJsonArray {
            criteria.forEach { c ->
                add(buildJsonObject {
                    put("name", c.name)
                    put("required", c.required)
                    put("actual", c.actual)
                    put("met", c.met?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                })
            }
        })
    }
}

/**
 * Nota de qualidade da Etapa 0 (S3 §2, Qualidade): acerto de intenção, valor, papel, data, categoria e
 * correção, perguntas desnecessárias e propostas aceitas pelo validador porém erradas. A autoavaliação do
 * modelo não é medida nem usada.
 */
object ChatGrading {
    // v2 alinha o contador crítico ao S3 §2 (autorização explícita em 25/10/2026).
    const val CRITERIA_VERSION = 2
    const val SHORT_PHRASE_MAX = 60
    const val MIN_INTENT = 0.90
    const val MIN_AMOUNT_ROLE = 0.90
    const val MIN_CATEGORY = 0.80
    const val MIN_DATE = 0.80
    const val MAX_UNNECESSARY = 0.15

    /**
     * Casos da bateria fictícia que medem o MODELO. O grupo Robustez fica de fora: seus casos fixam a saída
     * ruim (JSON inválido, SQL, número cru) para testar o validador, o que um modelo real não reproduz.
     */
    fun fictitiousCases(): List<GradedCase> =
        ChatBattery.cases.filter { it.group != "Robustez" }.map { GradedCase(it.id, it.group, it.text, it.expected) }

    fun grade(case: GradedCase, decision: ChatDecision): CaseGrade {
        val expected = case.expected
        val outcome = ChatBattery.grade(expected, decision)
        val register = decision as? ChatDecision.Register
        val asked = (decision as? ChatDecision.Ask)?.question?.field

        val amountRole = when {
            expected is ChatBattery.Expected.Registers -> register?.entry?.let {
                it.amount.cents == expected.cents && it.installments == expected.installments && it.role == expected.role
            } ?: false
            expected is ChatBattery.Expected.Asks && expected.field in AMOUNT_FIELDS -> asked == expected.field
            else -> null
        }
        val date = when {
            expected is ChatBattery.Expected.Registers -> register?.entry?.let { it.date == expected.date } ?: false
            expected is ChatBattery.Expected.Asks && expected.field == QuestionField.Date -> asked == QuestionField.Date
            else -> null
        }
        val category = when (expected) {
            is ChatBattery.Expected.Registers -> register?.entry?.let { it.categoryId == expected.categoryId } ?: false
            else -> null
        }
        val correction = when (expected) {
            is ChatBattery.Expected.Corrects -> (decision as? ChatDecision.Correct)?.plan?.let {
                it.change == expected.change && it.receiptId == expected.receiptId && it.entryId == expected.entryId
            } == true
            else -> null
        }
        val askedAnything = decision is ChatDecision.Ask || decision is ChatDecision.AskCorrection
        val saved = decision is ChatDecision.Register || decision is ChatDecision.Correct
        val criticalWrong = when (expected) {
            is ChatBattery.Expected.Registers -> if (register == null) saved else register.entry.let { entry ->
                entry.amount.cents != expected.cents || entry.installments != expected.installments || entry.role != expected.role ||
                    entry.date != expected.date || entry.nature != expected.nature || entry.payment != expected.payment || entry.accountId != expected.accountId
            }
            is ChatBattery.Expected.Corrects -> if (decision !is ChatDecision.Correct) saved else
                decision.plan.receiptId != expected.receiptId || decision.plan.entryId != expected.entryId || decision.plan.change != expected.change
            else -> saved
        }
        val unnecessary = when (expected) {
            is ChatBattery.Expected.Asks -> null
            else -> askedAnything
        }
        return CaseGrade(
            outcome = outcome,
            decisionKind = kindOf(decision),
            intent = intentOf(expected) == intentOf(decision),
            amountRole = amountRole,
            date = date,
            category = category,
            correction = correction,
            unnecessaryQuestion = unnecessary,
            refusedFormat = decision is ChatDecision.Refuse && decision.reason == RefuseReason.BadFormat,
            criticalWrong = criticalWrong,
        )
    }

    private val AMOUNT_FIELDS = setOf(QuestionField.Amount, QuestionField.AmountRole)

    private fun intentOf(expected: ChatBattery.Expected) = when (expected) {
        is ChatBattery.Expected.Registers, is ChatBattery.Expected.Asks -> "register"
        is ChatBattery.Expected.Corrects -> "correct"
        is ChatBattery.Expected.Replies -> "reply:${expected.reply.name}"
        ChatBattery.Expected.Refuses -> "refuse"
    }

    private fun intentOf(decision: ChatDecision) = when (decision) {
        is ChatDecision.Register, is ChatDecision.Ask -> "register"
        is ChatDecision.Correct, is ChatDecision.AskCorrection, is ChatDecision.OpenEdit -> "correct"
        is ChatDecision.Reply -> "reply:${decision.reply.name}"
        is ChatDecision.Refuse -> "refuse"
        is ChatDecision.Answer -> "answer"
        is ChatDecision.Consult, is ChatDecision.ConsultAsk -> "consult"
    }

    /** Rótulo categórico da decisão, sem texto da conversa: serve para registrar casos sensíveis. */
    fun kindOf(decision: ChatDecision): String = when (decision) {
        is ChatDecision.Register -> "register"
        is ChatDecision.Ask -> "ask:${decision.question.field.name}"
        is ChatDecision.Correct -> "correct"
        is ChatDecision.AskCorrection -> "ask_correction:${decision.question.field.name}"
        is ChatDecision.Answer -> "answer"
        is ChatDecision.Reply -> "reply:${decision.reply.name}"
        is ChatDecision.OpenEdit -> "open_edit"
        is ChatDecision.Refuse -> "refuse:${decision.reason.name}"
        is ChatDecision.Consult -> "consult:${decision.request.type.name}"
        is ChatDecision.ConsultAsk -> "consult_ask:${decision.ask.reason.name}"
    }

    fun summarize(cases: List<GradedCase>, grades: List<CaseGrade>): QualitySummary {
        require(cases.size == grades.size) { "um resultado por caso" }
        fun rate(pick: (CaseGrade) -> Boolean?): Rate {
            val applicable = grades.mapNotNull(pick)
            return Rate(applicable.count { it }, applicable.size)
        }
        val wrong = cases.zip(grades).filter { (_, g) -> g.outcome == ChatBattery.Outcome.AcceptedWrong }
        val unnecessary = rate { it.unnecessaryQuestion }
        return QualitySummary(
            total = cases.size,
            clearTotal = cases.count { it.clear },
            intent = Rate(grades.count { it.intent }, grades.size),
            amountRole = rate { it.amountRole },
            date = rate { it.date },
            category = rate { it.category },
            correction = rate { it.correction },
            // Perguntas desnecessárias: `ok` conta as perguntas feitas onde o gabarito não pedia pergunta.
            unnecessaryQuestions = unnecessary,
            acceptedWrongClear = cases.zip(grades).count { (c, g) -> c.clear && g.criticalWrong },
            acceptedWrongAllClear = wrong.count { (c, _) -> c.clear },
            acceptedWrongOther = wrong.count { (c, _) -> !c.clear },
            refusedFormat = grades.count { it.refusedFormat },
        )
    }
}
