package app.accord.core.sync

import app.accord.core.space.EntityRef

/**
 * Estado de sincronização como as telas o mostram (S2 "Estado de sincronização"). Não há campo
 * sobre pendências do outro aparelho: o app não sabe o que ficou offline lá (tela 26).
 */
data class SyncSummary(
    /** Operações locais por enviar (pendentes ou retidas). É contagem de operações, não de despesas. */
    val awaitingOperations: Int,
    val openConflicts: Int,
    /** Operações que o serviço recusou por motivo que não é conflito. */
    val rejectedOperations: Int,
    /** Último ciclo concluído por completo, no relógio do aparelho (só exibição). */
    val lastSyncMillis: Long?,
    val cursor: Long,
)

/** Uma das duas versões de uma entidade em conflito. */
data class ConflictVersion(
    /** Estado completo (JSON); as telas leem os campos pelos nomes do protocolo. */
    val state: String,
    val tombstone: Boolean,
    val version: Long?,
    val authorId: String?,
    /** Só exibição ("momento conhecido da edição"); nunca decide o conflito. */
    val serviceTimeMillis: Long?,
)

data class ConflictEntity(
    val ref: EntityRef,
    val local: ConflictVersion,
    /** Nulo quando o serviço ainda não conhece a entidade. */
    val remote: ConflictVersion?,
    /** Campos do estado que diferem entre as duas versões, só para rotular a comparação. */
    val differingFields: List<String>,
    /**
     * Divergência de conversão (ADR 0048): o compromisso já reconhecido pelo serviço para a mesma intenção. [ref] é o compromisso deste aparelho e
     * [remote] o já reconhecido, com identidade própria. Nulo num conflito comum de versão.
     */
    val counterpart: EntityRef? = null,
)

data class ConflictView(
    val operationId: String,
    val commandType: String,
    val deferred: Boolean,
    val createdMillis: Long,
    val entities: List<ConflictEntity>,
    /** Operações retidas por causa deste conflito. */
    val heldOperationIds: List<String>,
) {
    /** Versão remota vista de cada entidade; a escolha só vale se nenhuma mudou desde então. */
    val seenRemoteVersions: Map<EntityRef, Long>
        get() = entities.mapNotNull { e -> e.remote?.version?.let { e.ref to it } }.toMap()
}

enum class ConflictChoice { Local, Remote }

sealed interface ResolveResult {
    /** [newOperationId] é a nova operação enviada com o estado escolhido, se houve. */
    data class Resolved(val newOperationId: String?) : ResolveResult

    /** Mudou algo durante a revisão: nada foi aplicado e a comparação foi atualizada. */
    data class Stale(val view: ConflictView) : ResolveResult
    data object NotFound : ResolveResult

    /** Manter o compromisso deste aparelho não vale quando os dois são de tipos diferentes (por exemplo compra no cartão e boleto): só vale manter o já reconhecido. */
    data object KindsDiffer : ResolveResult
}

enum class ProblemKind { Conflict, Rejected, Held }

/** Operação que precisa de atenção, com a causa categorizada (sem valores nem descrições). */
data class ProblemOperation(
    val operationId: String,
    val kind: ProblemKind,
    val commandType: String,
    val failure: String?,
    val entities: List<EntityRef>,
)

/** Resultado de aplicar uma página recebida. */
data class ReceiveSummary(val applied: List<EntityRef>, val conflicts: Int)
