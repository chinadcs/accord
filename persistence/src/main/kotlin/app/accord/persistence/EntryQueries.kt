package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.ActivityGroup
import app.accord.core.space.ActivityLine
import app.accord.core.space.ActivitySubgroup
import app.accord.core.space.ActivityWeek
import app.accord.core.space.AssignmentState
import app.accord.core.space.PurchaseActivity
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
import app.accord.core.space.TotalBalance
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.label
import app.accord.core.sum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

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
    private val invoiceDifference: suspend (YearMonth, String?) -> Money?,
    private val today: () -> LocalDate,
) {
    private companion object {
        /** Antes de qualquer dado possível do app; as consultas por mês só percorrem séries que existem. */
        val EARLIEST_MONTH: YearMonth = YearMonth.of(2000, 1)
    }

    private enum class Bucket { Expense, Income, Credit, InvoicePayment }

    private class Built(val row: EntryRow, val bucket: Bucket, val month: YearMonth, val spentBy: String?)

    private fun methodOf(kind: String) = when (DirectPaymentKind.valueOf(kind)) {
        DirectPaymentKind.Pix -> PaymentMethod.Pix
        DirectPaymentKind.Debit -> PaymentMethod.Debit
        DirectPaymentKind.Cash -> PaymentMethod.Cash
    }

    /**
     * Parcela que a linha da compra exibe: a que vence (fatura) no período filtrado; sem nenhuma nele, a primeira que vence depois; por fim a última.
     * Parcelas canceladas e confirmadas só entram se não houver outra.
     */
    private fun shownInstallment(rows: List<InstallmentRow>, f: EntryFilter): InstallmentRow? {
        val live = rows.filter { it.cancelState != CancellationPlanner.CANCEL_DONE }.ifEmpty { rows }.sortedBy { it.number }
        fun month(r: InstallmentRow) = YearMonth.parse(r.invoiceMonth)
        return live.firstOrNull { month(it) in f.from..f.to }
            ?: live.firstOrNull { month(it) > f.to }
            ?: live.lastOrNull()
    }

    private fun months(from: YearMonth, to: YearMonth): List<YearMonth> =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList()

    // ---------------------------------------------------------------- lista (tela 07)

    /**
     * Linhas de todos os tipos no critério de [f], antes dos filtros de categoria, forma de pagamento, situação, pessoa e busca. A lista de
     * Lançamentos (07) e a atividade de compras (30) usam esta mesma construção, para nunca divergirem nas regras de data.
     */
    private suspend fun build(f: EntryFilter, installmentRows: Boolean = false): List<Built> {
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
        // Na lista de Lançamentos a compra parcelada é sempre uma linha por parcela, no mês da fatura (ADR 0090); o critério "data da compra" fica
        // para a compra à vista. A atividade de compras (30) e as pistas seguem pela compra original.
        if (byBudget || installmentRows) {
            val installments = dao.installmentsInMonths(f.from.toString(), f.to.toString())
            val counts = mutableMapOf<String, Int>()
            val paidInvoices = mutableMapOf<String, Boolean>()
            installments.filter { it.cancelState != CancellationPlanner.CANCEL_DONE }.forEach { i ->
                val p = purchases.getOrPut(i.purchaseId) { dao.purchase(i.purchaseId)!! }
                val count = counts.getOrPut(i.purchaseId) { dao.installments(i.purchaseId).size }
                // Compra à vista no critério "data da compra": entra uma vez, pela data, no bloco de baixo.
                if (!byBudget && count == 1) return@forEach
                val month = YearMonth.parse(i.invoiceMonth)
                val paid = paidInvoices.getOrPut("${p.cardId}:${i.invoiceMonth}") { dao.isInvoicePaid(i.invoiceMonth, p.cardId) }
                built += Built(
                    EntryRow(
                        id = "${p.id}#${i.number}", kind = EntryKind.CardInstallment, description = p.description,
                        categoryId = p.classificationId, amount = Money(i.amountCents), date = LocalDate.ofEpochDay(p.purchaseEpochDay),
                        dueDate = (p.cardId?.let { dao.card(it) }?.let { app.accord.core.CardCycle(it.closingDay, it.dueDay) } ?: card).dueDateOfInvoice(month), method = PaymentMethod.Card,
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
        }
        if (!byBudget) {
            dao.purchasesBetween(fromDay, toDay).forEach { p ->
                val rows = dao.installments(p.id)
                if (installmentRows && rows.size > 1) return@forEach
                val date = LocalDate.ofEpochDay(p.purchaseEpochDay)
                // Parcela cancelada e confirmada deixa de contar, como no critério do orçamento; a compra segue na lista, marcada.
                val cancelled = rows.filter { it.cancelState == CancellationPlanner.CANCEL_DONE }
                val counted = if (cancelled.isEmpty()) p.totalCents else rows.sumOf { it.amountCents } - cancelled.sumOf { it.amountCents }
                // Compra parcelada: a linha mostra a parcela do mês filtrado (ou a mais próxima), não o total; as somas continuam pelo total.
                val shown = if (rows.size > 1) shownInstallment(rows, f) else null
                built += Built(
                    EntryRow(
                        id = p.id, kind = EntryKind.CardPurchase, description = p.description, categoryId = p.classificationId,
                        amount = Money(counted), date = date, dueDate = null, method = PaymentMethod.Card,
                        installmentNumber = shown?.number, installmentCount = rows.size, installmentAmount = shown?.let { Money(it.amountCents) },
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
                    id = i.id, kind = EntryKind.Income, description = i.description, categoryId = null, incomeCategoryId = i.incomeCategoryId,
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
        // Sem série, não há o que expandir; com série, nada existe antes do mês da âncora (folga para pagamento antecipado).
        val firstSeries = dao.activeRecurrences().minOfOrNull { YearMonth.from(LocalDate.ofEpochDay(it.anchorEpochDay)) }
        val occurrenceMonths = if (firstSeries == null) {
            emptyList()
        } else {
            val start = if (byBudget) f.from else f.from.minusMonths(1)
            val end = if (byBudget) f.to else f.to.plusMonths(1)
            months(maxOf(start, firstSeries.minusMonths(2)), end)
        }
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
                        dueDate = if (onCard) (o.cardId?.let { dao.card(it) }?.let { app.accord.core.CardCycle(it.closingDay, it.dueDay) } ?: card).dueDateOfInvoice(o.budgetMonth) else o.scheduledDate,
                        method = if (onCard) PaymentMethod.Card else null,
                        installmentNumber = null, installmentCount = null,
                        situations = setOfNotNull(
                            if (o.paid || (onCard && dao.isInvoicePaid(o.budgetMonth.toString(), o.cardId))) EntrySituation.Paid else EntrySituation.NotPaid,
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
        return built.map { b ->
            val row = b.row
            val cardId = when (row.kind) {
                EntryKind.CardInstallment, EntryKind.CardPurchase -> dao.purchase(row.id.substringBefore('#'))?.cardId
                EntryKind.RecurrenceOccurrence -> dao.recurrence(row.id.substringBefore(':'))?.cardId
                EntryKind.InvoicePayment -> dao.invoicePayment(row.id)?.cardId
                EntryKind.Refund -> dao.refund(row.id)?.let { dao.purchase(it.originId)?.cardId }
                else -> null
            }
            Built(row.copy(cardId = cardId), b.bucket, b.month, b.spentBy)
        }
    }

    suspend fun entries(f: EntryFilter, cursor: Int, pageSize: Int): EntryPage {
        require(cursor >= 0 && pageSize > 0) { "Cursor e tamanho da página precisam ser positivos" }
        val tree = tree()
        val built = build(f, installmentRows = true)
        val matching = built.filter { matches(it, f, tree) }
            .sortedWith(compareByDescending<Built> { it.row.date }.thenBy { it.row.id })
        val page = matching.drop(cursor).take(pageSize)
        val next = (cursor + pageSize).takeIf { it < matching.size }
        fun total(bucket: Bucket) = matching.filter { it.bucket == bucket }.map { it.row.amount }.sum()
        val receivedIncomes = matching
            .filter { it.bucket == Bucket.Income && EntrySituation.Paid in it.row.situations }
            .map { it.row.amount }.sum()
        return EntryPage(
            rows = page.map { it.row },
            nextCursor = next,
            total = matching.size,
            summary = EntrySummary(
                total(Bucket.Expense), total(Bucket.Income), total(Bucket.Credit), total(Bucket.InvoicePayment),
                receivedIncomes,
                incomeCount = matching.count { it.bucket == Bucket.Income },
                expenseCount = matching.count { it.bucket == Bucket.Expense },
            ),
        )
    }

    // ---------------------------------------------------------------- atividade de compras (tela 30)

    /**
     * Compras pela data original. Usa as linhas do critério "data da compra" da lista de Lançamentos e as agrega; só despesas de compra entram
     * no total (Pix, débito, dinheiro, cartão e ocorrência de despesa recorrente). Boleto não tem data de compra: vira lacuna.
     */
    suspend fun purchaseActivity(from: LocalDate, untilExclusive: LocalDate, categoryId: String?): PurchaseActivity {
        require(from.isBefore(untilExclusive)) { "O período precisa ter ao menos um dia" }
        val tree = tree()
        val f = EntryFilter(YearMonth.from(from), YearMonth.from(untilExclusive.minusDays(1)), DateCriterion.PurchaseDate, categoryId = categoryId)
        val rows = build(f).filter { b -> !b.row.date.isBefore(from) && b.row.date.isBefore(untilExclusive) && matches(b, f, tree) }

        fun line(items: List<Built>) = ActivityLine(items.map { it.row.amount }.sum(), items.size)
        fun nullable(items: List<Built>) = if (items.isEmpty()) null else line(items)

        val purchases = rows.filter { it.bucket == Bucket.Expense && it.row.kind != EntryKind.Boleto }
        val classified = purchases.filter { it.row.categoryId != null }
        val groups = classified.groupBy { tree.mainAt(it.row.categoryId!!, YearMonth.from(it.row.date)) }
            .map { (main, items) ->
                val subgroups = items.groupBy { tree.subAt(it.row.categoryId!!, YearMonth.from(it.row.date)) }
                    .map { (sub, subItems) -> ActivitySubgroup(sub, line(subItems)) }
                    .sortedWith(compareByDescending<ActivitySubgroup> { it.line.total }.thenBy { it.subcategoryId ?: "" })
                ActivityGroup(main, line(items), subgroups)
            }
            .sortedWith(compareByDescending<ActivityGroup> { it.line.total }.thenBy { it.categoryId })

        val weeks = purchases.groupBy { it.row.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (monday, items) -> ActivityWeek(monday, line(items)) }
            .sortedBy { it.weekStart }

        return PurchaseActivity(
            from = from, untilExclusive = untilExclusive, categoryId = categoryId,
            total = line(purchases),
            groups = groups,
            toClassify = nullable(purchases.filter { it.row.categoryId == null }),
            weeks = weeks,
            credits = nullable(rows.filter { it.bucket == Bucket.Credit }),
            gaps = nullable(rows.filter { it.bucket == Bucket.Expense && it.row.kind == EntryKind.Boleto }),
            withCancellation = purchases.count { r -> r.row.situations.any { it == EntrySituation.CancelRequested || it == EntrySituation.Cancelled } },
        )
    }

    /** Same original-date builder and exclusions as activity; linked credits remain identifiable evidence. */
    suspend fun clueRows(from: LocalDate, untilExclusive: LocalDate): List<EntryRow> {
        require(from < untilExclusive)
        return build(EntryFilter(YearMonth.from(from), YearMonth.from(untilExclusive.minusDays(1)), DateCriterion.PurchaseDate))
            .filter { it.bucket == Bucket.Credit || (it.bucket == Bucket.Expense && it.row.kind != EntryKind.Boleto) }
            .map { it.row }.filter { it.date >= from && it.date < untilExclusive }
    }

    /**
     * Acumulado até o fim de [month], o mês exibido na lista (no critério escolhido), e o próprio [month]; reaproveita a lista para valer a
     * mesma regra de cada tipo. Mês passado ou futuro vale igual: o saldo é o que foi lançado até o fim dele.
     * Receita e Despesa do mês seguem sempre o mês do orçamento, como o Comprometido (decisão do usuário, accord-jy5): uma compra
     * parcelada entra pela parcela da fatura, não inteira, mesmo com a lista na data da compra.
     */
    suspend fun totalBalance(criterion: DateCriterion, month: YearMonth): TotalBalance = TotalBalance(
        total = entries(EntryFilter(minOf(EARLIEST_MONTH, month), month, criterion), 0, 1).summary,
        month = entries(EntryFilter(month, month, DateCriterion.BudgetMonth), 0, 1).summary,
    )

    /** Descrição e classificação da origem de um estorno; nulo se a origem foi desfeita ou excluída. */
    private suspend fun originInfo(r: RefundRow): Pair<String, String?>? = when (r.originType) {
        RefundPlanner.ORIGIN_PURCHASE -> dao.purchase(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
        RefundPlanner.ORIGIN_MOVEMENT -> dao.movement(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
        else -> dao.boleto(r.originId)?.takeIf { !it.voided }?.let { it.description to it.classificationId }
    }

    private fun matches(b: Built, f: EntryFilter, tree: CategoryTree): Boolean {
        val row = b.row
        if (f.cardId != null && row.cardId != f.cardId) return false
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
            val purchases = mutableMapOf<String, CardPurchaseRow>()
            val counts = mutableMapOf<String, Int>()
            dao.installmentsInMonths(month.toString(), month.toString())
                .filter { it.cancelState != CancellationPlanner.CANCEL_DONE }
                .forEach { i ->
                    val p = purchases.getOrPut(i.purchaseId) { dao.purchase(i.purchaseId)!! }
                    items += Commitment(
                        id = "${p.id}#${i.number}", nature = CommitmentNature.Finite, description = p.description,
                        categoryId = p.classificationId, amount = Money(i.amountCents), dueDate = (p.cardId?.let { dao.card(it) }?.let { app.accord.core.CardCycle(it.closingDay, it.dueDay) } ?: card).dueDateOfInvoice(month),
                        number = i.number, count = counts.getOrPut(p.id) { dao.installments(p.id).size },
                        situations = setOfNotNull(
                            if (dao.isInvoicePaid(month.toString(), p.cardId)) EntrySituation.Paid else EntrySituation.NotPaid,
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
                    dueDate = if (onCard) (o.cardId?.let { dao.card(it) }?.let { app.accord.core.CardCycle(it.closingDay, it.dueDay) } ?: card).dueDateOfInvoice(o.budgetMonth) else o.scheduledDate,
                    number = null, count = null,
                    situations = setOfNotNull(
                        if (o.paid || (onCard && dao.isInvoicePaid(o.budgetMonth.toString(), o.cardId))) EntrySituation.Paid else EntrySituation.NotPaid,
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
            items += PendingItem.InvoiceLink(p.id, p.description, Money(i.amountCents), YearMonth.parse(i.invoiceMonth), p.cardId)
        }
        dao.divergentPayments().forEach {
            items += PendingItem.DivergentInvoicePayment(it.id, Money(it.amountCents), LocalDate.ofEpochDay(it.epochDay), YearMonth.parse(it.dueMonth), it.cardId)
        }
        dao.invoiceRows().forEach { row ->
            val month = YearMonth.parse(row.dueMonth)
            invoiceDifference(month, row.cardId)?.let { items += PendingItem.InvoiceTotalDifference(it, month, row.cardId) }
        }
        return items.sortedBy { it.month }
    }
}
