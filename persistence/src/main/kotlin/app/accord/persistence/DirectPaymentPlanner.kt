package app.accord.persistence

import app.accord.core.space.CategorySeed
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.Session
import java.time.YearMonth
import java.util.UUID

/** Pix, débito e dinheiro: lançamentos diretos que já nascem pagos. */
internal class DirectPaymentPlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val accounts: AccountPlanner,
) {
    suspend fun plan(session: Session, origin: Origin, c: RegisterDirectPayment): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val chosenId = rules.validateClassification(c.classification, errors)
        // Encargo confirmado sem classificação usa Serviços > Tarifas e juros, pela identidade da semente (como na compra no cartão).
        val classificationId = chosenId
            ?: if (c.isCharge) DeterministicIds.seedCategory(session.spaceId, CategorySeed.FEES_AND_INTEREST) else null

        var accountId: String? = null
        rules.originError(c.originIntentionId)?.let { errors += it }
        if (c.kind == DirectPaymentKind.Cash) {
            if (c.accountId != null) errors += FieldError("accountId", ErrorCode.NotAllowed, "Dinheiro não tem conta.")
        } else {
            val chosen = c.accountId
                ?: accounts.effectiveDefault(session)
            when {
                chosen == null ->
                    errors += FieldError("accountId", ErrorCode.Required, "Escolha a conta; você ainda não definiu uma conta padrão.")
                dao.account(chosen) == null ->
                    errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
                else -> accountId = chosen
            }
        }
        if (errors.isNotEmpty()) return stop(errors)

        return Planned.Go(months = setOf(YearMonth.from(c.date)), undoable = true) {
            val row = MovementEntity(
                id = UUID.randomUUID().toString(),
                kind = c.kind.name,
                description = c.description.trim(),
                classificationId = classificationId,
                epochDay = c.date.toEpochDay(),
                amountCents = c.amount.cents,
                accountId = accountId,
                spentBy = c.spentBy,
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                isCharge = c.isCharge,
                originIntentionId = c.originIntentionId,
            )
            dao.insertMovement(row)
            listOf(EntityChange(EntityType.Movement, row.id, null, 1))
        }
    }
}
