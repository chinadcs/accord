package app.accord.remote

import app.accord.core.sync.AppliedVersion
import app.accord.core.sync.AuthSession
import app.accord.core.sync.EntityChangeEnvelope
import app.accord.core.sync.EntityKey
import app.accord.core.sync.InviteCode
import app.accord.core.sync.InviteStatus
import app.accord.core.sync.IssuedInvite
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.PROTOCOL_VERSION
import app.accord.core.sync.Participant
import app.accord.core.sync.Participants
import app.accord.core.sync.PullPage
import app.accord.core.sync.RejectReason
import app.accord.core.sync.RemoteEntityState
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.SharedTypes
import app.accord.core.sync.SpaceRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.UUID

/** Estado do serviço para a injeção de falhas. */
enum class FakeServiceState { Healthy, Unavailable, Paused, QuotaExceeded }

/**
 * Servidor fake em memória do Serviço remoto do espaço (S2 "Seam de fronteira"). Reproduz as
 * regras do serviço real: associação por espaço, convite atômico (8 caracteres, 3 dias, uso único,
 * 2 pessoas), versão por entidade, idempotência por ID de operação e cursor sem lacunas. A suíte
 * de contrato roda contra ele e contra o Supabase local para provar que não se afasta do real.
 *
 * Todas as chamadas são serializadas por um [Mutex], como a serialização por espaço no banco.
 * Relógio, tokens e geração do código são injetáveis. Nada aqui envia e-mails.
 */
