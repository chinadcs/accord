package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.alerts.AlertDisplay
import app.accord.core.alerts.EventSource
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
import app.accord.core.sync.RejectReason
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
            awaitingCategoryOperations = sync.countWithChangesOf(listOf(OpState.PENDING, OpState.HELD), listOf(EntityType.Category.name, EntityType.IncomeCategory.name)),
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
                            // Evento de aviso (ADR 0044): se o outro aparelho chegou primeiro, o serviço devolve o canônico em vez de conflito.
                            createIfAbsent = it.entityType == EntityType.AlertEvent.name && it.baseVersion == null,
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
        // O serviço já tinha o evento: o canônico substitui o estado local, e a exibição já registrada para a chave continua valendo.
        result.absorbed.forEach { adoptAlertEvent(it, EventSource.Received) }
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
            // ADR 0048: outro compromisso, de identidade diferente, já converteu a mesma intenção.
            val counterpart = if (remote == null) divergentCounterpart(change, remotes) else null
            val shown = remote ?: counterpart
            sync.upsertConflictEntity(
                ConflictEntityRow(
                    operationId = op.operationId, entityType = change.entityType, entityId = change.entityId,
                    localState = change.state, localTombstone = change.tombstone,
                    remoteState = shown?.state, remoteTombstone = shown?.tombstone ?: false,
                    remoteVersion = shown?.version, remoteAuthorId = shown?.authorId, remoteServiceMillis = shown?.serviceTimeMillis,
                    counterpartType = counterpart?.type, counterpartId = counterpart?.id,
                ),
            )
            if (counterpart != null) holdDivergentConversion(change, counterpart)
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
     * Adota o estado canônico de um evento de aviso e garante a situação de exibição deste aparelho: a que já existe para a chave é mantida (um aparelho que
     * já exibiu não exibe de novo); sem ela, nasce da origem do conhecimento (S5, [AlertDisplay.onKnown]).
     */
    // ---- divergência de conversão (ADR 0048) ----

    private val rootTypes = setOf("Movement", "CardPurchase", "Boleto", "Recurrence")

    private fun originOf(state: String): String? = runCatching {
        (json.parseToJsonElement(state).jsonObject["originIntentionId"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
    }.getOrNull()

    /** O compromisso já reconhecido pelo serviço para a mesma intenção, de identidade diferente da deste aparelho. */
    private fun divergentCounterpart(change: OperationChangeRow, remotes: List<RemoteEntityState>): RemoteEntityState? {
        if (change.entityType !in rootTypes || change.tombstone) return null
        val origin = originOf(change.state) ?: return null
        return remotes.firstOrNull { it.type in rootTypes && it.id != change.entityId && originOf(it.state) == origin }
    }

    private suspend fun refreshDivergence(remote: RemoteEntityState) {
        for (conflict in sync.unresolvedConflicts()) {
            for (row in sync.conflictEntities(conflict.operationId)) {
                if (row.counterpartType == remote.type && row.counterpartId == remote.id && (row.remoteVersion ?: 0) < remote.version) {
                    sync.upsertConflictEntity(
                        row.copy(
                            remoteState = remote.state, remoteTombstone = remote.tombstone, remoteVersion = remote.version,
                            remoteAuthorId = remote.authorId, remoteServiceMillis = remote.serviceTimeMillis,
                        ),
                    )
                }
            }
        }
    }

    /** Operação ainda aberta deste aparelho que converte a mesma intenção com outra identidade, se houver. */
    private suspend fun divergentLocalOperation(remote: RemoteEntityState): OperationRow? {
        if (remote.type !in rootTypes) return null
        val origin = originOf(remote.state) ?: return null
        return sync.operationsIn(OpState.OPEN).firstOrNull { op ->
            op.syncState != OpState.CONFLICT && sync.changesOf(op.operationId).any {
                it.entityType in rootTypes && it.entityId != remote.id && !it.tombstone && originOf(it.state) == origin
            }
        }
    }

    /**
     * Enquanto a divergência está aberta o orçamento conta uma única compra: a já reconhecida entra aqui e a deste aparelho fica oculta
     * (excluída só localmente, sem operação), com o estado guardado no conflito para a escolha. A data não decide qual vale.
     */
    private suspend fun holdDivergentConversion(change: OperationChangeRow, counterpart: RemoteEntityState) {
        EntityStates.apply(dao, EntityType.valueOf(counterpart.type), counterpart.id, counterpart.state)
        dao.upsertSync(listOf(EntitySyncRow(counterpart.type, counterpart.id, counterpart.version)))
        val type = EntityType.valueOf(change.entityType)
        val current = EntityStates.read(dao, type, change.entityId) ?: return
        val hidden = kotlinx.serialization.json.JsonObject(
            json.parseToJsonElement(current.state).jsonObject.filterKeys { it != "originIntentionId" } +
                ("voided" to kotlinx.serialization.json.JsonPrimitive(true)),
        )
        EntityStates.apply(dao, type, change.entityId, hidden.toString())
    }

    private suspend fun adoptAlertEvent(remote: RemoteEntityState, source: EventSource) {
        EntityStates.apply(dao, EntityType.AlertEvent, remote.id, remote.state)
        dao.upsertSync(listOf(EntitySyncRow(remote.type, remote.id, remote.version)))
        val event = dao.alertEvent(remote.id) ?: return
        val existing = dao.alertDisplay(remote.id)
        if (existing != null) return
        val now = nowMillis()
        val state = AlertDisplay.onKnown(java.time.LocalDate.parse(event.windowMonday), source, java.time.Instant.ofEpochMilli(now), null)
        dao.upsertAlertDisplay(AlertDisplayRow(remote.id, state.name, now))
    }

    /**
     * Aplica uma página recebida numa transação. Entidade sem operação local aberta passa a ter o
     * estado recebido; com operação aberta, a diferença de versão vira conflito e o estado local
     * permanece visível como "versão local pendente". Nenhuma operação de saída é gerada.
     */
    suspend fun applyPage(page: PullPage): ReceiveSummary = transaction {
        val applied = mutableListOf<EntityRef>()
        var conflicts = 0
        // Antes do primeiro ciclo completo, o que já existe no espaço é "já conhecido" (S5): não notifica.
        val firstCycle = sync.syncState()?.lastSyncMillis == null
        for (remote in page.changes.sortedBy { it.cursor }) {
            val type = EntityType.entries.firstOrNull { it.name == remote.type } ?: continue
            val known = dao.serviceVersion(remote.type, remote.id) ?: 0
            if (remote.version <= known) continue
            val open = sync.openOperationsOn(remote.type, remote.id)
            if (type == EntityType.AlertEvent) {
                // Evento de aviso é imutável e criado por chave: nunca vira conflito. A operação local ainda aberta (mesma chave, mesmo ID) é substituída.
                open.forEach { sync.setOperationState(it.operationId, OpState.SUPERSEDED, true, null) }
                adoptAlertEvent(remote, if (firstCycle) EventSource.FirstSync else EventSource.Received)
                applied += EntityRef(type, remote.id)
            } else if (open.isEmpty()) {
                EntityStates.apply(dao, type, remote.id, remote.state)
                dao.upsertSync(listOf(EntitySyncRow(remote.type, remote.id, remote.version)))
                applied += EntityRef(type, remote.id)
                // ADR 0048: uma divergência em revisão passa a mostrar a versão mais nova do já reconhecido (nova edição exige nova comparação).
                refreshDivergence(remote)
                // ADR 0048: chegou o compromisso do outro aparelho para uma intenção que este aparelho também converteu e ainda não enviou.
                divergentLocalOperation(remote)?.let { local ->
                    recordConflict(local, listOf(remote))
                    conflicts++
                }
            } else {
                val op = open.first()
                val existing = sync.conflict(op.operationId)
                if (existing == null || existing.status == ConflictStatus.RESOLVED) {
                    recordConflict(op, listOf(remote))
                    conflicts++
                } else {
                    // A comparação em andamento passa a mostrar a versão mais nova.
                    val row = sync.conflictEntities(op.operationId).firstOrNull {
                        (it.entityType == remote.type && it.entityId == remote.id) || (it.counterpartType == remote.type && it.counterpartId == remote.id)
                    }
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
        repairRemovedCategories()
        recomputeHolds()
        ReceiveSummary(applied, conflicts)
    }

    /**
     * ADR 0039: um celular removeu uma categoria enquanto o outro ainda tinha lançamento nela. O lançamento nunca se
     * perde nem vai para outra categoria sozinho: fica salvo sem categoria ("a classificar" em Revisar), e a operação que
     * o leva ao serviço é reescrita com o estado novo.
     *  - Lançamento novo ainda não reconhecido numa categoria removida pelo outro celular: a operação dele é reescrita.
     *  - Remoção daqui recusada porque o outro celular acrescentou lançamentos na categoria: eles entram na própria
     *    operação de remoção, que volta à fila (sem isso a remoção nunca seria aceita).
     * O serviço só recusa o que ainda cita a categoria removida, então nenhuma operação que ele já aplicou é reescrita.
     */
    private suspend fun repairRemovedCategories() {
        val removed = dao.categories().filter { it.removed }.map { it.id }.toSet()
        if (removed.isEmpty()) return
        val retryable = setOf(RejectReason.CategoryRemoved.name, RejectReason.CategoryInUse.name)
        val candidates = sync.operationsIn(OpState.OPEN).filter {
            it.syncState == OpState.PENDING || it.syncState == OpState.HELD ||
                (it.syncState == OpState.REJECTED && it.lastFailure?.substringBefore(':') in retryable)
        }
        val inOpen = mutableSetOf<String>()
        for (op in candidates) {
            var changed = false
            for (row in sync.changesOf(op.operationId)) {
                inOpen += key(row.entityType, row.entityId)
                val type = EntityType.entries.firstOrNull { it.name == row.entityType } ?: continue
                if (!clearRemovedCategory(type, row.entityId, removed)) continue
                rewrite(op.operationId, type, row.entityId)
                changed = true
            }
            // Lançamentos que o outro celular acrescentou numa categoria que esta operação remove.
            for (categoryId in removalsOf(op)) {
                for ((type, id) in strays(categoryId)) {
                    if (key(type.name, id) in inOpen || sync.openOperationsOn(type.name, id).isNotEmpty()) continue
                    clearRemovedCategory(type, id, removed)
                    val snapshot = checkNotNull(EntityStates.read(dao, type, id))
                    sync.insertChanges(
                        listOf(
                            OperationChangeRow(
                                operationId = op.operationId, entityType = type.name, entityId = id,
                                baseVersion = dao.serviceVersion(type.name, id), revision = EntityStates.revisionOf(dao, type, id),
                                state = snapshot.state, tombstone = snapshot.tombstone,
                                refs = EntityStates.encodeReferences(snapshot.references),
                            ),
                        ),
                    )
                    inOpen += key(type.name, id)
                    changed = true
                }
            }
            if (changed && op.syncState == OpState.REJECTED) sync.setOperationState(op.operationId, OpState.PENDING, false, null)
        }
    }

    private suspend fun rewrite(operationId: String, type: EntityType, id: String) {
        val snapshot = checkNotNull(EntityStates.read(dao, type, id))
        sync.rewriteChange(
            operationId, type.name, id, snapshot.state,
            EntityStates.encodeReferences(snapshot.references), EntityStates.revisionOf(dao, type, id),
        )
    }

    /** Categorias que a operação marca como removidas. */
    private suspend fun removalsOf(op: OperationRow): List<String> =
        sync.changesOf(op.operationId).filter { it.entityType == EntityType.Category.name }
            .filter { row -> dao.category(row.entityId)?.removed == true && json.parseToJsonElement(row.state).jsonObject["removed"]?.toString() == "true" }
            .map { it.entityId }

    /** Lançamentos vivos que ainda citam a categoria [categoryId]. */
    private suspend fun strays(categoryId: String): List<Pair<EntityType, String>> {
        val ids = listOf(categoryId)
        return dao.movementsIn(ids).filter { !it.voided }.map { EntityType.Movement to it.id } +
            dao.purchasesIn(ids).filter { !it.voided }.map { EntityType.CardPurchase to it.id } +
            dao.boletosIn(ids).filter { !it.voided }.map { EntityType.Boleto to it.id } +
            (dao.segmentsIn(ids).map { it.recurrenceId } + dao.overridesIn(ids).map { it.recurrenceId }).distinct()
                .filter { dao.recurrence(it)?.voided == false }.map { EntityType.Recurrence to it }
    }

    /** Tira dos lançamentos [removed] como classificação (voltam a "a classificar"); verdadeiro se algo mudou. */
    private suspend fun clearRemovedCategory(type: EntityType, id: String, removed: Set<String>): Boolean {
        fun gone(classification: String?) = classification != null && classification in removed
        return when (type) {
            EntityType.Movement -> dao.movement(id)?.takeIf { !it.voided && gone(it.classificationId) }
                ?.let { dao.upsertMovement(it.copy(classificationId = null, revision = it.revision + 1)); true } ?: false
            EntityType.CardPurchase -> dao.purchase(id)?.takeIf { !it.voided && gone(it.classificationId) }
                ?.let { dao.upsertPurchase(it.copy(classificationId = null, revision = it.revision + 1)); true } ?: false
            EntityType.Boleto -> dao.boleto(id)?.takeIf { !it.voided && gone(it.classificationId) }
                ?.let { dao.upsertBoleto(it.copy(classificationId = null, revision = it.revision + 1)); true } ?: false
            EntityType.Recurrence -> {
                val row = dao.recurrence(id)
                val segments = dao.segments(id)
                val overrides = dao.overrides(id)
                if (row == null || row.voided || (segments.none { gone(it.classificationId) } && overrides.none { gone(it.classificationId) })) {
                    false
                } else {
                    dao.upsertSegments(segments.map { if (gone(it.classificationId)) it.copy(classificationId = null) else it })
                    dao.upsertOverrides(overrides.map { if (gone(it.classificationId)) it.copy(classificationId = null) else it })
                    dao.upsertRecurrence(row.copy(revision = row.revision + 1))
                    true
                }
            }
            else -> false
        }
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
                counterpart = e.counterpartId?.let { EntityRef(EntityType.valueOf(checkNotNull(e.counterpartType)), it) },
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
            val targetType = row.counterpartType ?: row.entityType
            val targetId = row.counterpartId ?: row.entityId
            EntityStates.apply(dao, EntityType.valueOf(targetType), targetId, state)
            dao.upsertSync(listOf(EntitySyncRow(targetType, targetId, checkNotNull(row.remoteVersion))))
            adopted += key(targetType, targetId)
            // O compromisso deste aparelho nunca foi reconhecido e já está oculto: nada dele volta à fila.
            if (row.counterpartId != null) adopted += key(row.entityType, row.entityId)
        }
        val keys = changedKeys(closure)
        // Uma criação que não existe no serviço também não sobra como cofrinho zerado.
        // O snapshot da operação substituída conserva o diagnóstico da tentativa descartada.
        for (k in keys.filter { it.type == EntityType.SavingsPocket.name && dao.serviceVersion(it.type,it.id) == null }) {
            for (move in dao.savingsMoves().filter { it.pocketId == k.id }) {
                if (dao.serviceVersion(EntityType.SavingsMove.name,move.id) == null) {
                    dao.discardUnrecognizedSavingsMove(move.id)
                    adopted += key(EntityType.SavingsMove.name,move.id)
                }
            }
            dao.discardUnrecognizedSavingsPocket(k.id)
            adopted += key(k.type,k.id)
        }
        // Cofrinho é uma unidade atômica: escolher o serviço também descarta os movimentos
        // novos que ele nunca reconheceu. Eles não podem sobrar como transferências avulsas.
        for (k in keys.filter { it.type == EntityType.SavingsMove.name }) {
            if (dao.serviceVersion(k.type,k.id) == null) {
                dao.savingsMove(k.id)?.let { dao.upsertSavingsMove(it.copy(voided = true, revision = it.revision + 1)) }
                adopted += key(k.type,k.id)
            }
        }
        // Valor a receber é uma unidade atômica (ADR 0083): o que o serviço nunca reconheceu sai inteiro, com as rendas e a despesa ligadas,
        // e o devedor novo que sobraria sem nenhum valor a receber também.
        for (k in keys.filter { it.type == EntityType.Receivable.name && dao.serviceVersion(it.type, it.id) == null }) {
            for (income in dao.allIncomesOfReceivable(k.id).filter { dao.serviceVersion(EntityType.Income.name, it.id) == null && !it.voided }) {
                dao.upsertIncome(income.copy(voided = true, revision = income.revision + 1))
                adopted += key(EntityType.Income.name, income.id)
            }
            for (movement in dao.movementsOfReceivable(k.id).filter { dao.serviceVersion(EntityType.Movement.name, it.id) == null && !it.voided }) {
                dao.upsertMovement(movement.copy(voided = true, revision = movement.revision + 1))
                adopted += key(EntityType.Movement.name, movement.id)
            }
            dao.discardUnrecognizedReceivable(k.id)
            adopted += key(k.type, k.id)
        }
        // Um recebimento ("Recebi") é uma unidade: a renda nova que a operação descartada criou também sai, senão seria reenviada e contaria em dobro.
        for (k in keys.filter { it.type == EntityType.Income.name && dao.serviceVersion(it.type, it.id) == null }) {
            dao.income(k.id)?.takeIf { it.receivableId != null && !it.voided }?.let {
                dao.upsertIncome(it.copy(voided = true, revision = it.revision + 1))
                adopted += key(k.type, k.id)
            }
        }
        for (k in keys.filter { it.type == EntityType.Debtor.name && dao.serviceVersion(it.type, it.id) == null }) {
            if (dao.receivables().none { it.debtorId == k.id }) {
                dao.discardUnrecognizedDebtor(k.id)
                adopted += key(k.type, k.id)
            }
        }
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
        // ADR 0048: manter o compromisso deste aparelho aplica os dados dele como edição do já reconhecido, que conserva a identidade.
        if (rows.any { it.counterpartId != null && it.counterpartType != it.entityType }) return@transaction ResolveResult.KindsDiffer
        val renamed = mutableMapOf<String, EntityKey>()
        for (row in rows) {
            val counterpartId = row.counterpartId
            if (counterpartId != null) {
                EntityStates.apply(dao, EntityType.valueOf(row.entityType), counterpartId, row.localState.replace(row.entityId, counterpartId))
                dao.upsertSync(listOf(EntitySyncRow(row.entityType, counterpartId, checkNotNull(row.remoteVersion))))
                renamed[key(row.entityType, row.entityId)] = EntityKey(row.entityType, counterpartId)
            } else {
                row.remoteVersion?.let { dao.upsertSync(listOf(EntitySyncRow(row.entityType, row.entityId, it))) }
            }
        }
        val keys = changedKeys(closure).map { renamed[key(it.type, it.id)] ?: it }.distinct()
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
