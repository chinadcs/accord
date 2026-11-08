package app.accord.persistence

import app.accord.core.space.CorrectOccurrenceInvoice
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.BudgetClock
import app.accord.core.CardCycle
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.core.space.AlertEventView
import app.accord.core.space.AlertNotice
import app.accord.core.space.AlertStatus
import app.accord.core.space.RecordAlertEvent
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.ForecastDetail
import app.accord.core.Frequency
import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.Recurrence
import app.accord.core.space.AccountInfo
import app.accord.core.space.AccountsView
import app.accord.core.space.AddAccount
import app.accord.core.space.EditCategory
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.RemoveCategory
import app.accord.core.space.AssignmentState
import app.accord.core.space.DateCriterion
import app.accord.core.space.TotalBalance
import app.accord.core.space.BankRef
import app.accord.core.space.BoletoView
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryLimit
import app.accord.core.space.CategoryNode
import app.accord.core.space.CategorySeed
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.IncomeCategorySeed
import app.accord.core.space.Classification
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.CancelState
import app.accord.core.space.Command
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfigureCard
import app.accord.core.space.ConfirmImportBatch
import app.accord.core.space.ResolveImportItem
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.ConfirmRefund
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.CreateCategory
import app.accord.core.space.ClearIconAssociation
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.CreateIntention
import app.accord.core.space.EditIntention
import app.accord.core.space.IconAssociationView
import app.accord.core.space.IntentionView
import app.accord.core.space.SetIconAssociation
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DefineLimits
import app.accord.core.space.DeleteEntry
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.DraftSource
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.CommitmentKind
import app.accord.core.space.EditAccount
import app.accord.core.space.EditRecurrence
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityRef
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryFilter
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryPage
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.FieldError
import app.accord.core.space.FinanceSpace
import app.accord.core.space.IncomeSummary
import app.accord.core.space.MonthCommitments
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PendingItem
import app.accord.core.space.ScheduleRow
import app.accord.core.space.SyncStatusProvider
import app.accord.core.space.InvoiceView
import app.accord.core.space.IncomeView
import app.accord.core.space.LocalDraft
import app.accord.core.space.LocalOperation
import app.accord.core.space.MonthBudget
import app.accord.core.space.MovementRow
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.NameKey
import app.accord.core.space.OccurrenceView
import app.accord.core.space.Origin
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.PayOccurrence
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.PeriodComparison
import app.accord.core.space.PurchaseActivity
import app.accord.core.space.WeeklyReviewSummary
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.space.SubscriptionDetail
import app.accord.core.space.SubscriptionList
import app.accord.core.space.PreviewResult
import app.accord.core.space.PurchaseDetail
import app.accord.core.space.QueryResult
import app.accord.core.space.Receipt
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceView
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RenameCategory
import app.accord.core.space.RenameIncomeCategory
import app.accord.core.space.ReorderCategories
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.RequestInstallmentCancellation
import app.accord.core.space.Session
import app.accord.core.space.SessionProvider
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import app.accord.core.importing.ImportSession
import app.accord.core.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Implementação local da fachada sobre Room. Todo comando aplicado grava as entidades e a
 * operação local **na mesma transação** (S1 §5.1); o ID do comando é o ID da operação, o que
 * dá idempotência local. Banco e fila vivem no aparelho; nada aqui depende de rede.
 *
 * Cada comando é primeiro **planejado** (valida, calcula, sem gravar) e só então gravado; a
 * prévia usa o mesmo planejamento, por isso mostra o que a gravação faria. A gravação replanejar
 * dentro da transação garante que regras e versões sejam reaplicadas.
 *
 * [beforeCommit] roda dentro da transação, depois das gravações: serve a testes que injetam
 * falha para provar que nada fica pela metade.
 *
 * A fachada só despacha: o planejamento de cada agregado vive no seu planejador (contas e cartão,
 * lançamento direto, compra no cartão, boleto, renda, recorrência, categorias, limites, estornos,
 * importação e desfazer) e as consultas do orçamento e da fatura em `BudgetQueries` e `InvoiceQueries`
 * (accord-dj6a). Nada mudou em regra financeira, identidade ou sincronização.
 */
