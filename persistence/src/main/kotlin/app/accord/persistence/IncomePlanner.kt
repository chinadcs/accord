package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.CommandResult
import app.accord.core.space.CategoryPalette
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.EditIncomeCategory
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
import app.accord.core.space.UndoIncomeReceipt
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
        val parentId = c.parentId
        if (parentId != null) {
            val parent = all.firstOrNull { it.id == parentId }
                ?: return stop("parentId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
            if (parent.parentId != null) {
                return stop("parentId", ErrorCode.NotAllowed, "Uma subcategoria de renda não pode ter subcategorias.")
            }
        }
        if (all.any { it.parentId == parentId && it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, DUPLICATE_NAME)
        }
        return Planned.Go(months = emptySet()) {
            val row = IncomeCategoryRow(
                id = UUID.randomUUID().toString(),
                name = name,
                nameKey = key,
                sortOrder = (all.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                iconKey = IncomeCategorySeed.CUSTOM_ICON,
                seedKey = null,
                revision = 1,
                parentId = parentId,
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
        if (dao.incomeCategories().any { it.id != row.id && it.parentId == row.parentId && it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, DUPLICATE_NAME)
        }
        return Planned.Go(months = emptySet()) {
            val next = row.copy(name = name, nameKey = key, revision = row.revision + 1)
            dao.upsertIncomeCategory(next)
            listOf(EntityChange(EntityType.IncomeCategory, next.id, null, next.revision))
        }
    }

    suspend fun planEditCategory(c: EditIncomeCategory): Planned {
        val row = dao.incomeCategory(c.categoryId)
            ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        if (c.name == null && c.iconKey == null && c.colorIndex == null) {
            return stop("fields", ErrorCode.Required, "Informe o que mudou.")
        }
        val errors = mutableListOf<FieldError>()
        val name = c.name?.trim()
        if (name != null && name.isEmpty()) errors += FieldError("name", ErrorCode.Required, "Informe o nome da categoria de renda.")
        if (c.iconKey?.isBlank() == true) errors += FieldError("iconKey", ErrorCode.Required, "Escolha um ícone.")
        val color = c.colorIndex
        if (color != null && color !in 0 until CategoryPalette.SIZE) {
            errors += FieldError("colorIndex", ErrorCode.OutOfRange, "Escolha uma das cores da paleta.")
        }
        if (!name.isNullOrEmpty()) {
            val key = NameKey.of(name)
            if (dao.incomeCategories().any { it.id != row.id && it.parentId == row.parentId && it.nameKey == key }) {
                errors += FieldError("name", ErrorCode.DuplicateName, DUPLICATE_NAME)
            }
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            val next = row.copy(
                name = name ?: row.name,
                nameKey = name?.let(NameKey::of) ?: row.nameKey,
                iconKey = c.iconKey ?: row.iconKey,
                colorIndex = color ?: row.colorIndex,
                revision = row.revision + 1,
            )
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

    /** Desliga o "Recebido": a renda volta a prevista e o mês do recebimento deixa de contá-la como confirmada. */
    suspend fun planUndoReceive(c: UndoIncomeReceipt): Planned {
        val income = dao.income(c.incomeId) ?: return stop("incomeId", ErrorCode.UnknownReference, "Renda não encontrada.")
        if (income.voided) return stop("incomeId", ErrorCode.NotAllowed, "Esta renda foi desfeita ou excluída.")
        if (c.expectedRevision != null && c.expectedRevision != income.revision) {
            return Planned.Stop(CommandResult.StaleVersion(income.id, income.revision))
        }
        val received = income.receivedEpochDay ?: return stop("incomeId", ErrorCode.NotAllowed, "Esta renda ainda não foi recebida.")
        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(income.expectedEpochDay)), YearMonth.from(LocalDate.ofEpochDay(received)))
        // O desfazer do aviso religa o recebido: o que estava recebido fica na operação (`paid=<dia>,<centavos>`).
        return Planned.Go(months = months, undoable = true, detail = "paid=$received,${income.receivedCents ?: income.expectedCents}") {
            val next = income.copy(receivedEpochDay = null, receivedCents = null, revision = income.revision + 1)
            dao.upsertIncome(next)
            listOf(EntityChange(EntityType.Income, next.id, null, next.revision))
        }
    }
}

private const val DUPLICATE_NAME = "Já existe uma categoria de renda com esse nome. Escolha outro nome."

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
        receivableId = receivableId,
    )
}
