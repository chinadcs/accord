package app.accord.core.chat

import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntryFilter
import app.accord.core.space.EntryKind
import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** Linha de uma resposta: parcela de um cronograma ou compromisso do mês. Sempre em centavos. */
@Serializable
data class QueryLine(val number: Int? = null, val month: String? = null, val label: String? = null, val date: String? = null, val cents: Long)

/**
 * Resposta calculada pela fachada (S6): números em centavos, critério e período explícitos e a cobertura. O texto mostrado é do app.
 * É um cache de exibição, rotulado pelo instante do cálculo; a fonte de verdade continua sendo a fachada, e "Atualizar" recalcula.
 */
@Serializable
data class ChatQueryResult(
    val kind: Kind,
    /** `yyyy-MM` do mês consultado. */
    val month: String,
    val periodAssumed: Boolean,
    /** `BudgetMonth` ou `PurchaseDate`. */
    val criterion: String,
    val criterionAssumed: Boolean,
    /** Nome da categoria ou descrição da compra. */
    val label: String? = null,
    val categoryId: String? = null,
    val purchaseId: String? = null,
    val purchaseDate: String? = null,
    val totalCents: Long? = null,
    val paidCents: Long? = null,
    val limitCents: Long? = null,
    val remainingCents: Long? = null,
    val excessCents: Long? = null,
    val count: Int? = null,
    /** Parte do que aparece ainda é provisória (fatura "a conferir") ou ficou de fora (boleto sem data de compra). */
    val provisional: Boolean = false,
    /** Nos compromissos e no cronograma: soma das linhas de meses depois de [month]. */
    val laterCents: Long? = null,
    val laterCount: Int? = null,
    val lines: List<QueryLine> = emptyList(),
) {
    enum class Kind { Spend, Schedule, Commitments }
}

/** Resultado da execução: um número calculado, um pedido de complemento ou uma falha (que nunca vira zero). */
sealed interface ConsultOutcome {
    data class Result(val result: ChatQueryResult) : ConsultOutcome
    data class Ask(val ask: ConsultAskNotice) : ConsultOutcome
    data object Failed : ConsultOutcome
}

/**
 * Executa uma [ConsultRequest] validada **só pelas consultas da fachada** (S6): `budget`, `purchaseActivity`, `commitments`, `entries` e
 * `commitment`. Não há caminho de consulta exclusivo do Chat, então o número é o mesmo das telas. Leitura apenas: nenhum comando, operação
 * ou pendência.
 */
