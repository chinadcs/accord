package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.space.ItemSync
import app.accord.core.space.SyncStatusProvider
import app.accord.core.sync.ConflictEntity
import app.accord.core.sync.ConflictVersion
import app.accord.core.sync.ConflictView
import app.accord.core.sync.EntityChangeEnvelope
import app.accord.core.sync.EntityKey
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.PROTOCOL_VERSION
import app.accord.core.sync.ProblemKind
import app.accord.core.sync.ProblemOperation
import app.accord.core.sync.PullPage
import app.accord.core.sync.ReceiveSummary
import app.accord.core.sync.RemoteEntityState
import app.accord.core.sync.ResolveResult
import app.accord.core.sync.SyncSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** Situação de cada item para recibo e detalhe, derivada de fila, conflitos e reconhecimentos. */
class RoomSyncStatus(private val dao: SyncDao) : SyncStatusProvider {
    override suspend fun statusOf(type: EntityType, id: String): ItemSync = when {
        dao.inUnresolvedConflict(type.name, id) -> ItemSync.Conflict
        dao.hasOperationIn(type.name, id, OpState.REJECTED) -> ItemSync.Failed
        dao.hasOperationIn(type.name, id, OpState.PENDING) || dao.hasOperationIn(type.name, id, OpState.HELD) -> ItemSync.AwaitingSend
        dao.isKnownToService(type.name, id) -> ItemSync.Acknowledged
        else -> ItemSync.SavedOnDevice
    }
}

/**
 * Lado do aparelho do protocolo de sincronização (S2): fila de operações (padrão outbox, gravada
 * pela fachada junto com a alteração), resultados do envio, aplicação do que chegou do serviço e
 * conflitos. Não conhece rede nem regras financeiras; o motor de sincronização conduz o ciclo.
 *
 * Toda mudança de estado é uma transação local. Uma morte do processo entre "o serviço aplicou" e
 * "o aparelho registrou" se resolve pelo reenvio idempotente do mesmo ID de operação.
 */
