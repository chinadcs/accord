package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.importing.BatchResult
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DiscardOutcome
import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.PendingMovement
import app.accord.core.importing.Reconciler
import app.accord.core.importing.RefundCandidate
import app.accord.core.importing.ResolveOutcome
import app.accord.core.importing.Resolution
import app.accord.core.importing.ResultItem
import app.accord.core.importing.SourceOutcome
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfirmImportBatch
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.FinanceSpace
import app.accord.core.space.Origin
import app.accord.core.space.QueryResult
import app.accord.core.space.ResolveImportItem
import app.accord.core.space.SessionProvider
import app.accord.persistence.ImportCodec.toInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.YearMonth

/**
 * Sessão de importação sobre Room (S4). Início, origem, decisões e descarte são trabalho local: não geram operação nem sincronizam.
 * A confirmação passa pelo comando [ConfirmImportBatch] da fachada, que aplica tudo numa só transação e é o que entra na fila de sync.
 */
internal class RoomImportSession(
    private val db: AccordDatabase,
    private val space: FinanceSpace,
    private val evaluator: ImportEvaluator,
    private val sessions: SessionProvider,
    private val nowMillis: () -> Long,
) : ImportSession {
    private val dao = db.dao()

    private suspend fun <T> query(block: suspend () -> T): QueryResult<T> = try {
        QueryResult.Data(block())
    } catch (e: CancellationException) {
        if (currentCoroutineContext().isActive) QueryResult.Failure(e) else throw e
    } catch (e: Exception) {
        QueryResult.Failure(e)
    }

    private suspend fun <T> write(block: suspend () -> T): T = db.useWriterConnection { it.immediateTransaction { block() } }

    override suspend fun start(request: StartImport): QueryResult<StartOutcome> = query {
        write {
            dao.importBatch(request.batchId)?.let { return@write StartOutcome.AlreadyStarted(it.toInfo()) }
            dao.importBatchesBySha(request.extraction.sha256).firstOrNull()?.let { return@write StartOutcome.KnownFile(it.toInfo()) }
            val session = sessions.current()
            val md = request.extraction.metadata
            val row = ImportBatchRow(
                id = request.batchId,
                status = BatchStatus.InReview.name,
                fileName = request.fileName,
                sizeBytes = request.sizeBytes,
                sha256 = request.extraction.sha256,
                format = md.format.name,
                ofxVersion = md.ofxVersion,
                declaredKind = md.kind?.name,
                kind = request.source.kind?.name,
                originId = request.source.originId,
                documentInvoice = request.source.documentInvoice?.toString(),
                currency = md.currency,
                periodStartDay = md.periodStart?.toEpochDay(),
                periodEndDay = md.periodEnd?.toEpochDay(),
                dueDay = md.dueDate?.toEpochDay(),
                institution = md.institution,
                ledgerBalanceCents = md.ledgerBalanceCents,
                ledgerBalanceDay = md.ledgerBalanceDate?.toEpochDay(),
                movementCount = request.extraction.movements.size,
                unrecognizedSegments = request.extraction.unrecognizedSegments,
                warnings = ImportCodec.encodeWarnings(request.extraction.warnings),
                startedBy = session.personId,
                startedAtMillis = nowMillis(),
                revision = 1,
            )
            dao.upsertImportBatch(row)
            dao.upsertImportMovements(request.extraction.movements.map { ImportCodec.movementRow(row.id, it) })
            StartOutcome.Started(row.toInfo())
        }
    }

    override suspend fun setSource(batchId: String, source: BatchSource, expectedRevision: Long): QueryResult<SourceOutcome> = query {
        write {
            val batch = dao.importBatch(batchId) ?: return@write SourceOutcome.NotFound
            if (batch.status != BatchStatus.InReview.name) return@write SourceOutcome.Rejected(listOf(notInReview()))
            if (batch.revision != expectedRevision) return@write SourceOutcome.Stale(batch.revision)
            val next = batch.copy(
                kind = source.kind?.name,
                originId = source.originId,
                documentInvoice = source.documentInvoice?.toString(),
                revision = batch.revision + 1,
            )
            dao.upsertImportBatch(next)
            // Link/separate choices that pointed at entries of the previous origin no longer apply: they go back to doubt.
            val linkable = evaluator.evaluate(next).facts.entries.filter { !it.hasOrigin }.map { it.id }.toSet()
            val dropped = dao.importDecisions(batchId).filter { (it.type == ImportDecisionType.LINK || it.type == ImportDecisionType.SEPARATE) && it.value !in linkable }
            dropped.forEach { dao.deleteImportDecision(batchId, it.ordinal, it.type) }
            SourceOutcome.Applied(next.revision, dropped.size)
        }
    }

    override suspend fun decide(batchId: String, decision: ImportDecision, expectedRevision: Long): QueryResult<DecideOutcome> = query {
        write {
            val batch = dao.importBatch(batchId) ?: return@write DecideOutcome.NotFound
            val confirmed = batch.status == BatchStatus.Confirmed.name
            if (batch.revision != expectedRevision) return@write DecideOutcome.Stale(batch.revision)
            val rows = dao.importMovements(batchId)
            // Em lote confirmado só as dúvidas guardadas (Pending) aceitam decisões; o que já foi aplicado não muda por aqui.
            val movements = rows.filter { !confirmed || it.resolution == Resolution.Pending.name }.map { it.ordinal }.toSet()
            if (confirmed && (decision is ImportDecision.DefaultCategory || (decision is ImportDecision.AcceptSuggestions && decision.ordinals == null))) {
                return@write DecideOutcome.Rejected(listOf(notInReview()))
            }
            var cached: Evaluation? = null
            suspend fun evaluation(): Evaluation = cached ?: evaluator.evaluate(batch).also { cached = it }

            fun unknownMovement(ordinal: Int) = DecideOutcome.Rejected(listOf(FieldError("ordinal", ErrorCode.UnknownReference, "Movimento $ordinal não existe neste lote.")))
            val touched: List<Int> = when (decision) {
                is ImportDecision.Category -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    if (dao.activeCategory(decision.categoryId) == null) return@write DecideOutcome.Rejected(listOf(FieldError("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")))
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.CATEGORY)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.CATEGORY, decision.categoryId)))
                    listOf(decision.ordinal)
                }
                is ImportDecision.DefaultCategory -> {
                    if (decision.categoryId != null && dao.activeCategory(decision.categoryId!!) == null) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")))
                    }
                    dao.deleteImportDecision(batchId, ImportDecisionType.BATCH_WIDE, ImportDecisionType.DEFAULT)
                    decision.categoryId?.let { dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, ImportDecisionType.BATCH_WIDE, ImportDecisionType.DEFAULT, it))) }
                    emptyList()
                }
                is ImportDecision.AcceptSuggestions -> {
                    val wanted = decision.ordinals?.toSet()
                    val suggestions = evaluation().preview.items
                        .filter { it.suggestedCategoryId != null && (wanted == null || it.movement.ordinal in wanted) }
                    if (wanted != null) (wanted - movements).firstOrNull()?.let { return@write unknownMovement(it) }
                    suggestions.forEach {
                        dao.deleteImportDecision(batchId, it.movement.ordinal, ImportDecisionType.CATEGORY)
                        dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, it.movement.ordinal, ImportDecisionType.CATEGORY, it.suggestedCategoryId!!)))
                    }
                    suggestions.map { it.movement.ordinal }
                }
                is ImportDecision.LinkTo -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    if (evaluation().facts.entries.none { it.id == decision.entryId && !it.hasOrigin }) return@write DecideOutcome.Rejected(listOf(unknownEntry()))
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.LINK)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.LINK, decision.entryId)))
                    // Linking is its own choice: a correction agreed for another entry no longer applies.
                    dao.importDecisions(batchId).filter { it.ordinal == decision.ordinal && it.type == ImportDecisionType.CORRECT_INVOICE && it.value != decision.entryId }
                        .forEach { dao.deleteImportDecision(batchId, it.ordinal, it.type) }
                    listOf(decision.ordinal)
                }
                is ImportDecision.CorrectInvoice -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    val entry = evaluation().facts.entries.firstOrNull { it.id == decision.entryId && !it.hasOrigin }
                    if (entry == null) return@write DecideOutcome.Rejected(listOf(unknownEntry()))
                    if (entry.card == null) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("entryId", ErrorCode.NotAllowed, "Só uma compra no cartão tem fatura para corrigir.")))
                    }
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.LINK)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.CORRECT_INVOICE)
                    dao.upsertImportDecisions(
                        listOf(
                            ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.LINK, decision.entryId),
                            ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.CORRECT_INVOICE, decision.entryId),
                        ),
                    )
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    listOf(decision.ordinal)
                }
                is ImportDecision.ShiftFirstInstallment -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.SHIFT_FIRST, "1")))
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    listOf(decision.ordinal)
                }
                is ImportDecision.AsRefund -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    if (!refundableOrigin(decision.originId)) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("originId", ErrorCode.UnknownReference, "Compra ou pagamento não encontrado para estornar.")))
                    }
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.REFUND)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.NEW_PURCHASE_REFUND)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.REFUND, decision.originId)))
                    // "É estorno", "é renda" e "é repetição" se contradizem: a última escolha vale.
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.INCOME)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    listOf(decision.ordinal)
                }
                is ImportDecision.AsInstallmentPurchase -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    val current = evaluation()
                    val item = current.preview.items.firstOrNull { it.movement.ordinal == decision.ordinal }
                    val line = item?.movement
                    val hint = line?.installment
                    val lineCents = line?.amountCents
                    if (current.kind != DocumentKind.Card || hint == null || lineCents == null || lineCents >= 0) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("ordinal", ErrorCode.NotAllowed, "Só uma parcela n/N de um documento de cartão vira compra parcelada.")))
                    }
                    // The total belongs to the whole purchase: it is kept on the first line of the series and must fit every line the file shows of it.
                    val series = item.installmentSeries?.let { info -> seriesOf(current, info.leaderOrdinal, info.total) }
                    val fits = series?.fits(decision.totalCents) ?: (decision.totalCents >= hint.total && Installments.lineFits(decision.totalCents, hint.total, hint.number, -lineCents))
                    if (!fits) {
                        return@write DecideOutcome.Rejected(
                            listOf(FieldError("totalCents", ErrorCode.OutOfRange, "O total precisa dividir em ${hint.total} parcelas com a parcela ${hint.number} igual ao valor desta linha.")),
                        )
                    }
                    val holder = series?.leader?.ordinal ?: decision.ordinal
                    if (holder !in movements) return@write unknownMovement(holder)
                    dao.deleteImportDecision(batchId, holder, ImportDecisionType.INSTALLMENT_PURCHASE)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, holder, ImportDecisionType.INSTALLMENT_PURCHASE, decision.totalCents.toString())))
                    dao.deleteImportDecision(batchId, holder, ImportDecisionType.DISMISS)
                    listOf(holder)
                }
                is ImportDecision.AcceptInstallmentTotals -> {
                    val wanted = decision.ordinals?.toSet()
                    if (wanted != null) (wanted - movements).firstOrNull()?.let { return@write unknownMovement(it) }
                    val current = evaluation()
                    val accepted = current.preview.items
                        .filter { it.installmentSeries != null && DoubtReason.InstallmentPurchaseNotRegistered in it.doubts && it.installmentSeries!!.acceptable }
                        .filter { wanted == null || it.movement.ordinal in wanted }
                        .map { it.installmentSeries!! }.distinctBy { it.leaderOrdinal }
                        .filter { it.leaderOrdinal in movements && seriesOf(current, it.leaderOrdinal, it.total)?.fits(it.suggestedTotalCents) == true }
                    accepted.forEach {
                        dao.deleteImportDecision(batchId, it.leaderOrdinal, ImportDecisionType.INSTALLMENT_PURCHASE)
                        dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, it.leaderOrdinal, ImportDecisionType.INSTALLMENT_PURCHASE, it.suggestedTotalCents.toString())))
                    }
                    accepted.map { it.leaderOrdinal }
                }
                is ImportDecision.AsNewPurchaseRefund -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    val current = evaluation()
                    val credit = current.extraction.movements.firstOrNull { it.ordinal == decision.ordinal }
                    if (current.kind != DocumentKind.Card || (credit?.amountCents ?: 0) <= 0) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("ordinal", ErrorCode.NotAllowed, "Só um crédito de um documento de cartão pode registrar a compra e estornar.")))
                    }
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.NEW_PURCHASE_REFUND)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.NEW_PURCHASE_REFUND, "1")))
                    // "É de uma compra que o app não tem", "é estorno dessa compra" e "é repetição" se contradizem: a última escolha vale.
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.REFUND)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.INCOME)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    listOf(decision.ordinal)
                }
                is ImportDecision.KeepSeparate -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    if (evaluation().facts.entries.none { it.id == decision.entryId && !it.hasOrigin }) return@write DecideOutcome.Rejected(listOf(unknownEntry()))
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.SEPARATE, decision.entryId)))
                    // A forced link to the same entry contradicts "they are different".
                    dao.importDecisions(batchId).filter { it.ordinal == decision.ordinal && (it.type == ImportDecisionType.LINK || it.type == ImportDecisionType.CORRECT_INVOICE) && it.value == decision.entryId }
                        .forEach { dao.deleteImportDecision(batchId, it.ordinal, it.type) }
                    listOf(decision.ordinal)
                }
                is ImportDecision.Reset -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    dao.deleteImportDecisionsOf(batchId, decision.ordinal)
                    listOf(decision.ordinal)
                }
                is ImportDecision.ConfirmNew -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.CONFIRM_NEW, "1")))
                    // "É novo" e "é repetição" se contradizem: a última escolha vale.
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    listOf(decision.ordinal)
                }
                is ImportDecision.Dismiss -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.DISMISS, "1")))
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.CONFIRM_NEW)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.REFUND)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.NEW_PURCHASE_REFUND)
                    listOf(decision.ordinal)
                }
                is ImportDecision.AsIncome -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    if (evaluation().kind == DocumentKind.Card) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("ordinal", ErrorCode.NotAllowed, "No cartão, todo valor positivo é estorno de uma compra, nunca renda.")))
                    }
                    if (dao.incomeCategory(decision.incomeCategoryId) == null) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("incomeCategoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")))
                    }
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.INCOME)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.REFUND)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.INCOME, decision.incomeCategoryId)))
                    listOf(decision.ordinal)
                }
                is ImportDecision.ReviewLater -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.POSTPONE, "1")))
                    listOf(decision.ordinal)
                }
                is ImportDecision.RepeatOf -> {
                    if (decision.ordinal !in movements) return@write unknownMovement(decision.ordinal)
                    val existing = dao.importDecisions(batchId)
                    val original = rows.firstOrNull { it.ordinal == decision.ofOrdinal }
                    val repeatsAlready = existing.any { it.ordinal == decision.ofOrdinal && (it.type == ImportDecisionType.REPEAT || it.type == ImportDecisionType.DISMISS) } ||
                        original?.resolution == Resolution.Dismissed.name
                    // The original must be another movement of this file that is not itself a repetition, and cannot be the original of someone else at the same time.
                    val originalOfSomeone = existing.any { it.type == ImportDecisionType.REPEAT && it.value == decision.ordinal.toString() }
                    if (original == null || decision.ofOrdinal == decision.ordinal || repeatsAlready || originalOfSomeone) {
                        return@write DecideOutcome.Rejected(listOf(FieldError("ofOrdinal", ErrorCode.UnknownReference, "Escolha outro movimento deste arquivo que não seja, ele mesmo, uma repetição.")))
                    }
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.REPEAT)
                    dao.upsertImportDecisions(listOf(ImportDecisionRow(batchId, decision.ordinal, ImportDecisionType.REPEAT, decision.ofOrdinal.toString())))
                    // Repetition replaces the other choices about this movement.
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.CONFIRM_NEW)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.DISMISS)
                    dao.deleteImportDecision(batchId, decision.ordinal, ImportDecisionType.POSTPONE)
                    listOf(decision.ordinal)
                }
            }
            // O lote confirmado já foi enviado com a sua revisão: decidir uma dúvida é trabalho local até resolver (que sim gera a operação).
            val next = if (confirmed) batch else batch.copy(revision = batch.revision + 1).also { dao.upsertImportBatch(it) }
            DecideOutcome.Applied(next.revision, touched)
        }
    }

    override suspend fun review(batchId: String): QueryResult<BatchReview?> = query {
        dao.importBatch(batchId)?.let { evaluator.evaluate(it).review() }
    }

    override suspend fun confirm(batchId: String, confirmationId: String, summaryToken: String): QueryResult<ConfirmOutcome> = query {
        val envelope = Envelope(CommandId(confirmationId), Origin.Import)
        when (val result = space.execute(envelope, ConfirmImportBatch(batchId, summaryToken))) {
            is CommandResult.Applied -> ConfirmOutcome.Applied(checkNotNull(resultOf(batchId)) { "Lote confirmado sem resultado" })
            is CommandResult.AlreadyApplied -> ConfirmOutcome.AlreadyApplied(checkNotNull(resultOf(batchId)) { "Lote confirmado sem resultado" })
            is CommandResult.StaleVersion -> dao.importBatch(batchId)?.let { ConfirmOutcome.ReviewNeeded(evaluator.evaluate(it).review()) } ?: ConfirmOutcome.NotFound
            is CommandResult.Rejected ->
                if (result.errors.any { it.code == ErrorCode.UnknownReference && it.field == "batchId" }) ConfirmOutcome.NotFound
                else blockersOf(batchId, result)
            is CommandResult.DecisionNeeded -> ConfirmOutcome.Invalid(listOf(FieldError("batch", ErrorCode.NotAllowed, "A confirmação pede uma escolha que a importação não consegue presumir.")))
        }
    }

    /** A refused confirmation is either a block explained by the review or a step the validator refused. */
    private suspend fun blockersOf(batchId: String, result: CommandResult.Rejected): ConfirmOutcome {
        val batch = dao.importBatch(batchId) ?: return ConfirmOutcome.NotFound
        val blockers = evaluator.evaluate(batch).blockers
        val sourceErrors = setOf("currency", "source", "documentInvoice", "batch", "batchId")
        return if (blockers.isNotEmpty() && result.errors.all { it.field in sourceErrors }) ConfirmOutcome.Blocked(blockers) else ConfirmOutcome.Invalid(result.errors)
    }

    override suspend fun discard(batchId: String): QueryResult<DiscardOutcome> = query {
        write {
            val batch = dao.importBatch(batchId) ?: return@write DiscardOutcome.Discarded
            if (batch.status != BatchStatus.InReview.name) return@write DiscardOutcome.NotAllowed
            dao.deleteImportDecisions(batchId)
            dao.deleteImportMovements(batchId)
            dao.deleteImportPairs(batchId)
            dao.deleteImportBatch(batchId)
            DiscardOutcome.Discarded
        }
    }

    override suspend fun result(batchId: String): QueryResult<BatchResult?> = query { resultOf(batchId) }

    private suspend fun resultOf(batchId: String): BatchResult? {
        val batch = dao.importBatch(batchId)?.takeIf { it.status == BatchStatus.Confirmed.name } ?: return null
        val items = dao.importMovements(batchId).map { r ->
            ResultItem(
                ordinal = r.ordinal,
                key = r.key.orEmpty(),
                resolution = Resolution.valueOf(checkNotNull(r.resolution) { "Movimento ${r.ordinal} sem resolução num lote confirmado" }),
                targetType = r.targetType?.let(EntityType::valueOf),
                targetId = r.targetId,
                doubts = ImportCodec.parse(r.pending, DoubtReason::valueOf),
                date = r.epochDay?.let(java.time.LocalDate::ofEpochDay),
                cents = r.amountCents,
                currency = r.currency,
                description = r.description,
                originalFields = ImportCodec.decodeFields(r.originalFields),
                repeatOf = r.repeatOf,
            )
        }
        return BatchResult(batch.toInfo(), items)
    }

    override suspend fun batches(): QueryResult<List<BatchInfo>> = query { dao.importBatches().map { it.toInfo() } }

    // ---- dúvidas guardadas de lotes confirmados ----

    override suspend fun pendings(): QueryResult<List<PendingMovement>> = query {
        dao.batchesWithPending().flatMap { id ->
            val batch = dao.importBatch(id) ?: return@flatMap emptyList()
            val evaluation = evaluator.evaluate(batch)
            dao.importMovements(id).filter { it.resolution == Resolution.Pending.name }.mapNotNull { evaluation.pendingOf(it.ordinal, it) }
        }
    }

    override suspend fun pending(batchId: String, ordinal: Int): QueryResult<PendingDetail?> = query { detailOf(batchId, ordinal) }

    private suspend fun detailOf(batchId: String, ordinal: Int): PendingDetail? {
        val batch = dao.importBatch(batchId)?.takeIf { it.status == BatchStatus.Confirmed.name } ?: return null
        val stored = dao.importMovements(batchId).firstOrNull { it.ordinal == ordinal && it.resolution == Resolution.Pending.name } ?: return null
        val evaluation = evaluator.evaluate(batch)
        val pending = evaluation.pendingOf(ordinal, stored) ?: return null
        val item = evaluation.preview.items.first { it.movement.ordinal == ordinal }
        return PendingDetail(
            pending, item, evaluation.facts.linkable(),
            refundCandidates = if ((item.movement.amountCents ?: 0) > 0) refundCandidates(evaluation, item.movement) else emptyList(),
            token = evaluation.token,
        )
    }

    /** True when [id] is a live card purchase, direct payment or boleto: the origins a refund can be tied to. */
    private suspend fun refundableOrigin(id: String): Boolean =
        dao.purchase(id)?.voided == false || dao.movement(id)?.voided == false || dao.boleto(id)?.voided == false

    /**
     * Purchases (card document) or payments (account document) of the origin in the months before the credit, newest first, that can still be refunded.
     * On a card document only the purchases of that card. The ones with the credit's value left to refund come first, then the ones that share a word with its
     * description, so the likely original is at the top; nothing is chosen for the person.
     */
    private suspend fun refundCandidates(e: Evaluation, credit: app.accord.core.importing.ExtractedMovement): List<RefundCandidate> {
        val date = credit.date ?: return emptyList()
        if (e.originId.isBlank()) return emptyList()
        val from = date.minusDays(REFUND_WINDOW_DAYS).toEpochDay()
        val to = date.toEpochDay()
        val rows: List<RefundCandidate> = when (e.kind) {
            DocumentKind.Card -> dao.purchasesBetween(from, to).filter { it.cardId == null || it.cardId == e.originId }.map {
                RefundCandidate(it.id, java.time.LocalDate.ofEpochDay(it.purchaseEpochDay), it.totalCents, it.totalCents - refunded(it.id), it.description)
            }
            DocumentKind.Account -> dao.movementsOfAccount(e.originId, from, to).map {
                RefundCandidate(it.id, java.time.LocalDate.ofEpochDay(it.epochDay), it.amountCents, it.amountCents - refunded(it.id), it.description)
            }
            null -> emptyList()
        }
        return rows.filter { it.remainingCents > 0 }
            .sortedWith(
                compareByDescending<RefundCandidate> { it.remainingCents == credit.amountCents }
                    .thenByDescending { Reconciler.sharesAWord(it.description, credit.description) }
                    .thenByDescending { it.date },
            )
            .take(REFUND_CANDIDATES_SHOWN)
    }

    private suspend fun refunded(originId: String): Long = dao.refundsOf(originId).filterNot { it.voided }.sumOf { it.amountCents }

    override suspend fun resolve(batchId: String, ordinal: Int, operationId: String, summaryToken: String): QueryResult<ResolveOutcome> = query {
        when (val result = space.execute(Envelope(CommandId(operationId), Origin.Import), ResolveImportItem(batchId, ordinal, summaryToken))) {
            is CommandResult.Applied -> ResolveOutcome.Applied(resolutionOf(batchId, ordinal))
            is CommandResult.AlreadyApplied -> ResolveOutcome.AlreadyApplied(resolutionOf(batchId, ordinal))
            is CommandResult.StaleVersion -> detailOf(batchId, ordinal)?.let { ResolveOutcome.ReviewNeeded(it) } ?: ResolveOutcome.NotFound
            is CommandResult.Rejected -> when {
                result.errors.any { it.field == "ordinal" || (it.field == "batchId" && it.code == ErrorCode.UnknownReference) } -> ResolveOutcome.NotFound
                result.errors.any { it.field == "decision" } -> detailOf(batchId, ordinal)?.let { ResolveOutcome.NotReady(it) } ?: ResolveOutcome.NotFound
                else -> ResolveOutcome.Invalid(result.errors)
            }
            is CommandResult.DecisionNeeded -> ResolveOutcome.Invalid(listOf(FieldError("batch", ErrorCode.NotAllowed, "A resolução pede uma escolha que a importação não consegue presumir.")))
        }
    }

    private suspend fun resolutionOf(batchId: String, ordinal: Int): Resolution =
        Resolution.valueOf(checkNotNull(dao.importMovements(batchId).firstOrNull { it.ordinal == ordinal }?.resolution) { "Movimento sem resolução" })

    /** The open movement as the queue shows it, read now: [PendingMovement.ready] when applying it would work. */
    private fun Evaluation.pendingOf(ordinal: Int, stored: ImportMovementRow): PendingMovement? {
        val item = preview.items.firstOrNull { it.movement.ordinal == ordinal } ?: return null
        val step = plan.steps.first { it.item.movement.ordinal == ordinal }
        return PendingMovement(
            batchId = batch.id, ordinal = ordinal, fileName = batch.fileName, kind = kind,
            date = item.movement.date, cents = item.movement.amountCents, currency = item.movement.currency, description = item.movement.description,
            classification = item.classification, doubts = item.doubts,
            ready = step.resolution != Resolution.Pending && step.resolution != Resolution.AlreadyKnown,
            batchStartedAtMillis = batch.startedAtMillis,
            documentInvoice = batch.documentInvoice?.let(YearMonth::parse),
            // The doubts stored when it was confirmed remember a "review later" the person chose, which is gone from the live reading.
            postponed = DoubtReason.ReviewLater.name in stored.pending.split(","),
        )
    }

    private companion object {
        /** Proposal: how far back a credit may point for the purchase it refunds, and how many are listed. */
        const val REFUND_WINDOW_DAYS = 180L
        const val REFUND_CANDIDATES_SHOWN = 40
    }

    /** The lines of the file that show the purchase led by [leaderOrdinal], as the series the reconciliation grouped. */
    private fun seriesOf(e: Evaluation, leaderOrdinal: Int, total: Int): app.accord.core.importing.InstallmentSeries? {
        val members = e.preview.items.filter { it.installmentSeries?.leaderOrdinal == leaderOrdinal }.mapNotNull { item ->
            val hint = item.movement.installment ?: return@mapNotNull null
            val cents = item.movement.amountCents ?: return@mapNotNull null
            app.accord.core.importing.InstallmentSeries.Member(item.movement.ordinal, hint.number, -cents)
        }.sortedWith(compareBy({ it.number }, { it.ordinal }))
        return members.takeIf { it.isNotEmpty() }?.let { app.accord.core.importing.InstallmentSeries(total, it) }
    }

    private fun notInReview() = FieldError("batchId", ErrorCode.NotAllowed, "Este lote já foi confirmado e não pode mais ser alterado.")
    private fun unknownEntry() = FieldError("entryId", ErrorCode.UnknownReference, "Esse lançamento não está disponível para vincular a este documento.")
}
