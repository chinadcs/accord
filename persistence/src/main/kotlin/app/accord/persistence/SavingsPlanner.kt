package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.space.*
import java.time.LocalDate
import java.util.UUID

internal class SavingsPlanner(private val dao: SpaceDao, private val clock: BudgetClock) {
    private val balances = BalanceReader(dao)

    suspend fun view(row: SavingsPocketRow): SavingsPocket = SavingsPocket(
        row.id, row.accountId, row.name, Money(row.initialCents), LocalDate.ofEpochDay(row.cutoffEpochDay),
        row.goalCents?.let(::Money), row.deadlineEpochDay?.let(LocalDate::ofEpochDay), row.archived,
        AccountBalances.balance(Money(row.initialCents), LocalDate.ofEpochDay(row.cutoffEpochDay), events(row)), row.revision, row.createdBy,
    )

    private suspend fun events(row: SavingsPocketRow): List<BalanceEvent> = dao.savingsMoves()
        .filter { it.pocketId == row.id && !it.voided }.map { m ->
            BalanceEvent(LocalDate.ofEpochDay(m.epochDay), Money(if (m.direction == "Deposit") m.amountCents else -m.amountCents), BalanceSource.TransferIn)
        }

    private fun metadata(name: String, goal: Money?): List<FieldError> = buildList {
        if (name.isBlank() || name.trim().length > 80) add(FieldError("name", ErrorCode.OutOfRange, "Informe um nome de até 80 caracteres."))
        if (goal != null && goal.cents <= 0) add(FieldError("goal", ErrorCode.MustBePositive, "O alvo precisa ser maior que zero ou ficar em branco."))
    }

    private suspend fun validateMove(pocket: SavingsPocketRow, account: AccountRow, direction: SavingsDirection, amount: Money, date: LocalDate): Planned.Stop? {
        if (pocket.archived) return stop("pocketId", ErrorCode.NotAllowed, "Este cofrinho está arquivado.")
        if (amount.cents <= 0) return stop("amount", ErrorCode.MustBePositive, "Informe um valor maior que zero.")
        if (date > clock.today() || date.toEpochDay() < pocket.cutoffEpochDay ||
            (account.balanceCutoffEpochDay != null && date.toEpochDay() < account.balanceCutoffEpochDay))
            return stop("date", ErrorCode.OutOfRange, "Use uma data efetiva a partir dos saldos iniciais, até hoje.")
        if (direction == SavingsDirection.Deposit) {
            val initial = account.initialBalanceCents ?: return stop("accountId", ErrorCode.Required, "Defina o saldo próprio da conta antes de guardar.")
            val cutoff = account.balanceCutoffEpochDay ?: return stop("accountId", ErrorCode.Required, "Defina a data do saldo próprio da conta.")
            val minimum = SavingsBalances.minimumFrom(Money(initial), LocalDate.ofEpochDay(cutoff), balances.events(account, cutoff), date)
            if (minimum.cents < amount.cents) return stop("amount", ErrorCode.OutOfRange, "O saldo próprio da conta não é suficiente nesta data e nas movimentações posteriores conhecidas.")
        } else {
            val minimum = SavingsBalances.minimumFrom(Money(pocket.initialCents), LocalDate.ofEpochDay(pocket.cutoffEpochDay), events(pocket), date)
            if (minimum.cents < amount.cents) return stop("amount", ErrorCode.OutOfRange, "O saldo do cofrinho não é suficiente nesta data e nas movimentações posteriores conhecidas.")
        }
        return null
    }

