package app.accord.persistence

import app.accord.core.label
import app.accord.core.space.CorrectOccurrenceInvoice
import app.accord.core.CardCycle
import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.OccurrenceOverride
import app.accord.core.Recurrence
import app.accord.core.RecurrenceEdit
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.RecurrenceSeries
import app.accord.core.Template
import app.accord.core.space.AssignmentState
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.Decision
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.EditRecurrence
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.OccurrenceView
import app.accord.core.space.Origin
import app.accord.core.space.PayOccurrence
import app.accord.core.space.UndoOccurrencePayment
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.Session
import app.accord.core.space.SessionProvider
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Recorrências (ADR 0011): série, exceções, pagamento de ocorrência e a posição de cada ocorrência no orçamento. */
internal class RecurrencePlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val accounts: AccountPlanner,
    private val sessions: SessionProvider,
    private val cycle: suspend (String) -> CardCycle,
) {
    suspend fun loadSeries(row: RecurrenceRow): LoadedSeries {
        val segments = dao.segments(row.id).associate {
            it.fromNumber to Template(it.description, it.classificationId, Money(it.amountCents), it.accountId)
        }
        val overrides = dao.overrides(row.id).associate {
            it.number to OccurrenceOverride(
                it.deleted,
                RecurrenceFields(it.description, it.classificationId, it.amountCents?.let(::Money), it.accountId),
                it.invoiceMonth?.let(YearMonth::parse),
            )
        }
        val series = RecurrenceSeries.restore(
            Recurrence(LocalDate.ofEpochDay(row.anchorEpochDay), Frequency.valueOf(row.frequency)),
            segments, overrides, row.endsBefore,
        )
        return LoadedSeries(row, series, dao.settlements(row.id).associateBy { it.number })
    }

    private suspend fun saveSeries(id: String, series: RecurrenceSeries) {
        dao.deleteSegments(id)
        dao.insertSegments(
            series.segments.map { (from, t) ->
                RecurrenceSegmentRow(id, from, t.description, t.classificationId, t.amount.cents, t.accountId)
            },
        )
        dao.deleteOverrides(id)
        dao.insertOverrides(
            series.overrides.map { (n, o) ->
                RecurrenceOverrideRow(
                    id, n, o.deleted, o.fields.description, o.fields.classificationId, o.fields.amount?.cents, o.fields.accountId,
                    o.invoice?.toString(), dao.recurrence(id)?.cardId,
                )
            },
        )
    }

    suspend fun planCreate(session: Session, origin: Origin, c: CreateRecurrence): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val categoryId = c.categoryId
        when (c.kind) {
            RecurrenceKind.Expense -> when {
                categoryId == null -> errors += FieldError("categoryId", ErrorCode.Required, "Escolha a categoria.")
                dao.activeCategory(categoryId) == null ->
                    errors += FieldError("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")
            }
            RecurrenceKind.Income -> if (categoryId != null) {
                errors += FieldError("categoryId", ErrorCode.NotAllowed, "Renda não tem categoria.")
            }
        }
        val cardId = if (c.form == RecurrenceForm.Card) accounts.resolveCard(session, c.cardId, errors) else null
        val accountId = c.accountId
        if (accountId != null && dao.account(accountId) == null) {
            errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        }
        rules.originError(c.originIntentionId)?.let { errors += it }
        if (errors.isNotEmpty()) return stop(errors)

        return Planned.Go(months = setOf(YearMonth.from(c.firstDate)), undoable = true) {
            val id = UUID.randomUUID().toString()
            dao.insertRecurrence(
                RecurrenceRow(
                    id = id,
                    kind = c.kind.name,
                    form = (if (c.kind == RecurrenceKind.Expense) c.form else RecurrenceForm.OffCard).name,
                    frequency = c.frequency.name,
                    anchorEpochDay = c.firstDate.toEpochDay(),
                    endsBefore = null,
                    authorId = session.personId,
                    origin = origin.name,
                    revision = 1,
                    voided = false,
                    originIntentionId = c.originIntentionId,
                    cardId = cardId,
                ),
            )
            dao.insertSegments(
                listOf(RecurrenceSegmentRow(id, 1, c.description.trim(), categoryId, c.amount.cents, accountId)),
            )
            listOf(EntityChange(EntityType.Recurrence, id, null, 1))
        }
    }

    /** Carrega a série e checa existência, exclusão e versão; devolve o carregamento ou a parada. */
    private suspend fun loadForCommand(id: String, expectedRevision: Long?): Pair<LoadedSeries?, Planned.Stop?> {
        val row = dao.recurrence(id)
            ?: return null to Planned.Stop(CommandResult.Rejected(listOf(FieldError("recurrenceId", ErrorCode.UnknownReference, "Recorrência não encontrada."))))
        if (row.voided) {
            return null to Planned.Stop(CommandResult.Rejected(listOf(FieldError("recurrenceId", ErrorCode.NotAllowed, "Esta recorrência foi desfeita ou excluída."))))
        }
        if (expectedRevision != null && expectedRevision != row.revision) {
            return null to Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        return loadSeries(row) to null
    }

    suspend fun planEdit(c: EditRecurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val scope = c.scope ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.ScopeRequired))
        val errors = mutableListOf<FieldError>()
        if (c.fields.isEmpty) errors += FieldError("fields", ErrorCode.Required, "Informe o que mudou.")
        c.fields.description?.let { if (it.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.") }
        c.fields.amount?.let { if (it.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.") }
        c.fields.classificationId?.let {
            when {
                loaded.row.kind == RecurrenceKind.Income.name ->
                    errors += FieldError("classificationId", ErrorCode.NotAllowed, "Renda não tem categoria.")
                dao.activeCategory(it) == null ->
                    errors += FieldError("classificationId", ErrorCode.UnknownReference, "Categoria não encontrada.")
            }
        }
        c.fields.accountId?.let {
            if (dao.account(it) == null) errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        }
        val occurrence = loaded.series.occurrence(c.occurrence)
        if (occurrence == null) errors += FieldError("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        if (errors.isNotEmpty()) return stop(errors)

        val outcome = loaded.series.edit(c.occurrence, c.fields, scope, loaded.settlements.keys, c.confirmRealized)
        if (outcome is RecurrenceEdit.NeedsConfirmation) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.RealizedOccurrences(outcome.realized)))
        }
        outcome as RecurrenceEdit.Applied
        return Planned.Go(months = setOf(YearMonth.from(occurrence!!.date)), replaced = outcome.replacedExceptions) {
            saveSeries(loaded.row.id, outcome.series)
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    suspend fun planDelete(c: DeleteRecurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val scope = c.scope ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.ScopeRequired))
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")

        val outcome = loaded.series.delete(c.occurrence, scope, loaded.settlements.keys, c.confirmRealized)
        if (outcome is RecurrenceEdit.NeedsConfirmation) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.RealizedOccurrences(outcome.realized)))
        }
        outcome as RecurrenceEdit.Applied
        return Planned.Go(months = setOf(YearMonth.from(occurrence.date))) {
            saveSeries(loaded.row.id, outcome.series)
            // Ocorrência excluída deixa de existir, inclusive o pagamento que a confirmação autorizou remover.
            if (scope == RecurrenceScope.OnlyThis) dao.deleteSettlement(loaded.row.id, c.occurrence)
            else dao.deleteSettlementsFrom(loaded.row.id, c.occurrence)
            val next = loaded.row.copy(revision = loaded.row.revision + 1, endsBefore = outcome.series.endsBefore)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    /**
     * Confere ou corrige a fatura de uma ocorrência no cartão (ADR 0049). Só esta ocorrência muda; a escolha fica como exceção
     * da ocorrência, tira o selo "a conferir" e nunca altera o ciclo das demais. Mudar de fatura exige que nenhuma das duas
     * esteja quitada, como na correção da fatura de uma parcela.
     */
    suspend fun planCorrectInvoice(c: CorrectOccurrenceInvoice): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        if (loaded.row.kind != RecurrenceKind.Expense.name || loaded.row.form != RecurrenceForm.Card.name) {
            return stop("occurrence", ErrorCode.NotAllowed, "Só a ocorrência de uma despesa no cartão tem fatura.")
        }
        val cycleOfSpace = cycle(loaded.row.cardId ?: cardIdOf(sessions.current().spaceId))
        val view = place(loaded, c.occurrence, cycleOfSpace)!!
        val estimate = view.estimatedInvoice!!
        val current = view.budgetMonth
        if (kotlin.math.abs(java.time.temporal.ChronoUnit.MONTHS.between(estimate, c.invoice)) > 1) {
            return stop("invoice", ErrorCode.OutOfRange, "A fatura pode ficar no máximo um mês da estimada (${estimate.label()}).")
        }
        if (loaded.series.overrides[c.occurrence]?.invoice == c.invoice) {
            return stop("invoice", ErrorCode.NotAllowed, "Esta fatura já está confirmada para a ocorrência.")
        }
        if (c.invoice != current) {
            val errors = buildList {
                setOf(current, c.invoice).filter { dao.isInvoicePaid(it.toString()) }
                    .forEach { add(FieldError("invoice", ErrorCode.NotAllowed, "A fatura de ${it.label()} já foi quitada.")) }
            }
            if (errors.isNotEmpty()) return stop(errors)
        }
        val series = loaded.series.withInvoice(c.occurrence, c.invoice)
        return Planned.Go(months = setOf(current, c.invoice, estimate)) {
            saveSeries(loaded.row.id, series)
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    suspend fun planPay(session: Session, c: PayOccurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        if (loaded.row.kind == RecurrenceKind.Expense.name && loaded.row.form == RecurrenceForm.Card.name) {
            return stop("occurrence", ErrorCode.NotAllowed, "Ocorrência no cartão é paga pela quitação da fatura.")
        }
        if (loaded.settlements.containsKey(c.occurrence)) {
            return stop("occurrence", ErrorCode.NotAllowed, "Esta ocorrência já foi registrada. Desfaça antes de registrar outra.")
        }
        val errors = mutableListOf<FieldError>()
        rules.validateSettlement(c.settlement, "settlement", errors)
        val accountId = accounts.resolveRequired(session, c.accountId ?: occurrence.template.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(occurrence.date), YearMonth.from(c.settlement.date))
        return Planned.Go(months = months, undoable = true, detail = "occurrence=${c.occurrence}") {
            dao.upsertSettlement(
                RecurrenceSettlementRow(
                    loaded.row.id, c.occurrence, c.settlement.date.toEpochDay(),
                    (c.settlement.amount ?: occurrence.template.amount).cents, accountId,
                ),
            )
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    suspend fun planUndoPay(c: UndoOccurrencePayment): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        val settled = loaded.settlements[c.occurrence]
            ?: return stop("occurrence", ErrorCode.NotAllowed, "Esta ocorrência ainda não foi registrada.")
        val months = setOf(YearMonth.from(occurrence.date), YearMonth.from(LocalDate.ofEpochDay(settled.epochDay)))
        return Planned.Go(months = months) {
            dao.deleteSettlement(loaded.row.id, c.occurrence)
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    /** Ocorrência de [series] já posicionada no orçamento; nula se a ocorrência não existe. */
    fun place(loaded: LoadedSeries, number: Int, cycle: CardCycle): OccurrenceView? {
        val occurrence = loaded.series.occurrence(number) ?: return null
        val settled = loaded.settlements[number]
        val kind = RecurrenceKind.valueOf(loaded.row.kind)
        val form = RecurrenceForm.valueOf(loaded.row.form)
        val settledDate = settled?.let { LocalDate.ofEpochDay(it.epochDay) }
        val onCard = kind == RecurrenceKind.Expense && form == RecurrenceForm.Card
        val estimate = if (onCard) cycle.assign(occurrence.date) else null
        // A fatura que a pessoa escolheu (ADR 0049) vale sobre o ciclo e tira o selo "a conferir".
        val chosen = loaded.series.overrides[number]?.invoice?.takeIf { onCard }
        return OccurrenceView(
            recurrenceId = loaded.row.id,
            number = number,
            kind = kind,
            form = form,
            scheduledDate = occurrence.date,
            description = occurrence.template.description,
            categoryId = occurrence.template.classificationId,
            expectedAmount = occurrence.template.amount,
            accountId = settled?.accountId ?: occurrence.template.accountId,
            settledDate = settledDate,
            settledAmount = settled?.let { Money(it.cents) },
            budgetMonth = chosen ?: estimate?.dueMonth ?: YearMonth.from(settledDate ?: occurrence.date),
            paid = settled != null,
            cardId = loaded.row.cardId,
            assignment = estimate?.let {
                when {
                    chosen != null -> AssignmentState.Confirmed
                    it.toConfirm -> AssignmentState.ToConfirm
                    else -> AssignmentState.Estimated
                }
            },
            estimatedInvoice = estimate?.dueMonth,
        )
    }

    suspend fun occurrencesIn(month: YearMonth): List<OccurrenceView> {
        return dao.activeRecurrences().flatMap { row ->
            val loaded = loadSeries(row)
            // O mês de uma ocorrência pode diferir do mês da data prevista (pagamento antecipado ou
            // atrasado, fatura seguinte): olha os meses vizinhos e os pagamentos registrados.
            // A fatura escolhida pode ficar um mês além da estimada: olha mais um mês para trás.
            val candidates = listOf(month.minusMonths(3), month.minusMonths(2), month.minusMonths(1), month, month.plusMonths(1))
                .mapNotNull { loaded.series.recurrence.numberIn(it) }.toSet() +
                loaded.settlements.filterValues { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }.keys
            candidates.mapNotNull { place(loaded, it, cycle(row.cardId ?: cardIdOf(sessions.current().spaceId))) }.filter { it.budgetMonth == month }
        }.sortedWith(compareBy({ it.scheduledDate }, { it.recurrenceId }, { it.number }))
    }
}

internal data class LoadedSeries(
    val row: RecurrenceRow,
    val series: RecurrenceSeries,
    val settlements: Map<Int, RecurrenceSettlementRow>,
)
