package app.accord.persistence

import app.accord.core.space.FinanceSpace
import app.accord.core.sync.AccessAction
import app.accord.core.sync.AccessFailure
import app.accord.core.sync.AccessField
import app.accord.core.sync.AccessOutcome
import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.AccessState
import app.accord.core.sync.AccessStore
import app.accord.core.sync.AccountView
import app.accord.core.sync.AuthSession
import app.accord.core.sync.EntryStage
import app.accord.core.sync.FieldProblem
import app.accord.core.sync.FirstStep
import app.accord.core.sync.FirstSteps
import app.accord.core.sync.InviteCache
import app.accord.core.sync.InviteCode
import app.accord.core.sync.InviteView
import app.accord.core.sync.ParticipantsCache
import app.accord.core.sync.ParticipantsView
import app.accord.core.sync.PendingSpace
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.RenewResult
import app.accord.core.sync.SessionCheck
import app.accord.core.sync.SessionState
import app.accord.core.sync.SignOutOutcome
import app.accord.core.sync.SpaceBinding
import app.accord.core.sync.SpaceRef
import app.accord.core.sync.SpaceView
import app.accord.core.sync.StoredSession
import app.accord.core.sync.SyncAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import app.accord.core.sync.PrivacyAction
import app.accord.core.sync.PrivacyCommand
import app.accord.core.sync.ArchivedSpace
import app.accord.core.sync.PendingAccountDeletion
import java.time.YearMonth
import java.util.UUID

/** Tudo o que o aparelho abre para um (conta, espaço): fachada financeira, fila e motor de sincronização. */
class SpaceRuntime(
    val space: FinanceSpace,
    val syncStore: SyncStore,
    val engine: SyncEngine,
    /** Histórico do Chat deste aparelho, no mesmo banco e fora da fila de sincronização (S3). */
    val chatStore: app.accord.core.chat.ChatStore? = null,
    /** Immutable owner of this opened database; available before the UI access state is published. */
    val identity: app.accord.core.space.Session,
    /** Sessão de importação OFX/PDF do espaço (S4); nula onde o espaço não a oferece (testes de acesso). */
    val imports: app.accord.core.importing.ImportSession? = null,
    val dataExport: RoomDataExport? = null,
    /** Limpa os dados financeiros e a fila deste aparelho depois de um reset do espaço (ADR 0088) e refaz as linhas iniciais. */
    val resetLocal: suspend () -> Unit = {},
) {
    /** Cancela consumidores antes de fechar/apagar o banco, sem acoplar a persistência ao Android. */
    @Volatile var beforeClose: () -> Unit = {}
}

/**
 * Abre o banco do par (conta, espaço). Cada par tem o próprio banco: duas contas no mesmo aparelho
 * nunca compartilham dados nem fila (S2 "Autenticação sem e-mails"). Sair não apaga o banco.
 */
interface SpaceRuntimeFactory {
    suspend fun open(accountId: String, spaceId: String, access: SyncAccess): SpaceRuntime
    fun close(accountId: String, spaceId: String)
    suspend fun eraseAccountData(accountId: String) {}
    suspend fun archiveSpace(accountId: String, spaceId: String) {}
    suspend fun archivedChatData(accountId: String, spaceId: String): String? = null
}

/**
 * Fachada de acesso e espaço (S2): conta, sessão, criação do espaço, convite e primeiros passos.
 * Mantém o estado observável das telas 01, 02 e 03 e entrega ao motor de sincronização a sessão
 * certa para os dados deste aparelho.
 *
 * - A senha existe só durante a chamada; nunca é guardada, registrada nem repassada.
 * - Cadastro e entrada não disparam nenhum e-mail: o serviço devolve a sessão na hora.
 * - Criar espaço e entrar com código são idempotentes: o ID do espaço é gerado e guardado antes da
 *   chamada, e o serviço devolve o mesmo espaço se a resposta se perdeu.
 */