class SyncStore(
    private val db: AccordDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.dao()
    private val sync = db.syncDao()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    private fun key(type: String, id: String) = "$type/$id"

    // ---- estado do aparelho ----

    private suspend fun stateRow(): SyncStateRow = sync.syncState() ?: SyncStateRow(
        cursor = 0, lastSyncMillis = null, installationId = newId(),
    ).also { sync.upsertSyncState(it) }

    suspend fun installationId(): String = transaction { stateRow().installationId }

    suspend fun cursor(): Long = sync.syncState()?.cursor ?: 0

    /** Um ciclo terminou com todas as operações enviáveis resolvidas e o recebimento no cursor mais recente. */
    suspend fun markCycleComplete() = transaction { sync.upsertSyncState(stateRow().copy(lastSyncMillis = nowMillis())) }

    suspend fun summary(): SyncSummary {
        val state = sync.syncState()
        return SyncSummary(
            awaitingOperations = sync.countIn(listOf(OpState.PENDING, OpState.HELD)),
            openConflicts = sync.unresolvedConflicts().size,
            rejectedOperations = sync.countIn(listOf(OpState.REJECTED)),
            lastSyncMillis = state?.lastSyncMillis,
            cursor = state?.cursor ?: 0,
        )
    }

    // ---- envio ----

    /**
     * Operações prontas para enviar, em ordem. Uma operação encadeada a outra ainda não
     * reconhecida só sai depois dela; as retidas por conflito ou recusa não saem.
     */
    suspend fun nextBatch(limit: Int): List<OperationEnvelope> = transaction {
        recomputeHolds()
        val installation = stateRow().installationId
        val open = sync.operationsIn(OpState.OPEN)
        val changes = sync.changesOfAll(open.map { it.operationId }).groupBy { it.operationId }
        val earlier = mutableSetOf<String>()
        val batch = mutableListOf<OperationEnvelope>()
        for (op in open) {
            val rows = changes[op.operationId].orEmpty()
            val keys = rows.map { key(it.entityType, it.entityId) }
            if (op.syncState == OpState.PENDING && batch.size < limit && keys.none { it in earlier }) {
                batch += OperationEnvelope(
                    operationId = op.operationId,
                    spaceId = op.spaceId,
                    protocolVersion = PROTOCOL_VERSION,
                    installationId = installation,
                    localTimeMillis = op.deviceTimeMillis,
                    changes = rows.map {
                        EntityChangeEnvelope(
                            type = it.entityType, id = it.entityId, baseVersion = it.baseVersion, state = it.state,
                            tombstone = it.tombstone, references = EntityStates.decodeReferences(it.refs),
                        )
                    },
                )
            }
            earlier += keys
        }
        batch
    }

    /** Falha do lote inteiro (sem rede, sessão, serviço): a fila fica como está, com a causa registrada. */
    suspend fun recordBatchFailure(operationIds: List<String>, cause: String) = transaction {
        if (operationIds.isNotEmpty()) sync.recordAttempt(operationIds, cause)
    }

    /** Processa os resultados do envio. Repetições de operações já resolvidas são ignoradas. */
    suspend fun recordResults(results: List<OperationResult>) = transaction {
        for (result in results) {
            val op = sync.operation(result.operationId) ?: continue
            if (op.syncState == OpState.ACKNOWLEDGED || op.syncState == OpState.SUPERSEDED) continue
            when (result) {
                is OperationResult.Applied -> acknowledge(op, result)
                is OperationResult.Conflict -> recordConflict(op, result.current + result.unchanged)
                is OperationResult.Rejected -> {
                    val cause = listOfNotNull(result.reason.name, result.detail).joinToString(": ")
                    sync.setOperationState(op.operationId, OpState.REJECTED, false, cause)
                }
            }
        }
        recomputeHolds()
    }

    private suspend fun acknowledge(op: OperationRow, result: OperationResult.Applied) {
        sync.setOperationState(op.operationId, OpState.ACKNOWLEDGED, true, null)
        dao.upsertSync(result.versions.map { EntitySyncRow(it.type, it.id, it.version) })
        sync.conflict(op.operationId)?.let {
            if (it.status != ConflictStatus.RESOLVED) sync.upsertConflict(it.copy(status = ConflictStatus.RESOLVED, choice = "ACKNOWLEDGED"))
        }
    }

    private suspend fun recordConflict(op: OperationRow, remotes: List<RemoteEntityState>) {
        val existing = sync.conflict(op.operationId)
        sync.upsertConflict(
            ConflictRow(
                operationId = op.operationId,
                status = existing?.status?.takeIf { it != ConflictStatus.RESOLVED } ?: ConflictStatus.OPEN,
                createdMillis = existing?.createdMillis ?: nowMillis(),
            ),
        )
        for (change in sync.changesOf(op.operationId)) {
            val remote = remotes.firstOrNull { it.type == change.entityType && it.id == change.entityId }
            sync.upsertConflictEntity(
                ConflictEntityRow(
                    operationId = op.operationId, entityType = change.entityType, entityId = change.entityId,
                    localState = change.state, localTombstone = change.tombstone,
                    remoteState = remote?.state, remoteTombstone = remote?.tombstone ?: false,
                    remoteVersion = remote?.version, remoteAuthorId = remote?.authorId, remoteServiceMillis = remote?.serviceTimeMillis,
                ),
            )
        }
        sync.setOperationState(op.operationId, OpState.CONFLICT, false, null)
    }

    /**
     * Reavalia retenção e encadeamento em ordem de sequência: uma operação que depende de outra em
     * conflito ou recusada fica retida; as demais seguem pendentes; versões base de operações
     * encadeadas são fixadas quando já não há operação anterior aberta sobre a entidade.
     */
    private suspend fun recomputeHolds() {
        val open = sync.operationsIn(OpState.OPEN)
        if (open.isEmpty()) return
        val changes = sync.changesOfAll(open.map { it.operationId }).groupBy { it.operationId }
        val blocking = mutableSetOf<String>()
        val earlier = mutableSetOf<String>()
        for (op in open) {
            val rows = changes[op.operationId].orEmpty()
            val keys = rows.map { key(it.entityType, it.entityId) }
            rows.filter { it.chained && key(it.entityType, it.entityId) !in earlier }.forEach {
                sync.resolveBase(op.operationId, it.entityType, it.entityId, dao.serviceVersion(it.entityType, it.entityId))
            }
            when (op.syncState) {
                OpState.CONFLICT, OpState.REJECTED -> blocking += keys
                else -> {
                    val depends = (keys + rows.flatMap { EntityStates.decodeReferences(it.refs).map { r -> key(r.type, r.id) } })
                        .any { it in blocking }
                    val next = if (depends) OpState.HELD else OpState.PENDING
                    if (next != op.syncState) sync.setOperationState(op.operationId, next, false, op.lastFailure)
                    if (depends) blocking += keys
                }
            }
            earlier += keys
        }
    }

    // ---- recebimento ----

    /**
     * Aplica uma página recebida numa transação. Entidade sem operação local aberta passa a ter o
     * estado recebido; com operação aberta, a diferença de versão vira conflito e o estado local
     * permanece visível como "versão local pendente". Nenhuma operação de saída é gerada.
     */
    suspend fun applyPage(page: PullPage): ReceiveSummary = transaction {
        val applied = mutableListOf<EntityRef>()
        var conflicts = 0
        for (remote in page.changes.sortedBy { it.cursor }) {
            val type = EntityType.entries.firstOrNull { it.name == remote.type } ?: continue
            val known = dao.serviceVersion(remote.type, remote.id) ?: 0
            if (remote.version <= known) continue
            val open = sync.openOperationsOn(remote.type, remote.id)
            if (open.isEmpty()) {
                EntityStates.apply(dao, type, remote.id, remote.state)
                dao.upsertSync(listOf(EntitySyncRow(remote.type, remote.id, remote.version)))
                applied += EntityRef(type, remote.id)
            } else {
                val op = open.first()
                val existing = sync.conflict(op.operationId)
                if (existing == null || existing.status == ConflictStatus.RESOLVED) {
                    recordConflict(op, listOf(remote))
                    conflicts++
                } else {
                    // A comparação em andamento passa a mostrar a versão mais nova.
                    val row = sync.conflictEntities(op.operationId).firstOrNull { it.entityType == remote.type && it.entityId == remote.id }
                    val change = sync.changesOf(op.operationId).firstOrNull { it.entityType == remote.type && it.entityId == remote.id }
                    val base = row ?: change?.let {
                        ConflictEntityRow(
                            op.operationId, remote.type, remote.id, it.state, it.tombstone,
                            remoteState = null, remoteTombstone = false, remoteVersion = null, remoteAuthorId = null, remoteServiceMillis = null,
                        )
                    }
                    if (base != null && (base.remoteVersion ?: 0) < remote.version) {
                        sync.upsertConflictEntity(
                            base.copy(
                                remoteState = remote.state, remoteTombstone = remote.tombstone, remoteVersion = remote.version,
                                remoteAuthorId = remote.authorId, remoteServiceMillis = remote.serviceTimeMillis,
                            ),
                        )
                    }
                }
            }
        }
        if (page.nextCursor > stateRow().cursor) sync.upsertSyncState(stateRow().copy(cursor = page.nextCursor))
        recomputeHolds()
        ReceiveSummary(applied, conflicts)
    }

    // ---- conflitos ----

    suspend fun conflicts(): List<ConflictView> = sync.unresolvedConflicts().map { view(it) }

    suspend fun conflict(operationId: String): ConflictView? =
        sync.conflict(operationId)?.takeIf { it.status != ConflictStatus.RESOLVED }?.let { view(it) }

    private suspend fun view(conflict: ConflictRow): ConflictView {
        val op = checkNotNull(sync.operation(conflict.operationId))
        val entities = sync.conflictEntities(conflict.operationId).map { e ->
            val local = ConflictVersion(e.localState, e.localTombstone, null, op.authorId, op.deviceTimeMillis)
            val remote = e.remoteState?.let {
                ConflictVersion(it, e.remoteTombstone, e.remoteVersion, e.remoteAuthorId, e.remoteServiceMillis)
            }
            ConflictEntity(
                ref = EntityRef(EntityType.valueOf(e.entityType), e.entityId),
                local = local,
                remote = remote,
                differingFields = if (remote == null) emptyList() else differingFields(e.localState, remote.state),
            )
        }
        val closure = closureOf(op).drop(1).map { it.operationId }
        return ConflictView(
            operationId = op.operationId, commandType = op.commandType, deferred = conflict.status == ConflictStatus.DEFERRED,
            createdMillis = conflict.createdMillis, entities = entities, heldOperationIds = closure,
        )
    }

    private fun differingFields(local: String, remote: String): List<String> {
        val a = runCatching { json.parseToJsonElement(local).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        val b = runCatching { json.parseToJsonElement(remote).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        return (a.keys + b.keys).filter { a[it] != b[it] }.sorted()
    }

    /** A operação e as abertas depois dela que alteram, direta ou indiretamente, as mesmas entidades. */
    private suspend fun closureOf(op: OperationRow): List<OperationRow> {
        val later = sync.operationsIn(OpState.OPEN).filter { it.sequence > op.sequence }
        val changes = sync.changesOfAll((later + op).map { it.operationId }).groupBy { it.operationId }
        fun keysOf(o: OperationRow) = changes[o.operationId].orEmpty().map { key(it.entityType, it.entityId) }
        val keys = keysOf(op).toMutableSet()
        val result = mutableListOf(op)
        for (candidate in later) {
            val candidateKeys = keysOf(candidate)
            if (candidateKeys.any { it in keys }) {
                result += candidate
                keys += candidateKeys
            }
        }
        return result
    }

    /** "Revisar depois": as duas versões continuam guardadas e o conflito segue aberto. */
    suspend fun defer(operationId: String) = transaction {
        val conflict = sync.conflict(operationId) ?: return@transaction
        if (conflict.status == ConflictStatus.OPEN) sync.upsertConflict(conflict.copy(status = ConflictStatus.DEFERRED))
    }

    /**
     * Mantém a edição da outra pessoa. A operação local e as encadeadas a ela passam a substituídas;
     * o que elas alteravam em outras entidades e não foi escolhido segue como nova operação.
     */
    suspend fun resolveWithRemote(operationId: String, seen: Map<EntityRef, Long>): ResolveResult = transaction {
        val (conflict, op, rows) = loadForResolution(operationId) ?: return@transaction ResolveResult.NotFound
        if (isStale(rows, seen)) return@transaction ResolveResult.Stale(view(conflict))
        val closure = closureOf(op)
        val adopted = mutableSetOf<String>()
        for (row in rows) {
            val state = row.remoteState ?: continue
            EntityStates.apply(dao, EntityType.valueOf(row.entityType), row.entityId, state)
            dao.upsertSync(listOf(EntitySyncRow(row.entityType, row.entityId, checkNotNull(row.remoteVersion))))
            adopted += key(row.entityType, row.entityId)
        }
        val keys = changedKeys(closure)
        closure.forEach { sync.setOperationState(it.operationId, OpState.SUPERSEDED, false, null) }
        sync.upsertConflict(conflict.copy(status = ConflictStatus.RESOLVED, choice = "REMOTE"))
        val leftovers = keys.filter { key(it.type, it.id) !in adopted }
        val created = if (leftovers.isEmpty()) null else createOperation(op, closure, leftovers)
        recomputeHolds()
        ResolveResult.Resolved(created)
    }

    /**
     * Mantém a edição deste aparelho: uma nova operação, com novo ID, leva o estado local completo
     * com base na versão remota vista. Se o serviço tiver algo mais novo, volta a ser conflito.
     */
    suspend fun resolveWithLocal(operationId: String, seen: Map<EntityRef, Long>): ResolveResult = transaction {
        val (conflict, op, rows) = loadForResolution(operationId) ?: return@transaction ResolveResult.NotFound
        if (isStale(rows, seen)) return@transaction ResolveResult.Stale(view(conflict))
        val closure = closureOf(op)
        for (row in rows) {
            row.remoteVersion?.let { dao.upsertSync(listOf(EntitySyncRow(row.entityType, row.entityId, it))) }
        }
        val keys = changedKeys(closure)
        closure.forEach { sync.setOperationState(it.operationId, OpState.SUPERSEDED, false, null) }
        sync.upsertConflict(conflict.copy(status = ConflictStatus.RESOLVED, choice = "LOCAL"))
        val created = createOperation(op, closure, keys)
        recomputeHolds()
        ResolveResult.Resolved(created)
    }

    private suspend fun loadForResolution(operationId: String): Triple<ConflictRow, OperationRow, List<ConflictEntityRow>>? {
        val conflict = sync.conflict(operationId)?.takeIf { it.status != ConflictStatus.RESOLVED } ?: return null
        val op = sync.operation(operationId) ?: return null
        return Triple(conflict, op, sync.conflictEntities(operationId))
    }

    private fun isStale(rows: List<ConflictEntityRow>, seen: Map<EntityRef, Long>) = rows.any {
        it.remoteVersion != null && seen[EntityRef(EntityType.valueOf(it.entityType), it.entityId)] != it.remoteVersion
    }

    private suspend fun changedKeys(closure: List<OperationRow>): List<EntityKey> =
        sync.changesOfAll(closure.map { it.operationId }).map { EntityKey(it.entityType, it.entityId) }.distinct()

    /** Nova operação com o estado local atual das entidades; a ordem de sequência a coloca no fim da fila. */
    private suspend fun createOperation(origin: OperationRow, closure: List<OperationRow>, keys: List<EntityKey>): String {
        val id = newId()
        val months = closure.flatMap { it.affectedMonths.split(",").filter(String::isNotEmpty) }.toSortedSet().joinToString(",")
        sync.insertOperation(
            OperationRow(
                operationId = id, spaceId = origin.spaceId, authorId = origin.authorId, origin = origin.origin,
                commandType = "ResolveConflict", deviceTimeMillis = nowMillis(), affectedMonths = months,
                undoable = false, acknowledged = false,
            ),
        )
        val rows = keys.mapNotNull { k ->
            val type = EntityType.valueOf(k.type)
            val state = EntityStates.read(dao, type, k.id) ?: return@mapNotNull null
            OperationChangeRow(
                operationId = id, entityType = k.type, entityId = k.id,
                baseVersion = dao.serviceVersion(k.type, k.id), revision = EntityStates.revisionOf(dao, type, k.id),
                state = state.state, tombstone = state.tombstone, refs = EntityStates.encodeReferences(state.references),
            )
        }
        sync.insertChanges(rows)
        return id
    }

    // ---- diagnóstico ----

    /**
     * Relatório mínimo para quem precisa entender uma falha: IDs de operação, tipo de comando e de
     * entidade, estado, categoria do erro, tentativas e versões. Nunca inclui e-mail, senha, token,
     * valores, descrições, estabelecimentos, texto do Chat nem conteúdo bancário (S2 "Diagnóstico").
     */
    suspend fun diagnostics(limit: Int = 50): String {
        val state = sync.syncState()
        val summary = summary()
        val recent = sync.recentOperations(limit)
        val changes = sync.changesOfAll(recent.map { it.operationId }).groupBy { it.operationId }
        val unresolved = sync.unresolvedConflicts()
        return buildString {
            appendLine("accord-diagnostico v1")
            appendLine("instalacao=${state?.installationId ?: "-"}")
            appendLine("cursor=${summary.cursor} ultima-sincronizacao=${summary.lastSyncMillis ?: "-"}")
            appendLine("aguardando=${summary.awaitingOperations} conflitos=${summary.openConflicts} recusadas=${summary.rejectedOperations}")
            for (op in recent) {
                val touched = changes[op.operationId].orEmpty().joinToString(",") { "${it.entityType}/base:${it.baseVersion ?: "-"}" }
                appendLine(
                    "op id=${op.operationId} estado=${op.syncState} comando=${op.commandType} tentativas=${op.attempts} " +
                        "falha=${op.lastFailure?.substringBefore(':') ?: "-"} alteracoes=$touched",
                )
            }
            for (conflict in unresolved) {
                val rows = sync.conflictEntities(conflict.operationId)
                val entities = rows.joinToString(",") { "${it.entityType}(remota v${it.remoteVersion ?: "-"})" }
                appendLine("conflito op=${conflict.operationId} situacao=${conflict.status} entidades=$entities")
            }
        }
    }

    // ---- operações com problema ----

    suspend fun problems(): List<ProblemOperation> {
        val ops = sync.operationsIn(listOf(OpState.CONFLICT, OpState.REJECTED, OpState.HELD))
        val changes = sync.changesOfAll(ops.map { it.operationId }).groupBy { it.operationId }
        return ops.map { op ->
            ProblemOperation(
                operationId = op.operationId,
                kind = when (op.syncState) {
                    OpState.CONFLICT -> ProblemKind.Conflict
                    OpState.REJECTED -> ProblemKind.Rejected
                    else -> ProblemKind.Held
                },
                commandType = op.commandType,
                failure = op.lastFailure,
                entities = changes[op.operationId].orEmpty().map { EntityRef(EntityType.valueOf(it.entityType), it.entityId) },
            )
        }
    }

    /** Tenta de novo uma operação recusada (por exemplo, depois de atualizar o aplicativo). */
    suspend fun retryRejected(operationId: String) = transaction {
        val op = sync.operation(operationId)
        if (op?.syncState == OpState.REJECTED) {
            sync.setOperationState(operationId, OpState.PENDING, false, null)
            recomputeHolds()
        }
    }

    /** Descarte explícito, pedido pela pessoa, de uma operação recusada; nunca acontece sozinho. */
    suspend fun discardRejected(operationId: String) = transaction {
        val op = sync.operation(operationId)
        if (op?.syncState == OpState.REJECTED) {
            sync.setOperationState(operationId, OpState.SUPERSEDED, false, op.lastFailure)
            recomputeHolds()
        }
    }
}
