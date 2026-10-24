package app.accord.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Estados persistidos de uma operação local (S2 "Estado de uma operação local"). "Em envio" não é
 * persistido: uma morte do processo no meio do envio volta a Pendente e o reenvio é idempotente.
 */
object OpState {
    const val PENDING = "PENDING"

    /** Retida: encadeada a uma operação em conflito ou recusada. */
    const val HELD = "HELD"
    const val CONFLICT = "CONFLICT"
    const val REJECTED = "REJECTED"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"
    const val SUPERSEDED = "SUPERSEDED"

    /** Ainda não reconhecidas nem substituídas. */
    val OPEN = listOf(PENDING, HELD, CONFLICT, REJECTED)
}

object ConflictStatus {
    const val OPEN = "OPEN"
    const val DEFERRED = "DEFERRED"
    const val RESOLVED = "RESOLVED"
    val UNRESOLVED = listOf(OPEN, DEFERRED)
}

/** Linha única por banco: cursor de recebimento, última sincronização concluída e ID da instalação. */
@Entity(tableName = "sync_state")
data class SyncStateRow(
    @PrimaryKey val id: Int = 1,
    val cursor: Long,
    /** Momento (relógio do aparelho, só para exibição) em que um ciclo terminou por completo. */
    val lastSyncMillis: Long?,
    val installationId: String,
)

/** Um conflito por operação; as versões ficam em [ConflictEntityRow]. */
@Entity(tableName = "sync_conflict")
data class ConflictRow(
    @PrimaryKey val operationId: String,
    /** [ConflictStatus]. */
    val status: String,
    val createdMillis: Long,
    /** `REMOTE` ou `LOCAL` depois de resolvido. */
    val choice: String? = null,
)

/**
 * As duas versões completas de uma entidade em conflito. [remoteVersion] nulo quer dizer que o
 * serviço ainda não conhece a entidade (criação feita só neste aparelho).
 */
@Entity(tableName = "sync_conflict_entity", primaryKeys = ["operationId", "entityType", "entityId"])
data class ConflictEntityRow(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val localState: String,
    val localTombstone: Boolean,
    val remoteState: String?,
    val remoteTombstone: Boolean,
    val remoteVersion: Long?,
    val remoteAuthorId: String?,
    val remoteServiceMillis: Long?,
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun syncState(): SyncStateRow?
    @Upsert suspend fun upsertSyncState(row: SyncStateRow)

    // ---- operações ----
    @Query("SELECT * FROM operation WHERE syncState IN (:states) ORDER BY sequence")
    suspend fun operationsIn(states: List<String>): List<OperationRow>
    @Query("SELECT * FROM operation WHERE operationId = :id") suspend fun operation(id: String): OperationRow?
    @Query("UPDATE operation SET syncState = :state, acknowledged = :acknowledged, lastFailure = :failure WHERE operationId = :id")
    suspend fun setOperationState(id: String, state: String, acknowledged: Boolean, failure: String?)
    @Query("UPDATE operation SET attempts = attempts + 1, lastFailure = :failure WHERE operationId IN (:ids)")
    suspend fun recordAttempt(ids: List<String>, failure: String?)
    @Query("SELECT * FROM operation_change WHERE operationId = :id ORDER BY entityType, entityId")
    suspend fun changesOf(id: String): List<OperationChangeRow>
    @Query("SELECT * FROM operation_change WHERE operationId IN (:ids)") suspend fun changesOfAll(ids: List<String>): List<OperationChangeRow>
    @Query(
        "UPDATE operation_change SET baseVersion = :base, chained = 0 " +
            "WHERE operationId = :operationId AND entityType = :type AND entityId = :entityId",
    )
    suspend fun resolveBase(operationId: String, type: String, entityId: String, base: Long?)
    @Query("SELECT COUNT(*) FROM operation WHERE syncState IN (:states)") suspend fun countIn(states: List<String>): Int

    /** Operações ainda abertas que alteram a entidade, da mais antiga para a mais nova. */
    @Query(
        "SELECT o.* FROM operation o JOIN operation_change c ON c.operationId = o.operationId " +
            "WHERE c.entityType = :type AND c.entityId = :id AND o.syncState IN ('PENDING','HELD','CONFLICT','REJECTED') ORDER BY o.sequence",
    )
    suspend fun openOperationsOn(type: String, id: String): List<OperationRow>

    // ---- conflitos ----
    @Query("SELECT * FROM sync_conflict WHERE operationId = :operationId") suspend fun conflict(operationId: String): ConflictRow?
    @Upsert suspend fun upsertConflict(row: ConflictRow)
    @Query("SELECT * FROM sync_conflict WHERE status IN ('OPEN','DEFERRED') ORDER BY createdMillis, operationId")
    suspend fun unresolvedConflicts(): List<ConflictRow>
    @Query("SELECT * FROM sync_conflict_entity WHERE operationId = :operationId ORDER BY entityType, entityId")
    suspend fun conflictEntities(operationId: String): List<ConflictEntityRow>
    @Upsert suspend fun upsertConflictEntity(row: ConflictEntityRow)
    @Query(
        "SELECT COUNT(*) > 0 FROM sync_conflict_entity e JOIN sync_conflict c ON c.operationId = e.operationId " +
            "WHERE e.entityType = :type AND e.entityId = :id AND c.status IN ('OPEN','DEFERRED')",
    )
    suspend fun inUnresolvedConflict(type: String, id: String): Boolean

    // ---- situação por item ----
    @Query(
        "SELECT COUNT(*) > 0 FROM operation o JOIN operation_change c ON c.operationId = o.operationId " +
            "WHERE c.entityType = :type AND c.entityId = :id AND o.syncState = :state",
    )
    suspend fun hasOperationIn(type: String, id: String, state: String): Boolean
    @Query("SELECT COUNT(*) > 0 FROM entity_sync WHERE entityType = :type AND entityId = :id")
    suspend fun isKnownToService(type: String, id: String): Boolean

    @Insert suspend fun insertOperation(row: OperationRow): Long
    @Insert suspend fun insertChanges(rows: List<OperationChangeRow>)
}