class FakeSpaceService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: java.util.Random = SecureRandom(),
    private val accessTtlMillis: Long = 60 * 60 * 1000L,
    private val minPasswordLength: Int = 6,
    private val maxFailedInviteAttempts: Int = InviteCode.MAX_FAILED_ATTEMPTS,
    private val attemptWindowMillis: Long = InviteCode.ATTEMPT_WINDOW_MILLIS,
) : RemoteSpaceService {
    private val mutex = Mutex()

    private class Account(val id: String, val email: String, var password: String)
    private class Token(val accountId: String, val expiresAtMillis: Long)

    private enum class InviteState { Active, Consumed, Replaced }
    private class Invite(
        val code: String,
        val generatedAtMillis: Long,
        val validUntilMillis: Long,
        var state: InviteState = InviteState.Active,
        var consumedBy: String? = null,
    )

    private class Member(val accountId: String, val nickname: String, val joinedAtMillis: Long)
    private class Space(val id: String, val name: String) {
        val members = mutableListOf<Member>()
        val invites = mutableListOf<Invite>()
        val entities = linkedMapOf<EntityKey, RemoteEntityState>()
        var cursor = 0L
    }

    private class AppliedRecord(val spaceId: String, val changes: List<EntityChangeEnvelope>, val result: OperationResult.Applied)

    private val accounts = mutableMapOf<String, Account>()
    private val accountByEmail = mutableMapOf<String, String>()
    private val accessTokens = mutableMapOf<String, Token>()
    private val refreshTokens = mutableMapOf<String, String>()
    private val spaces = mutableMapOf<String, Space>()
    private val spaceOfAccount = mutableMapOf<String, String>()
    private val entityOwner = mutableMapOf<EntityKey, String>()
    private val applied = mutableMapOf<String, AppliedRecord>()
    private val failedAttempts = mutableMapOf<String, MutableList<Long>>()

    // ---- injeção de falhas e observação (só para testes e desenvolvimento) ----

    @Volatile var online: Boolean = true

    /** Versão do protocolo que o serviço aceita; mudar simula um serviço mais novo que o aplicativo. */
    @Volatile var supportedProtocolVersion: Int = PROTOCOL_VERSION
    @Volatile var serviceState: FakeServiceState = FakeServiceState.Healthy

    private val queuedFailures = ArrayDeque<RemoteFailure>()
    private var dropNextResponse = false

    /** A próxima chamada (de qualquer tipo) falha sem efeito. */
    fun failNext(failure: RemoteFailure) = queuedFailures.addLast(failure)

    /** A próxima chamada é aplicada, mas a resposta se perde: quem chamou vê [RemoteFailure.NoNetwork]. */
    fun loseNextResponse() {
        dropNextResponse = true
    }

    /** Tudo o que o serviço recebeu em `push`, na ordem, para provar o que nunca sai do aparelho. */
    val receivedOperations: List<OperationEnvelope> get() = received.toList()
    private val received = mutableListOf<OperationEnvelope>()

    /** O serviço nunca envia e-mails (piloto sem SMTP). */
    val sentEmails: List<String> get() = emptyList()

    fun revokeSessions(email: String) {
        val id = accountByEmail[normalizeEmail(email)] ?: return
        accessTokens.entries.removeAll { it.value.accountId == id }
        refreshTokens.entries.removeAll { it.value == id }
    }

    /** Redefinição feita pelo responsável, fora do aplicativo. Revoga as sessões existentes. */
    fun adminResetPassword(email: String, newPassword: String) {
        val id = accountByEmail[normalizeEmail(email)] ?: return
        accounts.getValue(id).password = newPassword
        revokeSessions(email)
    }

    fun memberCount(spaceId: String): Int = spaces[spaceId]?.members?.size ?: 0
    fun spaceCount(): Int = spaces.size
    fun storedSpaceIds(): List<String> = spaces.keys.toList()
    fun accountCount(): Int = accounts.size
    fun storedEntity(spaceId: String, type: String, id: String): RemoteEntityState? =
        spaces[spaceId]?.entities?.get(EntityKey(type, id))

    fun storedEntities(spaceId: String, type: String): List<RemoteEntityState> =
        spaces[spaceId]?.entities?.values?.filter { it.type == type }.orEmpty()

    fun entityCount(spaceId: String): Int = spaces[spaceId]?.entities?.size ?: 0
    fun spaceCursor(spaceId: String): Long = spaces[spaceId]?.cursor ?: 0

    /** Recua a geração dos convites do espaço, simulando a passagem do tempo no serviço. */
    fun backdateInvites(spaceId: String, byMillis: Long) {
        val space = spaces[spaceId] ?: return
        val shifted = space.invites.map {
            Invite(it.code, it.generatedAtMillis - byMillis, it.validUntilMillis - byMillis, it.state, it.consumedBy)
        }
        space.invites.clear()
        space.invites.addAll(shifted)
    }

    // ---- chamada com falhas injetadas ----

    private suspend fun <T> call(block: () -> RemoteResult<T>): RemoteResult<T> = mutex.withLock {
        if (!online) return@withLock RemoteResult.Failed(RemoteFailure.NoNetwork)
        when (serviceState) {
            FakeServiceState.Healthy -> Unit
            FakeServiceState.Unavailable -> return@withLock RemoteResult.Failed(RemoteFailure.ServiceUnavailable)
            FakeServiceState.Paused -> return@withLock RemoteResult.Failed(RemoteFailure.ServicePaused)
            FakeServiceState.QuotaExceeded -> return@withLock RemoteResult.Failed(RemoteFailure.QuotaExceeded)
        }
        queuedFailures.removeFirstOrNull()?.let { return@withLock RemoteResult.Failed(it) }
        val result = block()
        if (dropNextResponse) {
            dropNextResponse = false
            RemoteResult.Failed(RemoteFailure.NoNetwork)
        } else {
            result
        }
    }

    private fun fail(failure: RemoteFailure) = RemoteResult.Failed(failure)

    private fun normalizeEmail(email: String) = email.trim().lowercase()

    // ---- conta ----

    private fun issue(account: Account): AuthSession {
        val access = UUID.randomUUID().toString()
        val refresh = UUID.randomUUID().toString()
        val expires = clock() + accessTtlMillis
        accessTokens[access] = Token(account.id, expires)
        refreshTokens[refresh] = account.id
        return AuthSession(account.id, account.email, access, refresh, expires)
    }

    private fun authenticate(accessToken: String): Account? {
        val token = accessTokens[accessToken] ?: return null
        if (token.expiresAtMillis <= clock()) return null
        return accounts[token.accountId]
    }

    override suspend fun signUp(email: String, password: String): RemoteResult<AuthSession> = call {
        val key = normalizeEmail(email)
        if (!key.contains('@')) return@call fail(RemoteFailure.Unknown("invalid_email"))
        if (password.length < minPasswordLength) return@call fail(RemoteFailure.WeakPassword)
        if (key in accountByEmail) return@call fail(RemoteFailure.EmailAlreadyRegistered)
        val account = Account(UUID.randomUUID().toString(), key, password)
        accounts[account.id] = account
        accountByEmail[key] = account.id
        // Confirm Email desativado: o cadastro devolve a sessão na hora.
        RemoteResult.Ok(issue(account))
    }

    override suspend fun signIn(email: String, password: String): RemoteResult<AuthSession> = call {
        val account = accountByEmail[normalizeEmail(email)]?.let { accounts[it] }
        if (account == null || account.password != password) fail(RemoteFailure.InvalidCredentials)
        else RemoteResult.Ok(issue(account))
    }

    override suspend fun refresh(refreshToken: String): RemoteResult<AuthSession> = call {
        val accountId = refreshTokens.remove(refreshToken) ?: return@call fail(RemoteFailure.SessionExpired)
        RemoteResult.Ok(issue(accounts.getValue(accountId)))
    }

    override suspend fun signOut(accessToken: String): RemoteResult<Unit> = call {
        val token = accessTokens.remove(accessToken)
        if (token != null) refreshTokens.entries.removeAll { it.value == token.accountId }
        RemoteResult.Ok(Unit)
    }

    // ---- espaço e convite ----

    override suspend fun createSpace(accessToken: String, spaceId: String, name: String): RemoteResult<SpaceRef> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val current = spaceOfAccount[account.id]
        if (current == spaceId) {
            return@call RemoteResult.Ok(SpaceRef(spaceId, spaces.getValue(spaceId).name))
        }
        if (current != null) {
            return@call RemoteResult.Ok(SpaceRef(current, spaces.getValue(current).name, adoptedExisting = true))
        }
        if (spaceId in spaces) return@call fail(RemoteFailure.NotMember)
        val space = Space(spaceId, name.trim())
        space.members += Member(account.id, nicknameOf(account), clock())
        spaces[spaceId] = space
        spaceOfAccount[account.id] = spaceId
        RemoteResult.Ok(SpaceRef(spaceId, space.name))
    }

    override suspend fun currentSpace(accessToken: String): RemoteResult<SpaceRef?> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        RemoteResult.Ok(spaceOfAccount[account.id]?.let { SpaceRef(it, spaces.getValue(it).name) })
    }

    private fun nicknameOf(account: Account) = account.email.substringBefore('@')

    private fun memberSpace(account: Account, spaceId: String): Space? =
        spaces[spaceId]?.takeIf { s -> s.members.any { it.accountId == account.id } }

    private fun newCode(space: Space): String {
        while (true) {
            val code = buildString { repeat(InviteCode.LENGTH) { append(InviteCode.ALPHABET[random.nextInt(InviteCode.ALPHABET.length)]) } }
            if (spaces.values.none { s -> s.invites.any { it.code == code } }) return code
        }
    }

    override suspend fun generateInvite(accessToken: String, spaceId: String): RemoteResult<IssuedInvite> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val space = memberSpace(account, spaceId) ?: return@call fail(RemoteFailure.NotMember)
        if (space.members.size >= InviteCode.MAX_MEMBERS) return@call fail(RemoteFailure.SpaceFull)
        space.invites.filter { it.state == InviteState.Active }.forEach { it.state = InviteState.Replaced }
        val now = clock()
        val invite = Invite(newCode(space), now, now + InviteCode.VALIDITY_MILLIS)
        space.invites += invite
        RemoteResult.Ok(IssuedInvite(invite.code, invite.generatedAtMillis, invite.validUntilMillis))
    }

    override suspend fun activeInvite(accessToken: String, spaceId: String): RemoteResult<InviteStatus?> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val space = memberSpace(account, spaceId) ?: return@call fail(RemoteFailure.NotMember)
        val active = space.invites.lastOrNull { it.state == InviteState.Active }
        RemoteResult.Ok(active?.let { InviteStatus(it.validUntilMillis, expired = clock() >= it.validUntilMillis) })
    }

    override suspend fun joinWithCode(accessToken: String, code: String): RemoteResult<SpaceRef> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val now = clock()
        val attempts = failedAttempts.getOrPut(account.id) { mutableListOf() }
        attempts.removeAll { it <= now - attemptWindowMillis }
        if (attempts.size >= maxFailedInviteAttempts) {
            return@call fail(RemoteFailure.RateLimited(attempts.first() + attemptWindowMillis - now))
        }
        val normalized = InviteCode.normalize(code)
        val (space, invite) = spaces.values.firstNotNullOfOrNull { s ->
            s.invites.firstOrNull { it.code == normalized }?.let { s to it }
        } ?: (null to null)

        // Repetição pela conta que consumiu: conclui a mesma associação, sem erro.
        if (space != null && invite != null && invite.state == InviteState.Consumed && invite.consumedBy == account.id) {
            return@call RemoteResult.Ok(SpaceRef(space.id, space.name))
        }
        if (account.id in spaceOfAccount) return@call fail(RemoteFailure.AlreadyInAnotherSpace)
        if (space == null || invite == null || invite.state != InviteState.Active || now >= invite.validUntilMillis) {
            attempts += now
            return@call fail(RemoteFailure.InvalidInvite)
        }
        if (space.members.size >= InviteCode.MAX_MEMBERS) return@call fail(RemoteFailure.SpaceFull)
        space.members += Member(account.id, nicknameOf(account), now)
        spaceOfAccount[account.id] = space.id
        invite.state = InviteState.Consumed
        invite.consumedBy = account.id
        RemoteResult.Ok(SpaceRef(space.id, space.name))
    }

    override suspend fun participants(accessToken: String, spaceId: String): RemoteResult<Participants> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val space = memberSpace(account, spaceId) ?: return@call fail(RemoteFailure.NotMember)
        RemoteResult.Ok(
            Participants(space.members.map { Participant(it.accountId, it.nickname, it.joinedAtMillis) }, clock()),
        )
    }

    // ---- envio e recebimento ----

    override suspend fun push(
        accessToken: String,
        spaceId: String,
        operations: List<OperationEnvelope>,
    ): RemoteResult<List<OperationResult>> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val space = memberSpace(account, spaceId) ?: return@call fail(RemoteFailure.NotMember)
        received += operations
        RemoteResult.Ok(operations.map { applyOperation(account, space, it) })
    }

    private fun reject(op: OperationEnvelope, reason: RejectReason, detail: String? = null) =
        OperationResult.Rejected(op.operationId, reason, detail)

    private fun applyOperation(account: Account, space: Space, op: OperationEnvelope): OperationResult {
        if (op.protocolVersion != supportedProtocolVersion) return reject(op, RejectReason.ProtocolUnsupported)
        if (op.spaceId != space.id) return reject(op, RejectReason.ForeignSpace, "espaço da operação difere do espaço do envio")
        applied[op.operationId]?.let { previous ->
            return if (previous.spaceId == op.spaceId && previous.changes == op.changes) {
                previous.result.copy(repeated = true)
            } else {
                reject(op, RejectReason.DivergentOperation)
            }
        }
        if (op.changes.isEmpty()) return reject(op, RejectReason.InvalidEnvelope, "operação sem alterações")
        val keys = op.changes.map { EntityKey(it.type, it.id) }
        if (keys.toSet().size != keys.size) return reject(op, RejectReason.InvalidEnvelope, "entidade repetida na operação")
        for (change in op.changes) {
            if (change.type !in SharedTypes.all) return reject(op, RejectReason.TypeNotAllowed, change.type)
            if (change.createIfAbsent && change.type != ALERT_EVENT) return reject(op, RejectReason.InvalidEnvelope, "createIfAbsent só vale para evento de aviso")
            val base = change.baseVersion
            if (base != null && base < 1) return reject(op, RejectReason.InvalidEnvelope)
            val owner = entityOwner[EntityKey(change.type, change.id)]
            if (owner != null && owner != space.id) return reject(op, RejectReason.ForeignSpace)
            for (ref in change.references) {
                val refOwner = entityOwner[ref]
                if (refOwner != null && refOwner != space.id) return reject(op, RejectReason.ForeignSpace)
            }
        }
        // Evento de aviso (ADR 0044): imutável. Já existente, só `createIfAbsent` é aceito e devolve o canônico, sem conflito nem nova versão.
        val absorbed = mutableListOf<RemoteEntityState>()
        for (change in op.changes.filter { it.type == ALERT_EVENT }) {
            val current = space.entities[EntityKey(change.type, change.id)]
            if (change.tombstone) return reject(op, RejectReason.AlertEventImmutable, "evento de aviso não se remove")
            if (current != null) {
                if (!change.createIfAbsent) return reject(op, RejectReason.AlertEventImmutable, "evento de aviso não se altera")
                absorbed += current
            } else if (change.baseVersion != null) {
                return reject(op, RejectReason.InvalidEnvelope, "versão base sem entidade")
            }
        }
        val absorbedKeys = absorbed.map { EntityKey(it.type, it.id) }.toSet()
        val writes = op.changes.filter { EntityKey(it.type, it.id) !in absorbedKeys }
        val divergent = mutableListOf<RemoteEntityState>()
        for (change in writes.filter { it.type != ALERT_EVENT }) {
            val current = space.entities[EntityKey(change.type, change.id)]
            val base = change.baseVersion
            when {
                base == null && current != null -> divergent += current
                base != null && current == null -> return reject(op, RejectReason.InvalidEnvelope, "versão base sem entidade")
                base != null && current != null && current.version != base -> divergent += current
            }
        }
        if (divergent.isNotEmpty()) {
            val unchanged = writes.mapNotNull { space.entities[EntityKey(it.type, it.id)] }.filter { it !in divergent }
            return OperationResult.Conflict(op.operationId, divergent, unchanged)
        }

        conversionRule(op, writes, space)?.let { return it }
        categoryRule(op, space)?.let { return it }

        val now = clock()
        val versions = writes.map { change ->
            val key = EntityKey(change.type, change.id)
            val version = (space.entities[key]?.version ?: 0) + 1
            space.cursor += 1
            space.entities[key] = RemoteEntityState(
                type = change.type, id = change.id, version = version, state = preserveOrigin(change, space.entities[key]),
                tombstone = change.tombstone, authorId = account.id, serviceTimeMillis = now, cursor = space.cursor,
            )
            entityOwner[key] = space.id
            AppliedVersion(change.type, change.id, version)
        }
        val result = OperationResult.Applied(op.operationId, versions, space.cursor, repeated = false, absorbed = absorbed)
        applied[op.operationId] = AppliedRecord(space.id, op.changes, result)
        return result
    }

    /** Compromissos raiz que podem nascer de uma intenção (ADR 0048). */
    private val originTypes = setOf("Movement", "CardPurchase", "Boleto", "Recurrence")

    private fun originOf(state: String): String? =
        ((parse(state) as? kotlinx.serialization.json.JsonObject)?.get("originIntentionId") as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    /**
     * Vínculo de conversão (ADR 0048), igual ao SQL: o compromisso novo com origem exige a intenção no espaço (OriginNotFound) e o serviço
     * aceita um só por intenção; o segundo volta como conflito com o compromisso já reconhecido, sem aplicar nada.
     */
    private fun conversionRule(op: OperationEnvelope, writes: List<EntityChangeEnvelope>, space: Space): OperationResult? {
        for (change in writes.filter { it.type in originTypes && !it.tombstone }) {
            if (space.entities.containsKey(EntityKey(change.type, change.id))) continue // a origem de quem já existe nunca muda
            val origin = originOf(change.state) ?: continue
            if (space.entities[EntityKey("Intention", origin)] == null) return reject(op, RejectReason.OriginNotFound, origin)
            val other = space.entities.values.firstOrNull { it.type in originTypes && originOf(it.state) == origin }
            if (other != null) return OperationResult.Conflict(op.operationId, listOf(other), emptyList())
        }
        return null
    }

    /** Atualização de compromisso que omite ou troca a origem não a apaga nem a muda: vale a do estado guardado. */
    private fun preserveOrigin(change: EntityChangeEnvelope, current: RemoteEntityState?): String {
        if (change.type !in originTypes || current == null) return change.state
        val incoming = parse(change.state) as? kotlinx.serialization.json.JsonObject ?: return change.state
        val kept = originOf(current.state)
        val fields = incoming.filterKeys { it != "originIntentionId" } + (if (kept != null) mapOf("originIntentionId" to kotlinx.serialization.json.JsonPrimitive(kept)) else emptyMap())
        return kotlinx.serialization.json.JsonObject(fields).toString()
    }

    /** Tipos cujo estado aponta para categoria de despesa por `classificationId` (inclusive em trechos de recorrência). */
    private val classified = setOf("Movement", "CardPurchase", "Boleto", "Recurrence")

    private companion object {
        const val ALERT_EVENT = "AlertEvent"
    }

    private val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun classificationsIn(element: kotlinx.serialization.json.JsonElement): Set<String> = buildSet {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> for ((key, value) in element) {
                val text = (value as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                if (key == "classificationId" && text != null) add(text) else addAll(classificationsIn(value))
            }
            is kotlinx.serialization.json.JsonArray -> element.forEach { addAll(classificationsIn(it)) }
            else -> Unit
        }
    }

    private fun parse(state: String) = runCatching { lenient.parseToJsonElement(state) }.getOrNull()

    private fun isRemovedCategory(state: String): Boolean =
        ((parse(state) as? kotlinx.serialization.json.JsonObject)?.get("removed") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"

    /**
     * Categoria removida (ADR 0039), igual ao SQL: remover exige que nenhum lançamento vivo ainda aponte para a categoria
     * (CategoryInUse) e lançamento novo não pode apontar para categoria já removida (CategoryRemoved).
     */
    private fun categoryRule(op: OperationEnvelope, space: Space): OperationResult? {
        val inOp = op.changes.map { EntityKey(it.type, it.id) }.toSet()
        for (change in op.changes.filter { it.type == "Category" && isRemovedCategory(it.state) }) {
            val storedUse = space.entities.values.any { e ->
                e.type in classified && !e.tombstone && EntityKey(e.type, e.id) !in inOp &&
                    parse(e.state)?.let { change.id in classificationsIn(it) } == true
            }
            val opUse = op.changes.any { c ->
                c.type in classified && !c.tombstone && parse(c.state)?.let { change.id in classificationsIn(it) } == true
            }
            if (storedUse || opUse) return reject(op, RejectReason.CategoryInUse, change.id)
        }
        for (change in op.changes.filter { it.type in classified && !it.tombstone }) {
            val used = parse(change.state)?.let(::classificationsIn).orEmpty()
            val removed = used.any { id ->
                space.entities[EntityKey("Category", id)]?.let { isRemovedCategory(it.state) } == true
            }
            if (removed) return reject(op, RejectReason.CategoryRemoved, change.id)
        }
        return null
    }

    override suspend fun pull(accessToken: String, spaceId: String, afterCursor: Long, limit: Int): RemoteResult<PullPage> = call {
        val account = authenticate(accessToken) ?: return@call fail(RemoteFailure.SessionExpired)
        val space = memberSpace(account, spaceId) ?: return@call fail(RemoteFailure.NotMember)
        val pending = space.entities.values.filter { it.cursor > afterCursor }.sortedBy { it.cursor }
        val page = pending.take(limit.coerceAtLeast(1))
        RemoteResult.Ok(PullPage(page, page.lastOrNull()?.cursor ?: afterCursor, hasMore = pending.size > page.size))
    }
}