class AccessCenter(
    private val remote: RemoteSpaceService,
    private val store: AccessStore,
    private val runtimes: SpaceRuntimeFactory,
    private val currentMonth: () -> YearMonth,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newPrivacyProof: () -> String = {
        ByteArray(32).also(java.security.SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    },
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(AccessState())
    private val runtimeState = MutableStateFlow<SpaceRuntime?>(null)
    private var openKey: Pair<String, String>? = null

    /** Código recém-gerado, só na memória deste processo. */
    private var issuedCode: Pair<String, Long>? = null

    val status: StateFlow<AccessState> = state.asStateFlow()

    /** Fachada financeira aberta, se houver conta e espaço associados a este aparelho. */
    val runtime: StateFlow<SpaceRuntime?> = runtimeState.asStateFlow()

    // ---- restauração ----

    /** Lê o estado salvo no aparelho e, se já houver espaço, abre o banco. Não usa a rede. */
    suspend fun restore() = mutex.lockedPublish {
        val snapshot = store.read()
        openRuntimeFor(snapshot)
        snapshot
    }

    /**
     * Para quem já está na conta mas ainda não tem espaço neste aparelho: descobre no serviço o espaço
     * de que a conta participa (aparelho novo, reinstalação). Sem rede, nada muda.
     */
    suspend fun discoverSpace(): AccessOutcome = guarded(AccessAction.Refreshing) {
        val snapshot = discoverSpaceIfMissing(store.read())
        openRuntimeFor(snapshot)
        AccessOutcome.Done(stageOf(snapshot))
    }

    // ---- entrar, cadastrar, sair ----

    suspend fun signIn(email: String, password: String): AccessOutcome = guarded(AccessAction.SigningIn) {
        validateCredentials(email, password)?.let { return@guarded it }
        remember(email)
        when (val result = remote.signIn(email.trim(), password)) {
            is RemoteResult.Failed -> fail(result.failure)
            is RemoteResult.Ok -> afterAuthentication(result.value)
        }
    }

    suspend fun signUp(email: String, password: String, confirmation: String): AccessOutcome = guarded(AccessAction.SigningUp) {
        validateCredentials(email, password)?.let { return@guarded it }
        if (confirmation != password) return@guarded AccessOutcome.Invalid(AccessField.PasswordConfirmation, FieldProblem.Mismatch)
        remember(email)
        when (val result = remote.signUp(email.trim(), password)) {
            is RemoteResult.Ok -> afterAuthentication(result.value)
            is RemoteResult.Failed -> if (result.failure == RemoteFailure.EmailAlreadyRegistered) {
                // Resposta perdida num cadastro anterior: a conta existe. Uma entrada com a senha digitada decide.
                when (val retry = remote.signIn(email.trim(), password)) {
                    is RemoteResult.Ok -> afterAuthentication(retry.value)
                    is RemoteResult.Failed -> AccessOutcome.Failed(AccessFailure.WrongCredentials)
                }
            } else {
                fail(result.failure)
            }
        }
    }

    /**
     * Sai preservando dados e fila no aparelho para a mesma conta. Com operações por enviar, pede
     * uma decisão explícita informando quantas são.
     */
    suspend fun signOut(confirmPending: Boolean): SignOutOutcome {
        mutex.lock()
        try {
            val pending = pendingOperations()
            if (pending > 0 && !confirmPending) return SignOutOutcome.NeedsConfirmation(pending)
            val snapshot = store.read()
            snapshot.session?.let { remote.signOut(it.accessToken) } // melhor esforço; sair funciona sem rede
            val updated = store.update { it.copy(session = null) }
            closeRuntime()
            issuedCode = null
            publish(updated)
            return SignOutOutcome.SignedOut
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun pendingOperations(): Int {
        val summary = runtimeState.value?.syncStore?.summary() ?: return 0
        return summary.awaitingOperations + summary.openConflicts + summary.rejectedOperations
    }

    private fun validateCredentials(email: String, password: String): AccessOutcome? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return AccessOutcome.Invalid(AccessField.Email, FieldProblem.Required)
        if (!EMAIL.matches(trimmed)) return AccessOutcome.Invalid(AccessField.Email, FieldProblem.InvalidFormat)
        if (password.isEmpty()) return AccessOutcome.Invalid(AccessField.Password, FieldProblem.Required)
        return null
    }

    private suspend fun remember(email: String) {
        store.update { it.copy(lastEmail = email.trim()) }
    }

    private suspend fun afterAuthentication(auth: AuthSession): AccessOutcome {
        var snapshot = store.update {
            it.copy(
                lastEmail = auth.email,
                session = StoredSession(auth.accountId, auth.email, auth.accessToken, auth.refreshToken, auth.accessExpiresAtMillis),
            )
        }
        snapshot = discoverSpaceIfMissing(snapshot)
        openRuntimeFor(snapshot)
        return AccessOutcome.Done(stageOf(snapshot))
    }

    /**
     * Quem já participa de um espaço no serviço (aparelho novo, reinstalação) chega direto ao app:
     * o espaço é adotado e a primeira sincronização traz todo o estado.
     */
    private suspend fun discoverSpaceIfMissing(snapshot: AccessSnapshot): AccessSnapshot {
        val session = snapshot.session ?: return snapshot
        if (session.needsRenewal || snapshot.bindings.containsKey(session.accountId) || snapshot.pending.containsKey(session.accountId)) return snapshot
        return when (val result = authorized { remote.currentSpace(it) }) {
            is RemoteResult.Ok -> result.value?.let { bind(snapshot.session!!.accountId, it, dismissedSteps = true) } ?: snapshot
            is RemoteResult.Failed -> snapshot
        }
    }

    private suspend fun bind(accountId: String, ref: SpaceRef, dismissedSteps: Boolean, joinedByInvite: Boolean = false): AccessSnapshot = store.update {
        it.copy(
            bindings = it.bindings + (accountId to SpaceBinding(accountId, ref.spaceId, ref.name, dismissedSteps, joinedByInvite)),
            pending = it.pending - accountId,
        )
    }

    // ---- espaço e convite ----

    /** Cria o espaço uma única vez; repetir depois de uma resposta perdida recupera o mesmo. */
    suspend fun createSpace(name: String): AccessOutcome = guarded(AccessAction.CreatingSpace) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@guarded AccessOutcome.Invalid(AccessField.SpaceName, FieldProblem.Required)
        val session = usableSession() ?: return@guarded AccessOutcome.Failed(sessionFailure())
        var snapshot = store.read()
        if (snapshot.bindings.containsKey(session.accountId)) return@guarded AccessOutcome.Failed(AccessFailure.AlreadyHasSpace)
        val pending = snapshot.pending[session.accountId]?.copy(name = trimmed) ?: PendingSpace(newId(), trimmed)
        snapshot = store.update { it.copy(pending = it.pending + (session.accountId to pending)) }
        publish(snapshot)
        when (val result = authorized { remote.createSpace(it, pending.spaceId, trimmed) }) {
            is RemoteResult.Failed -> fail(result.failure)
            is RemoteResult.Ok -> {
                snapshot = bind(session.accountId, result.value, dismissedSteps = result.value.adoptedExisting)
                openRuntimeFor(snapshot)
                AccessOutcome.Done(stageOf(snapshot))
            }
        }
    }

    /** O código aparece uma vez, em [AccessState.invite]; o serviço guarda só um resumo. */
    suspend fun generateInvite(): AccessOutcome = guarded(AccessAction.GeneratingInvite) {
        val (session, binding) = boundSession() ?: return@guarded AccessOutcome.Failed(sessionFailureOrNoSpace())
        when (val result = authorized { remote.generateInvite(it, binding.spaceId) }) {
            is RemoteResult.Failed -> fail(result.failure)
            is RemoteResult.Ok -> {
                val invite = result.value
                issuedCode = invite.code to invite.validUntilMillis
                store.update {
                    it.copy(invites = it.invites + (session.accountId to InviteCache(invite.validUntilMillis, expired = false, checkedAtMillis = nowMillis())))
                }
                AccessOutcome.Done(stageOf(store.read()))
            }
        }
    }

    /** Invalida o código ativo (ele pode ter vazado ou já cumpriu o papel); outro só se gera por pedido. */
    suspend fun revokeInvite(): AccessOutcome = guarded(AccessAction.RevokingInvite) {
        val (session, binding) = boundSession() ?: return@guarded AccessOutcome.Failed(sessionFailureOrNoSpace())
        when (val result = authorized { remote.revokeInvite(it, binding.spaceId) }) {
            is RemoteResult.Failed -> fail(result.failure)
            is RemoteResult.Ok -> {
                issuedCode = null
                store.update { it.copy(invites = it.invites - session.accountId) }
                AccessOutcome.Done(stageOf(store.read()))
            }
        }
    }

    /** Esquece o código mostrado (ao sair da tela, por exemplo). */
    suspend fun forgetIssuedCode() {
        issuedCode = null
        publish(store.read())
    }

    suspend fun joinWithCode(code: String): AccessOutcome = guarded(AccessAction.Joining) {
        val normalized = InviteCode.normalize(code)
        if (normalized.isEmpty()) return@guarded AccessOutcome.Invalid(AccessField.InviteCode, FieldProblem.Required)
        if (!InviteCode.isWellFormed(normalized)) return@guarded AccessOutcome.Invalid(AccessField.InviteCode, FieldProblem.InvalidFormat)
        val session = usableSession() ?: return@guarded AccessOutcome.Failed(sessionFailure())
        if (store.read().bindings.containsKey(session.accountId)) return@guarded AccessOutcome.Failed(AccessFailure.AlreadyHasSpace)
        when (val result = authorized { remote.joinWithCode(it, normalized) }) {
            is RemoteResult.Failed -> fail(result.failure)
            is RemoteResult.Ok -> {
                // Quem já estava no espaço (código repetido) não é tratado como convidado novo.
                val snapshot = bind(session.accountId, result.value, dismissedSteps = result.value.adoptedExisting, joinedByInvite = !result.value.adoptedExisting)
                openRuntimeFor(snapshot)
                AccessOutcome.Done(stageOf(snapshot))
            }
        }
    }

    /** Relê participantes e convite; sem rede, o último resultado continua visível com a hora da verificação. */
    suspend fun refreshSpaceInfo(): AccessOutcome = guarded(AccessAction.Refreshing) {
        val (session, binding) = boundSession() ?: return@guarded AccessOutcome.Failed(sessionFailureOrNoSpace())
        when (val result = authorized { remote.participants(it, binding.spaceId) }) {
            is RemoteResult.Failed -> return@guarded fail(result.failure)
            is RemoteResult.Ok -> store.update {
                it.copy(participants = it.participants + (session.accountId to ParticipantsCache(result.value.items, result.value.checkedAtMillis)))
            }
        }
        when (val result = authorized { remote.activeInvite(it, binding.spaceId) }) {
            is RemoteResult.Failed -> return@guarded fail(result.failure)
            is RemoteResult.Ok -> store.update {
                val cache = result.value?.let { s -> InviteCache(s.validUntilMillis, s.expired, nowMillis()) }
                it.copy(invites = if (cache == null) it.invites - session.accountId else it.invites + (session.accountId to cache))
            }
        }
        AccessOutcome.Done(stageOf(store.read()))
    }

    // ---- primeiros passos ----

    /** Só o endpoint do serviço pode fornecer os dados pessoais enviados; nunca expõe a sessão à UI. */
    suspend fun exportData(scope: app.accord.core.sync.DataExportScope): RemoteResult<String> {
        val spaceId = store.read().let { snapshot -> snapshot.session?.let { snapshot.bindings[it.accountId]?.spaceId } }
        return authorized { token -> remote.exportData(token, scope, spaceId) }
    }

    /** A confirmação já foi feita na UI. A chamada preserva comprovante mesmo se a UI/processo sair. */
    suspend fun prepareAccountDeletion(password: String): RemoteResult<String> = withContext(NonCancellable) {
        mutex.withLock {
            val snapshot = store.read()
            val session = snapshot.session ?: return@withLock RemoteResult.Failed(RemoteFailure.SessionExpired)
            val pending = snapshot.pendingAccountDeletion?.takeIf { it.accountId == session.accountId }
                ?: PendingAccountDeletion(session.accountId, newId(), newPrivacyProof())
            store.update { it.copy(pendingAccountDeletion = pending) }
            val result = authorized { token -> remote.privacy(token, PrivacyCommand(PrivacyAction.PrepareAccountDeletion,
                pending.requestId, pending.proof, password)) }
            if (snapshot.pendingAccountDeletion == null && result is RemoteResult.Failed && result.failure == RemoteFailure.InvalidCredentials) {
                store.update { it.copy(pendingAccountDeletion = null) }
            }
            publish(store.read())
            result
        }
    }

    suspend fun finishAccountDeletion(): RemoteResult<String> = withContext(NonCancellable) {
        mutex.withLock {
            val pending = store.read().pendingAccountDeletion
                ?: return@withLock RemoteResult.Ok("{\"status\":\"Completed\"}")
            val result = remote.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, pending.requestId, pending.proof))
            if (result is RemoteResult.Ok && Json.parseToJsonElement(result.value).jsonObject["status"]?.jsonPrimitive?.contentOrNull == "Completed") {
                closeRuntime()
                runtimes.eraseAccountData(pending.accountId)
                val updated = store.update { current -> current.copy(
                    lastEmail = if (current.session?.accountId == pending.accountId) null else current.lastEmail,
                    session = current.session?.takeUnless { it.accountId == pending.accountId },
                    bindings = current.bindings - pending.accountId, pending = current.pending - pending.accountId,
                    participants = current.participants - pending.accountId, invites = current.invites - pending.accountId,
                    archivedSpaces = current.archivedSpaces.filterNot { it.accountId == pending.accountId }, pendingAccountDeletion = null,
                ) }
                publish(updated)
                state.update { it.copy(accountDeletionCompleted = true) }
            }
            result
        }
    }

    /** Não guarda senha; aprovações são validadas novamente contra os membros atuais pelo serviço. */
    suspend fun privacySpaceAction(action: PrivacyAction, requestId: String, password: String? = null): RemoteResult<String> = withContext(NonCancellable) {
        val binding = store.read().let { it.session?.accountId?.let(it.bindings::get) }
            ?: return@withContext RemoteResult.Failed(RemoteFailure.NotMember)
        val result = authorized { token -> remote.privacy(token, PrivacyCommand(action, requestId, password = password, spaceId = binding.spaceId)) }
        if (result is RemoteResult.Ok) reconcilePrivacy()
        result
    }

    /** Avisos autenticados permitem limpar finanças encerradas preservando o Chat local somente leitura. */
    suspend fun reconcilePrivacy(): RemoteResult<String> = mutex.withLock {
        val snapshot = store.read()
        val accountId = snapshot.session?.accountId ?: return@withLock RemoteResult.Failed(RemoteFailure.SessionExpired)
        val result = authorized { remote.privacy(it, PrivacyCommand(PrivacyAction.Status)) }
        if (result is RemoteResult.Ok) {
            val data = Json.parseToJsonElement(result.value).jsonObject
            val notices = data["closedSpaces"] as? JsonArray ?: JsonArray(emptyList())
            for (element in notices) {
                val notice = element as? JsonObject ?: continue
                val spaceId = notice["spaceId"]?.jsonPrimitive?.contentOrNull ?: continue
                if (store.read().archivedSpaces.any { it.accountId == accountId && it.spaceId == spaceId }) continue
                if (openKey == (accountId to spaceId)) closeRuntime()
                runtimes.archiveSpace(accountId, spaceId)
                store.update { current -> current.copy(
                    bindings = if (current.bindings[accountId]?.spaceId == spaceId) current.bindings - accountId else current.bindings,
                    participants = current.participants - accountId, invites = current.invites - accountId,
                    archivedSpaces = current.archivedSpaces + ArchivedSpace(accountId, spaceId,
                        notice["name"]?.jsonPrimitive?.contentOrNull ?: "Espaço encerrado"),
                ) }
            }
            applyResetIfNeeded(accountId, data)
            val currentBinding = store.read().bindings[accountId]
            if (currentBinding != null) {
                val participants = authorized { remote.participants(it, currentBinding.spaceId) }
                if (participants is RemoteResult.Ok) store.update { current -> current.copy(
                    participants = current.participants + (accountId to ParticipantsCache(participants.value.items, participants.value.checkedAtMillis)),
                ) }
            }
            publish(store.read())
        }
        result
    }

    /**
     * Reset do espaço (ADR 0088): se o serviço está numa época de reset maior que a já aplicada aqui, os dados financeiros e a fila
     * deste aparelho são descartados (o espaço voltou a vazio para todos) e a época nova é guardada. Só depois de limpar com êxito:
     * falha ou banco ainda fechado deixa a época antiga, e a próxima leitura tenta de novo. Alterações deste aparelho ainda não
     * enviadas também se perdem, e o serviço não tem como dizer quantas eram.
     */
    private suspend fun applyResetIfNeeded(accountId: String, status: JsonObject) {
        val binding = store.read().bindings[accountId] ?: return
        val remoteEpoch = (status["spaces"] as? JsonArray).orEmpty().firstNotNullOfOrNull { element ->
            (element as? JsonObject)?.takeIf { it["spaceId"]?.jsonPrimitive?.contentOrNull == binding.spaceId }
                ?.get("resetEpoch")?.jsonPrimitive?.longOrNull
        } ?: return
        if (remoteEpoch <= binding.resetEpoch) return
        val runtime = runtimeState.value?.takeIf { openKey == (accountId to binding.spaceId) } ?: return
        try {
            runtime.resetLocal()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        store.update { current ->
            val now = current.bindings[accountId]?.takeIf { it.spaceId == binding.spaceId } ?: return@update current
            current.copy(bindings = current.bindings + (accountId to now.copy(resetEpoch = remoteEpoch)))
        }
        runtime.engine.refresh()
    }

    suspend fun archivedChatData(spaceId: String): String? {
        val snapshot = store.read()
        val accountId = snapshot.session?.accountId ?: return null
        if (snapshot.archivedSpaces.none { it.accountId == accountId && it.spaceId == spaceId }) return null
        return runtimes.archivedChatData(accountId, spaceId)
    }

    fun dismissDeletionConfirmation() { state.update { it.copy(accountDeletionCompleted = false) } }

    suspend fun firstSteps(): List<FirstStep> {
        val runtime = runtimeState.value ?: return emptyList()
        val snapshot = store.read()
        val accountId = snapshot.session?.accountId
        val cached = accountId?.let { snapshot.participants[it] }
        val participants = cached?.let { app.accord.core.sync.Participants(it.items, it.checkedAtMillis) }
        if (accountId?.let { snapshot.bindings[it] }?.joinedByInvite == true) return FirstSteps.deriveGuest(runtime.space)
        return FirstSteps.derive(runtime.space, currentMonth(), participants)
    }

    /** Nome do espaço de quem entrou por convite neste aparelho; nulo para quem criou o espaço. */
    suspend fun guestSpaceName(): String? {
        val snapshot = store.read()
        val accountId = snapshot.session?.accountId ?: return null
        return snapshot.bindings[accountId]?.takeIf { it.joinedByInvite }?.spaceName
    }

    /** "Começar a registrar": encerra a lista neste aparelho; ela continua acessível em Mais. */
    suspend fun dismissFirstSteps() = mutex.lockedPublish {
        store.update { snapshot ->
            val accountId = snapshot.session?.accountId ?: return@update snapshot
            val binding = snapshot.bindings[accountId] ?: return@update snapshot
            snapshot.copy(bindings = snapshot.bindings + (accountId to binding.copy(firstStepsDismissed = true)))
        }
    }

    // ---- acesso do motor de sincronização ----

    /** Sessão que o motor de um (conta, espaço) pode usar: outra conta no aparelho nunca envia esta fila. */
    fun accessFor(accountId: String, spaceId: String): SyncAccess = object : SyncAccess {
        override suspend fun check(): SessionCheck {
            val snapshot = store.read()
            val session = snapshot.session ?: return SessionCheck.SignedOut
            if (session.accountId != accountId || snapshot.bindings[accountId]?.spaceId != spaceId) return SessionCheck.OtherAccount
            if (session.needsRenewal) return SessionCheck.NeedsRenewal
            return SessionCheck.Usable(session.toAuth(), spaceId)
        }

        override suspend fun renew(): RenewResult = renewSession(accountId)
    }

    private suspend fun renewSession(accountId: String): RenewResult {
        val session = store.read().session?.takeIf { it.accountId == accountId } ?: return RenewResult.Expired
        return when (val result = remote.refresh(session.refreshToken)) {
            is RemoteResult.Ok -> {
                val auth = result.value
                if (auth.accountId != accountId) return RenewResult.Expired
                val updated = store.update {
                    it.copy(session = StoredSession(auth.accountId, auth.email, auth.accessToken, auth.refreshToken, auth.accessExpiresAtMillis))
                }
                publish(updated)
                RenewResult.Renewed(auth)
            }
            is RemoteResult.Failed -> if (result.failure == RemoteFailure.SessionExpired || result.failure == RemoteFailure.InvalidCredentials) {
                // Fila e dados ficam; só a pessoa pode entrar de novo, e com a mesma conta.
                publish(store.update { it.copy(session = it.session?.copy(needsRenewal = true)) })
                RenewResult.Expired
            } else {
                RenewResult.Failed(result.failure)
            }
        }
    }

    // ---- internos ----

    private fun StoredSession.toAuth() = AuthSession(accountId, email, accessToken, refreshToken, accessExpiresAtMillis)

    private suspend fun usableSession(): StoredSession? = store.read().session?.takeIf { !it.needsRenewal }

    private suspend fun sessionFailure(): AccessFailure =
        if (store.read().session == null) AccessFailure.NotSignedIn else AccessFailure.SessionExpired

    private suspend fun sessionFailureOrNoSpace(): AccessFailure {
        val snapshot = store.read()
        val session = snapshot.session ?: return AccessFailure.NotSignedIn
        if (session.needsRenewal) return AccessFailure.SessionExpired
        return AccessFailure.NoSpace
    }

    private suspend fun boundSession(): Pair<StoredSession, SpaceBinding>? {
        val snapshot = store.read()
        val session = snapshot.session?.takeIf { !it.needsRenewal } ?: return null
        val binding = snapshot.bindings[session.accountId] ?: return null
        return session to binding
    }

    /** Chama o serviço com a sessão atual; se ela venceu, renova uma vez e repete. */
    private suspend fun <T> authorized(call: suspend (String) -> RemoteResult<T>): RemoteResult<T> {
        val session = store.read().session ?: return RemoteResult.Failed(RemoteFailure.SessionExpired)
        val first = call(session.accessToken)
        if (first !is RemoteResult.Failed || first.failure != RemoteFailure.SessionExpired) return first
        return when (val renewed = renewSession(session.accountId)) {
            is RenewResult.Renewed -> call(renewed.session.accessToken)
            RenewResult.Expired -> first
            is RenewResult.Failed -> RemoteResult.Failed(renewed.failure)
        }
    }

    private fun fail(failure: RemoteFailure): AccessOutcome.Failed = AccessOutcome.Failed(
        when (failure) {
            RemoteFailure.InvalidCredentials -> AccessFailure.WrongCredentials
            RemoteFailure.WeakPassword -> AccessFailure.WeakPassword
            RemoteFailure.NoNetwork -> AccessFailure.NoNetwork
            RemoteFailure.ServiceUnavailable -> AccessFailure.ServiceUnavailable
            RemoteFailure.ServicePaused -> AccessFailure.ServicePaused
            RemoteFailure.QuotaExceeded -> AccessFailure.QuotaExceeded
            RemoteFailure.InvalidInvite -> AccessFailure.InvalidInvite
            RemoteFailure.SpaceFull -> AccessFailure.SpaceFull
            RemoteFailure.AlreadyInAnotherSpace -> AccessFailure.AlreadyInAnotherSpace
            is RemoteFailure.RateLimited -> AccessFailure.RateLimited
            RemoteFailure.SessionExpired -> AccessFailure.SessionExpired
            RemoteFailure.NotMember -> AccessFailure.NotMember
            else -> AccessFailure.Unknown
        },
    )

    private fun stageOf(snapshot: AccessSnapshot): EntryStage {
        val session = snapshot.session ?: return EntryStage.SignedOut
        val binding = snapshot.bindings[session.accountId] ?: return EntryStage.NoSpace
        return if (binding.firstStepsDismissed) EntryStage.Ready else EntryStage.FirstSteps
    }

    private suspend fun openRuntimeFor(snapshot: AccessSnapshot) {
        val session = snapshot.session
        val binding = session?.let { snapshot.bindings[it.accountId] }
        val wanted = binding?.let { it.accountId to it.spaceId }
        if (wanted == openKey) return
        closeRuntime()
        if (binding != null && wanted != null) {
            runtimeState.value = runtimes.open(binding.accountId, binding.spaceId, accessFor(binding.accountId, binding.spaceId))
            openKey = wanted
        }
    }

    private fun closeRuntime() {
        runtimeState.value?.beforeClose?.invoke()
        runtimeState.value = null
        openKey?.let { (account, space) -> runtimes.close(account, space) }
        openKey = null
    }

    private suspend fun publish(snapshot: AccessSnapshot) {
        val session = snapshot.session
        val binding = session?.let { snapshot.bindings[it.accountId] }
        val accountId = session?.accountId
        val participants = accountId?.let { snapshot.participants[it] }
        val invite = accountId?.let { snapshot.invites[it] }
        val code = issuedCode?.takeIf { invite != null && it.second == invite.validUntilMillis }?.first
        state.update {
            it.copy(
                stage = stageOf(snapshot),
                accountDeletionPending = snapshot.pendingAccountDeletion != null,
                archivedSpaces = snapshot.archivedSpaces.filter { it.accountId == accountId },
                lastEmail = snapshot.lastEmail,
                account = session?.let { s -> AccountView(s.accountId, s.email) },
                session = when {
                    session == null -> SessionState.SignedOut
                    session.needsRenewal -> SessionState.NeedsRenewal
                    else -> SessionState.Valid
                },
                space = binding?.let { b -> SpaceView(b.spaceId, b.spaceName) },
                pendingSpaceName = accountId?.let { id -> snapshot.pending[id]?.name },
                participants = participants?.let { p -> ParticipantsView(p.items, p.checkedAtMillis, accountId) },
                invite = invite?.let { i -> InviteView(i.validUntilMillis, i.expired, code) },
            )
        }
    }

    private suspend fun guarded(action: AccessAction, block: suspend () -> AccessOutcome): AccessOutcome {
        if (!mutex.tryLock()) return AccessOutcome.Failed(AccessFailure.Busy)
        try {
            state.update { it.copy(busy = action) }
            val outcome = block()
            publish(store.read())
            return outcome
        } finally {
            state.update { it.copy(busy = null) }
            mutex.unlock()
        }
    }

    private suspend fun Mutex.lockedPublish(block: suspend () -> AccessSnapshot) {
        lock()
        try {
            publish(block())
        } finally {
            unlock()
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
