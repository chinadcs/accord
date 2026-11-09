package app.accord.persistence

import app.accord.core.sync.AuthSession
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.OperationResult
import app.accord.core.sync.RejectReason
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.RenewResult
import app.accord.core.sync.ServiceState
import app.accord.core.sync.SessionCheck
import app.accord.core.sync.SessionState
import app.accord.core.sync.StopCause
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.core.sync.SyncStage
import app.accord.core.sync.SyncStatus
import app.accord.core.sync.SyncSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/**
 * Motor de sincronização do aparelho (S2 "Motor de sincronização"): um ciclo envia a fila e depois
 * recebe o que o serviço aceitou. Não contém regra financeira nem decide conflito por relógio.
 *
 * - Enviar vem antes de receber: o reenvio de uma operação cuja resposta se perdeu é reconhecido
 *   antes de o serviço devolver a própria alteração, então ela nunca vira conflito consigo mesma.
 * - Falha de rede, sessão, serviço ou quota para o ciclo sem descartar, reordenar nem reescrever
 *   operações; o próximo ciclo recomeça do que está persistido.
 * - A sessão é renovada uma vez por ciclo; se a renovação for recusada, a fila espera a mesma
 *   conta entrar de novo.
 */
class SyncEngine(
    private val store: SyncStore,
    private val remote: RemoteSpaceService,
    private val access: SyncAccess,
    private val batchSize: Int = 20,
    private val pageSize: Int = 100,
    /** Avalia avisos após alterações recebidas; somente avaliações de pistas não disparam essa avaliação. */
    private val onReceived: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(SyncStatus(SyncSummary(0, 0, 0, null, 0)))

    /** Estado observável para a tela de Sincronização e os indicadores. */
    val status: StateFlow<SyncStatus> = state.asStateFlow()

    /** Relê a fila e os conflitos; chamado depois de um comando local ou de uma escolha de conflito. */
    suspend fun refresh() {
        val summary = store.summary()
        state.update { it.copy(summary = summary) }
    }

    suspend fun syncNow(): SyncOutcome {
        if (!mutex.tryLock()) return SyncOutcome.AlreadyRunning
        try {
            return runCycle()
        } finally {
            val summary = store.summary()
            state.update { it.copy(stage = SyncStage.Idle, summary = summary) }
            mutex.unlock()
        }
    }

    private class Run(var session: AuthSession, val spaceId: String)

    private suspend fun runCycle(): SyncOutcome {
        val run = when (val check = access.check()) {
            is SessionCheck.Usable -> Run(check.session, check.spaceId)
            SessionCheck.SignedOut -> return stopped(SessionState.SignedOut, StopCause.SignedOut)
            SessionCheck.NeedsRenewal -> return stopped(SessionState.NeedsRenewal, StopCause.SessionExpired)
            SessionCheck.OtherAccount -> return stopped(SessionState.OtherAccount, StopCause.OtherAccount)
        }

        var sent = 0
        setStage(SyncStage.Sending)
        var guard = 0
        while (guard++ < MAX_BATCHES) {
            val batch = store.nextBatch(batchSize)
            if (batch.isEmpty()) break
            val ids = batch.map { it.operationId }
            val results = when (val call = authorized(run) { remote.push(it, run.spaceId, batch) }) {
                is RemoteResult.Failed -> {
                    store.recordBatchFailure(ids, call.failure::class.simpleName ?: "Unknown")
                    return failed(call.failure, sent)
                }
                is RemoteResult.Ok -> call.value
            }
            val answered = results.filter { it.operationId in ids }
            if (answered.isEmpty()) {
                // Sem resposta por operação não há progresso: parar em vez de reenviar para sempre.
                store.recordBatchFailure(ids, "NoResult")
                return failed(RemoteFailure.Unknown("sem_resultado"), sent)
            }
            store.recordResults(results)
            sent += answered.count { it is OperationResult.Applied }
            if (answered.any { it is OperationResult.Rejected && it.reason == RejectReason.ProtocolUnsupported }) {
                state.update { it.copy(updateRequired = true) }
            }
            refresh()
        }

        setStage(SyncStage.Receiving)
        var received = 0
        var receivedBeyondClueEvaluations = false
        var conflicts = 0
        while (true) {
            val page = when (val call = authorized(run) { remote.pull(it, run.spaceId, store.cursor(), pageSize) }) {
                is RemoteResult.Failed -> return failed(call.failure, sent)
                is RemoteResult.Ok -> call.value
            }
            val summary = store.applyPage(page)
            received += summary.applied.size
            if (summary.applied.any { it.type != app.accord.core.space.EntityType.ClueEvaluation }) receivedBeyondClueEvaluations = true
            conflicts += summary.conflicts
            refresh()
            if (!page.hasMore) break
        }

        if (receivedBeyondClueEvaluations) onReceived()
        store.markCycleComplete()
        state.update {
            it.copy(connection = ConnectionState.Online, service = ServiceState.Responding, session = SessionState.Valid, lastFailure = null)
        }
        return SyncOutcome.Completed(sent, received, conflicts)
    }

    /** Chama o serviço com a sessão; se ela venceu, renova uma vez e repete a mesma chamada. */
    private suspend fun <T> authorized(run: Run, call: suspend (String) -> RemoteResult<T>): RemoteResult<T> {
        val first = call(run.session.accessToken)
        if (first !is RemoteResult.Failed || first.failure != RemoteFailure.SessionExpired) return first
        return when (val renewed = access.renew()) {
            is RenewResult.Renewed -> {
                run.session = renewed.session
                call(renewed.session.accessToken)
            }
            RenewResult.Expired -> first
            is RenewResult.Failed -> RemoteResult.Failed(renewed.failure)
        }
    }

    private fun setStage(stage: SyncStage) = state.update { it.copy(stage = stage) }

    private suspend fun stopped(session: SessionState, cause: StopCause): SyncOutcome {
        val summary = store.summary()
        state.update { it.copy(summary = summary, session = session) }
        return SyncOutcome.Stopped(cause)
    }

    private suspend fun failed(failure: RemoteFailure, sent: Int): SyncOutcome {
        val summary = store.summary()
        state.update {
            when (failure) {
                RemoteFailure.NoNetwork -> it.copy(connection = ConnectionState.Offline)
                RemoteFailure.SessionExpired -> it.copy(connection = ConnectionState.Online, session = SessionState.NeedsRenewal)
                RemoteFailure.ServiceUnavailable -> it.copy(connection = ConnectionState.Online, service = ServiceState.Unavailable)
                RemoteFailure.ServicePaused -> it.copy(connection = ConnectionState.Online, service = ServiceState.Paused)
                RemoteFailure.QuotaExceeded -> it.copy(connection = ConnectionState.Online, service = ServiceState.QuotaExceeded)
                else -> it.copy(connection = ConnectionState.Online, service = ServiceState.Unknown)
            }.copy(summary = summary, lastFailure = failure)
        }
        return SyncOutcome.Stopped(if (failure == RemoteFailure.SessionExpired) StopCause.SessionExpired else StopCause.Remote(failure), sent)
    }

    private companion object {
        const val MAX_BATCHES = 500
    }
}