class ChatQueryRunner(private val space: FinanceSpace, private val clock: BudgetClock) {
    suspend fun run(request: ConsultRequest): ConsultOutcome = try {
        when (request.type) {
            ConsultType.CategorySpend -> spend(request)
            ConsultType.PurchaseSchedule -> schedule(request)
            ConsultType.MonthCommitments -> commitments(request)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ConsultOutcome.Failed
    }

    private fun base(request: ConsultRequest, kind: ChatQueryResult.Kind) = ChatQueryResult(
        kind = kind,
        month = request.month.toString(),
        periodAssumed = request.periodAssumed,
        criterion = request.criterion.name,
        criterionAssumed = request.criterionAssumed,
    )

    private suspend fun spend(request: ConsultRequest): ConsultOutcome {
        val categoryId = request.categoryId ?: return ConsultOutcome.Failed
        val nodes = (space.categories() as? QueryResult.Data)?.value ?: return ConsultOutcome.Failed
        val node = nodes.firstOrNull { it.id == categoryId } ?: return ConsultOutcome.Failed
        val seed = base(request, ChatQueryResult.Kind.Spend).copy(label = node.name, categoryId = categoryId)
        return when (request.criterion) {
            DateCriterion.BudgetMonth -> {
                val budget = (space.budget(request.month) as? QueryResult.Data)?.value ?: return ConsultOutcome.Failed
                val mainId = node.parentId ?: node.id
                val main = budget.categories.firstOrNull { it.categoryId == mainId }
                val result = if (node.parentId == null) {
                    seed.copy(
                        totalCents = (main?.net ?: Money.ZERO).cents,
                        paidCents = (main?.paid ?: Money.ZERO).cents,
                        limitCents = main?.limit?.total?.cents,
                        remainingCents = main?.limit?.remaining?.cents,
                        excessCents = main?.limit?.excess?.cents,
                    )
                } else {
                    seed.copy(totalCents = (main?.bySubcategory?.get(node.id) ?: Money.ZERO).cents)
                }
                ConsultOutcome.Result(result.copy(provisional = budget.invoiceLinksToConfirm > 0))
            }
            DateCriterion.PurchaseDate -> {
                val activity = (space.purchaseActivity(request.month.atDay(1), request.month.plusMonths(1).atDay(1), categoryId) as? QueryResult.Data)?.value
                    ?: return ConsultOutcome.Failed
                ConsultOutcome.Result(
                    seed.copy(totalCents = activity.total.total.cents, count = activity.total.count, provisional = activity.gaps != null),
                )
            }
        }
    }

    private suspend fun commitments(request: ConsultRequest): ConsultOutcome {
        val month = (space.commitments(request.month) as? QueryResult.Data)?.value ?: return ConsultOutcome.Failed
        val lines = month.items.take(MAX_LINES).map {
            QueryLine(number = it.number, label = it.description, date = it.dueDate.toString(), cents = it.amount.cents)
        }
        return ConsultOutcome.Result(
            base(request, ChatQueryResult.Kind.Commitments).copy(
                totalCents = month.knownTotal.cents,
                count = month.items.size,
                lines = lines,
            ),
        )
    }

    private suspend fun schedule(request: ConsultRequest): ConsultOutcome {
        val target = request.target ?: return ConsultOutcome.Failed
        val today = clock.today()
        val filter = EntryFilter(
            from = YearMonth.from(today).minusYears(SEARCH_YEARS_BACK.toLong()),
            to = YearMonth.from(today).plusYears(SEARCH_YEARS_AHEAD.toLong()),
            criterion = DateCriterion.PurchaseDate,
            search = target,
        )
        val page = (space.entries(filter, 0, SEARCH_LIMIT) as? QueryResult.Data)?.value ?: return ConsultOutcome.Failed
        var found = page.rows.filter { it.kind == EntryKind.CardPurchase }
        if (request.targetDate != null) found = found.filter { it.date == request.targetDate }
        found = found.distinctBy { it.id }
        if (found.isEmpty()) return ConsultOutcome.Ask(ConsultAskNotice(ConsultAskNotice.Reason.UnknownPurchase))
        if (found.size > 1) {
            val options = if (request.targetDate != null) emptyList() else found.take(MAX_OPTIONS).map {
                val date = it.date
                ConsultOption("${it.description} (${date.dayOfMonth.pad()}/${date.monthValue.pad()}/${date.year})", "quais são as parcelas de ${it.description} de ${date.dayOfMonth.pad()}/${date.monthValue.pad()}/${date.year}?")
            }.distinctBy { it.text }
            return ConsultOutcome.Ask(ConsultAskNotice(ConsultAskNotice.Reason.AmbiguousPurchase, options))
        }
        val purchase = found.single()
        val detail = (space.commitment("${purchase.id}#1") as? QueryResult.Data)?.value as? CommitmentDetail.Installments
            ?: return ConsultOutcome.Ask(ConsultAskNotice(ConsultAskNotice.Reason.UnknownPurchase))
        val rows = detail.schedule
        val later = rows.filter { it.invoiceMonth > request.month }
        return ConsultOutcome.Result(
            base(request, ChatQueryResult.Kind.Schedule).copy(
                label = detail.purchase.description,
                purchaseId = detail.purchase.id,
                purchaseDate = detail.purchase.purchaseDate.toString(),
                totalCents = detail.purchase.total.cents,
                count = rows.size,
                laterCents = later.fold(Money.ZERO) { sum, row -> sum + row.amount }.cents,
                laterCount = later.size,
                lines = rows.map { QueryLine(number = it.number, month = it.invoiceMonth.toString(), date = it.dueDate.toString(), cents = it.amount.cents) },
            ),
        )
    }

    private fun Int.pad() = toString().padStart(2, '0')

    companion object {
        const val MAX_LINES = 8
        private const val MAX_OPTIONS = 6
        private const val SEARCH_YEARS_BACK = 5
        private const val SEARCH_YEARS_AHEAD = 1
        private const val SEARCH_LIMIT = 50
    }
}

/**
 * O que fica no histórico local da mensagem de consulta (nunca sincroniza): parâmetros validados, instante do cálculo e a resposta como cache de
 * exibição. O texto da pergunta já está na mensagem. Guardado em `ChatMessage.fields`.
 */
@Serializable
data class ConsultSnapshot(
    val type: String,
    val month: String,
    val periodAssumed: Boolean,
    val criterion: String,
    val criterionAssumed: Boolean,
    val categoryId: String? = null,
    val target: String? = null,
    val targetDate: String? = null,
    val calculatedAtMillis: Long,
    val status: Status,
    val result: ChatQueryResult? = null,
    val askReason: String? = null,
    val askOptions: List<AskOption> = emptyList(),
) {
    enum class Status { Result, Ask, Failed }

    @Serializable
    data class AskOption(val label: String, val text: String)

    fun request(): ConsultRequest = ConsultRequest(
        type = ConsultType.valueOf(type),
        month = YearMonth.parse(month),
        periodAssumed = periodAssumed,
        criterion = DateCriterion.valueOf(criterion),
        criterionAssumed = criterionAssumed,
        categoryId = categoryId,
        target = target,
        targetDate = targetDate?.let(LocalDate::parse),
    )

    fun ask(): ConsultAskNotice? = askReason?.let { ConsultAskNotice(ConsultAskNotice.Reason.valueOf(it), askOptions.map { o -> ConsultOption(o.label, o.text) }) }

    companion object {
        private const val KEY = "consult"
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun of(request: ConsultRequest, outcome: ConsultOutcome, atMillis: Long) = ConsultSnapshot(
            type = request.type.name,
            month = request.month.toString(),
            periodAssumed = request.periodAssumed,
            criterion = request.criterion.name,
            criterionAssumed = request.criterionAssumed,
            categoryId = request.categoryId,
            target = request.target,
            targetDate = request.targetDate?.toString(),
            calculatedAtMillis = atMillis,
            status = when (outcome) {
                is ConsultOutcome.Result -> Status.Result
                is ConsultOutcome.Ask -> Status.Ask
                ConsultOutcome.Failed -> Status.Failed
            },
            result = (outcome as? ConsultOutcome.Result)?.result,
            askReason = (outcome as? ConsultOutcome.Ask)?.ask?.reason?.name,
            askOptions = (outcome as? ConsultOutcome.Ask)?.ask?.options?.map { AskOption(it.label, it.text) }.orEmpty(),
        )

        /** Pedido de complemento antes de executar (categoria ausente ou ambígua): sem número, com opções. */
        fun ofAsk(request: ConsultRequest?, ask: ConsultAskNotice, atMillis: Long) = ConsultSnapshot(
            type = request?.type?.name ?: ConsultType.CategorySpend.name,
            month = (request?.month ?: YearMonth.of(2000, 1)).toString(),
            periodAssumed = request?.periodAssumed ?: true,
            criterion = (request?.criterion ?: DateCriterion.BudgetMonth).name,
            criterionAssumed = request?.criterionAssumed ?: true,
            calculatedAtMillis = atMillis,
            status = Status.Ask,
            askReason = ask.reason.name,
            askOptions = ask.options.map { AskOption(it.label, it.text) },
        )

        fun toFields(snapshot: ConsultSnapshot): Map<String, String> = mapOf(KEY to json.encodeToString(serializer(), snapshot))

        fun fromFields(fields: Map<String, String>): ConsultSnapshot? =
            fields[KEY]?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}