class RoomFinanceSpace private constructor(
    private val db: AccordDatabase,
    private val clock: BudgetClock,
    private val sessions: SessionProvider,
    private val nowMillis: () -> Long,
    private val syncStatus: SyncStatusProvider,
    private val beforeCommit: () -> Unit,
) : FinanceSpace {
    private val dao = db.dao()
    private val syncDao = db.syncDao()
    private val refundPlanner = RefundPlanner(dao) { cycleOf(sessions.current().spaceId) }
    private val cancellationPlanner = CancellationPlanner(dao)
    private val deletePlanner = DeletePlanner(dao)
    private val correctionPlanner = CorrectionPlanner(dao) { cycleOf(sessions.current().spaceId) }
    private val paymentMethodPlanner = PaymentMethodPlanner(dao) { effectiveDefaultAccount(it) }
    private val limitsPlanner = LimitsPlanner(dao) { tree() }
    private val accountPlanner = AccountPlanner(dao)
    private val commonRules = CommonRules(dao)
    private val directPaymentPlanner = DirectPaymentPlanner(dao, commonRules, accountPlanner)
    private val cardPurchasePlanner = CardPurchasePlanner(dao, commonRules) { cycleOf(it) }
    private val boletoPlanner = BoletoPlanner(dao, commonRules, accountPlanner)
    private val incomePlanner = IncomePlanner(dao, commonRules, accountPlanner)
    private val recurrencePlanner = RecurrencePlanner(dao, commonRules, accountPlanner, sessions) { cycleOf(it) }
    private val classifyPlanner = ClassifyPlanner(dao)
    private val invoiceQueries = InvoiceQueries(dao, clock, { recurrencePlanner.occurrencesIn(it) }) { cycleOf(sessions.current().spaceId) }
    private val budgetQueries = BudgetQueries(dao, { tree() }, { recurrencePlanner.occurrencesIn(it) }, limitsPlanner)
    private val entryQueries = EntryQueries(
        dao,
        tree = { tree() },
        cycle = { cycleOf(sessions.current().spaceId) },
        occurrences = { occurrencesIn(it) },
        invoiceDifference = { invoiceView(it).totalDifference },
        today = { clock.today() },
    )
    internal val importEvaluator = ImportEvaluator(
        dao, sessions,
        cycle = { cycleOf(sessions.current().spaceId) },
        invoice = { invoiceView(it) },
        cardId = { cardId(it) },
        applicable = { command -> plan(sessions.current(), Origin.Import, command) is Planned.Go },
    )
    private val forecastQueries = ForecastQueries(
        dao, syncDao,
        tree = { tree() },
        cycle = { cycleOf(sessions.current().spaceId) },
        today = { clock.today() },
        nowMillis = nowMillis,
        budget = { (budget(it) as QueryResult.Data).value },
        limit = { categoryId, month -> limitsPlanner.timeline(categoryId).settingFor(month) },
        occurrences = { occurrencesIn(it) },
        pendings = { entryQueries.pendings() },
        feesCategoryId = { DeterministicIds.seedCategory(sessions.current().spaceId, CategorySeed.FEES_AND_INTEREST) },
        invoiceConferredClosed = { invoiceView(it).let { view -> view.conferred || view.paid } },
    )
    private val subscriptionQueries = SubscriptionQueries(
        dao,
        tree = { tree() },
        subscriptionsMainId = { DeterministicIds.seedCategory(sessions.current().spaceId, "assinaturas") },
        load = { loadSeries(it) },
        place = { loaded, number -> place(loaded, number, cycleOf(sessions.current().spaceId)) },
        month = { clock.currentMonth() },
    )
    private val comparisonQueries = ComparisonQueries(
        budget = { (budget(it) as QueryResult.Data).value },
        activity = { from, until -> entryQueries.purchaseActivity(from, until, null) },
        today = { clock.today() },
    )
    private val weeklyReviewQueries = WeeklyReviewQueries(
        activity = { from, until -> entryQueries.purchaseActivity(from, until, null) },
        forecast = { forecastQueries.forecasts(it) },
        pendings = { entryQueries.pendings() },
        commitments = { entryQueries.commitments(it, CommitmentKind.All) },
    )
    private val alertCenter = AlertCenter(
        db, clock, sessions,
        forecast = { forecastQueries.forecasts(it) },
        run = { envelope, command -> execute(envelope, command) },
        recorded = { alertsRecorded() },
    )

    /**
     * O adaptador de notificações do aparelho (S5.5) se registra aqui para ser avisado quando um evento novo é gravado. Fica fora do contrato
     * da fachada porque é do aparelho, não do espaço.
     */
    @Volatile
    var onAlertRecorded: suspend () -> Unit = {}

    private suspend fun alertsRecorded() = onAlertRecorded()
    private val importPlanner = ImportPlanner(dao, importEvaluator, { session, envelope, command -> applyInTransaction(session, envelope, command) }, nowMillis)
    private val iconPlanner = IconPlanner(dao) { entryDetail(it) != null }
    private val intentionPlanner = IntentionPlanner(dao, nowMillis)
    private val categoryPlanner = CategoryPlanner(dao, limitsPlanner, { tree() }) { clock.currentMonth() }
    private val invoicePlanner = InvoicePlanner(
        dao,
        knownTotal = { composition(it).known },
        requiredAccount = { session, given, errors -> resolveRequiredAccount(session, given, errors) },
    )

    private val undoPlanner = UndoPlanner(dao, correctionPlanner, paymentMethodPlanner, cancellationPlanner, invoicePlanner)

    // ---- planejamento ----

    private suspend fun plan(session: Session, origin: Origin, command: Command): Planned = when (command) {
        is CreateCategory -> categoryPlanner.planCreate(command)
        is CreateIncomeCategory -> incomePlanner.planCreateCategory(command)
        is RenameIncomeCategory -> incomePlanner.planRenameCategory(command)
        is SetIconAssociation -> iconPlanner.planSet(session, command)
        is ClearIconAssociation -> iconPlanner.planClear(session, command)
        is CreateIntention -> intentionPlanner.planCreate(session, command)
        is EditIntention -> intentionPlanner.planEdit(session, command)
        is AddAccount -> accountPlanner.planAdd(command)
        is SetMyDefaultAccount -> accountPlanner.planSetDefault(session, command)
        is RegisterDirectPayment -> directPaymentPlanner.plan(session, origin, command)
        is ConfigureCard -> accountPlanner.planConfigureCard(session, command)
        is RegisterCardPurchase -> cardPurchasePlanner.plan(session, origin, command)
        is CorrectInvoiceAssignment -> cardPurchasePlanner.planCorrectInvoice(command)
        is RegisterBoleto -> boletoPlanner.planRegister(session, origin, command)
        is PayBoleto -> boletoPlanner.planPay(session, command)
        is RegisterIncome -> incomePlanner.planRegister(session, origin, command)
        is ReceiveIncome -> incomePlanner.planReceive(session, command)
        is CreateRecurrence -> recurrencePlanner.planCreate(session, origin, command)
        is EditRecurrence -> recurrencePlanner.planEdit(command)
        is DeleteRecurrence -> recurrencePlanner.planDelete(command)
        is PayOccurrence -> recurrencePlanner.planPay(session, command)
        is CorrectOccurrenceInvoice -> recurrencePlanner.planCorrectInvoice(command)
        is RegisterRefund -> refundPlanner.planRegister(session, origin, command)
        is ConfirmRefund -> refundPlanner.planConfirm(command)
        is RequestInstallmentCancellation -> cancellationPlanner.planRequest(command)
        is ConfirmNonCharge -> cancellationPlanner.planConfirm(command)
        is RegisterInvoiceTotal -> invoicePlanner.planTotal(session, command)
        is PayInvoice -> invoicePlanner.planPay(session, origin, command)
        is DeleteEntry -> deletePlanner.plan(command)
        is CorrectEntry -> correctionPlanner.plan(command)
        is ChangePaymentMethod -> paymentMethodPlanner.plan(session, command)
        is DefineLimits -> limitsPlanner.planDefine(session, command)
        is RenameCategory -> categoryPlanner.planRename(command)
        is ReorderCategories -> categoryPlanner.planReorder(command)
        is MoveSubcategory -> categoryPlanner.planMove(session, command)
        is EditCategory -> categoryPlanner.planEdit(command)
        is MoveCategoryEntries -> categoryPlanner.planMoveEntries(session, command)
        is RemoveCategory -> categoryPlanner.planRemove(session, command)
        is EditAccount -> accountPlanner.planEdit(command)
        is ClassifyEntry -> classifyPlanner.plan(command)
        is RecordAlertEvent -> alertCenter.plan(command, nowMillis())
        is ConfirmImportBatch -> importPlanner.plan(session, command)
        is ResolveImportItem -> importPlanner.planResolve(session, command)
        is Undo -> undoPlanner.plan(command)
    }

    // ---- execução e prévia ----

    override suspend fun execute(envelope: Envelope, command: Command): CommandResult {
        val result = executeCommand(envelope, command)
        // S5: depois de um comando confirmado, a fachada avalia os avisos do mês corrente (fora da transação do comando, que já está válido).
        if (result is CommandResult.Applied && changesBudgetNumbers(command)) evaluateAlerts()
        return result
    }

    /** Cadastros de conta e de categoria de renda não mudam número nenhum do orçamento; o próprio registro do evento nunca dispara avaliação. */
    private fun changesBudgetNumbers(command: Command) = command !is RecordAlertEvent && command !is AddAccount && command !is SetMyDefaultAccount &&
        command !is CreateIncomeCategory && command !is RenameIncomeCategory && command !is SetIconAssociation && command !is ClearIconAssociation &&
        command !is CreateIntention && command !is EditIntention

    /**
     * Avaliação dos avisos de possível excesso (S5, ADR 0044). Só mudança nos dados avalia: a fachada a chama depois dos comandos confirmados e o motor de
     * sincronização depois de aplicar o que chegou. Nunca falha quem a chamou.
     */
    suspend fun evaluateAlerts() = alertCenter.evaluate(app.accord.core.alerts.AlertTrigger.DataChange)

    private suspend fun executeCommand(envelope: Envelope, command: Command): CommandResult {
        val session = sessions.current()
        return try {
            db.useWriterConnection { connection ->
                connection.immediateTransaction { applyInTransaction(session, envelope, command) }
            }
        } catch (abort: ImportAbort) {
            // Um passo da confirmação de importação foi recusado: a transação inteira foi desfeita.
            abort.result
        }
    }

    /**
     * Planeja um comando e grava as entidades e a operação **na transação já aberta**. A confirmação de importação reaproveita este caminho
     * para cada comando de S1 que aplica, o que mantém um só validador e um só registro de operações (S4 "Confirmação").
     */
    private suspend fun applyInTransaction(session: Session, envelope: Envelope, command: Command): CommandResult {
        val existing = dao.operation(envelope.commandId.value)
        if (existing != null) return CommandResult.AlreadyApplied(receiptOf(existing))

        return when (val planned = plan(session, envelope.origin, command)) {
            is Planned.Stop -> planned.result
            is Planned.Go -> {
                val committed = planned.commit()
                // Estado completo de cada entidade depois do comando: é o que a operação leva ao serviço.
                // A versão base é a última reconhecida; fica em aberto (encadeada) quando outra operação
                // ainda não reconhecida altera a mesma entidade, e é fixada quando ela for reconhecida.
                val changeRows = committed.map { change ->
                    val snapshot = checkNotNull(EntityStates.read(dao, change.type, change.id)) {
                        "Entidade ${change.type} ${change.id} sumiu depois do comando"
                    }
                    val chained = syncDao.openOperationsOn(change.type.name, change.id).isNotEmpty()
                    OperationChangeRow(
                        operationId = envelope.commandId.value,
                        entityType = change.type.name,
                        entityId = change.id,
                        baseVersion = if (chained) null else dao.serviceVersion(change.type.name, change.id),
                        revision = change.revision,
                        state = snapshot.state,
                        tombstone = snapshot.tombstone,
                        refs = EntityStates.encodeReferences(snapshot.references),
                        chained = chained,
                    )
                }
                val changes = changeRows.map { EntityChange(EntityType.valueOf(it.entityType), it.entityId, it.baseVersion, it.revision) }
                val row = OperationRow(
                    operationId = envelope.commandId.value,
                    spaceId = session.spaceId,
                    authorId = session.personId,
                    origin = envelope.origin.name,
                    commandType = command::class.simpleName.orEmpty(),
                    deviceTimeMillis = nowMillis(),
                    affectedMonths = planned.months.sorted().joinToString(","),
                    undoable = planned.undoable,
                    acknowledged = false,
                    detail = planned.detail,
                )
                val sequence = dao.insertOperation(row)
                dao.insertChanges(changeRows)
                envelope.draftId?.let { dao.deleteDraft(it) }
                beforeCommit()
                CommandResult.Applied(receiptOf(row.copy(sequence = sequence), changes))
            }
        }
    }

    /** Sessão de importação OFX/PDF (S4) sobre esta fachada: a confirmação passa por [ConfirmImportBatch]. */
    fun importSession(): ImportSession = RoomImportSession(db, this, importEvaluator, sessions, nowMillis)

    override suspend fun preview(command: Command): PreviewResult =
        when (val planned = plan(sessions.current(), Origin.Manual, command)) {
            is Planned.Stop -> PreviewResult.Blocked(planned.result)
            is Planned.Go -> PreviewResult.Ready(
                planned.before, planned.after, planned.months, planned.replaced, planned.linkedRefundIds,
                planned.replacedLimitMonths, planned.suggestedInvoice, planned.categoryMove,
            )
        }

    // ---- apoio compartilhado pelos planejadores e consultas ----

    private suspend fun tree() = CategoryTree(dao.categories().filterNot { it.removed }.associateBy { it.id }, dao.categoryMoves())

    private fun cardId(spaceId: String) = cardIdOf(spaceId)

    private suspend fun effectiveDefaultAccount(session: Session) = accountPlanner.effectiveDefault(session)

    private suspend fun resolveRequiredAccount(session: Session, given: String?, errors: MutableList<FieldError>) =
        accountPlanner.resolveRequired(session, given, errors)

    private suspend fun occurrencesIn(month: YearMonth) = recurrencePlanner.occurrencesIn(month)

    private suspend fun composition(month: YearMonth) = invoiceQueries.composition(month)

    private suspend fun invoiceView(month: YearMonth) = invoiceQueries.view(month)

    internal suspend fun loadSeries(row: RecurrenceRow) = recurrencePlanner.loadSeries(row)

    internal fun place(loaded: LoadedSeries, number: Int, cycle: CardCycle) = recurrencePlanner.place(loaded, number, cycle)

    private suspend fun cycleOf(spaceId: String): CardCycle {
        val card = dao.card(cardId(spaceId)) ?: error("Cartão do espaço não foi criado")
        return CardCycle(card.closingDay, card.dueDay)
    }

    // ---- recibos e operações ----

    private suspend fun receiptOf(op: OperationRow, changes: List<EntityChange>? = null): Receipt = Receipt(
        operationId = op.operationId,
        sequence = op.sequence,
        commandType = op.commandType,
        changes = changes ?: dao.changesOf(listOf(op.operationId)).map { it.toChange() },
        affectedMonths = parseMonths(op.affectedMonths),
        undoable = op.undoable,
    )

    private fun OperationChangeRow.toChange() =
        EntityChange(EntityType.valueOf(entityType), entityId, baseVersion, revision)

    // ---- lista, detalhe, compromissos e pendências ----

    private suspend fun <T> unwrap(result: QueryResult<T>): T = when (result) {
        is QueryResult.Data -> result.value
        is QueryResult.Failure -> throw result.cause
    }

    override suspend fun entries(filter: EntryFilter, cursor: Int, pageSize: Int): QueryResult<EntryPage> =
        query { entryQueries.entries(filter, cursor, pageSize) }

    override suspend fun totalBalance(criterion: DateCriterion, month: YearMonth): QueryResult<TotalBalance> =
        query { entryQueries.totalBalance(criterion, month) }

    override suspend fun commitments(month: YearMonth, kind: CommitmentKind): QueryResult<MonthCommitments> =
        query { entryQueries.commitments(month, kind) }

    override suspend fun pendings(): QueryResult<List<PendingItem>> = query { entryQueries.pendings() }

    override suspend fun subscriptions(from: LocalDate, untilExclusive: LocalDate): QueryResult<SubscriptionList> =
        query { subscriptionQueries.list(from, untilExclusive) }

    override suspend fun purchaseActivity(from: LocalDate, untilExclusive: LocalDate, categoryId: String?): QueryResult<PurchaseActivity> =
        query { entryQueries.purchaseActivity(from, untilExclusive, categoryId) }

    override suspend fun comparePeriods(
        criterion: DateCriterion,
        grouping: ComparisonGrouping,
        a: ComparisonPeriod,
        b: ComparisonPeriod,
    ): QueryResult<PeriodComparison> = query { comparisonQueries.compare(criterion, grouping, a, b) }

    override suspend fun weeklyReview(period: ReviewPeriod, reference: LocalDate): QueryResult<WeeklyReviewSummary> =
        query { weeklyReviewQueries.review(period, reference) }

    override suspend fun subscription(recurrenceId: String): QueryResult<SubscriptionDetail?> =
        query { subscriptionQueries.detail(recurrenceId, clock.today()) }

    private fun directMethod(kind: String) = when (DirectPaymentKind.valueOf(kind)) {
        DirectPaymentKind.Pix -> PaymentMethod.Pix
        DirectPaymentKind.Debit -> PaymentMethod.Debit
        DirectPaymentKind.Cash -> PaymentMethod.Cash
    }

    private suspend fun occurrenceView(recurrenceId: String, number: Int): OccurrenceView? {
        val row = dao.recurrence(recurrenceId)?.takeIf { !it.voided } ?: return null
        return place(loadSeries(row), number, cycleOf(sessions.current().spaceId))
    }

    override suspend fun entry(id: String): QueryResult<EntryDetail?> = query { entryDetail(id) }

    private suspend fun entryDetail(id: String): EntryDetail? {
        val card = cycleOf(sessions.current().spaceId)
        suspend fun sync(type: EntityType, entityId: String) = syncStatus.statusOf(type, entityId)

        if ('#' in id) {
            val purchaseId = id.substringBefore('#')
            val number = id.substringAfter('#').toIntOrNull() ?: return null
            val purchase = unwrap(purchase(purchaseId)) ?: return null
            val installment = purchase.installments.firstOrNull { it.number == number } ?: return null
            return EntryDetail(
                id = id, kind = EntryKind.CardInstallment, description = purchase.description,
                classification = purchase.classification, amount = installment.amount,
                purchaseDate = purchase.purchaseDate, dueDate = card.dueDateOfInvoice(installment.invoiceMonth), settledDate = null,
                method = PaymentMethod.Card, accountId = null,
                authorId = dao.purchase(purchaseId)?.authorId, spentBy = dao.purchase(purchaseId)?.spentBy,
                origin = dao.purchase(purchaseId)?.origin?.let(Origin::valueOf),
                revision = purchase.revision, voided = purchase.voided, sync = sync(EntityType.CardPurchase, purchaseId),
                purchase = purchase, boleto = null, income = null, occurrence = null,
                refunds = unwrap(refunds(purchaseId)),
            )
        }
        if (':' in id) {
            val recurrenceId = id.substringBefore(':')
            val number = id.substringAfter(':').toIntOrNull() ?: return null
            val occurrence = occurrenceView(recurrenceId, number) ?: return null
            val row = dao.recurrence(recurrenceId)!!
            return EntryDetail(
                id = id, kind = EntryKind.RecurrenceOccurrence, description = occurrence.description,
                classification = occurrence.categoryId?.let(Classification::Category) ?: Classification.ToClassify,
                amount = occurrence.settledAmount ?: occurrence.expectedAmount,
                purchaseDate = null, dueDate = occurrence.scheduledDate, settledDate = occurrence.settledDate,
                method = if (occurrence.form == RecurrenceForm.Card && occurrence.kind == RecurrenceKind.Expense) PaymentMethod.Card else null,
                accountId = occurrence.accountId, authorId = row.authorId, spentBy = null, origin = Origin.valueOf(row.origin),
                revision = row.revision, voided = false, sync = sync(EntityType.Recurrence, recurrenceId),
                purchase = null, boleto = null, income = null, occurrence = occurrence, refunds = emptyList(),
            )
        }
        dao.movement(id)?.let { m ->
            return EntryDetail(
                id = id, kind = EntryKind.DirectPayment, description = m.description,
                classification = m.classificationId?.let(Classification::Category) ?: Classification.ToClassify,
                amount = Money(m.amountCents), purchaseDate = LocalDate.ofEpochDay(m.epochDay), dueDate = null, settledDate = null,
                method = directMethod(m.kind), accountId = m.accountId, authorId = m.authorId, spentBy = m.spentBy,
                origin = Origin.valueOf(m.origin), revision = m.revision, voided = m.voided, sync = sync(EntityType.Movement, id),
                purchase = null, boleto = null, income = null, occurrence = null, refunds = unwrap(refunds(id)),
            )
        }
        dao.purchase(id)?.let { p ->
            val detail = unwrap(purchase(id))!!
            return EntryDetail(
                id = id, kind = EntryKind.CardPurchase, description = p.description, classification = detail.classification,
                amount = Money(p.totalCents), purchaseDate = LocalDate.ofEpochDay(p.purchaseEpochDay), dueDate = null, settledDate = null,
                method = PaymentMethod.Card, accountId = null, authorId = p.authorId, spentBy = p.spentBy,
                origin = Origin.valueOf(p.origin), revision = p.revision, voided = p.voided, sync = sync(EntityType.CardPurchase, id),
                purchase = detail, boleto = null, income = null, occurrence = null, refunds = unwrap(refunds(id)),
            )
        }
        dao.boleto(id)?.let { b ->
            val view = unwrap(boleto(id))!!
            return EntryDetail(
                id = id, kind = EntryKind.Boleto, description = b.description, classification = view.classification,
                amount = view.paidAmount ?: view.expectedAmount, purchaseDate = null, dueDate = view.dueDate, settledDate = view.paidDate,
                method = PaymentMethod.Boleto, accountId = b.accountId, authorId = b.authorId, spentBy = null,
                origin = Origin.valueOf(b.origin), revision = b.revision, voided = b.voided, sync = sync(EntityType.Boleto, id),
                purchase = null, boleto = view, income = null, occurrence = null, refunds = unwrap(refunds(id)),
            )
        }
        dao.income(id)?.let { i ->
            val view = i.toView()
            return EntryDetail(
                id = id, kind = EntryKind.Income, description = i.description, classification = Classification.ToClassify,
                amount = view.receivedAmount ?: view.expectedAmount, purchaseDate = null, dueDate = view.expectedDate,
                settledDate = view.receivedDate, method = null, accountId = i.accountId, authorId = i.authorId, spentBy = null,
                origin = Origin.valueOf(i.origin), revision = i.revision, voided = i.voided, sync = sync(EntityType.Income, id),
                purchase = null, boleto = null, income = view, occurrence = null, refunds = emptyList(),
            )
        }
        dao.refund(id)?.let { r ->
            val originDescription = when (r.originType) {
                RefundPlanner.ORIGIN_PURCHASE -> dao.purchase(r.originId)?.description
                RefundPlanner.ORIGIN_MOVEMENT -> dao.movement(r.originId)?.description
                else -> dao.boleto(r.originId)?.description
            }
            val originClassification = when (r.originType) {
                RefundPlanner.ORIGIN_PURCHASE -> dao.purchase(r.originId)?.classificationId
                RefundPlanner.ORIGIN_MOVEMENT -> dao.movement(r.originId)?.classificationId
                else -> dao.boleto(r.originId)?.classificationId
            }
            return EntryDetail(
                id = id, kind = EntryKind.Refund, description = "Estorno: ${originDescription.orEmpty()}",
                classification = originClassification?.let(Classification::Category) ?: Classification.ToClassify,
                amount = Money(r.amountCents), purchaseDate = null, dueDate = null, settledDate = r.epochDay?.let(LocalDate::ofEpochDay),
                method = null, accountId = null, authorId = r.authorId, spentBy = null, origin = Origin.valueOf(r.origin),
                revision = r.revision, voided = r.voided, sync = sync(EntityType.Refund, id),
                purchase = null, boleto = null, income = null, occurrence = null, refunds = unwrap(refunds(r.originId)),
            )
        }
        dao.invoicePayment(id)?.let { p ->
            return EntryDetail(
                id = id, kind = EntryKind.InvoicePayment, description = "Pagamento da fatura de ${YearMonth.parse(p.dueMonth).label()}",
                classification = Classification.ToClassify, amount = Money(p.amountCents), purchaseDate = null, dueDate = null,
                settledDate = LocalDate.ofEpochDay(p.epochDay), method = null, accountId = p.accountId, authorId = p.authorId,
                spentBy = null, origin = Origin.valueOf(p.origin), revision = p.revision, voided = p.voided,
                sync = sync(EntityType.InvoicePayment, id),
                purchase = null, boleto = null, income = null, occurrence = null, refunds = emptyList(),
            )
        }
        return null
    }

    override suspend fun commitment(id: String): QueryResult<CommitmentDetail?> = query { commitmentDetail(id) }

    private suspend fun commitmentDetail(id: String): CommitmentDetail? {
        val card = cycleOf(sessions.current().spaceId)
        if ('#' in id) {
            val purchase = unwrap(purchase(id.substringBefore('#'))) ?: return null
            if (purchase.voided) return null
            val paidMonths = dao.paidInvoiceMonths().toSet()
            return CommitmentDetail.Installments(
                id = id, purchase = purchase,
                schedule = purchase.installments.map { i ->
                    ScheduleRow(
                        number = i.number, invoiceMonth = i.invoiceMonth, dueDate = card.dueDateOfInvoice(i.invoiceMonth), amount = i.amount,
                        situations = setOfNotNull(
                            if (i.invoiceMonth.toString() in paidMonths) EntrySituation.Paid else EntrySituation.NotPaid,
                            EntrySituation.CancelRequested.takeIf { i.cancel == CancelState.CancelRequested },
                            EntrySituation.Cancelled.takeIf { i.cancel == CancelState.Cancelled },
                            EntrySituation.InvoiceLinkToConfirm.takeIf { i.assignment == AssignmentState.ToConfirm },
                        ),
                    )
                },
            )
        }
        if (':' in id) {
            val recurrenceId = id.substringBefore(':')
            val number = id.substringAfter(':').toIntOrNull() ?: return null
            val row = dao.recurrence(recurrenceId)?.takeIf { !it.voided } ?: return null
            val loaded = loadSeries(row)
            val current = place(loaded, number, card) ?: return null
            // Recorrência não tem total: só frequência e a próxima ocorrência que ainda existe.
            val next = (number + 1..number + 120).firstNotNullOfOrNull { place(loaded, it, card) }
            return CommitmentDetail.Recurring(id, unwrap(recurrence(recurrenceId))!!, current, next)
        }
        val boleto = unwrap(boleto(id))?.takeIf { !it.voided } ?: return null
        return CommitmentDetail.Bill(id, boleto)
    }

    // ---- reconhecimento pelo serviço ----

    override suspend fun recordAcknowledgement(operationId: String, serviceVersions: Map<EntityRef, Long>): QueryResult<Unit> = query {
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                require(dao.operation(operationId) != null) { "Operação desconhecida: $operationId" }
                dao.upsertSync(serviceVersions.map { (ref, version) -> EntitySyncRow(ref.type.name, ref.id, version) })
                dao.markAcknowledged(operationId)
            }
        }
    }

    // ---- rascunhos locais ----

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)
    private fun decode(text: String) = URLDecoder.decode(text, Charsets.UTF_8)

    override suspend fun saveDraft(draft: LocalDraft): QueryResult<String> = query {
        val id = draft.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsertDraft(
            DraftRow(
                id = id,
                source = draft.source.name,
                fields = draft.fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" },
                missing = draft.missing.joinToString("&") { encode(it) },
                reason = draft.reason,
                updatedAtMillis = nowMillis(),
            ),
        )
        id
    }

    override suspend fun discardDraft(id: String): QueryResult<Unit> = query { dao.deleteDraft(id) }

    override suspend fun drafts(): QueryResult<List<LocalDraft>> = query {
        dao.drafts().map {
            LocalDraft(
                id = it.id,
                source = DraftSource.valueOf(it.source),
                fields = if (it.fields.isEmpty()) emptyMap()
                else it.fields.split("&").associate { pair -> decode(pair.substringBefore("=")) to decode(pair.substringAfter("=")) },
                missing = if (it.missing.isEmpty()) emptyList() else it.missing.split("&").map(::decode),
                reason = it.reason,
                updatedAtMillis = it.updatedAtMillis,
            )
        }
    }

    // ---- consultas ----

    private suspend fun <T> query(block: suspend () -> T): QueryResult<T> = try {
        QueryResult.Data(block())
    } catch (e: CancellationException) {
        // Cancelamento de quem chamou segue adiante; já o banco fechado por baixo (sair da conta, por
        // exemplo) cancela a consulta sem cancelar quem chamou e vira falha identificada, nunca zero.
        if (currentCoroutineContext().isActive) QueryResult.Failure(e) else throw e
    } catch (e: Exception) {
        QueryResult.Failure(e)
    }

    override suspend fun categories(): QueryResult<List<CategoryNode>> = query {
        val now = clock.currentMonth()
        val categories = tree()
        dao.categories().filterNot { it.removed }.map {
            CategoryNode(it.id, categories.parentAt(it.id, now), it.name, it.sortOrder, it.iconKey, it.seedKey, it.colorIndex, it.revision)
        }
    }

    override suspend fun iconAssociations(): QueryResult<List<IconAssociationView>> = query { iconPlanner.viewsOf(dao.iconAssociations()) }

    override suspend fun intentions(): QueryResult<List<IntentionView>> = query {
        val links = dao.originLinks().associateBy { it.intentionId }
        dao.intentions().map { intentionPlanner.viewOf(it, syncStatus.statusOf(EntityType.Intention, it.id), links[it.id]) }
    }

    override suspend fun intention(id: String): QueryResult<IntentionView?> = query {
        dao.intention(id)?.let { row ->
            intentionPlanner.viewOf(row, syncStatus.statusOf(EntityType.Intention, row.id), dao.originLinks().firstOrNull { it.intentionId == row.id })
        }
    }

    override suspend fun incomeCategories(): QueryResult<List<IncomeCategoryNode>> = query {
        dao.incomeCategories().map { IncomeCategoryNode(it.id, it.name, it.sortOrder, it.iconKey, it.seedKey, it.revision) }
    }

    override suspend fun accounts(): QueryResult<AccountsView> = query {
        val session = sessions.current()
        val defaultId = effectiveDefaultAccount(session)
        AccountsView(
            accounts = dao.accounts().map {
                val bank = if (it.bankIspb != null) BankRef.Catalog(it.bankIspb, it.bankName) else BankRef.Other(it.bankName)
                AccountInfo(it.id, bank, it.nickname)
            },
            myDefaultAccountId = defaultId,
        )
    }

    override suspend fun card(): QueryResult<CardInfo> = query {
        val row = dao.card(cardId(sessions.current().spaceId)) ?: error("Cartão do espaço não foi criado")
        val bank = row.bankName?.let { if (row.bankIspb != null) BankRef.Catalog(row.bankIspb, it) else BankRef.Other(it) }
        CardInfo(row.id, bank, row.nickname, row.closingDay, row.dueDay, row.creditLimitCents?.let(::Money), configured = row.revision > 0)
    }

    override suspend fun purchase(id: String): QueryResult<PurchaseDetail?> = query {
        dao.purchase(id)?.let { p ->
            PurchaseDetail(
                id = p.id,
                description = p.description,
                classification = p.classificationId?.let(Classification::Category) ?: Classification.ToClassify,
                purchaseDate = LocalDate.ofEpochDay(p.purchaseEpochDay),
                total = Money(p.totalCents),
                isCharge = p.isCharge,
                revision = p.revision,
                voided = p.voided,
                installments = dao.installments(p.id).map { it.toView() },
            )
        }
    }

    override suspend fun movements(month: YearMonth): QueryResult<List<MovementRow>> = query {
        dao.movementsBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map {
            MovementRow(
                id = it.id,
                kind = DirectPaymentKind.valueOf(it.kind),
                description = it.description,
                classification = it.classificationId?.let(Classification::Category) ?: Classification.ToClassify,
                date = LocalDate.ofEpochDay(it.epochDay),
                amount = Money(it.amountCents),
                accountId = it.accountId,
                spentBy = it.spentBy,
                revision = it.revision,
            )
        }
    }

    // ---- faturas ----

    override suspend fun invoice(dueMonth: YearMonth): QueryResult<InvoiceView> = query { invoiceView(dueMonth) }

    override suspend fun invoices(): QueryResult<List<InvoiceView>> = query {
        dao.invoiceMonthsWithData().map(YearMonth::parse).sorted().map { invoiceView(it) }
    }

    override suspend fun limits(month: YearMonth): QueryResult<List<CategoryLimit>> = query { limitsPlanner.views(month) }

    override suspend fun refunds(originId: String): QueryResult<List<RefundView>> = query {
        dao.refundsOf(originId).filterNot { it.voided }.map {
            RefundView(
                id = it.id,
                originId = it.originId,
                amount = Money(it.amountCents),
                status = RefundStatus.valueOf(it.status),
                date = it.epochDay?.let(LocalDate::ofEpochDay),
                creditMonth = it.creditMonth?.let(YearMonth::parse),
                revision = it.revision,
                voided = it.voided,
            )
        }
    }

    override suspend fun recurrence(id: String): QueryResult<RecurrenceView?> = query {
        dao.recurrence(id)?.let {
            RecurrenceView(
                id = it.id,
                kind = RecurrenceKind.valueOf(it.kind),
                form = RecurrenceForm.valueOf(it.form),
                frequency = Frequency.valueOf(it.frequency),
                firstDate = LocalDate.ofEpochDay(it.anchorEpochDay),
                endsBefore = it.endsBefore,
                revision = it.revision,
                voided = it.voided,
            )
        }
    }

    override suspend fun occurrences(month: YearMonth): QueryResult<List<OccurrenceView>> = query { occurrencesIn(month) }

    override suspend fun boleto(id: String): QueryResult<BoletoView?> = query {
        dao.boleto(id)?.let { b ->
            val due = LocalDate.ofEpochDay(b.dueEpochDay)
            val paid = b.paidEpochDay?.let(LocalDate::ofEpochDay)
            BoletoView(
                id = b.id,
                description = b.description,
                classification = b.classificationId?.let(Classification::Category) ?: Classification.ToClassify,
                dueDate = due,
                expectedAmount = Money(b.expectedCents),
                paidDate = paid,
                paidAmount = b.paidCents?.let(::Money),
                accountId = b.accountId,
                budgetMonth = YearMonth.from(paid ?: due),
                overdue = paid == null && due.isBefore(clock.today()),
                revision = b.revision,
                voided = b.voided,
            )
        }
    }

    override suspend fun income(id: String): QueryResult<IncomeView?> = query { dao.income(id)?.toView() }

    override suspend fun incomes(month: YearMonth): QueryResult<IncomeSummary> = query { budgetQueries.incomes(month) }

    override suspend fun budget(month: YearMonth): QueryResult<MonthBudget> = query { budgetQueries.budget(month) }

    override suspend fun alertNotices(preference: AlertPreference, access: NotificationAccess): QueryResult<List<AlertNotice>> =
        query { alertCenter.notices(preference, access) }

    override suspend fun markAlertShown(eventId: String): QueryResult<Unit> = query { alertCenter.markShown(eventId) }

    override suspend fun alertStatuses(): QueryResult<List<AlertStatus>> = query { alertCenter.statuses() }

    override suspend fun alertEvent(id: String): QueryResult<AlertEventView?> = query { alertCenter.event(id) }

    override suspend fun alertPreference(): QueryResult<AlertPreference> = query { alertCenter.preference() }

    override suspend fun setAlertPreference(preference: AlertPreference): QueryResult<Unit> = query { alertCenter.setPreference(preference) }

    override suspend fun forecasts(month: YearMonth): QueryResult<MonthForecast> = query { forecastQueries.forecasts(month) }

    override suspend fun forecastDetail(categoryId: String, month: YearMonth): QueryResult<ForecastDetail?> = query { forecastQueries.detail(categoryId, month) }

    override suspend fun operations(afterSequence: Long, limit: Int): QueryResult<List<LocalOperation>> = query {
        val rows = dao.operationsAfter(afterSequence, limit)
        val changes = if (rows.isEmpty()) emptyMap() else dao.changesOf(rows.map { it.operationId }).groupBy { it.operationId }
        rows.map {
            LocalOperation(
                sequence = it.sequence,
                operationId = it.operationId,
                spaceId = it.spaceId,
                authorId = it.authorId,
                origin = Origin.valueOf(it.origin),
                commandType = it.commandType,
                deviceTimeMillis = it.deviceTimeMillis,
                changes = changes[it.operationId].orEmpty().map { c -> c.toChange() },
                acknowledged = it.acknowledged,
            )
        }
    }

    companion object {
        /**
         * Abre a fachada e garante as categorias iniciais e o cartão. A semente tem identidade
         * determinística e não gera operação: todo aparelho do espaço chega às mesmas linhas.
         */
        suspend fun open(
            database: AccordDatabase,
            clock: BudgetClock,
            sessions: SessionProvider,
            nowMillis: () -> Long = System::currentTimeMillis,
            // Sem operação nem reconhecimento, o item continua "salvo neste aparelho".
            syncStatus: SyncStatusProvider = RoomSyncStatus(database.syncDao()),
            beforeCommit: () -> Unit = {},
        ): RoomFinanceSpace {
            val space = RoomFinanceSpace(database, clock, sessions, nowMillis, syncStatus, beforeCommit)
            space.seed(sessions.current().spaceId)
            return space
        }
    }

    private suspend fun seed(spaceId: String) {
        val orders = mutableMapOf<String?, Int>()
        val rows = CategorySeed.items.map { item ->
            val order = orders.merge(item.parentKey, 1, Int::plus)!! - 1
            CategoryRow(
                id = DeterministicIds.seedCategory(spaceId, item.key),
                parentId = item.parentKey?.let { DeterministicIds.seedCategory(spaceId, it) },
                name = item.name,
                nameKey = NameKey.of(item.name),
                sortOrder = order,
                iconKey = item.icon,
                seedKey = item.key,
                revision = 0,
            )
        }
        dao.insertSeedCategories(rows)
        dao.insertSeedIncomeCategories(
            IncomeCategorySeed.items.mapIndexed { index, item ->
                IncomeCategoryRow(
                    id = DeterministicIds.seedIncomeCategory(spaceId, item.key),
                    name = item.name,
                    nameKey = NameKey.of(item.name),
                    sortOrder = index,
                    iconKey = item.icon,
                    seedKey = item.key,
                    revision = 0,
                )
            },
        )
        // Um cartão por espaço, com os dias padrão 8 e 15 até a pessoa configurar.
        dao.insertSeedCard(CardRow(cardId(spaceId), null, null, "Cartão", CardCycle().closingDay, CardCycle().dueDay, 0))
    }
}
