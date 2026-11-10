package app.accord.persistence

import app.accord.core.space.RecurrenceForm
import app.accord.core.CardCycle
import app.accord.core.LimitSetting
import app.accord.core.Money
import app.accord.core.forecast.CategoryForecastInput
import app.accord.core.forecast.Channel
import app.accord.core.forecast.DataQuality
import app.accord.core.forecast.Forecast
import app.accord.core.forecast.ForecastRequest
import app.accord.core.forecast.MonthForecast
import app.accord.core.forecast.VariableSpend
import app.accord.core.space.AssignmentState
import app.accord.core.space.ForecastDetail
import app.accord.core.space.KnownLine
import app.accord.core.space.KnownNature
import app.accord.core.space.MonthBudget
import app.accord.core.space.OccurrenceView
import app.accord.core.space.PendingItem
import app.accord.core.space.RecurrenceKind
import java.time.LocalDate
import java.time.YearMonth

/**
 * Previsões do mês (S5): lê do banco local o que a regra pura [Forecast] precisa e devolve o resultado, sem persistir nada. Os conhecidos são os do
 * orçamento do mês (a mesma conta de `budget`), de modo que previsão e orçamento nunca discordam. Os gastos variáveis são só despesas efetivas à vista: ficam
 * de fora parcela, recorrência, boleto, estorno, quitação, renda, intenção, o que é "a classificar" e os encargos.
 *
 * Encargo: o pagamento direto leva a marca `isCharge` (como a compra no cartão) e é excluído por ela, não pelo nome da categoria, que é editável. Por
 * compatibilidade, movimento anterior à marca que esteja na categoria semente "Tarifas e juros" também fica de fora.
 *
 * Cobertura (`coverageComplete`): derivada dos lotes de importação confirmados, nunca gravada ([coverageComplete]). Não há lote nem prova de que todos os
 * gastos foram registrados; só a lacuna conhecida (dúvida aberta ou trecho não extraído de um lote que toca o período) tira a cobertura de completa.
 */
