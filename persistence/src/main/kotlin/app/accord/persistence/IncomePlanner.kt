package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.IncomeCategorySeed
import app.accord.core.space.IncomeView
import app.accord.core.space.NameKey
import app.accord.core.space.Origin
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RenameIncomeCategory
import app.accord.core.space.Session
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Renda prevista e recebida, e as categorias de renda (ADR 0038). */
internal class IncomePlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val accounts: AccountPlanner,
) {
    suspend fun planCreateCategory(c: CreateIncomeCategory): Planned {
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria de renda.")
        val key = NameKey.of(name)
        val all = dao.incomeCategories()
        if (all.any { it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria de renda com esse nome. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val row = IncomeCategoryRow(
                id = UUID.randomUUID().toString(),
                name = name,
                nameKey = key,
                sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                iconKey = IncomeCategorySeed.CUSTOM_ICON,
                seedKey = null,
                revision = 1,
            )
            dao.insertIncomeCategory(row)
            listOf(EntityChange(EntityType.IncomeCategory, row.id, null, 1))
        }
    }

    suspend fun planRenameCategory(c: RenameIncomeCategory): Planned {
        val row = dao.incomeCategory(c.categoryId)
            ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria de renda.")
        val key = NameKey.of(name)
        if (dao.incomeCategories().any { it.id != row.id && it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria de renda com esse nome. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val next = row.copy(name = name, nameKey = key, revision = row.revision + 1)
            dao.upsertIncomeCategory(next)
            listOf(EntityChange(EntityType.IncomeCategory, next.id, null, next.revision))
        }
    }

    /** Categoria de renda informada: precisa existir; nula é permitida (renda sem categoria). */
    private suspend fun validateIncomeCategory(id: String?, errors: MutableList<FieldError>) {
        if (id != null && dao.incomeCategory(id) == null) {
            errors += FieldError("incomeCategoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        }
    }

    suspend fun planRegister(session: Session, origin: Origin, c: RegisterIncome): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.expectedAmount.cents <= 0) {
            errors += FieldError("expectedAmount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        }
        val received = c.received
        if (received != null) rules.validateSettlement(received, "received", errors)
        val accountId = accounts.resolveRequired(session, c.accountId, errors)
        validateIncomeCategory(c.incomeCategoryId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOfNotNull(YearMonth.from(c.expectedDate), received?.let { YearMonth.from(it.date) })
        return Planned.Go(months = months, undoable = true) {
            val row = IncomeRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                expectedCents = c.expectedAmount.cents,
                expectedEpochDay = c.expectedDate.toEpochDay(),
                accountId = accountId,
                receivedEpochDay = received?.date?.toEpochDay(),
                receivedCents = received?.let { (it.amount ?: c.expectedAmount).cents },
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                incomeCategoryId = c.incomeCategoryId,
            )
            dao.insertIncome(row)
            listOf(EntityChange(EntityType.Income, row.id, null, 1))
        }
    }

    suspend fun planReceive(session: Session, c: ReceiveIncome): Planned {
        val income = dao.income(c.incomeId) ?: return stop("incomeId", ErrorCode.UnknownReference, "Renda não encontrada.")
        if (income.voided) return stop("incomeId", ErrorCode.NotAllowed, "Esta renda foi desfeita ou excluída.")
        if (c.expectedRevision != null && c.expectedRevision != income.revision) {
            return Planned.Stop(CommandResult.StaleVersion(income.id, income.revision))
        }
        if (income.receivedEpochDay != null) {
            return stop("incomeId", ErrorCode.NotAllowed, "Esta renda já foi recebida. Desfaça o recebimento antes de registrar outro.")
        }
        val errors = mutableListOf<FieldError>()
        rules.validateSettlement(c.receipt, "receipt", errors)
        val accountId = accounts.resolveRequired(session, c.accountId ?: income.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(income.expectedEpochDay)), YearMonth.from(c.receipt.date))
        return Planned.Go(months = months, undoable = true) {
            val next = income.copy(
                accountId = accountId,
                receivedEpochDay = c.receipt.date.toEpochDay(),
                receivedCents = c.receipt.amount?.cents ?: income.expectedCents,
                revision = income.revision + 1,
            )
            dao.upsertIncome(next)
            listOf(EntityChange(EntityType.Income, next.id, null, next.revision))
        }
    }
}

/** Visão de contrato de uma renda; usada nas consultas da fachada e no orçamento. */
internal fun IncomeRow.toView(): IncomeView {
    val expected = LocalDate.ofEpochDay(expectedEpochDay)
    val received = receivedEpochDay?.let(LocalDate::ofEpochDay)
    return IncomeView(
        id = id,
        description = description,
        expectedAmount = Money(expectedCents),
        expectedDate = expected,
        receivedDate = received,
        receivedAmount = receivedCents?.let(::Money),
        accountId = accountId,
        budgetMonth = YearMonth.from(received ?: expected),
        revision = revision,
        voided = voided,
        incomeCategoryId = incomeCategoryId,
    )
}
