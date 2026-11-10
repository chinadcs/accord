package app.accord.persistence

import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.CategoryBudget
import app.accord.core.space.GeneralBudgetView
import app.accord.core.space.IncomeSummary
import app.accord.core.space.IncomeTotals
import app.accord.core.space.IncomeView
import app.accord.core.space.MonthBudget
import app.accord.core.space.OccurrenceView
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.ToClassifyLine
import app.accord.core.sum
import java.time.YearMonth

/** Renda do mês e orçamento do mês: o que está comprometido, pago, creditado e dentro de cada limite. */
internal class BudgetQueries(
    private val dao: SpaceDao,
    private val tree: suspend () -> CategoryTree,
    private val occurrences: suspend (YearMonth) -> List<OccurrenceView>,
    private val limits: LimitsPlanner,
) {
    suspend fun incomes(month: YearMonth): IncomeSummary {
        val recurring = occurrences(month).filter { it.kind == RecurrenceKind.Income }.map {
            IncomeView(
                id = "${it.recurrenceId}:${it.number}",
                description = it.description,
                expectedAmount = it.expectedAmount,
                expectedDate = it.scheduledDate,
                receivedDate = it.settledDate,
                receivedAmount = it.settledAmount,
                accountId = it.accountId,
                budgetMonth = it.budgetMonth,
                revision = dao.recurrence(it.recurrenceId)?.revision ?: 0,
                voided = false,
            )
        }
        // Recebimento de devedor só conta na renda do mês quando o valor a receber foi criado com "contar como despesa e renda" (ADR 0083).
        val counted = dao.receivables().associate { it.id to it.countsInBudget }
        val items = dao.incomesBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { row ->
            row.toView().let { view -> if (row.receivableId == null) view else view.copy(countsInIncome = counted[row.receivableId] == true) }
        } + recurring
        val inIncome = items.filter { it.countsInIncome }
        val fromDebtors = items.filter { !it.countsInIncome }
        return IncomeSummary(
            received = inIncome.mapNotNull { it.receivedAmount }.sum(),
            toReceive = inIncome.filter { it.receivedDate == null }.map { it.expectedAmount }.sum(),
            items = items,
            fromDebtors = IncomeTotals(
                fromDebtors.mapNotNull { it.receivedAmount }.sum(),
                fromDebtors.filter { it.receivedDate == null }.map { it.expectedAmount }.sum(),
            ),
        )
    }

    private data class Credit(val classificationId: String?, val cents: Long, val reducesPaid: Boolean)

    /** Crédito de um estorno confirmado; nulo se a origem foi desfeita ou excluída (o crédito some com ela). */
    private suspend fun creditOf(refund: RefundRow): Credit? = when (refund.originType) {
        RefundPlanner.ORIGIN_PURCHASE ->
            dao.purchase(refund.originId)?.takeIf { !it.voided }?.let {
                // Crédito de cartão só desconta do pago quando a fatura que o recebe está quitada.
                Credit(it.classificationId, refund.amountCents, reducesPaid = refund.creditMonth?.let { m -> dao.isInvoicePaid(m, it.cardId) } == true)
            }
        RefundPlanner.ORIGIN_MOVEMENT ->
            dao.movement(refund.originId)?.takeIf { !it.voided }?.let { Credit(it.classificationId, refund.amountCents, reducesPaid = true) }
        else ->
            dao.boleto(refund.originId)?.takeIf { !it.voided }?.let { Credit(it.classificationId, refund.amountCents, reducesPaid = true) }
    }

    /** Soma do mês por classificação; [paid] é o que já está pago dentro de [total]. */
    private data class Aggregate(val classificationId: String?, val total: Long, val paid: Long, val count: Int)

    suspend fun budget(month: YearMonth): MonthBudget {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        val categories = tree()
        val recurringExpenses = occurrences(month).filter { it.kind == RecurrenceKind.Expense }

        // Pix, débito e dinheiro já nascem pagos; parcelas só passam a pagas com a quitação da fatura.
        val aggregates = dao.sumsByClassification(from, to).map { Aggregate(it.classificationId, it.totalCents, it.totalCents, it.count) } +
            dao.installmentsInMonths(month.toString(), month.toString()).filter { it.cancelState != CancellationPlanner.CANCEL_DONE }.mapNotNull { i ->
                dao.purchase(i.purchaseId)?.takeIf { !it.voided }?.let { p ->
                    Aggregate(p.classificationId, i.amountCents, if (dao.isInvoicePaid(i.invoiceMonth, p.cardId)) i.amountCents else 0, 1)
                }
            } +
            dao.boletoSums(from, to).map { Aggregate(it.classificationId, it.totalCents, it.paidCents, it.count) } +
            recurringExpenses.map {
                val amount = (it.settledAmount ?: it.expectedAmount).cents
                val onCard = it.form == RecurrenceForm.Card
                Aggregate(it.categoryId, amount, if (it.paid || (onCard && dao.isInvoicePaid(month.toString(), it.cardId))) amount else 0, 1)
            }
        val income = incomes(month)

        val toClassify = aggregates.filter { it.classificationId == null }
        val perCategory = aggregates.filter { it.classificationId != null }
            .groupBy { categories.mainAt(it.classificationId!!, month) }

        // Crédito de estorno confirmado abate a categoria da origem no mês do crédito. Crédito de
        // cartão ainda não desconta do pago: isso depende da quitação da fatura que o recebe.
        val credits = dao.confirmedRefundsIn(month.toString()).mapNotNull { refund -> creditOf(refund) }
        val creditsByMain = credits.filter { it.classificationId != null }
            .groupBy { categories.mainAt(it.classificationId!!, month) }
        val unclassifiedCredit = credits.filter { it.classificationId == null }.sumOf { it.cents }

        val limitSettings = categories.mains.associate { it.id to limits.timeline(it.id).settingFor(month) }
        // Orçamento geral: gasto líquido de tudo (inclusive "a classificar"), sem depender de limite por categoria.
        val generalLimit = limits.generalView(month)
        val generalSpent = Money(aggregates.sumOf { it.total } - credits.sumOf { it.cents })

        return MonthBudget(
            month = month,
            committed = Money(aggregates.sumOf { it.total }),
            totalLimits = LimitView.totalOfLimits(limitSettings.values),
            paid = Money(aggregates.sumOf { it.paid } - credits.filter { it.reducesPaid }.sumOf { it.cents }),
            toClassify = ToClassifyLine(Money(toClassify.sumOf { it.total } - unclassifiedCredit), toClassify.sumOf { it.count }),
            credits = Money(credits.sumOf { it.cents }),
            invoiceLinksToConfirm = dao.cards().sumOf { dao.linksToConfirm(month.toString(), it.id) } +
                recurringExpenses.count { it.assignment == AssignmentState.ToConfirm },
            income = IncomeTotals(income.received, income.toReceive),
            general = GeneralBudgetView(generalLimit, generalSpent, LimitView.of(generalLimit.setting, generalSpent, emptyMap())),
            categories = categories.mains.map { main ->
                val rows = perCategory[main.id].orEmpty()
                val gross = Money(rows.sumOf { it.total })
                val mainCredits = creditsByMain[main.id].orEmpty()
                val credit = Money(mainCredits.sumOf { it.cents })
                CategoryBudget(
                    categoryId = main.id,
                    gross = gross,
                    credits = credit,
                    net = gross - credit,
                    paid = Money(rows.sumOf { it.paid } - mainCredits.filter { it.reducesPaid }.sumOf { it.cents }),
                    bySubcategory = rows.groupBy { r -> categories.subAt(r.classificationId!!, month) }
                        .mapValues { (_, group) -> Money(group.sumOf { it.total }) },
                    limit = LimitView.of(
                        limitSettings.getValue(main.id),
                        categoryNet = gross - credit,
                        subNet = rows.filter { categories.subAt(it.classificationId!!, month) != null }
                            .groupBy { it.classificationId!! }
                            .mapValues { (id, group) ->
                                Money(group.sumOf { it.total } - mainCredits.filter { c -> c.classificationId == id }.sumOf { it.cents })
                            },
                    ),
                )
            },
        )
    }
}