internal class ForecastQueries(
    private val dao: SpaceDao,
    private val syncDao: SyncDao,
    private val tree: suspend () -> CategoryTree,
    private val cycle: suspend () -> CardCycle,
    private val today: () -> LocalDate,
    private val nowMillis: () -> Long,
    private val budget: suspend (YearMonth) -> MonthBudget,
    private val limit: suspend (String, YearMonth) -> LimitSetting,
    private val occurrences: suspend (YearMonth) -> List<OccurrenceView>,
    private val pendings: suspend () -> List<PendingItem>,
    private val feesCategoryId: suspend () -> String?,
    private val invoiceConferredClosed: suspend (YearMonth) -> Boolean,
) {
    suspend fun forecasts(month: YearMonth): MonthForecast {
        val budget = budget(month)
        val tree = tree()
        val today = today()
        val spends = variableSpends(month, today, tree)
        val request = ForecastRequest(
            month = month,
            today = today,
            cycle = cycle(),
            historyStart = historyStart(),
            categories = budget.categories.map {
                CategoryForecastInput(it.categoryId, limit(it.categoryId, month).total?.cents, it.net.cents, spends[it.categoryId].orEmpty())
            },
            quality = quality(month, budget, tree),
            cards = dao.cards().takeIf { it.size > 1 }.orEmpty().map { card ->
                val invoice = dao.invoice(month.toString(), card.id)
                val parts = dao.invoiceParts(month.toString(), card.id)
                val recurring = occurrences(month).filter { it.form == RecurrenceForm.Card && it.cardId == card.id }.sumOf { it.expectedAmount.cents }
                val total = parts.purchasesCents + parts.chargesCents + recurring - dao.invoiceCredits(month.toString(), card.id)
                app.accord.core.forecast.CardForecastConfig(card.id, card.nickname, CardCycle(card.closingDay, card.dueDay),
                    dao.isInvoicePaid(month.toString(), card.id) || invoice?.checkedCents == total)
            },
            invoiceConferredClosed = invoiceConferredClosed(month).takeIf { it },
        )
        return Forecast.compute(request).copy(calculatedAtMillis = nowMillis())
    }

    suspend fun detail(categoryId: String, month: YearMonth): ForecastDetail? {
        val budget = budget(month)
        val category = budget.categories.firstOrNull { it.categoryId == categoryId } ?: return null
        val forecast = forecasts(month)
        val tree = tree()
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        fun belongs(classificationId: String?) = classificationId != null && tree.mainAt(classificationId, month) == categoryId

        val kinds = dao.installmentSumsByKind(month.toString()).filter { belongs(it.classificationId) }
        val recurring = occurrences(month).filter { it.kind == RecurrenceKind.Expense && belongs(it.categoryId) }
        val realized = dao.sumsByClassification(from, to).filter { belongs(it.classificationId) }
        val boletos = dao.boletoSums(from, to).filter { belongs(it.classificationId) }

        val lines = listOf(
            KnownLine(KnownNature.Realized, realized.sumOf { it.totalCents } + kinds.filter { !it.multi }.sumOf { it.totalCents }, realized.sumOf { it.count } + kinds.filter { !it.multi }.sumOf { it.count }),
            KnownLine(KnownNature.Installments, kinds.filter { it.multi }.sumOf { it.totalCents }, kinds.filter { it.multi }.sumOf { it.count }),
            KnownLine(KnownNature.Recurrences, recurring.sumOf { (it.settledAmount ?: it.expectedAmount).cents }, recurring.size),
            KnownLine(KnownNature.Boletos, boletos.sumOf { it.totalCents }, boletos.sumOf { it.count }),
            KnownLine(KnownNature.Credits, -category.credits.cents, null),
        ).filter { it.cents != 0L || (it.count ?: 0) > 0 }

        val confirmInstallments = dao.toConfirmSums(month.toString()).filter { belongs(it.classificationId) }
        val confirmRecurring = recurring.filter { it.assignment == AssignmentState.ToConfirm }
        val toConfirm = (confirmInstallments.sumOf { it.count } + confirmRecurring.size).takeIf { it > 0 }?.let { count ->
            KnownLine(
                KnownNature.Realized,
                confirmInstallments.sumOf { it.totalCents } + confirmRecurring.sumOf { (it.settledAmount ?: it.expectedAmount).cents },
                count,
            )
        }
        return ForecastDetail(
            month = month,
            forecast = forecast.categories.first { it.categoryId == categoryId },
            ruleVersion = forecast.ruleVersion,
            invoice = forecast.invoice,
            known = lines,
            toConfirm = toConfirm,
            bySubcategory = category.bySubcategory,
            calculatedAtMillis = forecast.calculatedAtMillis,
        )
    }

    // ---- entradas da regra ----

    /**
     * Gastos variáveis por categoria principal, na classificação atual (uma subcategoria reorganizada leva o histórico). Só o passado recente importa: o
     * ritmo usa os últimos períodos completos, que cabem nos [LOOKBACK_MONTHS] meses antes de hoje.
     */
    private suspend fun variableSpends(month: YearMonth, today: LocalDate, tree: CategoryTree): Map<String, List<VariableSpend>> {
        val from = YearMonth.from(today).minusMonths(LOOKBACK_MONTHS).atDay(1)
        val fees = feesCategoryId()
        fun main(classificationId: String?): String? =
            classificationId?.takeIf { it != fees }?.let { tree.mainAt(it, month) }

        val direct = dao.movementsBetween(from.toEpochDay(), today.toEpochDay()).filterNot { it.isCharge }.mapNotNull { m ->
            main(m.classificationId)?.let { it to VariableSpend(LocalDate.ofEpochDay(m.epochDay), Channel.Other, m.amountCents) }
        }
        val card = dao.singlePurchasesBetween(from.toEpochDay(), today.toEpochDay()).mapNotNull { p ->
            main(p.classificationId)?.let { it to VariableSpend(LocalDate.ofEpochDay(p.purchaseEpochDay), Channel.Card, p.totalCents, p.cardId) }
        }
        return (direct + card).groupBy({ it.first }, { it.second })
    }

    private suspend fun historyStart(): LocalDate? =
        listOfNotNull(dao.firstMovementDay(), dao.firstPurchaseDay()).minOrNull()?.let(LocalDate::ofEpochDay)

    /**
     * Qualidade dos dados do mês. "A classificar" tem a sua própria ressalva; pendência de fatura e de pagamento sem categoria própria reduz a confiança de
     * todas. Visão compartilhada possivelmente desatualizada: operações locais por enviar ou última sincronização há mais de [STALE_SYNC_MILLIS] (proposta).
     */
    private suspend fun quality(month: YearMonth, budget: MonthBudget, tree: CategoryTree): DataQuality {
        val items = pendings().filter { it.month == month }
        val byCategory = items.filterIsInstance<PendingItem.InvoiceLink>()
            .mapNotNull { link -> dao.purchase(link.purchaseId)?.classificationId?.let { tree.mainAt(it, month) } }
            .groupingBy { it }.eachCount()
        val withoutCategory = items.count { it is PendingItem.DivergentInvoicePayment || it is PendingItem.InvoiceTotalDifference } + syncDao.unresolvedConflicts().size
        val lastSync = syncDao.syncState()?.lastSyncMillis
        val stale = syncDao.operationsIn(listOf(OpState.PENDING, OpState.HELD)).isNotEmpty() ||
            (lastSync != null && nowMillis() - lastSync > STALE_SYNC_MILLIS)
        return DataQuality(
            coverageComplete = coverageComplete(month),
            pendingWithoutCategory = withoutCategory,
            pendingByCategory = byCategory,
            toClassifyCount = budget.toClassify.count,
            toClassifyCents = budget.toClassify.amount.cents,
            sharedViewPossiblyStale = stale,
        )
    }

    /**
     * Cobertura incompleta (Proposta, S4 "Cobertura e período conferido"): algum lote confirmado que toca a exposição da previsão do mês ainda tem dúvida
     * aberta ou trecho não extraído. A exposição vai do início da fatura que vence no mês ao fim do mês civil (a janela dos dois canais). O intervalo do
     * lote é o período declarado; na falta dele, o das datas dos movimentos; e, na fatura, também o mês de vencimento. Lote sem nenhuma data não toca.
     */
    private suspend fun coverageComplete(month: YearMonth): Boolean {
        val start = (dao.cards().map { CardCycle(it.closingDay, it.dueDay).closingDateOfInvoice(month.minusMonths(1)).plusDays(1) } + month.atDay(1)).min().toEpochDay()
        val end = month.atEndOfMonth().toEpochDay()
        val withPending = dao.batchesWithPending().toSet()
        return dao.confirmedImportBatches().none { batch ->
            if (batch.id !in withPending && batch.unrecognizedSegments == 0) return@none false
            if (batch.documentInvoice == month.toString()) return@none true
            val days = dao.importMovements(batch.id).mapNotNull { it.epochDay }
            val first = batch.periodStartDay ?: days.minOrNull()
            val last = batch.periodEndDay ?: days.maxOrNull()
            first != null && last != null && first <= end && last >= start
        }
    }

    private companion object {
        const val LOOKBACK_MONTHS = 7L
        const val STALE_SYNC_MILLIS = 24L * 60 * 60 * 1000
    }
}
