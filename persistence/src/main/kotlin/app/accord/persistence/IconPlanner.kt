package app.accord.persistence

import app.accord.core.space.ClearIconAssociation
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.IconKeys
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.core.space.NameKey
import app.accord.core.space.CommandResult
import app.accord.core.space.Session
import app.accord.core.space.SetIconAssociation

/**
 * Escolha de ícone por lançamento ou por nome (ADR 0047). Só visual: não toca lançamentos, categorias, valores nem meses.
 * A identidade é determinística por escopo e alvo, então os dois aparelhos convergem na mesma linha; remover marca a linha como removida.
 * Não é desfazível: escolher de novo ou remover é o caminho de volta.
 */
internal class IconPlanner(private val dao: SpaceDao, private val entryExists: suspend (String) -> Boolean) {
    private sealed interface Target {
        data class Valid(val key: String) : Target
        data class Invalid(val error: FieldError) : Target
    }

    private suspend fun targetOf(scope: IconScope, raw: String): Target = when (scope) {
        IconScope.Name -> NameKey.of(raw).let {
            when {
                it.isEmpty() -> Target.Invalid(FieldError("target", ErrorCode.Required, "Informe o nome do lançamento."))
                it.length > MAX_NAME -> Target.Invalid(FieldError("target", ErrorCode.OutOfRange, "O nome do lançamento é longo demais para um ícone."))
                else -> Target.Valid(it)
            }
        }
        IconScope.Entry -> raw.trim().let {
            when {
                it.isEmpty() -> Target.Invalid(FieldError("target", ErrorCode.Required, "Informe o lançamento."))
                it.length > MAX_ENTRY_ID || !entryExists(it) -> Target.Invalid(FieldError("target", ErrorCode.UnknownReference, "Lançamento não encontrado."))
                else -> Target.Valid(it)
            }
        }
    }

    private fun idOf(session: Session, scope: IconScope, key: String) = DeterministicIds.of(session.spaceId, "iconassociation", "${scope.name}:$key")

    suspend fun planSet(session: Session, c: SetIconAssociation): Planned {
        if (!IconKeys.isValid(c.icon)) return stop("icon", ErrorCode.NotAllowed, "Esse ícone não é uma escolha válida.")
        val key = when (val t = targetOf(c.scope, c.target)) {
            is Target.Invalid -> return stop(listOf(t.error))
            is Target.Valid -> t.key
        }
        val id = idOf(session, c.scope, key)
        val existing = dao.iconAssociation(id)
        if (c.expectedRevision != null && existing != null && c.expectedRevision != existing.revision) {
            return Planned.Stop(CommandResult.StaleVersion(id, existing.revision))
        }
        return Planned.Go(months = emptySet(), undoable = false) {
            val next = IconAssociationRow(
                id = id, scope = c.scope.name, target = key, iconSource = c.icon.source.name, iconKey = c.icon.key,
                voided = false, revision = (existing?.revision ?: 0) + 1,
            )
            dao.upsertIconAssociation(next)
            listOf(EntityChange(EntityType.IconAssociation, id, null, next.revision))
        }
    }

    suspend fun planClear(session: Session, c: ClearIconAssociation): Planned {
        val key = when (c.scope) {
            IconScope.Name -> NameKey.of(c.target)
            IconScope.Entry -> c.target.trim()
        }
        if (key.isEmpty()) return stop("target", ErrorCode.Required, "Informe o lançamento ou o nome.")
        val existing = dao.iconAssociation(idOf(session, c.scope, key))
        if (existing == null || existing.voided) return stop("target", ErrorCode.UnknownReference, "Não há ícone escolhido para remover.")
        if (c.expectedRevision != null && c.expectedRevision != existing.revision) {
            return Planned.Stop(CommandResult.StaleVersion(existing.id, existing.revision))
        }
        return Planned.Go(months = emptySet(), undoable = false) {
            val next = existing.copy(voided = true, revision = existing.revision + 1)
            dao.upsertIconAssociation(next)
            listOf(EntityChange(EntityType.IconAssociation, next.id, null, next.revision))
        }
    }

    fun viewsOf(rows: List<IconAssociationRow>) = rows.mapNotNull { row ->
        val source = runCatching { IconSource.valueOf(row.iconSource) }.getOrNull() ?: return@mapNotNull null
        val scope = runCatching { IconScope.valueOf(row.scope) }.getOrNull() ?: return@mapNotNull null
        app.accord.core.space.IconAssociationView(row.id, scope, row.target, IconRef(source, row.iconKey), row.revision)
    }

    private companion object {
        const val MAX_NAME = 200
        const val MAX_ENTRY_ID = 100
    }
}
