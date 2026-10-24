package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentKind
import app.accord.core.space.CommitmentNature
import app.accord.core.space.DateCriterion
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntryFilter
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryPage
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.MonthCommitments
import app.accord.core.space.NameKey
import app.accord.core.space.OccurrenceView
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PendingItem
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.label
import app.accord.core.sum
import java.time.LocalDate
import java.time.YearMonth

/**
 * Consultas de leitura sobre os dados locais: lista de Lançamentos (07), compromissos do mês (10)
 * e pendências (18). Tudo é derivado do estado; nada aqui grava. Os resultados distinguem
 * critérios (mês do orçamento ou data da compra) para nunca somar parcela e compra juntas.
 */
internal class EntryQueries(
    private val dao: SpaceDao,
    private val tree: suspend () -> CategoryTree,
    private val cycle: suspend () -> CardCycle,
    private val occurrences: suspend (YearMonth) -> List<OccurrenceView>,
    private val invoiceDifference: suspend (YearMonth) -> Money?,
    private val today: () -> LocalDate,
) {
    private enum class Bucket { Expense, Income, Credit, InvoicePayment }

    private class Built(val row: EntryRow, val bucket: Bucket, val month: YearMonth, val spentBy: String?)

    private fun methodOf(kind: String) = when (DirectPaymentKind.valueOf(kind)) {
        DirectPaymentKind.Pix -> PaymentMethod.Pix
        DirectPaymentKind.Debit -> PaymentMethod.Debit
        DirectPaymentKind.Cash -> PaymentMethod.Cash
    }

    private fun months(from: YearMonth, to: YearMonth): List<YearMonth> =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList()

    // ---------------------------------------------------------------- lista (tela 07)

    suspend fun entries(f: EntryFilter, cursor: Int, pageSize: Int): EntryPage {
        require(cursor >= 0 && pageSize > 0) { "Cursor e tamanho da página precisam ser positivos" }
        val tree = tree()
        val card = cycle()
        val byBudget = f.criterion == DateCriterion.BudgetMonth
        val fromDay = f.from.atDay(1).toEpochDay()
        val toDay = f.to.atEndOfMonth().toEpochDay()
        val built = mutableListOf<Built>()

        // Pix, débito e dinheiro: o mês da movimentação vale nos dois critérios.
        dao.movementsBetween(fromDay, toDay).forEach { m ->
            val date = LocalDate.ofEpochDay(m.epochDay)
            built += Built(
                EntryRow(
                    id = m.id, kind = EntryKind.DirectPayment, description = m.description, categoryId = m.classificationId,
                    amount = Money(m.amountCents), date = date, dueDate = null, method = methodOf(m.kind),
                    installmentNumber = null, installmentCount = null,
                    situations = setOfNotNull(EntrySituation.Paid, EntrySituation.ToClassify.takeIf { m.classificationId == null }),
                    authorId = m.authorId,
                ),
                Bucket.Expense, YearMonth.from(date), m.spentBy,
            )
        }

        // Cartão: parcelas no mês de vencimento da fatura, ou a compra uma vez pelo total.
        val purchases = mutableMapOf<String, CardPurchaseRow>()
        if (byBudget) {
            val installments = dao.installmentsInMonths(f.from.toString(), f.to.toString())
            val counts = mutableMapOf<String, Int>()
            val paidInvoices = mutableMapOf<String, Boolean>()
            installments.filter { it.cancelState != CancellationPlanner.CANCEL_DONE }.forEach { i ->
                val p = purchases.getOrPut(i.purchaseId) { dao.purchase(i.purchaseId)!! }
                val count = counts.getOrPut(i.purchaseId) { dao.installments(i.purchaseId).size }
                val month = YearMonth.parse(i.invoiceMonth)
                val paid = paidInvoices.getOrPut(i.invoiceMonth) { dao.isInvoicePaid(i.invoiceMonth) }
                built += Built(
                    EntryRow(
                        id = "${p.id}#${i.number}", kind = EntryKind.CardInstallment, description = p.description,
                        categoryId = p.classificationId, amount = Money(i.amountCents), date = LocalDate.ofEpochDay(p.purchaseEpochDay),
                        dueDate = card.dueDateOfInvoice(month), method = PaymentMethod.Card,
                        installmentNumber = i.number, installmentCount = count,
                        situations = setOfNotNull(
                            if (paid) EntrySituation.Paid else EntrySituation.NotPaid,
                            EntrySituation.ToClassify.takeIf { p.classificationId == null },
                            EntrySituation.InvoiceLinkToConfirm.takeIf { i.assignment == AssignmentState.ToConfirm.name },
                            EntrySituation.CancelRequested.takeIf { i.cancelState == CancellationPlanner.CANCEL_REQUESTED },
                        ),
                        authorId = p.authorId,
                    ),
                    Bucket.Expense, month, p.spentBy,
                )
            }
        } else {
            dao.purchasesBetween(fromDay, toDay).forEach { p ->
                val rows = dao.installments(p.id)
                val date = LocalDate.ofEpochDay(p.purchaseEpochDay)
                built += Built(
                    EntryRow(
                        id = p.id, kind = EntryKind.CardPurchase, description = p.description, categoryId = p.classificationId,
                        amount = Money(p.totalCents), date = date, dueDate = null, method = PaymentMethod.Card,
                        installmentNumber = null, installmentCount = rows.size,
                        situations = setOfNotNull(
                            EntrySituation.ToClassify.takeIf { p.classificationId == null },
                            EntrySituation.InvoiceLinkToConfirm.takeIf { rows.any { it.assignment == AssignmentState.ToConfirm.name } },
                            EntrySituation.CancelRequested.takeIf { rows.any { it.cancelState == CancellationPlanner.CANCEL_REQUESTED } },
                            EntrySituation.Cancelled.takeIf { rows.any { it.cancelState == CancellationPlanner.CANCEL_DONE } },
                        ),
                        authorId = p.authorId,
                    ),
                    Bucket.Expense, YearMonth.from(date), p.spentBy,
                )
            }
        }

        // Boleto: previsto no vencimento até ser pago; depois, só o pagamento efetivo.
        val boletos = if (byBudget) dao.boletosByBudget(fromDay, toDay) else dao.boletosByDue(fromDay, toDay)
        boletos.forEach { b ->
            val due = LocalDate.ofEpochDay(b.dueEpochDay)
            val paid = b.paidEpochDay?.let(LocalDate::ofEpochDay)
            val date = if (byBudget) paid ?: due else due
            built += Built(
                EntryRow(
                    id = b.id, kind = EntryKind.Boleto, description = b.description, categoryId = b.classificationId,
                    amount = Money(b.paidCents ?: b.expectedCents), date = date, dueDate = due, method = PaymentMethod.Boleto,
                    installmentNumber = null, installmentCount = null,
                    situations = setOfNotNull(
                        if (paid != null) EntrySituation.Paid else EntrySituation.NotPaid,
                        EntrySituation.Overdue.takeIf { paid == null && due.isBefore(today()) },
                        EntrySituation.ToClassify.takeIf { b.classificationId == null },
                    ),
                    authorId = b.authorId,
                ),
                Bucket.Expense, YearMonth.from(date), null,
            )
        }

        dao.incomesBetween(fromDay, toDay).forEach { i ->
            val received = i.receivedEpochDay?.let(LocalDate::ofEpochDay)
            val date = received ?: LocalDate.ofEpochDay(i.expectedEpochDay)
            built += Built(
                EntryRow(
                    id = i.id, kind = EntryKind.Income, description = i.description, categoryId = null,
                    amount = Money(i.receivedCents ?: i.expectedCents), date = date, dueDate = null, method = null,
                    installmentNumber = null, installmentCount = null,
                    situations = setOf(if (received != null) EntrySituation.Paid else EntrySituation.NotPaid),
                    authorId = i.authorId,
                ),
                Bucket.Income, YearMonth.from(date), null,
            )
        }

        // Estorno confirmado: crédito vinculado à compra original, no mês do crédito.
        val refunds = if (byBudget) dao.refundsByCreditMonth(f.from.toString(), f.to.toString()) else dao.refundsByDay(fromDay, toDay)
        refunds.forEach { r ->
            val origin = originInfo(r) ?: return@forEach
            val date = LocalDate.ofEpochDay(r.epochDay!!)
            built += Built(
                EntryRow(
                    id = r.id, kind = EntryKind.Refund, description = "Estorno: ${origin.first}", categoryId = origin.second,
                    amount = Money(r.amountCents), date = date, dueDate = null, method = null,
                    installmentNumber = null, installmentCount = null, situations = emptySet(), authorId = r.authorId,
                ),
                Bucket.Credit, r.creditMonth?.let(YearMonth::parse) ?: YearMonth.from(date), null,
            )
        }

        // Quitação de fatura: identificada como quitação, nunca como despesa de categoria.
        dao.invoicePaymentsBetween(fromDay, toDay).forEach { p ->
            val date = LocalDate.ofEpochDay(p.epochDay)
            built += Built(
                EntryRow(
                    id = p.id, kind = EntryKind.InvoicePayment, description = "Pagamento da fatura de ${YearMonth.parse(p.dueMonth).label()}", categoryId = null,
                    amount = Money(p.amountCents), date = date, dueDate = null, method = null,
                    installmentNumber = null, installmentCount = null,
                    situations = setOf(if (p.accepted) EntrySituation.Paid else EntrySituation.DivergentPayment),
                    authorId = p.authorId,
                ),
                Bucket.InvoicePayment, YearMonth.from(date), null,
            )
        }

        // Ocorrências de recorrência são calculadas sob demanda para os meses pedidos.
        val seen = mutableSetOf<String>()
        val occurrenceMonths = if (byBudget) months(f.from, f.to) else months(f.from.minusMonths(1), f.to.plusMonths(1))
        for (month in occurrenceMonths) {
            for (o in occurrences(month)) {
                val date = o.settledDate ?: o.scheduledDate
                if (!byBudget && (o.scheduledDate.toEpochDay() !in fromDay..toDay)) continue
                if (!seen.add("${o.recurrenceId}:${o.number}")) continue
                val onCard = o.form == RecurrenceForm.Card && o.kind == RecurrenceKind.Expense
                built += Built(
                    EntryRow(
                        id = "${o.recurrenceId}:${o.number}", kind = EntryKind.RecurrenceOccurrence, description = o.description,
                        categoryId = o.categoryId, amount = o.settledAmount ?: o.expectedAmount,
                        date = if (byBudget) date else o.scheduledDate,
                        dueDate = if (onCard) card.dueDateOfInvoice(o.budgetMonth) else o.scheduledDate,
                        method = if (onCard) PaymentMethod.Card else null,
                        installmentNumber = null, installmentCount = null,
                        situations = setOfNotNull(
                            if (o.paid) EntrySituation.Paid else EntrySituation.NotPaid,
                            EntrySituation.Overdue.takeIf { !o.paid && !onCard && o.scheduledDate.isBefore(today()) },
                            EntrySituation.InvoiceLinkToConfirm.takeIf { o.assignment == AssignmentState.ToConfirm },
                        ),
                        authorId = null,
                    ),
                    if (o.kind == RecurrenceKind.Income) Bucket.Income else Bucket.Expense,
                    o.budgetMonth, null,
                )
            }
        }

        val matching = built.filter { matches(it, f, tree) }
            .sortedWith(compareByDescending<Built> { it.row.date }.thenBy { it.row.id })
        val page = matching.drop(cursor).take(pageSize)
        val next = (cursor + pageSize).takeIf { it < matching.size }
        fun total(bucket: Bucket) = matching.filter { it.bucket == bucket }.map { it.row.amount }.sum()
        return EntryPage(
            rows = page.map { it.row },
            nextCursor = next,
            total = matching.size,
            summary = EntrySummary(total(Bucket.Expense), total(Bucket.Income), total(Bucket.Credit), total(Bucket.InvoicePayment)),
        )
    }

    /** Descrição e classificação da origem de um estorno; nulo se a origem foi desfeita ou excluída. */
    private suspend fun originInfo(r: RefundRow): Pair<String, String?>? = when (r.originType) {
        RefundPlanner.ORIGIN_PURCHASE -> dao.purchase(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
        RefundPlanner.ORIGIN_MOVEMENT -> dao.movement(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
        else -> dao.boleto(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
    }

    private fun matches(b: Built, f: EntryFilter, tree: CategoryTree): Boolean {
        val row = b.row
        f.categoryId?.let { wanted ->
            val classification = row.categoryId ?: return false
            if (classification != wanted && tree.mainAt(classification, b.month) != wanted) return false
        }
        if (f.methods.isNotEmpty() && row.method !in f.methods) return false
        if (f.situations.isNotEmpty() && row.situations.none { it in f.situations }) return false
        f.person?.let { if (row.authorId != it && b.spentBy != it) return false }
        f.search?.takeIf { it.isNotBlank() }?.let { if (NameKey.of(it) !in NameKey.of(row.description)) return false }
        return true
    }

    // ---------------------------------------------------------------- compromissos (tela 10)

    suspend fun commitments(month: YearMonth, kind: CommitmentKind): MonthCommitments {
        val card = cycle()
        val items = mutableListOf<Commitment>()

        if (kind == CommitmentKind.All || kind == CommitmentKind.Installments) {
            val paid = dao.isInvoicePaid(month.toString())
            val purchases = mutableMapOf<String, CardPurchaseRow>()
            val counts = mutableMapOf<String, Int>()
            dao.installmentsInMonths(month.toString(), month.toString())
                .filter { it.cancelState != CancellationPlanner.CANCEL_DONE }
                .forEach { i ->
                    val p = purchases.getOrPut(i.purchaseId) { dao.purchase(i.purchaseId)!! }
                    items += Commitment(
                        id = "${p.id}#${i.number}", nature = CommitmentNature.Finite, description = p.description,
                        categoryId = p.classificationId, amount = Money(i.amountCents), dueDate = card.dueDateOfInvoice(month),
                        number = i.number, count = counts.getOrPut(p.id) { dao.installments(p.id).size },
                        situations = setOfNotNull(
                            if (paid) EntrySituation.Paid else EntrySituation.NotPaid,
                            EntrySituation.CancelRequested.takeIf { i.cancelState == CancellationPlanner.CANCEL_REQUESTED },
                            EntrySituation.InvoiceLinkToConfirm.takeIf { i.assignment == AssignmentState.ToConfirm.name },
                        ),
                        method = PaymentMethod.Card,
                    )
                }
        }

        if (kind == CommitmentKind.All || kind == CommitmentKind.Recurrences) {
            occurrences(month).filter { it.kind == RecurrenceKind.Expense }.forEach { o ->
                val onCard = o.form == RecurrenceForm.Card
                items += Commitment(
                    id = "${o.recurrenceId}:${o.number}", nature = CommitmentNature.Recurring, description = o.description,
                    categoryId = o.categoryId, amount = o.settledAmount ?: o.expectedAmount,
                    dueDate = if (onCard) card.dueDateOfInvoice(o.budgetMonth) else o.scheduledDate,
                    number = null, count = null,
                    situations = setOfNotNull(
                        if (o.paid) EntrySituation.Paid else EntrySituation.NotPaid,
                        EntrySituation.Overdue.takeIf { !o.paid && !onCard && o.scheduledDate.isBefore(today()) },
                        EntrySituation.InvoiceLinkToConfirm.takeIf { o.assignment == AssignmentState.ToConfirm },
                    ),
                    method = if (onCard) PaymentMethod.Card else null,
                )
            }
        }

        if (kind == CommitmentKind.All || kind == CommitmentKind.Bills) {
            dao.boletosByBudget(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).forEach { b ->
                val due = LocalDate.ofEpochDay(b.dueEpochDay)
                val paid = b.paidEpochDay != null
                items += Commitment(
                    id = b.id, nature = CommitmentNature.Bill, description = b.description, categoryId = b.classificationId,
                    amount = Money(b.paidCents ?: b.expectedCents), dueDate = due, number = null, count = null,
                    situations = setOfNotNull(
                        if (paid) EntrySituation.Paid else EntrySituation.NotPaid,
                        EntrySituation.Overdue.takeIf { !paid && due.isBefore(today()) },
                    ),
                    method = PaymentMethod.Boleto,
                )
            }
        }

        val sorted = items.sortedWith(compareBy({ it.dueDate }, { it.description }, { it.id }))
        return MonthCommitments(month, sorted, sorted.map { it.amount }.sum())
    }

    // ---------------------------------------------------------------- pendências (tela 18)

    suspend fun pendings(): List<PendingItem> {
        val items = mutableListOf<PendingItem>()
        dao.movementsToClassify().forEach {
            val date = LocalDate.ofEpochDay(it.epochDay)
            items += PendingItem.CategoryToClarify(it.id, it.description, Money(it.amountCents), YearMonth.from(date))
        }
        dao.purchasesToClassify().forEach { p ->
            val first = dao.installments(p.id).first()
            items += PendingItem.CategoryToClarify(p.id, p.description, Money(p.totalCents), YearMonth.parse(first.invoiceMonth))
        }
        dao.boletosToClassify().forEach {
            val date = LocalDate.ofEpochDay(it.paidEpochDay ?: it.dueEpochDay)
            items += PendingItem.CategoryToClarify(it.id, it.description, Money(it.paidCents ?: it.expectedCents), YearMonth.from(date))
        }
        dao.installmentsToConfirm().forEach { i ->
            val p = dao.purchase(i.purchaseId)!!
            items += PendingItem.InvoiceLink(p.id, p.description, Money(i.amountCents), YearMonth.parse(i.invoiceMonth))
        }
        dao.divergentPayments().forEach {
            items += PendingItem.DivergentInvoicePayment(it.id, Money(it.amountCents), LocalDate.ofEpochDay(it.epochDay), YearMonth.parse(it.dueMonth))
        }
        dao.invoiceRows().forEach { row ->
            val month = YearMonth.parse(row.dueMonth)
            invoiceDifference(month)?.let { items += PendingItem.InvoiceTotalDifference(it, month) }
        }
        return items.sortedBy { it.month }
    }
}
