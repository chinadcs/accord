package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateReceivable
import app.accord.core.space.DebtorNode
import app.accord.core.space.DebtorSummary
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.IncomeCategorySeed
import app.accord.core.space.NameKey
import app.accord.core.space.Origin
import app.accord.core.space.ReceivableSlot
import app.accord.core.space.ReceivableView
import app.accord.core.space.ReceivablesOverview
import app.accord.core.space.ReceiveFromDebtor
import app.accord.core.space.RemoveReceipt
import app.accord.core.space.RenameDebtor
import app.accord.core.space.ReplanReceivable
import app.accord.core.space.RescheduleReceivable
import app.accord.core.space.Session
import app.accord.core.space.VoidReceivable
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * "Quem me deve" (ADR 0083). O cronograma e os recebimentos são rendas ligadas por `receivableId`; em aberto, resolvido e sem data
 * combinada saem delas ([view]). Toda mudança é um comando, numa operação só.
 */
internal class ReceivablePlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val accounts: AccountPlanner,
) {
    // ---- leitura ----

    suspend fun view(row: ReceivableRow): ReceivableView {
        val incomes = dao.incomesOfReceivable(row.id)
        val received = incomes.filter { it.receivedEpochDay != null }
        val expected = incomes.filter { it.receivedEpochDay == null }
        val resolved = received.sumOf { it.receivedCents ?: it.expectedCents }
        val open = (row.totalCents - resolved).coerceAtLeast(0)
        val scheduled = expected.sumOf { it.expectedCents }
        val counts = row.countsInBudget
        return ReceivableView(
            id = row.id, debtorId = row.debtorId, total = Money(row.totalCents), accountId = row.accountId,
            incomeCategoryId = row.incomeCategoryId, registeredDate = LocalDate.ofEpochDay(row.registeredEpochDay), note = row.note,
            countsInBudget = counts, lentFromAccountId = row.lentFromAccountId, lentDate = row.lentEpochDay?.let(LocalDate::ofEpochDay),
            voided = row.voided, revision = row.revision,
            resolved = Money(resolved), open = Money(open), unscheduled = Money((open - scheduled).coerceAtLeast(0)),
            scheduled = expected.sortedBy { it.expectedEpochDay }.map { it.toView().copy(countsInIncome = counts) },
            receipts = received.sortedBy { it.receivedEpochDay }.map { it.toView().copy(countsInIncome = counts) },
            outflow = row.lentFromAccountId != null || dao.movementsOfReceivable(row.id).any { !it.voided },
        )
    }

    suspend fun overview(): ReceivablesOverview {
        val rows = dao.receivables().filter { !it.voided }
        val views = rows.map { view(it) }.groupBy { it.debtorId }
        val summaries = dao.debtors().filter { !it.voided }.mapNotNull { debtor ->
            val own = views[debtor.id].orEmpty()
            if (own.isEmpty()) return@mapNotNull null
            DebtorSummary(
                debtor = DebtorNode(debtor.id, debtor.name, debtor.revision),
                open = Money(own.sumOf { it.open.cents }),
                resolved = Money(own.sumOf { it.resolved.cents }),
                total = Money(own.sumOf { it.total.cents }),
                nextDate = own.flatMap { it.scheduled }.minOfOrNull { it.expectedDate },
                receivables = own,
            )
        }
        // Quem ainda deve vem primeiro, pela data combinada mais próxima (sem data por último); os quitados ficam no fim.
        val ordered = summaries.sortedWith(
            compareBy<DebtorSummary> { it.open.cents == 0L }
                .thenBy { it.nextDate == null }
                .thenBy { it.nextDate }
                .thenBy { it.debtor.name.lowercase() },
        )
        return ReceivablesOverview(Money(summaries.sumOf { it.open.cents }), ordered)
    }

    // ---- criar ----

    private fun validateSchedule(schedule: List<ReceivableSlot>, limitCents: Long, errors: MutableList<FieldError>) {
        if (schedule.any { it.amount.cents <= 0 }) {
            errors += FieldError("schedule", ErrorCode.MustBePositive, "Cada recebimento precisa ter valor maior que zero.")
        } else if (schedule.sumOf { it.amount.cents } > limitCents) {
            errors += FieldError("schedule", ErrorCode.OutOfRange, "A soma dos recebimentos passa do que está em aberto.")
        }
    }

    suspend fun planCreate(session: Session, origin: Origin, c: CreateReceivable): Planned {
        val errors = mutableListOf<FieldError>()
        val name = c.debtorName.trim()
        val existing = c.debtorId?.let { id ->
            dao.debtor(id)?.takeIf { !it.voided } ?: run {
                errors += FieldError("debtorId", ErrorCode.UnknownReference, "Devedor não encontrado.")
                null
            }
        }
        if (c.debtorId == null && name.isEmpty()) errors += FieldError("debtorName", ErrorCode.Required, "Informe quem vai pagar.")
        if (c.total.cents <= 0) errors += FieldError("total", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        validateSchedule(c.schedule, c.total.cents, errors)
        val accountId = accounts.resolveRequired(session, c.accountId, errors)
        val chosenIncomeCategory = c.incomeCategoryId
        if (chosenIncomeCategory != null && dao.incomeCategory(chosenIncomeCategory) == null) {
            errors += FieldError("incomeCategoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        }
        // Sem escolha, a subcategoria Outros › Empréstimos Recebidos, se ainda existir neste espaço.
        val incomeCategoryId = chosenIncomeCategory
            ?: DeterministicIds.seedIncomeCategory(session.spaceId, IncomeCategorySeed.LOANS_RECEIVED).takeIf { dao.incomeCategory(it) != null }
        // Despesa contada: a categoria de despesa é obrigatória, e só existe saída de caixa se o dinheiro saiu da conta.
        val expense = c.countsInBudget && !c.noOutflow
        val expenseCategory = if (expense) {
            val chosen = c.expenseCategoryId
            if (chosen == null) {
                errors += FieldError("expenseCategoryId", ErrorCode.Required, "Escolha a categoria da despesa.")
                null
            } else {
                rules.validateClassification(Classification.Category(chosen), errors)
            }
        } else {
            null
        }
        if (errors.isNotEmpty()) return stop(errors)

        val debtorName = existing?.name ?: name
        val key = NameKey.of(debtorName)
        val reused = existing ?: dao.debtors().firstOrNull { !it.voided && it.nameKey == key }
        val months = buildSet {
            add(YearMonth.from(c.date))
            c.schedule.forEach { add(YearMonth.from(it.date)) }
        }
        return Planned.Go(months = months, undoable = true) {
            val changes = mutableListOf<EntityChange>()
            val debtor = reused ?: DebtorRow(UUID.randomUUID().toString(), debtorName, key, session.personId, 1).also {
                dao.upsertDebtor(it)
                changes += EntityChange(EntityType.Debtor, it.id, null, 1)
            }
            val receivable = ReceivableRow(
                id = UUID.randomUUID().toString(), debtorId = debtor.id, totalCents = c.total.cents, accountId = accountId,
                incomeCategoryId = incomeCategoryId, registeredEpochDay = c.date.toEpochDay(), note = c.note.trim(),
                countsInBudget = c.countsInBudget,
                lentFromAccountId = accountId.takeIf { !c.noOutflow && !c.countsInBudget },
                lentEpochDay = c.date.toEpochDay().takeIf { !c.noOutflow && !c.countsInBudget },
                authorId = session.personId, voided = false, revision = 1,
            )
            dao.upsertReceivable(receivable)
            changes += EntityChange(EntityType.Receivable, receivable.id, null, 1)
            if (expense) {
                val movement = MovementEntity(
                    id = UUID.randomUUID().toString(), kind = "Pix", description = "A receber de ${debtor.name}",
                    classificationId = expenseCategory, epochDay = c.date.toEpochDay(), amountCents = c.total.cents,
                    accountId = accountId, spentBy = null, authorId = session.personId, origin = origin.name,
                    revision = 1, voided = false, receivableId = receivable.id,
                )
                dao.insertMovement(movement)
                changes += EntityChange(EntityType.Movement, movement.id, null, 1)
            }
            c.schedule.forEach { slot ->
                changes += insertExpected(session, origin, receivable, debtor.name, slot)
            }
            changes
        }
    }

    private suspend fun insertExpected(session: Session, origin: Origin, receivable: ReceivableRow, debtorName: String, slot: ReceivableSlot): EntityChange {
        val row = IncomeRow(
            id = UUID.randomUUID().toString(), description = "Recebimento de $debtorName", expectedCents = slot.amount.cents,
            expectedEpochDay = slot.date.toEpochDay(), accountId = receivable.accountId, receivedEpochDay = null, receivedCents = null,
            authorId = session.personId, origin = origin.name, revision = 1, voided = false,
            incomeCategoryId = receivable.incomeCategoryId, receivableId = receivable.id,
        )
        dao.insertIncome(row)
        return EntityChange(EntityType.Income, row.id, null, 1)
    }

    // ---- receber ----

    /** O valor a receber ativo, ou a recusa (não existe, foi cancelado ou a versão mudou). */
    private class Gate(val row: ReceivableRow?, val stop: Planned.Stop?)

    private suspend fun loadOpen(id: String, expectedRevision: Long?): Gate {
        val row = dao.receivable(id)?.takeIf { !it.voided }
            ?: return Gate(null, stop("receivableId", ErrorCode.UnknownReference, "Valor a receber não encontrado."))
        if (expectedRevision != null && expectedRevision != row.revision) return Gate(null, Planned.Stop(CommandResult.StaleVersion(row.id, row.revision)))
        return Gate(row, null)
    }

    /** "Recebi": abate sempre o previsto mais antigo; sem previsto, vira um recebimento avulso. */
    suspend fun planReceive(session: Session, origin: Origin, c: ReceiveFromDebtor): Planned {
        val gate = loadOpen(c.receivableId, c.expectedRevision)
        val receivable = gate.row ?: return gate.stop!!
        val errors = mutableListOf<FieldError>()
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val view = view(receivable)
        if (c.amount.cents > view.open.cents) {
            errors += FieldError("amount", ErrorCode.OutOfRange, "O valor passa do que está em aberto (${view.open.format()}).")
        }
        val accountId = accounts.resolveRequired(session, c.accountId ?: receivable.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)
        val debtor = checkNotNull(dao.debtor(receivable.debtorId))
        val expected = dao.incomesOfReceivable(receivable.id).filter { it.receivedEpochDay == null }
            .sortedWith(compareBy<IncomeRow> { it.expectedEpochDay }.thenBy { it.id })
        val months = buildSet {
            add(YearMonth.from(c.date))
            expected.forEach { add(YearMonth.from(LocalDate.ofEpochDay(it.expectedEpochDay))) }
        }
        return Planned.Go(months = months) {
            val changes = mutableListOf<EntityChange>()
            var remaining = c.amount.cents
            val day = c.date.toEpochDay()
            for (slot in expected) {
                if (remaining == 0L) break
                if (slot.expectedCents <= remaining) {
                    val next = slot.copy(accountId = accountId, receivedEpochDay = day, receivedCents = slot.expectedCents, revision = slot.revision + 1)
                    dao.upsertIncome(next)
                    changes += EntityChange(EntityType.Income, next.id, null, next.revision)
                    remaining -= slot.expectedCents
                } else {
                    // Sobra parcial: o previsto encolhe e a parte recebida vira um recebimento próprio.
                    val next = slot.copy(expectedCents = slot.expectedCents - remaining, revision = slot.revision + 1)
                    dao.upsertIncome(next)
                    changes += EntityChange(EntityType.Income, next.id, null, next.revision)
                    changes += insertReceipt(session, origin, receivable, debtor.name, remaining, day, accountId)
                    remaining = 0
                }
            }
            if (remaining > 0) changes += insertReceipt(session, origin, receivable, debtor.name, remaining, day, accountId)
            changes
        }
    }

    private suspend fun insertReceipt(
        session: Session, origin: Origin, receivable: ReceivableRow, debtorName: String, cents: Long, day: Long, accountId: String?,
    ): EntityChange {
        val row = IncomeRow(
            id = UUID.randomUUID().toString(), description = "Recebimento de $debtorName", expectedCents = cents, expectedEpochDay = day,
            accountId = accountId, receivedEpochDay = day, receivedCents = cents, authorId = session.personId, origin = origin.name,
            revision = 1, voided = false, incomeCategoryId = receivable.incomeCategoryId, receivableId = receivable.id,
        )
        dao.insertIncome(row)
        return EntityChange(EntityType.Income, row.id, null, 1)
    }

    // ---- remarcar e replanejar ----

    suspend fun planReschedule(c: RescheduleReceivable): Planned {
        val gate = loadOpen(c.receivableId, c.expectedRevision)
        val receivable = gate.row ?: return gate.stop!!
        val expected = dao.incomesOfReceivable(receivable.id).filter { it.receivedEpochDay == null }
            .sortedWith(compareBy<IncomeRow> { it.expectedEpochDay }.thenBy { it.id })
        val target = expected.firstOrNull { it.id == c.incomeId }
            ?: return stop("incomeId", ErrorCode.UnknownReference, "Recebimento previsto não encontrado.")
        val delta = c.newDate.toEpochDay() - target.expectedEpochDay
        if (delta == 0L) return stop("newDate", ErrorCode.NotAllowed, "Escolha uma data diferente.")
        val moved = if (c.shiftFollowing) expected.dropWhile { it.id != target.id } else listOf(target)
        val months = buildSet {
            moved.forEach {
                add(YearMonth.from(LocalDate.ofEpochDay(it.expectedEpochDay)))
                add(YearMonth.from(LocalDate.ofEpochDay(it.expectedEpochDay + delta)))
            }
        }
        return Planned.Go(months = months) {
            moved.map { slot ->
                val next = slot.copy(expectedEpochDay = slot.expectedEpochDay + delta, revision = slot.revision + 1)
                dao.upsertIncome(next)
                EntityChange(EntityType.Income, next.id, null, next.revision)
            }
        }
    }

    suspend fun planReplan(session: Session, origin: Origin, c: ReplanReceivable): Planned {
        val gate = loadOpen(c.receivableId, c.expectedRevision)
        val receivable = gate.row ?: return gate.stop!!
        val errors = mutableListOf<FieldError>()
        val open = view(receivable).open.cents
        validateSchedule(c.schedule, open, errors)
        if (errors.isNotEmpty()) return stop(errors)
        val debtor = checkNotNull(dao.debtor(receivable.debtorId))
        val old = dao.incomesOfReceivable(receivable.id).filter { it.receivedEpochDay == null }
        val months = buildSet {
            old.forEach { add(YearMonth.from(LocalDate.ofEpochDay(it.expectedEpochDay))) }
            c.schedule.forEach { add(YearMonth.from(it.date)) }
        }
        return Planned.Go(months = months) {
            val changes = mutableListOf<EntityChange>()
            old.forEach {
                val next = it.copy(voided = true, revision = it.revision + 1)
                dao.upsertIncome(next)
                changes += EntityChange(EntityType.Income, next.id, null, next.revision)
            }
            c.schedule.forEach { changes += insertExpected(session, origin, receivable, debtor.name, it) }
            changes
        }
    }

    // ---- cancelar, renomear, remover recebimento ----

    /** Anula o valor a receber como se não tivesse existido: previstos e a saída (despesa ou movimento de saldo) saem junto. Só sem recebimentos. */
    suspend fun planVoid(c: VoidReceivable): Planned {
        val gate = loadOpen(c.receivableId, c.expectedRevision)
        val receivable = gate.row ?: return gate.stop!!
        val incomes = dao.incomesOfReceivable(receivable.id)
        if (incomes.any { it.receivedEpochDay != null }) {
            return stop("receivableId", ErrorCode.NotAllowed, "Já há recebimentos. Remova-os do histórico antes de cancelar.")
        }
        val movements = dao.movementsOfReceivable(receivable.id).filter { !it.voided }
        val months = buildSet {
            add(YearMonth.from(LocalDate.ofEpochDay(receivable.registeredEpochDay)))
            incomes.forEach { add(YearMonth.from(LocalDate.ofEpochDay(it.expectedEpochDay))) }
        }
        return Planned.Go(months = months) {
            val changes = mutableListOf<EntityChange>()
            val next = receivable.copy(voided = true, revision = receivable.revision + 1)
            dao.upsertReceivable(next)
            changes += EntityChange(EntityType.Receivable, next.id, null, next.revision)
            incomes.forEach {
                val n = it.copy(voided = true, revision = it.revision + 1)
                dao.upsertIncome(n)
                changes += EntityChange(EntityType.Income, n.id, null, n.revision)
            }
            movements.forEach {
                val n = it.copy(voided = true, revision = it.revision + 1)
                dao.upsertMovement(n)
                changes += EntityChange(EntityType.Movement, n.id, null, n.revision)
            }
            changes
        }
    }

    suspend fun planRenameDebtor(c: RenameDebtor): Planned {
        val row = dao.debtor(c.debtorId)?.takeIf { !it.voided }
            ?: return stop("debtorId", ErrorCode.UnknownReference, "Devedor não encontrado.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome.")
        val key = NameKey.of(name)
        if (dao.debtors().any { it.id != row.id && !it.voided && it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe um devedor com esse nome.")
        }
        return Planned.Go(months = emptySet()) {
            val next = row.copy(name = name, nameKey = key, revision = row.revision + 1)
            dao.upsertDebtor(next)
            listOf(EntityChange(EntityType.Debtor, next.id, null, next.revision))
        }
    }

    /** Remove um recebimento do histórico: a renda é anulada e o valor volta a ficar em aberto, sem recriar previsto. */
    suspend fun planRemoveReceipt(c: RemoveReceipt): Planned {
        val income = dao.income(c.incomeId)?.takeIf { !it.voided && it.receivableId != null && it.receivedEpochDay != null }
            ?: return stop("incomeId", ErrorCode.UnknownReference, "Recebimento não encontrado.")
        if (c.expectedRevision != null && c.expectedRevision != income.revision) return Planned.Stop(CommandResult.StaleVersion(income.id, income.revision))
        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(income.receivedEpochDay!!)))
        return Planned.Go(months = months) {
            val next = income.copy(voided = true, revision = income.revision + 1)
            dao.upsertIncome(next)
            listOf(EntityChange(EntityType.Income, next.id, null, next.revision))
        }
    }

    /** Valida um recebimento já confirmado pela interface de rendas (soma recebida contra o total), usado pelo "Recebido" de uma renda ligada. */
    suspend fun receivedTotal(receivableId: String): Long = dao.incomesOfReceivable(receivableId).filter { it.receivedEpochDay != null }
        .sumOf { it.receivedCents ?: it.expectedCents }
}
