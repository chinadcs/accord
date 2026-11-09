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
import app.accord.alerts.AlertDispatcher
import app.accord.alerts.AndroidNotifications
import app.accord.access.SecureAccessStore
import app.accord.core.chat.ChatCoordinator
import app.accord.core.chat.ChatImportResult
import app.accord.core.model.DistributionSource
import app.accord.core.chat.RuleInterpreter
import app.accord.core.importing.ImportSession
import app.accord.core.model.ModelEngine
import app.accord.model.ModelCenter
import app.accord.model.UnconfiguredDistributionSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Last locally known member names; no network request and no claim about fresh membership. */
    fun memberNames(): Map<String, String> = emptyMap()
    val clock: BudgetClock

    /** Fase do produto deste build (P1 por padrão; P2 com a flag do S7). Controles de fase futura não aparecem antes. */
    val phase: app.accord.core.analysis.ProductPhase
    val personId: String
    val feedback: FeedbackBus
    suspend fun space(): FinanceSpace

    /** Sessão de importação do espaço aberto (S4): prévia, decisões e confirmação. */
    suspend fun imports(): ImportSession

    /** Sugeridor local de categorias do espaço aberto; nulo quando a variante não o inclui (release não tem pesos, ADR 0032). */
    suspend fun categorySuggester(): app.accord.core.chat.CategorySuggester? = null

    /** Rascunhos de intenção deste aparelho (S7, tela 35); nulo onde não existem. */
    fun intentionDrafts(): app.accord.ui.intentions.IntentionDrafts? = null

    /** Desfaz uma operação; só funciona se nada nas entidades afetadas mudou depois dela. */
    suspend fun undo(operationId: String): UndoOutcome

    /** O registro manual aberto a partir de uma mensagem do Chat foi salvo: a mensagem deixa de oferecer "Tentar de novo". */
    suspend fun onManualSaved(chatMessageId: String) {}

    /** Um lançamento foi editado fora do Chat: o card dele no Chat passa a mostrar a versão atual. */
    suspend fun onEntryEdited(entryId: String, operationId: String) {}
}

/**
 * Dependências do aplicativo. Banco e fila são locais e persistidos, um por (conta, espaço); a rede
 * financeira entra pelo [RemoteSpaceService], com a URL e a chave pública do build (nunca a chave secreta).
 * A consulta pública de versões é independente, sem conta ou dados financeiros (ADR 0066).
 * Tema e modelo são preferências do aparelho e não sincronizam.
 */
