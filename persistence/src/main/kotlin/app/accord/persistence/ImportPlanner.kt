package app.accord.persistence

import app.accord.core.importing.Blocker
import app.accord.core.importing.ConfirmStep
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ImportSteps
import app.accord.core.importing.Resolution
import app.accord.core.importing.StepTarget
import app.accord.core.space.Command
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfirmImportBatch
import app.accord.core.space.ResolveImportItem
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import app.accord.core.space.Receipt
import app.accord.core.space.Session

/** Aborts the surrounding local transaction when a step of the confirmation is refused; carries what to answer. */
internal class ImportAbort(val result: CommandResult) : RuntimeException(null, null, false, false)

/**
 * Plans [ConfirmImportBatch] (S4 "Confirmação, atomicidade e idempotência"): revalidates the reconciliation against the current state, then,
 * inside the transaction of the operation, runs every S1 command through [apply] (the same validator and the same operation log the Chat
 * and the forms use), records what happened to each movement and marks the batch confirmed. Any refusal throws [ImportAbort], which rolls
 * everything back.
 */
internal class ImportPlanner(
    private val dao: SpaceDao,
    private val evaluator: ImportEvaluator,
    private val apply: suspend (Session, Envelope, Command) -> CommandResult,
    private val nowMillis: () -> Long,
) {
    suspend fun plan(session: Session, c: ConfirmImportBatch): Planned {
        val batch = dao.importBatch(c.batchId) ?: return stop("batchId", ErrorCode.UnknownReference, "Lote de importação não encontrado.")
        if (batch.status == BatchStatus.Confirmed.name) {
            return stop("batchId", ErrorCode.NotAllowed, "Este lote já foi confirmado.")
        }
        val evaluation = evaluator.evaluate(batch)
        if (evaluation.blockers.isNotEmpty()) return stop(evaluation.blockers.map(::blockerError))
        if (evaluation.token != c.summaryToken) return Planned.Stop(CommandResult.StaleVersion(batch.id, batch.revision))
        return Planned.Go(months = evaluation.plan.summary.affectedMonths, undoable = false) {
            commit(session, batch, evaluation)
        }
    }

    /**
     * Resolves one open movement of a confirmed batch (S4 "Fila de pendências"). The movement is re-read with the person's decisions: only if it is
     * now clear (new, safely linked, paid or dismissed) its commands run, through the same validator, in this transaction.
     */
    suspend fun planResolve(session: Session, c: ResolveImportItem): Planned {
        val batch = dao.importBatch(c.batchId) ?: return stop("batchId", ErrorCode.UnknownReference, "Lote de importação não encontrado.")
        if (batch.status != BatchStatus.Confirmed.name) return stop("batchId", ErrorCode.NotAllowed, "Este lote ainda está em revisão.")
        val row = dao.importMovements(batch.id).firstOrNull { it.ordinal == c.ordinal && it.resolution == Resolution.Pending.name }
            ?: return stop("ordinal", ErrorCode.UnknownReference, "Esta pendência já foi resolvida.")
        val evaluation = evaluator.evaluate(batch)
        val step = evaluation.plan.steps.firstOrNull { it.item.movement.ordinal == c.ordinal }
            ?: return stop("ordinal", ErrorCode.UnknownReference, "Esta pendência já foi resolvida.")
        if (step.resolution == Resolution.Pending || step.resolution == Resolution.AlreadyKnown) {
            return stop("decision", ErrorCode.Required, "Ainda falta uma escolha ou há uma dúvida neste movimento.")
        }
        if (evaluation.token != c.summaryToken) return Planned.Stop(CommandResult.StaleVersion(batch.id, batch.revision))
        // The moved invoices count too: the document's invoice for a corrected purchase or refund, every month of a shifted schedule.
        val months = ImportSteps.affectedMonths(listOf(step), evaluation.kind == DocumentKind.Card, evaluation.facts.billing)
        return Planned.Go(months = months, undoable = false) {
            val repeat = dao.importDecisions(batch.id).firstOrNull { it.ordinal == c.ordinal && it.type == ImportDecisionType.REPEAT }?.value?.toInt()
            val resolved = resolve(session, batch, step, row, repeat)
            val pairs = dao.importDecisions(batch.id)
                .filter { it.ordinal == c.ordinal && it.type == ImportDecisionType.SEPARATE }
                .map { ImportPairRow(batch.id, step.item.key, it.value) }
            val next = batch.copy(revision = batch.revision + 1)
            dao.upsertImportBatch(next)
            dao.upsertImportMovements(listOf(resolved))
            dao.upsertImportPairs(pairs)
            dao.deleteImportDecisionsOf(batch.id, c.ordinal)
            listOf(EntityChange(EntityType.ImportBatch, batch.id, null, next.revision))
        }
    }

    private suspend fun commit(session: Session, batch: ImportBatchRow, e: Evaluation): List<EntityChange> {
        val stored = dao.importMovements(batch.id).associateBy { it.ordinal }
        val repeats = dao.importDecisions(batch.id).filter { it.type == ImportDecisionType.REPEAT }.associate { it.ordinal to it.value.toInt() }
        val resolved = e.plan.steps.map { step -> resolve(session, batch, step, stored.getValue(step.item.movement.ordinal), repeats[step.item.movement.ordinal]) }
        val keyOf = e.plan.steps.associate { it.item.movement.ordinal to it.item.key }
        val pairs = dao.importDecisions(batch.id)
            .filter { it.type == ImportDecisionType.SEPARATE }
            .mapNotNull { d -> keyOf[d.ordinal]?.let { ImportPairRow(batch.id, it, d.value) } }

        val next = batch.copy(
            status = BatchStatus.Confirmed.name,
            confirmedBy = session.personId,
            confirmedAtMillis = nowMillis(),
            revision = batch.revision + 1,
        )
        dao.upsertImportBatch(next)
        dao.upsertImportMovements(resolved)
        dao.upsertImportPairs(pairs)
        // O trabalho em revisão acabou: as decisões efetivadas ficam nos movimentos e nos pares, que sincronizam. O que a pessoa escolheu para um movimento
        // que continua aberto (inclusive a categoria padrão do lote, que passa a ser a categoria dele) fica, para ele não voltar sem a escolha. O "revisar depois" não:
        // era só o que o segurou fora da confirmação.
        val decisions = dao.importDecisions(batch.id)
        val stillOpen = e.plan.steps.filter { it.resolution == Resolution.Pending }.map { it.item.movement.ordinal }.toSet()
        val defaultCategory = decisions.firstOrNull { it.type == ImportDecisionType.DEFAULT }?.value
        val kept = decisions.filter { it.ordinal in stillOpen && it.type != ImportDecisionType.POSTPONE && it.type != ImportDecisionType.DEFAULT }.toMutableList()
        if (defaultCategory != null) {
            stillOpen.filter { o -> kept.none { it.ordinal == o && it.type == ImportDecisionType.CATEGORY } }
                .forEach { kept += ImportDecisionRow(batch.id, it, ImportDecisionType.CATEGORY, defaultCategory) }
        }
        dao.deleteImportDecisions(batch.id)
        dao.upsertImportDecisions(kept)
        return listOf(EntityChange(EntityType.ImportBatch, batch.id, null, next.revision))
    }

    private suspend fun resolve(session: Session, batch: ImportBatchRow, step: ConfirmStep, row: ImportMovementRow, repeatOf: Int? = null): ImportMovementRow {
        var created: Receipt? = null
        step.commands.forEachIndexed { index, command ->
            val id = DeterministicIds.of(session.spaceId, "import-op", "${batch.id}|${step.item.key}|$index")
            val receipt = when (val result = apply(session, Envelope(CommandId(id), Origin.Import), command)) {
                is CommandResult.Applied -> result.receipt
                is CommandResult.AlreadyApplied -> result.receipt
                else -> throw ImportAbort(refusal(step, result))
            }
            if (index == 0) created = receipt
        }
        val target: StepTarget? = step.target
            ?: step.createsType?.let { type -> created?.changes?.firstOrNull { it.type == type }?.let { StepTarget(type, it.id, step.createsNumber) } }
        return row.copy(
            key = step.item.key,
            resolution = step.resolution.name,
            targetType = target?.type?.name,
            targetId = target?.id,
            targetNumber = target?.number,
            pending = if (step.resolution == Resolution.Pending) step.item.doubts.map(DoubtReason::name).sorted().joinToString(",") else "",
            repeatOf = repeatOf.takeIf { step.resolution == Resolution.Dismissed },
        )
    }

    private fun refusal(step: ConfirmStep, result: CommandResult): CommandResult = when (result) {
        is CommandResult.Rejected -> result
        else -> CommandResult.Rejected(
            listOf(FieldError("movement.${step.item.movement.ordinal}", ErrorCode.NotAllowed, "O movimento ${step.item.movement.ordinal} mudou desde a revisão. Confira de novo antes de confirmar.")),
        )
    }

    private fun blockerError(blocker: Blocker) = when (blocker) {
        Blocker.UnsupportedCurrency -> FieldError("currency", ErrorCode.NotSupported, "A moeda do arquivo não é suportada. Nada é convertido nem confirmado.")
        Blocker.SourceUndefined -> FieldError("source", ErrorCode.Required, "Escolha o tipo do documento e a conta ou o cartão correspondente.")
        Blocker.NothingChecked -> FieldError("batch", ErrorCode.Required, "Não há nada conferido para confirmar.")
        Blocker.AlreadyConfirmed -> FieldError("batchId", ErrorCode.NotAllowed, "Este lote já foi confirmado.")
    }
}
