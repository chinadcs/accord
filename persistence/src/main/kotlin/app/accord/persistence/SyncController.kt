package app.accord.persistence

import app.accord.core.space.EntityRef
import app.accord.core.sync.ConflictChoice
import app.accord.core.sync.ConflictView
import app.accord.core.sync.ProblemOperation
import app.accord.core.sync.ResolveResult
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncOutcome
import app.accord.core.sync.SyncStatus
import app.accord.core.sync.SyncSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Ponto único das telas e do agendador de segundo plano para a sincronização do espaço aberto:
 * estado observável, "Sincronizar agora", problemas e conflitos. Sem espaço aberto não há o que
 * sincronizar e as consultas voltam vazias.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncController(private val access: AccessCenter, scope: CoroutineScope) {
    /**
     * Estado da fila e do serviço, ou nulo sem espaço aberto. A situação da sessão vem também da
     * fachada de acesso: uma renovação recusada aparece mesmo que nenhum ciclo esteja rodando.
     */
    val status: StateFlow<SyncStatus?> = combine(
        access.runtime.flatMapLatest { it?.engine?.status ?: flowOf(null) },
        access.status,
    ) { engine, accessState ->
        engine?.let {
            if (accessState.session == SessionState.NeedsRenewal) it.copy(session = SessionState.NeedsRenewal) else it
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val runtime get() = access.runtime.value

    /** Relê a fila e os conflitos depois de um comando local ou de uma escolha. */
    suspend fun refresh() {
        runtime?.engine?.refresh()
    }

    /** Um ciclo de envio e recebimento. Sem espaço aberto, não faz nada. */
    suspend fun syncNow(): SyncOutcome? = runtime?.engine?.syncNow()

    suspend fun summary(): SyncSummary? = runtime?.syncStore?.summary()
    suspend fun problems(): List<ProblemOperation> = runtime?.syncStore?.problems().orEmpty()
    suspend fun conflicts(): List<ConflictView> = runtime?.syncStore?.conflicts().orEmpty()
    suspend fun conflict(operationId: String): ConflictView? = runtime?.syncStore?.conflict(operationId)

    /** "Revisar depois": as duas versões continuam guardadas. */
    suspend fun defer(operationId: String) {
        runtime?.syncStore?.defer(operationId)
        refresh()
    }

    /**
     * Aplica a escolha explícita entre as duas versões. [seen] são as versões remotas que a pessoa
     * viu; se algo mudou durante a revisão, nada é aplicado e a comparação volta atualizada.
     */
    suspend fun resolve(operationId: String, choice: ConflictChoice, seen: Map<EntityRef, Long>): ResolveResult {
        val store = runtime?.syncStore ?: return ResolveResult.NotFound
        val result = when (choice) {
            ConflictChoice.Local -> store.resolveWithLocal(operationId, seen)
            ConflictChoice.Remote -> store.resolveWithRemote(operationId, seen)
        }
        refresh()
        // A nova operação, se houve, segue para o serviço assim que possível; sem rede ela só espera.
        if (result is ResolveResult.Resolved) runtime?.engine?.syncNow()
        return result
    }

    suspend fun retryRejected(operationId: String) {
        runtime?.syncStore?.retryRejected(operationId)
        refresh()
    }

    /** Descarte pedido pela pessoa, nunca automático. */
    suspend fun discardRejected(operationId: String) {
        runtime?.syncStore?.discardRejected(operationId)
        refresh()
    }
}