class AppContainer(
    private val context: Context,
    accessStore: AccessStore = SecureAccessStore(context),
    val remote: RemoteSpaceService = defaultRemote(),
    modelSource: DistributionSource = UnconfiguredDistributionSource,
    modelEngine: ModelEngine? = null,
    val categoryPilot: app.accord.pilot.ChatPilot = app.accord.pilot.createChatPilot(context),
    /** Fase do produto deste build: P1 por padrão; P2 (S7) só com `-Paccord.p2=true` em debug. Controles de fase futura não aparecem antes. */
    override val phase: app.accord.core.analysis.ProductPhase = defaultPhase(),
    releaseFeed: app.accord.updates.ReleaseFeed = app.accord.updates.PublicGitHubReleaseFeed(),
) : SpaceProvider {
    override val clock: BudgetClock = BudgetClock.system()
    override val feedback = FeedbackBus()

    /** Preferência de tema deste aparelho; não é dado financeiro. */
    val theme = ThemeStore(context)
    val appLock = app.accord.security.AppLockStore(context)

    private val intentionDraftStores = java.util.concurrent.ConcurrentHashMap<String, app.accord.ui.intentions.SharedPreferencesIntentionDrafts>()
    override fun intentionDrafts(): app.accord.ui.intentions.IntentionDrafts? {
        val identity = access.runtime.value?.identity ?: return null
        return intentionDraftStores.getOrPut("${identity.personId}|${identity.spaceId}") {
            app.accord.ui.intentions.SharedPreferencesIntentionDrafts(context, identity.personId, identity.spaceId)
        }
    }

    fun intentionDraftExport(): kotlinx.serialization.json.JsonObject = access.status.value.account?.accountId?.let {
        app.accord.ui.intentions.SharedPreferencesIntentionDrafts.exportAccount(context, it)
    } ?: kotlinx.serialization.json.JsonObject(emptyMap())

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val updates = app.accord.updates.ReleaseUpdates(
        appScope, releaseFeed,
        app.accord.updates.AndroidUpdateCheckStore(context), BuildConfig.VERSION_CODE,
    )

    /**
     * Modelo deste aparelho. Sem hospedagem escolhida nem motor integrado, o Chat guarda a mensagem e
     * oferece o registro manual; os testes de instrumentação trocam a fonte por uma fake.
     */
    val models = ModelCenter(context, appScope, modelSource, modelEngine)

    /**
     * ADR 0036: o Chat do P0 interpreta com regras locais, sem modelo. Um motor injetado (testes ou uma fase futura
     * com SLM opcional) ou o piloto de demonstração (interpretador fake) tomam o lugar das regras.
     */
    val usesRuleInterpreter: Boolean = !categoryPilot.replacesInterpreter && modelEngine == null

    private val chatState = MutableStateFlow<ChatCoordinator?>(null)

    /** Conversa do espaço aberto; nula onde não há espaço. O histórico mora no banco do par, só neste aparelho. */
    val chat: StateFlow<ChatCoordinator?> = chatState.asStateFlow()

    private val alertToOpen = MutableStateFlow<String?>(null)

    /** A chave do evento de uma notificação tocada, até a navegação a abrir (depois de entrar, se a sessão precisar). */
    val pendingAlertEvent: StateFlow<String?> = alertToOpen.asStateFlow()

    fun openAlertEvent(eventId: String?) {
        alertToOpen.value = eventId?.takeIf { it.isNotBlank() }
    }

    fun consumeAlertEvent() {
        alertToOpen.value = null
    }

    /** Notificações do Android para os avisos de possível excesso (S5.5): canal, situação real e publicação. */
    val notifications = AndroidNotifications(context).also { it.ensureChannel() }

    private val alertDispatcher: AlertDispatcher = AlertDispatcher(notifications) { access.runtime.value?.space }

    /** Conta, sessão, espaço, convite e primeiros passos (tela 01, 02 e 03). */
    val access: AccessCenter = AccessCenter(
        remote = remote,
        store = accessStore,
        runtimes = AndroidSpaceRuntimeFactory(context, clock, remote, alertDispatcher::dispatch),
        currentMonth = { clock.currentMonth() },
    )

    /** Estado, "Sincronizar agora", conflitos e problemas do espaço aberto (tela 26 e indicadores). */
    val sync = SyncController(access, appScope)

    /** Escolhas de ícone dos lançamentos (ADR 0047): lidas da fachada e atualizadas a cada gravação, desfazer ou sincronização. */
    val icons = app.accord.ui.vendors.IconChoicesStore(this, appScope, sync.status.map { })
    val iconActions = app.accord.ui.vendors.FacadeIconActions(this, icons)

    /** Envio em segundo plano quando houver conexão, com espera crescente entre tentativas. */
    val scheduler = SyncScheduler(context)

    /** Quem registra é a conta da sessão; as telas só existem com conta e espaço. */
    override val personId: String get() = access.status.value.account?.accountId.orEmpty()
    override fun memberNames(): Map<String, String> = access.status.value.participants?.items.orEmpty().associate { it.accountId to it.nickname } +
        ("deleted" to context.getString(R.string.privacy_account_deleted))

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
                    // Aviso gravado e ainda não exibido (processo encerrado antes) é tentado de novo ao abrir o espaço.
                    alertDispatcher.dispatch()
                }
            }
        }
        // Uma conversa por espaço aberto, com escopo próprio: trocar de conta ou sair encerra a fila da anterior.
        appScope.launch {
            var scope: CoroutineScope? = null
            access.runtime.collect { runtime ->
                scope?.cancel()
                scope = null
                chatState.value = null
                val store = runtime?.chatStore ?: return@collect
                val chatScope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Default).also { scope = it }
                runtime.beforeClose = { chatScope.cancel() }
                if (access.runtime.value !== runtime) { chatScope.cancel(); return@collect }
                val coordinator = ChatCoordinator(
                    space = runtime.space,
                    store = store,
                    interpreters = categoryPilot.interpreters ?: if (usesRuleInterpreter) RuleInterpreter.source else models.runtime.source,
                    clock = clock,
                    scope = chatScope,
                    onFinanceChanged = feedback::notifyChanged,
                    categorySuggester = categoryPilot.suggester(runtime.identity.spaceId),
                    onCategoryPilotEvent = categoryPilot::record,
                    // S6: o aplicativo expõe as telas 29–33, então o Chat responde as consultas do catálogo (fase P1).
                    phase = phase,
                )
                chatScope.launch {
                    coordinator.recover()
                    if (access.runtime.value === runtime) chatState.value = coordinator
                }
            }
        }
        // Todo registro salvo (e todo desfazer) entra na fila: atualiza os indicadores e agenda o envio.
        appScope.launch {
            merge(feedback.events.map { }, feedback.undone, feedback.changed).collect {
                sync.refresh()
                scheduler.enqueue()
            }
        }
    }

    /** Ao voltar ao primeiro plano: um ciclo e a leitura dos indicadores. */
    fun onForeground() {
        updates.check()
        appScope.launch {
            access.reconcilePrivacy()
            sync.refresh()
            sync.syncNow()
        }
    }

    /** Espera a fachada do espaço da conta atual; só é chamada por telas que existem com espaço aberto. */
    override suspend fun space(): FinanceSpace = access.runtime.filterNotNull().first().space

    override suspend fun imports(): ImportSession = checkNotNull(access.runtime.filterNotNull().first().imports) { "O espaço aberto não oferece importação" }

    override suspend fun categorySuggester(): app.accord.core.chat.CategorySuggester? =
        categoryPilot.suggester(access.runtime.filterNotNull().first().identity.spaceId)

    /** Conta e espaço a que o histórico do Chat deste aparelho pertence; o arquivo de transferência só entra no mesmo par. */
    private fun chatOwner(): String? {
        val state = access.status.value
        val account = state.account ?: return null
        val space = state.space ?: return null
        return "${account.accountId}|${space.spaceId}"
    }

    private val chatStore get() = access.runtime.value?.chatStore

    /** Texto do arquivo de histórico (S3 §12): conversas em claro, sem senhas, tokens nem lançamentos. */
    suspend fun exportChat(): String? {
        val owner = chatOwner() ?: return null
        return chatStore?.exportText(owner)
    }

    suspend fun importChat(text: String): ChatImportResult {
        val owner = chatOwner() ?: return ChatImportResult.Unreadable
        return chatStore?.importText(text, owner) ?: ChatImportResult.Unreadable
    }

    /** Apaga só o histórico deste aparelho; lançamentos e a fila de sincronização não mudam. */
    suspend fun clearChat() {
        val runtime = access.runtime.value ?: return
        val coordinator = chat.filterNotNull().first()
        if (access.runtime.value === runtime) coordinator.clearHistory()
    }

    override suspend fun onManualSaved(chatMessageId: String) {
        chat.value?.resolveManually(chatMessageId)
    }

    override suspend fun onEntryEdited(entryId: String, operationId: String) {
        chat.value?.entryEdited(entryId, operationId)
    }

    override suspend fun undo(operationId: String): UndoOutcome =
        when (val result = space().execute(Envelope(CommandId(UUID.randomUUID().toString())), Undo(operationId))) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                chat.value?.operationUndone(operationId)
                UndoOutcome.Done.also { feedback.notifyUndone() }
            }
            is CommandResult.Rejected ->
                if (result.errors.any { it.code == ErrorCode.ChangedAfter }) UndoOutcome.ChangedAfter else UndoOutcome.Failed
            else -> UndoOutcome.Failed
        }

    companion object {
        /** Identidade fixa só dos testes de instrumentação; o aplicativo real usa a conta da sessão. */
        const val DEV_SPACE_ID = "dev-space"
        const val DEV_PERSON_ID = "dev-person"
        const val DATABASE_NAME = "accord-$DEV_PERSON_ID-$DEV_SPACE_ID.db"

        /** Fase do build: P2 só quando a flag `accord.p2` está ligada; senão P1 (S6 sempre visível, como antes). */
        fun defaultPhase(): app.accord.core.analysis.ProductPhase =
            if (BuildConfig.PHASE_P2) app.accord.core.analysis.ProductPhase.P2 else app.accord.core.analysis.ProductPhase.P1

        /** URL e chave pública do build; sem elas, o serviço é "não configurado" e o app segue só neste aparelho. */
        fun defaultRemote(): RemoteSpaceService =
            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
                UnconfiguredSpaceService
            } else {
                SupabaseSpaceService(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            }
    }
}
