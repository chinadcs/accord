package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.BudgetClock
import app.accord.core.CardCycle
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.core.simulation.PurchaseSchedule
import app.accord.core.space.AlertEventView
import app.accord.core.space.AlertNotice
import app.accord.core.space.AlertStatus
import app.accord.core.space.RecordAlertEvent
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.ForecastDetail
import app.accord.core.Frequency
import app.accord.core.InvoicePhase
import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.OccurrenceOverride
import app.accord.core.Recurrence
import app.accord.core.RecurrenceEdit
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.RecurrenceSeries
import app.accord.core.ShiftResult
import app.accord.core.Template
import app.accord.core.space.AccountInfo
import app.accord.core.space.AccountsView
import app.accord.core.space.AddAccount
import app.accord.core.space.EditCategory
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.RemoveCategory
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.core.space.BankRef
import app.accord.core.space.BoletoView
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryBudget
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
import app.accord.core.space.Decision
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
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.FinanceSpace
import app.accord.core.space.IncomeSummary
import app.accord.core.space.ItemSync
import app.accord.core.space.MonthCommitments
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PendingItem
import app.accord.core.space.ScheduleRow
import app.accord.core.space.SyncStatusProvider
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.InvoiceView
import app.accord.core.space.PaymentView
import app.accord.core.space.IncomeTotals
import app.accord.core.space.IncomeView
import app.accord.core.space.InstallmentView
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
import app.accord.core.space.Settlement
import app.accord.core.space.ToClassifyLine
import app.accord.core.space.Undo
import app.accord.core.importing.ImportSession
import app.accord.core.label
import app.accord.core.sum
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

    // ---- planejamento ----

    private suspend fun plan(session: Session, origin: Origin, command: Command): Planned = when (command) {
        is CreateCategory -> planCreateCategory(command)
        is CreateIncomeCategory -> planCreateIncomeCategory(command)
        is RenameIncomeCategory -> planRenameIncomeCategory(command)
        is SetIconAssociation -> iconPlanner.planSet(session, command)
        is ClearIconAssociation -> iconPlanner.planClear(session, command)
        is CreateIntention -> intentionPlanner.planCreate(session, command)
        is EditIntention -> intentionPlanner.planEdit(session, command)
        is AddAccount -> planAddAccount(command)
        is SetMyDefaultAccount -> planSetDefaultAccount(session, command)
        is RegisterDirectPayment -> planDirectPayment(session, origin, command)
        is ConfigureCard -> planConfigureCard(session, command)
        is RegisterCardPurchase -> planCardPurchase(session, origin, command)
        is CorrectInvoiceAssignment -> planCorrectInvoice(command)
        is RegisterBoleto -> planRegisterBoleto(session, origin, command)
        is PayBoleto -> planPayBoleto(session, command)
        is RegisterIncome -> planRegisterIncome(session, origin, command)
        is ReceiveIncome -> planReceiveIncome(session, command)
        is CreateRecurrence -> planCreateRecurrence(session, origin, command)
        is EditRecurrence -> planEditRecurrence(command)
        is DeleteRecurrence -> planDeleteRecurrence(command)
        is PayOccurrence -> planPayOccurrence(session, command)
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
        is EditAccount -> planEditAccount(command)
        is ClassifyEntry -> planClassify(command)
        is RecordAlertEvent -> alertCenter.plan(command, nowMillis())
        is ConfirmImportBatch -> importPlanner.plan(session, command)
        is ResolveImportItem -> importPlanner.planResolve(session, command)
        is Undo -> planUndo(command)
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

    // ---- cadastros ----

    private suspend fun tree() = CategoryTree(dao.categories().filterNot { it.removed }.associateBy { it.id }, dao.categoryMoves())

    private suspend fun planCreateCategory(c: CreateCategory): Planned {
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria.")
        val parentId = c.parentId
        if (parentId != null) {
            val parent = dao.activeCategory(parentId)
                ?: return stop("parentId", ErrorCode.UnknownReference, "Categoria principal não encontrada.")
            if (parent.parentId != null) {
                return stop("parentId", ErrorCode.NotAllowed, "Subcategoria não pode ter subcategorias.")
            }
        }
        val key = NameKey.of(name)
        val now = clock.currentMonth()
        val siblings = tree().childrenAt(parentId, now)
        if (siblings.any { it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria com esse nome neste nível. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val row = CategoryRow(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                name = name,
                nameKey = key,
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                iconKey = CategorySeed.CUSTOM_ICON,
                seedKey = null,
                revision = 1,
            )
            dao.insertCategory(row)
            listOf(EntityChange(EntityType.Category, row.id, null, 1))
        }
    }

    private fun bankName(bank: BankRef) = when (bank) {
        is BankRef.Catalog -> bank.name
        is BankRef.Other -> bank.name
    }.trim()

    private suspend fun planAddAccount(c: AddAccount): Planned {
        val nickname = c.nickname.trim()
        val bankName = bankName(c.bank)
        val errors = buildList {
            if (bankName.isEmpty()) add(FieldError("bank", ErrorCode.Required, "Escolha o banco."))
            if (nickname.isEmpty()) add(FieldError("nickname", ErrorCode.Required, "Informe um apelido para a conta."))
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            val row = AccountRow(
                id = UUID.randomUUID().toString(),
                bankIspb = (c.bank as? BankRef.Catalog)?.ispb,
                bankName = bankName,
                nickname = nickname,
                revision = 1,
            )
            dao.insertAccount(row)
            listOf(EntityChange(EntityType.Account, row.id, null, 1))
        }
    }

    private suspend fun planEditAccount(c: EditAccount): Planned {
        val account = dao.account(c.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != account.revision) {
            return Planned.Stop(CommandResult.StaleVersion(account.id, account.revision))
        }
        if (c.nickname == null && c.bank == null) return stop("nickname", ErrorCode.Required, "Informe o que mudou.")
        val nickname = c.nickname?.trim()
        if (nickname != null && nickname.isEmpty()) return stop("nickname", ErrorCode.Required, "Informe um apelido para a conta.")
        val bank = c.bank
        if (bank != null && bankName(bank).isEmpty()) return stop("bank", ErrorCode.Required, "Escolha o banco.")
        // Correção de cadastro: mesma identidade; lançamentos antigos passam a exibir o banco e o apelido atuais.
        return Planned.Go(months = emptySet()) {
            val next = account.copy(
                nickname = nickname ?: account.nickname,
                bankIspb = if (bank != null) (bank as? BankRef.Catalog)?.ispb else account.bankIspb,
                bankName = if (bank != null) bankName(bank) else account.bankName,
                revision = account.revision + 1,
            )
            dao.upsertAccount(next)
            listOf(EntityChange(EntityType.Account, next.id, null, next.revision))
        }
    }

    private suspend fun planSetDefaultAccount(session: Session, c: SetMyDefaultAccount): Planned {
        dao.account(c.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        return Planned.Go(months = emptySet()) {
            val id = DeterministicIds.preferences(session.spaceId, session.personId)
            val revision = (dao.preferences(id)?.revision ?: 0) + 1
            dao.upsertPreferences(PreferencesRow(id, session.personId, c.accountId, revision))
            listOf(EntityChange(EntityType.Preferences, id, null, revision))
        }
    }

    private suspend fun planConfigureCard(session: Session, c: ConfigureCard): Planned {
        val bank = c.bank
        val errors = buildList {
            if (c.closingDay !in 1..31) add(FieldError("closingDay", ErrorCode.OutOfRange, "O dia de fechamento vai de 1 a 31."))
            if (c.dueDay !in 1..31) add(FieldError("dueDay", ErrorCode.OutOfRange, "O dia de vencimento vai de 1 a 31."))
            if (c.nickname.isBlank()) add(FieldError("nickname", ErrorCode.Required, "Informe um apelido para o cartão."))
            if (bank != null && bankName(bank).isEmpty()) add(FieldError("bank", ErrorCode.Required, "Informe o nome do banco."))
            if (c.creditLimit?.cents?.let { it <= 0 } == true) add(FieldError("creditLimit", ErrorCode.OutOfRange, "O limite deve ser maior que zero."))
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            // Não remapeia atribuições existentes: só as novas compras usam os dias novos.
            val id = cardId(session.spaceId)
            val revision = (dao.card(id)?.revision ?: 0) + 1
            dao.upsertCard(
                CardRow(
                    id = id,
                    bankIspb = (c.bank as? BankRef.Catalog)?.ispb,
                    bankName = c.bank?.let(::bankName),
                    nickname = c.nickname.trim(),
                    closingDay = c.closingDay,
                    dueDay = c.dueDay,
                    revision = revision,
                    creditLimitCents = c.creditLimit?.cents,
                ),
            )
            listOf(EntityChange(EntityType.Card, id, null, revision))
        }
    }

    private fun cardId(spaceId: String) = DeterministicIds.of(spaceId, "card", "main")

    // ---- lançamentos diretos ----

    private suspend fun planDirectPayment(session: Session, origin: Origin, c: RegisterDirectPayment): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val chosenId = validateClassification(c.classification, errors)
        // Encargo confirmado sem classificação usa Serviços > Tarifas e juros, pela identidade da semente (como na compra no cartão).
        val classificationId = chosenId
            ?: if (c.isCharge) DeterministicIds.seedCategory(session.spaceId, CategorySeed.FEES_AND_INTEREST) else null

        var accountId: String? = null
        if (c.kind == DirectPaymentKind.Cash) {
            if (c.accountId != null) errors += FieldError("accountId", ErrorCode.NotAllowed, "Dinheiro não tem conta.")
        } else {
            val chosen = c.accountId
                ?: effectiveDefaultAccount(session)
            when {
                chosen == null ->
                    errors += FieldError("accountId", ErrorCode.Required, "Escolha a conta; você ainda não definiu uma conta padrão.")
                dao.account(chosen) == null ->
                    errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
                else -> accountId = chosen
            }
        }
        if (errors.isNotEmpty()) return stop(errors)

        return Planned.Go(months = setOf(YearMonth.from(c.date)), undoable = true) {
            val row = MovementEntity(
                id = UUID.randomUUID().toString(),
                kind = c.kind.name,
                description = c.description.trim(),
                classificationId = classificationId,
                epochDay = c.date.toEpochDay(),
                amountCents = c.amount.cents,
                accountId = accountId,
                spentBy = c.spentBy,
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                isCharge = c.isCharge,
            )
            dao.insertMovement(row)
            listOf(EntityChange(EntityType.Movement, row.id, null, 1))
        }
    }

    /** Devolve o ID da classificação (nulo é "a classificar") e acumula erro se a categoria não existe. */
    private suspend fun validateClassification(classification: Classification, errors: MutableList<FieldError>): String? =
        when (classification) {
            Classification.ToClassify -> null
            is Classification.Category -> {
                if (dao.activeCategory(classification.id) == null) {
                    errors += FieldError("classification", ErrorCode.UnknownReference, "Categoria não encontrada.")
                }
                classification.id
            }
        }

    // ---- compra no cartão ----

    private suspend fun cycleOf(spaceId: String): CardCycle {
        val card = dao.card(cardId(spaceId)) ?: error("Cartão do espaço não foi criado")
        return CardCycle(card.closingDay, card.dueDay)
    }

    private suspend fun planCardPurchase(session: Session, origin: Origin, c: RegisterCardPurchase): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.installments < 1) errors += FieldError("installments", ErrorCode.OutOfRange, "A quantidade de parcelas começa em 1.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val chosenId = validateClassification(c.classification, errors)
        if (errors.isNotEmpty()) return stop(errors)

        if (c.installments > 1 && c.amountMeaning == null) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.AmountMeaningRequired(c.installments, c.amount)))
        }
        // Encargo confirmado sem classificação usa Serviços > Tarifas e juros, pela identidade da semente.
        val classificationId = chosenId
            ?: if (c.isCharge) DeterministicIds.seedCategory(session.spaceId, CategorySeed.FEES_AND_INTEREST) else null

        val informedInvoice = c.invoice
        val (firstMonth, firstState) = if (informedInvoice != null) {
            informedInvoice to AssignmentState.Confirmed
        } else {
            val assigned = cycleOf(session.spaceId).assign(c.date)
            assigned.dueMonth to if (assigned.toConfirm) AssignmentState.ToConfirm else AssignmentState.Estimated
        }
        // Cronograma único com o simulador do S7 (PurchaseSchedule): parcelas, centavos e meses de fatura.
        val views = try {
            PurchaseSchedule.build(c.amount, c.amountMeaning, c.installments, firstMonth, firstState)
        } catch (_: IllegalArgumentException) {
            return stop("amount", ErrorCode.MustBePositive, "Cada parcela precisa ter ao menos um centavo.")
        }
        val months = views.map { it.invoiceMonth }

        return Planned.Go(after = views, months = months.toSet(), undoable = true) {
            val purchase = CardPurchaseRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                classificationId = classificationId,
                purchaseEpochDay = c.date.toEpochDay(),
                totalCents = views.sumOf { it.amount.cents },
                isCharge = c.isCharge,
                spentBy = c.spentBy,
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
            )
            dao.insertPurchase(purchase)
            dao.insertInstallments(views.map { it.toRow(purchase.id) })
            listOf(EntityChange(EntityType.CardPurchase, purchase.id, null, 1))
        }
    }

    private fun CancelState.rowName() = when (this) {
        CancelState.Active -> CancellationPlanner.CANCEL_ACTIVE
        CancelState.CancelRequested -> CancellationPlanner.CANCEL_REQUESTED
        CancelState.Cancelled -> CancellationPlanner.CANCEL_DONE
    }

    private fun cancelOf(rowState: String) = when (rowState) {
        CancellationPlanner.CANCEL_ACTIVE -> CancelState.Active
        CancellationPlanner.CANCEL_REQUESTED -> CancelState.CancelRequested
        else -> CancelState.Cancelled
    }

    private fun InstallmentView.toRow(purchaseId: String) =
        InstallmentRow(purchaseId, number, amount.cents, invoiceMonth.toString(), assignment.name, cancel.rowName())

    private fun InstallmentRow.toView() =
        InstallmentView(number, Money(amountCents), YearMonth.parse(invoiceMonth), AssignmentState.valueOf(assignment), cancelOf(cancelState))

    private suspend fun planCorrectInvoice(c: CorrectInvoiceAssignment): Planned {
        val purchase = dao.purchase(c.purchaseId)
            ?: return stop("purchaseId", ErrorCode.UnknownReference, "Compra não encontrada.")
        if (purchase.voided) return stop("purchaseId", ErrorCode.NotAllowed, "Esta compra foi desfeita ou excluída.")
        if (c.expectedRevision != null && c.expectedRevision != purchase.revision) {
            return Planned.Stop(CommandResult.StaleVersion(purchase.id, purchase.revision))
        }
        if (c.installmentNumber != 1) {
            return stop(
                "installmentNumber", ErrorCode.NotAllowed,
                "Só a 1ª parcela muda de fatura; as seguintes acompanham. Divergência em outra parcela vira pendência para revisão.",
            )
        }
        val rows = dao.installments(purchase.id)
        val before = rows.map { it.toView() }
        val cancelled = rows.filter { it.cancelState == CancellationPlanner.CANCEL_DONE }.map { it.number }.toSet()
        val shift = Installments.shiftFirst(
            oldFirst = YearMonth.parse(rows.first().invoiceMonth),
            count = rows.size,
            newFirst = c.invoice,
            paidMonths = dao.paidInvoiceMonths().map(YearMonth::parse).toSet(),
            cancelledNumbers = cancelled,
        )
        when (shift) {
            is ShiftResult.Blocked -> return stop(
                buildList {
                    shift.paidMonths.forEach { add(FieldError("invoice", ErrorCode.NotAllowed, "A fatura de $it já foi quitada.")) }
                    shift.cancelledNumbers.forEach {
                        add(FieldError("installmentNumber", ErrorCode.NotAllowed, "A parcela $it está cancelada."))
                    }
                },
            )
            is ShiftResult.Shifted -> {
                val after = rows.mapIndexed { i, r ->
                    InstallmentView(
                        r.number, Money(r.amountCents), shift.after[i],
                        if (i == 0) AssignmentState.Confirmed else AssignmentState.valueOf(r.assignment),
                        cancelOf(r.cancelState),
                    )
                }
                val months = (shift.before + shift.after).toSet()
                return Planned.Go(before = before, after = after, months = months) {
                    dao.upsertInstallments(after.map { v -> v.toRow(purchase.id) })
                    val next = purchase.copy(revision = purchase.revision + 1)
                    dao.upsertPurchase(next)
                    listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
                }
            }
        }
    }

    // ---- boleto e renda ----

    /** Uma única conta já é o padrão; com várias, respeita a escolha individual persistida. */
    private suspend fun effectiveDefaultAccount(session: Session): String? =
        dao.preferences(DeterministicIds.preferences(session.spaceId, session.personId))?.defaultAccountId
            ?: dao.accounts().singleOrNull()?.id

    /** Conta de um pagamento ou recebimento: a informada ou a padrão do autor; obrigatória. */
    private suspend fun resolveRequiredAccount(session: Session, given: String?, errors: MutableList<FieldError>): String? {
        val chosen = given
            ?: effectiveDefaultAccount(session)
        return when {
            chosen == null -> {
                errors += FieldError("accountId", ErrorCode.Required, "Escolha a conta; você ainda não definiu uma conta padrão.")
                null
            }
            dao.account(chosen) == null -> {
                errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
                null
            }
            else -> chosen
        }
    }

    private fun validateSettlement(settlement: Settlement, field: String, errors: MutableList<FieldError>) {
        val amount = settlement.amount
        if (amount != null && amount.cents <= 0) {
            errors += FieldError(field, ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        }
    }

    private suspend fun planRegisterBoleto(session: Session, origin: Origin, c: RegisterBoleto): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val classificationId = validateClassification(c.classification, errors)
        val payment = c.payment
        if (payment != null) validateSettlement(payment, "payment", errors)
        val givenAccount = c.accountId
        var accountId: String? = null
        if (payment != null) {
            accountId = resolveRequiredAccount(session, givenAccount, errors)
        } else if (givenAccount != null) {
            if (dao.account(givenAccount) == null) {
                errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
            } else {
                accountId = givenAccount
            }
        }
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOfNotNull(YearMonth.from(c.dueDate), payment?.let { YearMonth.from(it.date) })
        return Planned.Go(months = months, undoable = true) {
            val row = BoletoRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                classificationId = classificationId,
                dueEpochDay = c.dueDate.toEpochDay(),
                expectedCents = c.amount.cents,
                accountId = accountId,
                paidEpochDay = payment?.date?.toEpochDay(),
                paidCents = payment?.let { (it.amount ?: c.amount).cents },
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
            )
            dao.insertBoleto(row)
            listOf(EntityChange(EntityType.Boleto, row.id, null, 1))
        }
    }

    private suspend fun planPayBoleto(session: Session, c: PayBoleto): Planned {
        val boleto = dao.boleto(c.boletoId) ?: return stop("boletoId", ErrorCode.UnknownReference, "Boleto não encontrado.")
        if (boleto.voided) return stop("boletoId", ErrorCode.NotAllowed, "Este boleto foi desfeito ou excluído.")
        if (c.expectedRevision != null && c.expectedRevision != boleto.revision) {
            return Planned.Stop(CommandResult.StaleVersion(boleto.id, boleto.revision))
        }
        if (boleto.paidEpochDay != null) {
            return stop("boletoId", ErrorCode.NotAllowed, "Este boleto já está pago. Desfaça o pagamento antes de registrar outro.")
        }
        val errors = mutableListOf<FieldError>()
        validateSettlement(c.payment, "payment", errors)
        val accountId = resolveRequiredAccount(session, c.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(boleto.dueEpochDay)), YearMonth.from(c.payment.date))
        return Planned.Go(months = months, undoable = true) {
            val next = boleto.copy(
                accountId = accountId,
                paidEpochDay = c.payment.date.toEpochDay(),
                paidCents = c.payment.amount?.cents ?: boleto.expectedCents,
                revision = boleto.revision + 1,
            )
            dao.upsertBoleto(next)
            listOf(EntityChange(EntityType.Boleto, next.id, null, next.revision))
        }
    }

    private suspend fun planCreateIncomeCategory(c: CreateIncomeCategory): Planned {
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria de renda.")
        val key = NameKey.of(name)
        val all = dao.incomeCategories()
        if (all.any { it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria de renda com esse nome. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val row = IncomeCategoryRow(
                id = UUID.randomUUID().toString(),
                name = name,
                nameKey = key,
                sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                iconKey = IncomeCategorySeed.CUSTOM_ICON,
                seedKey = null,
                revision = 1,
            )
            dao.insertIncomeCategory(row)
            listOf(EntityChange(EntityType.IncomeCategory, row.id, null, 1))
        }
    }

    private suspend fun planRenameIncomeCategory(c: RenameIncomeCategory): Planned {
        val row = dao.incomeCategory(c.categoryId)
            ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria de renda.")
        val key = NameKey.of(name)
        if (dao.incomeCategories().any { it.id != row.id && it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria de renda com esse nome. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val next = row.copy(name = name, nameKey = key, revision = row.revision + 1)
            dao.upsertIncomeCategory(next)
            listOf(EntityChange(EntityType.IncomeCategory, next.id, null, next.revision))
        }
    }

    /** Categoria de renda informada: precisa existir; nula é permitida (renda sem categoria). */
    private suspend fun validateIncomeCategory(id: String?, errors: MutableList<FieldError>) {
        if (id != null && dao.incomeCategory(id) == null) {
            errors += FieldError("incomeCategoryId", ErrorCode.UnknownReference, "Categoria de renda não encontrada.")
        }
    }

    private suspend fun planRegisterIncome(session: Session, origin: Origin, c: RegisterIncome): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.expectedAmount.cents <= 0) {
            errors += FieldError("expectedAmount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        }
        val received = c.received
        if (received != null) validateSettlement(received, "received", errors)
        val accountId = resolveRequiredAccount(session, c.accountId, errors)
        validateIncomeCategory(c.incomeCategoryId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOfNotNull(YearMonth.from(c.expectedDate), received?.let { YearMonth.from(it.date) })
        return Planned.Go(months = months, undoable = true) {
            val row = IncomeRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                expectedCents = c.expectedAmount.cents,
                expectedEpochDay = c.expectedDate.toEpochDay(),
                accountId = accountId,
                receivedEpochDay = received?.date?.toEpochDay(),
                receivedCents = received?.let { (it.amount ?: c.expectedAmount).cents },
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                incomeCategoryId = c.incomeCategoryId,
            )
            dao.insertIncome(row)
            listOf(EntityChange(EntityType.Income, row.id, null, 1))
        }
    }

    private suspend fun planReceiveIncome(session: Session, c: ReceiveIncome): Planned {
        val income = dao.income(c.incomeId) ?: return stop("incomeId", ErrorCode.UnknownReference, "Renda não encontrada.")
        if (income.voided) return stop("incomeId", ErrorCode.NotAllowed, "Esta renda foi desfeita ou excluída.")
        if (c.expectedRevision != null && c.expectedRevision != income.revision) {
            return Planned.Stop(CommandResult.StaleVersion(income.id, income.revision))
        }
        if (income.receivedEpochDay != null) {
            return stop("incomeId", ErrorCode.NotAllowed, "Esta renda já foi recebida. Desfaça o recebimento antes de registrar outro.")
        }
        val errors = mutableListOf<FieldError>()
        validateSettlement(c.receipt, "receipt", errors)
        val accountId = resolveRequiredAccount(session, c.accountId ?: income.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(income.expectedEpochDay)), YearMonth.from(c.receipt.date))
        return Planned.Go(months = months, undoable = true) {
            val next = income.copy(
                accountId = accountId,
                receivedEpochDay = c.receipt.date.toEpochDay(),
                receivedCents = c.receipt.amount?.cents ?: income.expectedCents,
                revision = income.revision + 1,
            )
            dao.upsertIncome(next)
            listOf(EntityChange(EntityType.Income, next.id, null, next.revision))
        }
    }

    // ---- recorrência ----

    internal data class LoadedSeries(
        val row: RecurrenceRow,
        val series: RecurrenceSeries,
        val settlements: Map<Int, RecurrenceSettlementRow>,
    )

    internal suspend fun loadSeries(row: RecurrenceRow): LoadedSeries {
        val segments = dao.segments(row.id).associate {
            it.fromNumber to Template(it.description, it.classificationId, Money(it.amountCents), it.accountId)
        }
        val overrides = dao.overrides(row.id).associate {
            it.number to OccurrenceOverride(
                it.deleted,
                RecurrenceFields(it.description, it.classificationId, it.amountCents?.let(::Money), it.accountId),
            )
        }
        val series = RecurrenceSeries.restore(
            Recurrence(LocalDate.ofEpochDay(row.anchorEpochDay), Frequency.valueOf(row.frequency)),
            segments, overrides, row.endsBefore,
        )
        return LoadedSeries(row, series, dao.settlements(row.id).associateBy { it.number })
    }

    private suspend fun saveSeries(id: String, series: RecurrenceSeries) {
        dao.deleteSegments(id)
        dao.insertSegments(
            series.segments.map { (from, t) ->
                RecurrenceSegmentRow(id, from, t.description, t.classificationId, t.amount.cents, t.accountId)
            },
        )
        dao.deleteOverrides(id)
        dao.insertOverrides(
            series.overrides.map { (n, o) ->
                RecurrenceOverrideRow(id, n, o.deleted, o.fields.description, o.fields.classificationId, o.fields.amount?.cents, o.fields.accountId)
            },
        )
    }

    private suspend fun planCreateRecurrence(session: Session, origin: Origin, c: CreateRecurrence): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val categoryId = c.categoryId
        when (c.kind) {
            RecurrenceKind.Expense -> when {
                categoryId == null -> errors += FieldError("categoryId", ErrorCode.Required, "Escolha a categoria.")
                dao.activeCategory(categoryId) == null ->
                    errors += FieldError("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")
            }
            RecurrenceKind.Income -> if (categoryId != null) {
                errors += FieldError("categoryId", ErrorCode.NotAllowed, "Renda não tem categoria.")
            }
        }
        val accountId = c.accountId
        if (accountId != null && dao.account(accountId) == null) {
            errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        }
        if (errors.isNotEmpty()) return stop(errors)

        return Planned.Go(months = setOf(YearMonth.from(c.firstDate)), undoable = true) {
            val id = UUID.randomUUID().toString()
            dao.insertRecurrence(
                RecurrenceRow(
                    id = id,
                    kind = c.kind.name,
                    form = (if (c.kind == RecurrenceKind.Expense) c.form else RecurrenceForm.OffCard).name,
                    frequency = c.frequency.name,
                    anchorEpochDay = c.firstDate.toEpochDay(),
                    endsBefore = null,
                    authorId = session.personId,
                    origin = origin.name,
                    revision = 1,
                    voided = false,
                ),
            )
            dao.insertSegments(
                listOf(RecurrenceSegmentRow(id, 1, c.description.trim(), categoryId, c.amount.cents, accountId)),
            )
            listOf(EntityChange(EntityType.Recurrence, id, null, 1))
        }
    }

    /** Carrega a série e checa existência, exclusão e versão; devolve o carregamento ou a parada. */
    private suspend fun loadForCommand(id: String, expectedRevision: Long?): Pair<LoadedSeries?, Planned.Stop?> {
        val row = dao.recurrence(id)
            ?: return null to Planned.Stop(CommandResult.Rejected(listOf(FieldError("recurrenceId", ErrorCode.UnknownReference, "Recorrência não encontrada."))))
        if (row.voided) {
            return null to Planned.Stop(CommandResult.Rejected(listOf(FieldError("recurrenceId", ErrorCode.NotAllowed, "Esta recorrência foi desfeita ou excluída."))))
        }
        if (expectedRevision != null && expectedRevision != row.revision) {
            return null to Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        return loadSeries(row) to null
    }

    private suspend fun planEditRecurrence(c: EditRecurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val scope = c.scope ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.ScopeRequired))
        val errors = mutableListOf<FieldError>()
        if (c.fields.isEmpty) errors += FieldError("fields", ErrorCode.Required, "Informe o que mudou.")
        c.fields.description?.let { if (it.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.") }
        c.fields.amount?.let { if (it.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.") }
        c.fields.classificationId?.let {
            when {
                loaded.row.kind == RecurrenceKind.Income.name ->
                    errors += FieldError("classificationId", ErrorCode.NotAllowed, "Renda não tem categoria.")
                dao.activeCategory(it) == null ->
                    errors += FieldError("classificationId", ErrorCode.UnknownReference, "Categoria não encontrada.")
            }
        }
        c.fields.accountId?.let {
            if (dao.account(it) == null) errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        }
        val occurrence = loaded.series.occurrence(c.occurrence)
        if (occurrence == null) errors += FieldError("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        if (errors.isNotEmpty()) return stop(errors)

        val outcome = loaded.series.edit(c.occurrence, c.fields, scope, loaded.settlements.keys, c.confirmRealized)
        if (outcome is RecurrenceEdit.NeedsConfirmation) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.RealizedOccurrences(outcome.realized)))
        }
        outcome as RecurrenceEdit.Applied
        return Planned.Go(months = setOf(YearMonth.from(occurrence!!.date)), replaced = outcome.replacedExceptions) {
            saveSeries(loaded.row.id, outcome.series)
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    private suspend fun planDeleteRecurrence(c: DeleteRecurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val scope = c.scope ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.ScopeRequired))
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")

        val outcome = loaded.series.delete(c.occurrence, scope, loaded.settlements.keys, c.confirmRealized)
        if (outcome is RecurrenceEdit.NeedsConfirmation) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.RealizedOccurrences(outcome.realized)))
        }
        outcome as RecurrenceEdit.Applied
        return Planned.Go(months = setOf(YearMonth.from(occurrence.date))) {
            saveSeries(loaded.row.id, outcome.series)
            // Ocorrência excluída deixa de existir, inclusive o pagamento que a confirmação autorizou remover.
            if (scope == RecurrenceScope.OnlyThis) dao.deleteSettlement(loaded.row.id, c.occurrence)
            else dao.deleteSettlementsFrom(loaded.row.id, c.occurrence)
            val next = loaded.row.copy(revision = loaded.row.revision + 1, endsBefore = outcome.series.endsBefore)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    private suspend fun planPayOccurrence(session: Session, c: PayOccurrence): Planned {
        val (loaded, stopped) = loadForCommand(c.recurrenceId, c.expectedRevision)
        if (loaded == null) return stopped!!
        val occurrence = loaded.series.occurrence(c.occurrence)
            ?: return stop("occurrence", ErrorCode.UnknownReference, "Ocorrência não encontrada.")
        if (loaded.row.kind == RecurrenceKind.Expense.name && loaded.row.form == RecurrenceForm.Card.name) {
            return stop("occurrence", ErrorCode.NotAllowed, "Ocorrência no cartão é paga pela quitação da fatura.")
        }
        if (loaded.settlements.containsKey(c.occurrence)) {
            return stop("occurrence", ErrorCode.NotAllowed, "Esta ocorrência já foi registrada. Desfaça antes de registrar outra.")
        }
        val errors = mutableListOf<FieldError>()
        validateSettlement(c.settlement, "settlement", errors)
        val accountId = resolveRequiredAccount(session, c.accountId ?: occurrence.template.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(occurrence.date), YearMonth.from(c.settlement.date))
        return Planned.Go(months = months, undoable = true, detail = "occurrence=${c.occurrence}") {
            dao.upsertSettlement(
                RecurrenceSettlementRow(
                    loaded.row.id, c.occurrence, c.settlement.date.toEpochDay(),
                    (c.settlement.amount ?: occurrence.template.amount).cents, accountId,
                ),
            )
            val next = loaded.row.copy(revision = loaded.row.revision + 1)
            dao.upsertRecurrence(next)
            listOf(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
        }
    }

    // ---- classificar ----

    private suspend fun planClassify(c: ClassifyEntry): Planned {
        if (dao.activeCategory(c.category.id) == null) {
            return stop("category", ErrorCode.UnknownReference, "Categoria não encontrada.")
        }
        val purchase = dao.purchase(c.entityId)
        val movement = if (purchase == null) dao.movement(c.entityId) else null
        val currentClassification: String?
        val revision: Long
        val voided: Boolean
        when {
            purchase != null -> { currentClassification = purchase.classificationId; revision = purchase.revision; voided = purchase.voided }
            movement != null -> { currentClassification = movement.classificationId; revision = movement.revision; voided = movement.voided }
            else -> return stop("entityId", ErrorCode.UnknownReference, "Lançamento não encontrado.")
        }
        if (voided) return stop("entityId", ErrorCode.NotAllowed, "Este lançamento foi desfeito ou excluído.")
        if (c.expectedRevision != null && c.expectedRevision != revision) {
            return Planned.Stop(CommandResult.StaleVersion(c.entityId, revision))
        }
        if (currentClassification != null) {
            return stop("entityId", ErrorCode.NotAllowed, "Este lançamento já tem categoria.")
        }
        return if (purchase != null) {
            val months = dao.installments(purchase.id).map { YearMonth.parse(it.invoiceMonth) }.toSet()
            Planned.Go(months = months, undoable = true) {
                val next = purchase.copy(classificationId = c.category.id, revision = purchase.revision + 1)
                dao.upsertPurchase(next)
                listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
            }
        } else {
            val m = movement!!
            Planned.Go(months = setOf(YearMonth.from(LocalDate.ofEpochDay(m.epochDay))), undoable = true) {
                val next = m.copy(classificationId = c.category.id, revision = m.revision + 1)
                dao.upsertMovement(next)
                listOf(EntityChange(EntityType.Movement, next.id, null, next.revision))
            }
        }
    }

    // ---- desfazer ----

    /** Revisão atual de cada entidade tocada por uma operação; nula se a entidade não existe. */
    private suspend fun currentRevision(change: OperationChangeRow): Long? = when (EntityType.valueOf(change.entityType)) {
        EntityType.Movement -> dao.movement(change.entityId)?.revision
        EntityType.CardPurchase -> dao.purchase(change.entityId)?.revision
        EntityType.Boleto -> dao.boleto(change.entityId)?.revision
        EntityType.Income -> dao.income(change.entityId)?.revision
        EntityType.IncomeCategory -> dao.incomeCategory(change.entityId)?.revision
        EntityType.Recurrence -> dao.recurrence(change.entityId)?.revision
        EntityType.Refund -> dao.refund(change.entityId)?.revision
        EntityType.Invoice -> dao.invoiceRows().firstOrNull { it.id == change.entityId }?.revision
        EntityType.InvoicePayment -> dao.invoicePayment(change.entityId)?.revision
        else -> null
    }

    /**
     * Desfaz uma operação: criações viram registro de exclusão com a mesma identidade; pagamento,
     * recebimento e classificação voltam ao estado anterior. Nada é apagado, e só se cada entidade
     * ainda está na revisão que a operação produziu.
     */
    private suspend fun planUndo(c: Undo): Planned {
        val op = dao.operation(c.operationId)
            ?: return stop("operationId", ErrorCode.UnknownReference, "Operação não encontrada.")
        if (!op.undoable) return stop("operationId", ErrorCode.NotSupported, "Esta operação não pode ser desfeita.")

        val changes = dao.changesOf(listOf(op.operationId))
        if (changes.any { currentRevision(it) != it.revision }) {
            return stop("operationId", ErrorCode.ChangedAfter, "Este lançamento foi alterado depois. Revise antes de desfazer.")
        }
        val creation = op.commandType in CREATION_COMMANDS
        return Planned.Go(months = parseMonths(op.affectedMonths)) {
            changes.map { ch ->
                val type = EntityType.valueOf(ch.entityType)
                if (op.commandType == "CorrectEntry") {
                    return@map EntityChange(type, ch.entityId, null, correctionPlanner.restore(type, ch.entityId, op.detail!!))
                }
                if (op.commandType == "ChangePaymentMethod") {
                    return@map EntityChange(type, ch.entityId, null, paymentMethodPlanner.restore(type, ch.entityId, op.detail!!))
                }
                val revision = when (type) {
                    EntityType.Movement -> {
                        val row = dao.movement(ch.entityId)!!
                        dao.upsertMovement(
                            if (creation) row.copy(revision = row.revision + 1, voided = true)
                            else row.copy(revision = row.revision + 1, classificationId = null),
                        )
                        row.revision + 1
                    }
                    EntityType.CardPurchase -> {
                        val row = dao.purchase(ch.entityId)!!
                        when {
                            creation -> dao.upsertPurchase(row.copy(revision = row.revision + 1, voided = true))
                            op.commandType == "ClassifyEntry" ->
                                dao.upsertPurchase(row.copy(revision = row.revision + 1, classificationId = null))
                            else -> {
                                // Pedido de cancelamento ou confirmação de não cobrança: restaura o estado anterior.
                                cancellationPlanner.restore(row.id, op.detail!!)
                                dao.upsertPurchase(row.copy(revision = row.revision + 1))
                            }
                        }
                        row.revision + 1
                    }
                    EntityType.Invoice -> {
                        val row = dao.invoiceRows().first { it.id == ch.entityId }
                        if (op.commandType == "RegisterInvoiceTotal") {
                            invoicePlanner.restoreTotal(YearMonth.parse(row.dueMonth), op.detail!!)
                        } else {
                            dao.upsertInvoice(row.copy(revision = row.revision + 1))
                        }
                        row.revision + 1
                    }
                    EntityType.InvoicePayment -> {
                        val row = dao.invoicePayment(ch.entityId)!!
                        dao.upsertInvoicePayment(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Refund -> {
                        val row = dao.refund(ch.entityId)!!
                        dao.upsertRefund(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Boleto -> {
                        val row = dao.boleto(ch.entityId)!!
                        dao.upsertBoleto(
                            if (creation) row.copy(revision = row.revision + 1, voided = true)
                            else row.copy(revision = row.revision + 1, paidEpochDay = null, paidCents = null),
                        )
                        row.revision + 1
                    }
                    EntityType.Income -> {
                        val row = dao.income(ch.entityId)!!
                        dao.upsertIncome(
                            if (creation) row.copy(revision = row.revision + 1, voided = true)
                            else row.copy(revision = row.revision + 1, receivedEpochDay = null, receivedCents = null),
                        )
                        row.revision + 1
                    }
                    EntityType.Recurrence -> {
                        val row = dao.recurrence(ch.entityId)!!
                        if (creation) {
                            dao.upsertRecurrence(row.copy(revision = row.revision + 1, voided = true))
                        } else {
                            // Pagamento de ocorrência: devolve a previsão ao mês original.
                            dao.deleteSettlement(row.id, op.detail!!.removePrefix("occurrence=").toInt())
                            dao.upsertRecurrence(row.copy(revision = row.revision + 1))
                        }
                        row.revision + 1
                    }
                    else -> error("Operação sobre ${ch.entityType} não é desfazível")
                }
                EntityChange(type, ch.entityId, null, revision)
            }
        }
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

    private fun parseMonths(text: String): Set<YearMonth> =
        if (text.isEmpty()) emptySet() else text.split(",").map(YearMonth::parse).toSet()

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

    override suspend fun intentions(): QueryResult<List<IntentionView>> = query { dao.intentions().map { intentionPlanner.viewOf(it, syncStatus.statusOf(EntityType.Intention, it.id)) } }

    override suspend fun intention(id: String): QueryResult<IntentionView?> = query { dao.intention(id)?.let { intentionPlanner.viewOf(it, syncStatus.statusOf(EntityType.Intention, it.id)) } }

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

    private data class Composition(val purchases: Money, val charges: Money, val credits: Money, val linksToConfirm: Int) {
        val known get() = purchases + charges - credits
    }

    /** Composição conhecida da fatura: compras e parcelas, encargos e créditos, separados. */
    private suspend fun composition(month: YearMonth): Composition {
        val parts = dao.invoiceParts(month.toString())
        val recurring = occurrencesIn(month).filter { it.kind == RecurrenceKind.Expense && it.form == RecurrenceForm.Card }
        return Composition(
            purchases = Money(parts.purchasesCents + recurring.sumOf { it.expectedAmount.cents }),
            charges = Money(parts.chargesCents),
            credits = Money(dao.invoiceCredits(month.toString())),
            linksToConfirm = dao.linksToConfirm(month.toString()) + recurring.count { it.assignment == AssignmentState.ToConfirm },
        )
    }

    private suspend fun invoiceView(month: YearMonth): InvoiceView {
        val parts = composition(month)
        val row = dao.invoice(month.toString())
        val payments = dao.invoicePayments(month.toString())
        val checked = row?.checkedCents?.let(::Money)
        val conferred = checked != null && checked == parts.known
        val accepted = payments.firstOrNull { it.accepted }
        val phase = clock.today().let { today -> cycleOf(sessions.current().spaceId).phase(month, today) }
        return InvoiceView(
            dueMonth = month,
            purchases = parts.purchases,
            charges = parts.charges,
            credits = parts.credits,
            knownTotal = parts.known,
            checkedTotal = checked,
            checkedSource = row?.checkedSource?.let(InvoiceTotalSource::valueOf),
            conferred = conferred,
            totalDifference = if (checked != null && !conferred) checked - parts.known else null,
            paid = accepted != null,
            paidDate = accepted?.let { LocalDate.ofEpochDay(it.epochDay) },
            status = when {
                checked != null -> InvoiceStatus.Closed
                phase == InvoicePhase.Open -> InvoiceStatus.Open
                else -> InvoiceStatus.ClosedEstimated
            },
            linksToConfirm = parts.linksToConfirm,
            divergentPayments = payments.filterNot { it.accepted }.map {
                PaymentView(it.id, Money(it.amountCents), LocalDate.ofEpochDay(it.epochDay), it.accountId, accepted = false)
            },
            revision = row?.revision ?: 0,
        )
    }

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

    /** Ocorrência de [series] já posicionada no orçamento; nula se a ocorrência não existe. */
    internal fun place(loaded: LoadedSeries, number: Int, cycle: CardCycle): OccurrenceView? {
        val occurrence = loaded.series.occurrence(number) ?: return null
        val settled = loaded.settlements[number]
        val kind = RecurrenceKind.valueOf(loaded.row.kind)
        val form = RecurrenceForm.valueOf(loaded.row.form)
        val settledDate = settled?.let { LocalDate.ofEpochDay(it.epochDay) }
        val onCard = kind == RecurrenceKind.Expense && form == RecurrenceForm.Card
        val assignment = if (onCard) cycle.assign(occurrence.date) else null
        return OccurrenceView(
            recurrenceId = loaded.row.id,
            number = number,
            kind = kind,
            form = form,
            scheduledDate = occurrence.date,
            description = occurrence.template.description,
            categoryId = occurrence.template.classificationId,
            expectedAmount = occurrence.template.amount,
            accountId = settled?.accountId ?: occurrence.template.accountId,
            settledDate = settledDate,
            settledAmount = settled?.let { Money(it.cents) },
            budgetMonth = assignment?.dueMonth ?: YearMonth.from(settledDate ?: occurrence.date),
            paid = settled != null,
            assignment = assignment?.let { if (it.toConfirm) AssignmentState.ToConfirm else AssignmentState.Estimated },
        )
    }

    private suspend fun occurrencesIn(month: YearMonth): List<OccurrenceView> {
        val cycle = cycleOf(sessions.current().spaceId)
        return dao.activeRecurrences().flatMap { row ->
            val loaded = loadSeries(row)
            // O mês de uma ocorrência pode diferir do mês da data prevista (pagamento antecipado ou
            // atrasado, fatura seguinte): olha os meses vizinhos e os pagamentos registrados.
            val candidates = listOf(month.minusMonths(1), month, month.plusMonths(1))
                .mapNotNull { loaded.series.recurrence.numberIn(it) }.toSet() +
                loaded.settlements.filterValues { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }.keys
            candidates.mapNotNull { place(loaded, it, cycle) }.filter { it.budgetMonth == month }
        }.sortedWith(compareBy({ it.scheduledDate }, { it.recurrenceId }, { it.number }))
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

    private fun IncomeRow.toView(): IncomeView {
        val expected = LocalDate.ofEpochDay(expectedEpochDay)
        val received = receivedEpochDay?.let(LocalDate::ofEpochDay)
        return IncomeView(
            id = id,
            description = description,
            expectedAmount = Money(expectedCents),
            expectedDate = expected,
            receivedDate = received,
            receivedAmount = receivedCents?.let(::Money),
            accountId = accountId,
            budgetMonth = YearMonth.from(received ?: expected),
            revision = revision,
            voided = voided,
            incomeCategoryId = incomeCategoryId,
        )
    }

    override suspend fun income(id: String): QueryResult<IncomeView?> = query { dao.income(id)?.toView() }

    private suspend fun incomeSummary(month: YearMonth): IncomeSummary {
        val recurring = occurrencesIn(month).filter { it.kind == RecurrenceKind.Income }.map {
            IncomeView(
                id = "${it.recurrenceId}:${it.number}",
                description = it.description,
                expectedAmount = it.expectedAmount,
                expectedDate = it.scheduledDate,
                receivedDate = it.settledDate,
                receivedAmount = it.settledAmount,
                accountId = it.accountId,
                budgetMonth = it.budgetMonth,
                revision = dao.recurrence(it.recurrenceId)?.revision ?: 0,
                voided = false,
            )
        }
        val items = dao.incomesBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { it.toView() } + recurring
        return IncomeSummary(
            received = items.mapNotNull { it.receivedAmount }.sum(),
            toReceive = items.filter { it.receivedDate == null }.map { it.expectedAmount }.sum(),
            items = items,
        )
    }

    override suspend fun incomes(month: YearMonth): QueryResult<IncomeSummary> = query { incomeSummary(month) }

    private data class Credit(val classificationId: String?, val cents: Long, val reducesPaid: Boolean)

    /** Crédito de um estorno confirmado; nulo se a origem foi desfeita ou excluída (o crédito some com ela). */
    private suspend fun creditOf(refund: RefundRow): Credit? = when (refund.originType) {
        RefundPlanner.ORIGIN_PURCHASE ->
            dao.purchase(refund.originId)?.takeIf { !it.voided }?.let {
                // Crédito de cartão só desconta do pago quando a fatura que o recebe está quitada.
                Credit(it.classificationId, refund.amountCents, reducesPaid = refund.creditMonth?.let { m -> dao.isInvoicePaid(m) } == true)
            }
        RefundPlanner.ORIGIN_MOVEMENT ->
            dao.movement(refund.originId)?.takeIf { !it.voided }?.let { Credit(it.classificationId, refund.amountCents, reducesPaid = true) }
        else ->
            dao.boleto(refund.originId)?.takeIf { !it.voided }?.let { Credit(it.classificationId, refund.amountCents, reducesPaid = true) }
    }

    /** Soma do mês por classificação; [paid] é o que já está pago dentro de [total]. */
    private data class Aggregate(val classificationId: String?, val total: Long, val paid: Long, val count: Int)

    override suspend fun budget(month: YearMonth): QueryResult<MonthBudget> = query {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        val categories = tree()
        val recurringExpenses = occurrencesIn(month).filter { it.kind == RecurrenceKind.Expense }

        // Pix, débito e dinheiro já nascem pagos; parcelas só passam a pagas com a quitação da fatura.
        val invoicePaid = dao.isInvoicePaid(month.toString())
        val aggregates = dao.sumsByClassification(from, to).map { Aggregate(it.classificationId, it.totalCents, it.totalCents, it.count) } +
            dao.installmentSums(month.toString()).map { Aggregate(it.classificationId, it.totalCents, if (invoicePaid) it.totalCents else 0, it.count) } +
            dao.boletoSums(from, to).map { Aggregate(it.classificationId, it.totalCents, it.paidCents, it.count) } +
            recurringExpenses.map {
                val amount = (it.settledAmount ?: it.expectedAmount).cents
                val onCard = it.form == RecurrenceForm.Card
                Aggregate(it.categoryId, amount, if (it.paid || (onCard && invoicePaid)) amount else 0, 1)
            }
        val income = incomeSummary(month)

        val toClassify = aggregates.filter { it.classificationId == null }
        val perCategory = aggregates.filter { it.classificationId != null }
            .groupBy { categories.mainAt(it.classificationId!!, month) }

        // Crédito de estorno confirmado abate a categoria da origem no mês do crédito. Crédito de
        // cartão ainda não desconta do pago: isso depende da quitação da fatura que o recebe.
        val credits = dao.confirmedRefundsIn(month.toString()).mapNotNull { refund -> creditOf(refund) }
        val creditsByMain = credits.filter { it.classificationId != null }
            .groupBy { categories.mainAt(it.classificationId!!, month) }
        val unclassifiedCredit = credits.filter { it.classificationId == null }.sumOf { it.cents }

        val limitSettings = categories.mains.associate { it.id to limitsPlanner.timeline(it.id).settingFor(month) }

        MonthBudget(
            month = month,
            committed = Money(aggregates.sumOf { it.total }),
            totalLimits = LimitView.totalOfLimits(limitSettings.values),
            paid = Money(aggregates.sumOf { it.paid } - credits.filter { it.reducesPaid }.sumOf { it.cents }),
            toClassify = ToClassifyLine(Money(toClassify.sumOf { it.total } - unclassifiedCredit), toClassify.sumOf { it.count }),
            credits = Money(credits.sumOf { it.cents }),
            invoiceLinksToConfirm = dao.linksToConfirm(month.toString()) +
                recurringExpenses.count { it.assignment == AssignmentState.ToConfirm },
            income = IncomeTotals(income.received, income.toReceive),
            categories = categories.mains.map { main ->
                val rows = perCategory[main.id].orEmpty()
                val gross = Money(rows.sumOf { it.total })
                val mainCredits = creditsByMain[main.id].orEmpty()
                val credit = Money(mainCredits.sumOf { it.cents })
                CategoryBudget(
                    categoryId = main.id,
                    gross = gross,
                    credits = credit,
                    net = gross - credit,
                    paid = Money(rows.sumOf { it.paid } - mainCredits.filter { it.reducesPaid }.sumOf { it.cents }),
                    bySubcategory = rows.groupBy { r -> categories.subAt(r.classificationId!!, month) }
                        .mapValues { (_, group) -> Money(group.sumOf { it.total }) },
                    limit = LimitView.of(
                        limitSettings.getValue(main.id),
                        categoryNet = gross - credit,
                        subNet = rows.filter { categories.subAt(it.classificationId!!, month) != null }
                            .groupBy { it.classificationId!! }
                            .mapValues { (id, group) ->
                                Money(group.sumOf { it.total } - mainCredits.filter { c -> c.classificationId == id }.sumOf { it.cents })
                            },
                    ),
                )
            },
        )
    }

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
        private val CREATION_COMMANDS = setOf(
            "RegisterDirectPayment", "RegisterCardPurchase", "RegisterBoleto", "RegisterIncome",
            "RegisterRefund", "CreateRecurrence",
        )

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