    suspend fun create(session: Session, c: CreateSavingsPocket): Planned {
        val errors = metadata(c.name, c.goal)
        if (errors.isNotEmpty()) return stop(errors)
        val parent = dao.account(c.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        if (c.expectedAccountRevision != null && c.expectedAccountRevision != parent.revision) return Planned.Stop(CommandResult.StaleVersion(parent.id, parent.revision))
        if (c.initialBalance.cents < 0 || c.cutoff > clock.today()) return stop("initialBalance", ErrorCode.OutOfRange, "Use saldo inicial não negativo e data até hoje.")
        val pocket = SavingsPocketRow(UUID.randomUUID().toString(), parent.id, c.name.trim(),
            if (c.initialFromAccount) 0 else c.initialBalance.cents, c.cutoff.toEpochDay(), c.goal?.cents, c.deadline?.toEpochDay(), false, session.personId, 1)
        val transfer = c.initialFromAccount && c.initialBalance.cents > 0
        if (transfer) validateMove(pocket, parent, SavingsDirection.Deposit, c.initialBalance, c.cutoff)?.let { return it }
        return Planned.Go(months = emptySet()) {
            dao.upsertSavingsPocket(pocket)
            val changes = mutableListOf(EntityChange(EntityType.SavingsPocket, pocket.id, null, pocket.revision))
            changes += touchParent(parent)
            if (transfer) {
                val move = SavingsMoveRow(UUID.randomUUID().toString(), pocket.id, parent.id, "Deposit", c.initialBalance.cents, c.cutoff.toEpochDay(), session.personId, false, 1)
                dao.upsertSavingsMove(move)
                changes += EntityChange(EntityType.SavingsMove, move.id, null, 1)
            }
            changes
        }
    }

    suspend fun edit(c: EditSavingsPocket): Planned {
        val errors = metadata(c.name, c.goal)
        if (errors.isNotEmpty()) return stop(errors)
        val old = dao.savingsPocket(c.pocketId) ?: return stop("pocketId", ErrorCode.UnknownReference, "Cofrinho não encontrado.")
        if (old.revision != c.expectedRevision) return Planned.Stop(CommandResult.StaleVersion(old.id, old.revision))
        if (old.archived) return stop("pocketId", ErrorCode.NotAllowed, "Este cofrinho está arquivado.")
        return Planned.Go(months = emptySet()) {
            val next = old.copy(name = c.name.trim(), goalCents = c.goal?.cents, deadlineEpochDay = c.deadline?.toEpochDay(), revision = old.revision + 1)
            dao.upsertSavingsPocket(next)
            listOf(EntityChange(EntityType.SavingsPocket, next.id, null, next.revision))
        }
    }

    suspend fun archive(c: ArchiveSavingsPocket): Planned {
        val old = dao.savingsPocket(c.pocketId) ?: return stop("pocketId", ErrorCode.UnknownReference, "Cofrinho não encontrado.")
        if (old.revision != c.expectedRevision) return Planned.Stop(CommandResult.StaleVersion(old.id, old.revision))
        if (view(old).balance.cents != 0L) return stop("pocketId", ErrorCode.NotAllowed, "Resgate todo o saldo antes de arquivar.")
        return Planned.Go(months = emptySet()) {
            val next = old.copy(archived = true, revision = old.revision + 1)
            dao.upsertSavingsPocket(next)
            listOf(EntityChange(EntityType.SavingsPocket, next.id, null, next.revision))
        }
    }

    suspend fun move(session: Session, c: MoveSavingsPocket): Planned {
        val pocket = dao.savingsPocket(c.pocketId) ?: return stop("pocketId", ErrorCode.UnknownReference, "Cofrinho não encontrado.")
        val account = dao.account(pocket.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        if (pocket.revision != c.expectedPocketRevision) return Planned.Stop(CommandResult.StaleVersion(pocket.id, pocket.revision))
        if (account.revision != c.expectedAccountRevision) return Planned.Stop(CommandResult.StaleVersion(account.id, account.revision))
        validateMove(pocket, account, c.direction, c.amount, c.date)?.let { return it }
        return Planned.Go(months = emptySet(), undoable = true) {
            val row = SavingsMoveRow(UUID.randomUUID().toString(), pocket.id, account.id, c.direction.name, c.amount.cents, c.date.toEpochDay(), session.personId, false, 1)
            dao.upsertSavingsMove(row)
            listOf(touchParent(account), touchPocket(pocket), EntityChange(EntityType.SavingsMove, row.id, null, 1))
        }
    }

    suspend fun undo(op: OperationRow): Planned {
        val changes = dao.changesOf(listOf(op.operationId))
        if (changes.any { EntityStates.revisionOf(dao, EntityType.valueOf(it.entityType), it.entityId) != it.revision })
            return stop("operationId", ErrorCode.ChangedAfter, "Houve alteração posterior. Revise o cofrinho antes de desfazer.")
        val moveId = changes.singleOrNull { it.entityType == "SavingsMove" }?.entityId ?: return stop("operationId", ErrorCode.NotAllowed, "Movimentação indisponível.")
        val move = dao.savingsMove(moveId) ?: return stop("operationId", ErrorCode.UnknownReference, "Movimentação não encontrada.")
        if (move.voided) return stop("operationId", ErrorCode.NotAllowed, "Movimentação já desfeita.")
        val pocket = checkNotNull(dao.savingsPocket(move.pocketId))
        val account = checkNotNull(dao.account(move.accountId))
        val reverse = if (move.direction == "Deposit") SavingsDirection.Withdraw else SavingsDirection.Deposit
        validateMove(pocket, account, reverse, Money(move.amountCents), LocalDate.ofEpochDay(move.epochDay))?.let { return it }
        return Planned.Go(months = emptySet()) {
            val next = move.copy(voided = true, revision = move.revision + 1)
            dao.upsertSavingsMove(next)
            listOf(touchParent(account), touchPocket(pocket), EntityChange(EntityType.SavingsMove, next.id, null, next.revision))
        }
    }

    private suspend fun touchParent(row: AccountRow): EntityChange {
        dao.upsertAccount(row.copy(revision = row.revision + 1))
        return EntityChange(EntityType.Account, row.id, null, row.revision + 1)
    }
    private suspend fun touchPocket(row: SavingsPocketRow): EntityChange {
        dao.upsertSavingsPocket(row.copy(revision = row.revision + 1))
        return EntityChange(EntityType.SavingsPocket, row.id, null, row.revision + 1)
    }
}
