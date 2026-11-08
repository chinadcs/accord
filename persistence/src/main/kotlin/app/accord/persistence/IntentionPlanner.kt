package app.accord.persistence

import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIntention
import app.accord.core.space.EditIntention
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.IntentionView
import app.accord.core.space.Session
import app.accord.core.Money
import java.time.YearMonth
import java.util.UUID

/**
 * Intenções de compra do casal (S7, ADR 0048). Um desejo ainda não assumido: nunca toca lançamentos, compras, limites, meses nem alertas.
 * A identidade nasce no aparelho; a edição troca a versão inteira (sem mesclar campos) e confere a versão esperada. Não é desfazível.
 */
internal class IntentionPlanner(private val dao: SpaceDao, private val nowMillis: () -> Long) {

    suspend fun planCreate(session: Session, c: CreateIntention): Planned {
        val errors = validate(c.name, c.estimatedValue, c.note)
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet(), undoable = false) {
            val now = nowMillis()
            val row = IntentionRow(
                id = UUID.randomUUID().toString(), name = c.name.trim(), estimatedCents = c.estimatedValue?.cents,
                desiredMonth = c.desiredMonth?.toString(), note = c.note?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = session.personId, updatedBy = session.personId, createdAtMillis = now, updatedAtMillis = now, revision = 1,
            )
            dao.upsertIntention(row)
            listOf(EntityChange(EntityType.Intention, row.id, null, row.revision))
        }
    }

    suspend fun planEdit(session: Session, c: EditIntention): Planned {
        val existing = dao.intention(c.intentionId)
            ?: return stop("intentionId", ErrorCode.UnknownReference, "Intenção não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != existing.revision) {
            return Planned.Stop(CommandResult.StaleVersion(existing.id, existing.revision))
        }
        val errors = validate(c.name, c.estimatedValue, c.note)
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet(), undoable = false) {
            val next = existing.copy(
                name = c.name.trim(), estimatedCents = c.estimatedValue?.cents, desiredMonth = c.desiredMonth?.toString(),
                note = c.note?.trim()?.takeIf { it.isNotEmpty() }, updatedBy = session.personId, updatedAtMillis = nowMillis(),
                revision = existing.revision + 1,
            )
            dao.upsertIntention(next)
            listOf(EntityChange(EntityType.Intention, next.id, null, next.revision))
        }
    }

    fun viewOf(row: IntentionRow) = IntentionView(
        id = row.id, name = row.name, estimatedValue = row.estimatedCents?.let(::Money),
        desiredMonth = row.desiredMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }, note = row.note,
        createdBy = row.createdBy, updatedBy = row.updatedBy, createdAtMillis = row.createdAtMillis, updatedAtMillis = row.updatedAtMillis,
        revision = row.revision,
    )

    private fun validate(name: String, value: Money?, note: String?): List<FieldError> = buildList {
        if (name.isBlank()) add(FieldError("name", ErrorCode.Required, "Dê um nome à intenção."))
        else if (name.trim().length > MAX_NAME) add(FieldError("name", ErrorCode.OutOfRange, "O nome da intenção é longo demais."))
        // Ausência é "sem valor definido"; zero ou negativo não é um valor estimado.
        if (value != null && value.cents <= 0) add(FieldError("estimatedValue", ErrorCode.MustBePositive, "O valor estimado precisa ser maior que zero."))
        if (note != null && note.trim().length > MAX_NOTE) add(FieldError("note", ErrorCode.OutOfRange, "A observação é longa demais."))
    }

    private companion object {
        const val MAX_NAME = 120
        const val MAX_NOTE = 1000
    }
}
