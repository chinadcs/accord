package app.accord

import android.content.Context
import app.accord.core.BudgetClock
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.FinanceSpace
import app.accord.core.space.Undo
import app.accord.access.AndroidSpaceRuntimeFactory
import app.accord.access.SecureAccessStore
import app.accord.core.sync.AccessStore
import app.accord.core.sync.RemoteSpaceService
import app.accord.persistence.AccessCenter
import app.accord.persistence.SyncController
import app.accord.sync.SyncScheduler
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import app.accord.remote.SupabaseSpaceService
import app.accord.remote.UnconfiguredSpaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** O que as telas precisam para ler e alterar dados: a fachada e o relógio do orçamento. */
interface SpaceProvider {
    val clock: BudgetClock
    val personId: String
    val feedback: FeedbackBus
    suspend fun space(): FinanceSpace

    /** Desfaz uma operação; só funciona se nada nas entidades afetadas mudou depois dela. */
    suspend fun undo(operationId: String): UndoOutcome
}

/**
 * Dependências do aplicativo. Banco e fila são locais e persistidos, um por (conta, espaço); a rede
 * só entra pelo [RemoteSpaceService], com a URL e a chave pública do build (nunca a chave secreta).
 * Tema e modelo são preferências do aparelho e não sincronizam.
 */
class AppContainer(
    private val context: Context,
    accessStore: AccessStore = SecureAccessStore(context),
    val remote: RemoteSpaceService = defaultRemote(),
) : SpaceProvider {
    override val clock: BudgetClock = BudgetClock.system()
    override val feedback = FeedbackBus()

    /** Preferência de tema deste aparelho; não é dado financeiro. */
    val theme = ThemeStore(context)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Conta, sessão, espaço, convite e primeiros passos (tela 01, 02 e 03). */
    val access = AccessCenter(
        remote = remote,
        store = accessStore,
        runtimes = AndroidSpaceRuntimeFactory(context, clock, remote),
        currentMonth = { clock.currentMonth() },
    )

    /** Estado, "Sincronizar agora", conflitos e problemas do espaço aberto (tela 26 e indicadores). */
    val sync = SyncController(access, appScope)

    /** Envio em segundo plano quando houver conexão, com espera crescente entre tentativas. */
    val scheduler = SyncScheduler(context)

    /** Quem registra é a conta da sessão; as telas só existem com conta e espaço. */
    override val personId: String get() = access.status.value.account?.accountId.orEmpty()

    private val ready = CompletableDeferred<Unit>()

    /** O estado salvo no aparelho já foi lido (a conta e o espaço, se houver, estão abertos). */
    suspend fun awaitReady() = ready.await()

    init {
        appScope.launch {
            access.restore()
            ready.complete(Unit)
            // Com um espaço aberto: um ciclo agora, a agenda do recebimento ocasional e o envio quando houver conexão.
            access.runtime.collectLatest { runtime ->
                if (runtime != null) {
                    scheduler.schedulePeriodic()
                    runtime.engine.syncNow()
                    scheduler.enqueue()
                }
            }
        }
        // Todo registro salvo (e todo desfazer) entra na fila: atualiza os indicadores e agenda o envio.
        appScope.launch {
            merge(feedback.events.map { }, feedback.undone).collect {
                sync.refresh()
                scheduler.enqueue()
            }
        }
    }

    /** Ao voltar ao primeiro plano: um ciclo e a leitura dos indicadores. */
    fun onForeground() {
        appScope.launch {
            sync.refresh()
            sync.syncNow()
        }
    }

    /** Espera a fachada do espaço da conta atual; só é chamada por telas que existem com espaço aberto. */
    override suspend fun space(): FinanceSpace = access.runtime.filterNotNull().first().space

    override suspend fun undo(operationId: String): UndoOutcome =
        when (val result = space().execute(Envelope(CommandId(UUID.randomUUID().toString())), Undo(operationId))) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> UndoOutcome.Done.also { feedback.notifyUndone() }
            is CommandResult.Rejected ->
                if (result.errors.any { it.code == ErrorCode.ChangedAfter }) UndoOutcome.ChangedAfter else UndoOutcome.Failed
            else -> UndoOutcome.Failed
        }

    companion object {
        /** Identidade fixa só dos testes de instrumentação; o aplicativo real usa a conta da sessão. */
        const val DEV_SPACE_ID = "dev-space"
        const val DEV_PERSON_ID = "dev-person"
        const val DATABASE_NAME = "accord-$DEV_PERSON_ID-$DEV_SPACE_ID.db"

        /** URL e chave pública do build; sem elas, o serviço é "não configurado" e o app segue só neste aparelho. */
        fun defaultRemote(): RemoteSpaceService =
            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
                UnconfiguredSpaceService
            } else {
                SupabaseSpaceService(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            }
    }
}
