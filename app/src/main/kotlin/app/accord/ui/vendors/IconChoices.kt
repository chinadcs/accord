package app.accord.ui.vendors

import androidx.compose.runtime.compositionLocalOf
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.ClearIconAssociation
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Envelope
import app.accord.core.space.IconAssociationView
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.NameKey
import app.accord.core.space.Origin
import app.accord.core.space.QueryResult
import app.accord.core.space.SetIconAssociation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Escolhas de ícone ativas (ADR 0047), com a precedência da exibição: a escolha do lançamento vale mais que a regra pelo nome do lançamento.
 * Quem usa soma o reconhecimento automático depois ([IconMatcher]); nada aqui muda dado financeiro.
 */
class IconChoices(views: List<IconAssociationView>) {
    private val byEntry = views.filter { it.scope == IconScope.Entry }.associate { it.target to it.icon }
    private val byName = views.filter { it.scope == IconScope.Name }.associate { it.target to it.icon }

    /** O ícone escolhido para este lançamento: o dele, senão o da regra pelo nome. Nulo deixa o reconhecimento automático decidir. */
    fun forEntry(entryId: String?, description: String): IconRef? = entryId?.let(byEntry::get) ?: byName[NameKey.of(description)]

    fun hasEntryChoice(entryId: String?) = entryId != null && entryId in byEntry

    fun hasNameChoice(description: String) = NameKey.of(description) in byName

    companion object {
        val Empty = IconChoices(emptyList())
    }
}

/** Mantém [IconChoices] em dia: lê ao abrir e de novo a cada gravação, desfazer, mudança vinda do Chat ou do serviço. */
class IconChoicesStore(private val provider: SpaceProvider, private val scope: CoroutineScope, triggers: kotlinx.coroutines.flow.Flow<Unit>) {
    private val _choices = MutableStateFlow(IconChoices.Empty)
    val choices: StateFlow<IconChoices> = _choices.asStateFlow()

    init {
        refresh()
        scope.launch {
            merge(provider.feedback.events.map { }, provider.feedback.undone, provider.feedback.changed, triggers).collect { read() }
        }
    }

    fun refresh() {
        scope.launch { read() }
    }

    private suspend fun read() {
        // Sem espaço aberto (antes do acesso) a consulta falha: fica como está e tenta de novo no próximo gatilho.
        val result = runCatching { provider.space().iconAssociations() }.getOrNull()
        if (result is QueryResult.Data) _choices.value = IconChoices(result.value)
    }
}

/** Grava e remove escolhas de ícone; devolve se deu certo. As telas só conhecem esta interface. */
interface IconActions {
    suspend fun set(scope: IconScope, target: String, icon: IconRef): Boolean
    suspend fun clear(scope: IconScope, target: String): Boolean
}

/** [IconActions] pela fachada financeira: avisa a pessoa e atualiza a leitura das telas. */
class FacadeIconActions(private val provider: SpaceProvider, private val store: IconChoicesStore) : IconActions {
    override suspend fun set(scope: IconScope, target: String, icon: IconRef): Boolean =
        finish(provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString()), Origin.Manual), SetIconAssociation(scope, target, icon)), R.string.feedback_icon_saved)

    override suspend fun clear(scope: IconScope, target: String): Boolean =
        finish(provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString()), Origin.Manual), ClearIconAssociation(scope, target)), R.string.feedback_icon_removed)

    private fun finish(result: CommandResult, success: Int): Boolean {
        val ok = result is CommandResult.Applied || result is CommandResult.AlreadyApplied
        provider.feedback.post(FeedbackEvent(if (ok) success else R.string.feedback_icon_failed))
        store.refresh()
        return ok
    }
}

/** Escolhas de ícone para as telas; vazio fora do aplicativo completo (telas sintéticas dos testes). */
val LocalIconChoices = compositionLocalOf { IconChoices.Empty }

/** Ação de escolher ícone; nula quando a tela não tem fachada, e então a escolha não é oferecida. */
val LocalIconActions = compositionLocalOf<IconActions?> { null }
