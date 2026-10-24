package app.accord.core.chat

import app.accord.core.BudgetClock
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DraftSource
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.FinanceSpace
import app.accord.core.space.LocalDraft
import app.accord.core.space.Origin
import app.accord.core.space.QueryResult
import app.accord.core.space.Receipt
import app.accord.core.space.Undo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Interpretador carregado e a versão do modelo que o produziu (diagnóstico local). */
class ActiveInterpreter(val interpreter: Interpreter, val modelVersion: String? = null)

/** Entrega o interpretador quando há modelo pronto; nulo é "sem modelo" (S3 §9). */
fun interface InterpreterSource {
    suspend fun current(): ActiveInterpreter?
}

enum class UndoResult { Done, ChangedAfter, NotAvailable, Failed }

/** O recibo cujo Desfazer ainda vale: o mais recente, ativo e não fechado pela próxima mensagem. */
fun ChatSnapshot.undoableReceipt(): ChatReceiptRecord? {
    val latest = receipts.maxByOrNull { it.createdAtMillis } ?: return null
    return latest.takeIf { it.state == ReceiptState.Active && !it.undoClosed }
}

/**
 * Coordenador da conversa (S3 §3, §6-§9): máquina de estados de cada mensagem, fila serial, chamada ao
 * interpretador, ao validador e à fachada S1, e histórico local. Não calcula dinheiro, mês nem fatura.
 * Cada mensagem tem identificador estável e o comando de S1 recebe um identificador de operação gravado
 * antes da chamada, então repetir após uma morte do processo nunca cria uma segunda despesa.
 */
class ChatCoordinator(
    private val space: FinanceSpace,
    private val store: ChatStore,
    private val interpreters: InterpreterSource,
    private val clock: BudgetClock,
    private val scope: CoroutineScope,
    private val members: suspend () -> List<MemberItem> = { emptyList() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** Avisado depois de qualquer gravação financeira (agenda o envio da fila de sincronização). */
    private val onFinanceChanged: () -> Unit = {},
) {
    val snapshot: Flow<ChatSnapshot> get() = store.observe()

    private val queue = Channel<String>(Channel.UNLIMITED)
    private val stateLock = Mutex()
    private val running = mutableMapOf<String, Deferred<Output>>()
    private val canceled = mutableSetOf<String>()

    private sealed interface Output {
        data class Text(val raw: String) : Output
        data class Failure(val error: Throwable) : Output
    }

    init {
        scope.launch {
            for (id in queue) {
                try {
                    process(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    stateLock.withLock { markFailed(id, "Internal") }
                }
            }
        }
    }

    // ---- API ----

    /** Grava a mensagem e a enfileira. Textos iguais em mensagens distintas são intenções distintas. */
    suspend fun send(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val id = newId()
        store.saveMessage(ChatMessage(id = id, text = trimmed, createdAtMillis = nowMillis()))
        queue.send(id)
        return id
    }

    /** Cancela só a tentativa: o texto fica e nada é aplicado; resultado tardio é descartado. */
    fun cancel(messageId: String) {
        scope.launch {
            val message = store.message(messageId) ?: return@launch
            when (message.state) {
                MessageState.Sent -> store.saveMessage(message.copy(state = MessageState.Canceled))
                MessageState.Interpreting -> {
                    canceled += messageId
                    running[messageId]?.cancel()
                }
                else -> Unit
            }
        }
    }

    /** "Tentar de novo" e "Interpretar agora": nova tentativa explícita, nunca automática. */
    suspend fun retry(messageId: String) {
        val message = store.message(messageId) ?: return
        if (message.state !in RETRYABLE) return
        canceled -= messageId
        store.saveMessage(message.copy(state = MessageState.Sent, failure = null, refuse = null, note = null, reply = null))
        queue.send(messageId)
    }

    /** A pessoa tocou numa opção da pergunta: o toque monta o comando direto, sem passar pelo modelo. */
    suspend fun answer(questionId: String, option: QuestionOption) {
        stateLock.withLock { answerWith(questionId, AnswerValue.Option(option.value)) }
    }

    /** Valor digitado como resposta de uma pergunta de valor, já lido pelo app (tela 19, modo esclarecer). */
    suspend fun answerAmount(questionId: String, text: String): Boolean {
        val read = ChatText.readAmount(text) as? AmountRead.Ok ?: return false
        stateLock.withLock { answerWith(questionId, AnswerValue.Amount(read.money)) }
        return true
    }

    /** Descarta uma pergunta e o rascunho local ligado a ela; nada entrou no orçamento. */
    suspend fun discardQuestion(questionId: String) {
        stateLock.withLock {
            val question = store.question(questionId) ?: return
            store.saveQuestion(question.copy(state = QuestionState.Discarded))
            (question.link as? QuestionLink.Draft)?.let { space.discardDraft(it.draftId) }
        }
    }

    /** Campos já entendidos de uma mensagem, para abrir o registro manual (tela 09) ainda sem salvar. */
    suspend fun manualPrefill(messageId: String): PartialEntry? =
        store.message(messageId)?.fields?.takeIf { it.isNotEmpty() }?.let(DraftCodec::fromFields)

    /** O registro manual foi salvo: a mensagem deixa de oferecer "Tentar de novo". */
    suspend fun resolveManually(messageId: String) {
        stateLock.withLock {
            val message = store.message(messageId) ?: return
            store.saveMessage(message.copy(state = MessageState.ResolvedManually))
            store.snapshot().questions.filter { it.messageId == messageId && it.state == QuestionState.Open }
                .forEach { store.saveQuestion(it.copy(state = QuestionState.Discarded)) }
            message.draftId?.let { space.discardDraft(it) }
        }
    }

    /** Desfaz a operação do recibo mais recente. Se algo mudou depois, a fachada recusa e nada é apagado. */
    suspend fun undo(receiptId: String): UndoResult = stateLock.withLock {
        val snapshot = store.snapshot()
        val receipt = snapshot.undoableReceipt()?.takeIf { it.id == receiptId } ?: return UndoResult.NotAvailable
        when (val result = space.execute(Envelope(CommandId(newId()), Origin.Chat), Undo(receipt.operationId))) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                store.saveReceipt(receipt.copy(state = ReceiptState.Undone))
                onFinanceChanged()
                val supersedes = receipt.supersedes?.let { store.receipt(it) }
                if (supersedes != null && supersedes.state == ReceiptState.ChangedLater) {
                    store.saveReceipt(supersedes.copy(state = ReceiptState.Active))
                }
                snapshot.questions.filter { it.link == QuestionLink.Entry(receipt.entryId) }.forEach { question ->
                    val next = when {
                        question.state == QuestionState.Open -> QuestionState.Discarded
                        receipt.kind == ReceiptKind.Classified && question.state == QuestionState.Answered -> QuestionState.Open
                        else -> question.state
                    }
                    if (next != question.state) store.saveQuestion(question.copy(state = next))
                }
                UndoResult.Done
            }
            is CommandResult.Rejected -> {
                store.saveReceipt(receipt.copy(state = ReceiptState.ChangedLater))
                UndoResult.ChangedAfter
            }
            else -> UndoResult.Failed
        }
    }

    /**
     * Ao reabrir o aplicativo: o que estava interpretando vira Interrompida (sem nova tentativa
     * automática), o que estava aplicando repete a chamada com o mesmo identificador de operação e o
     * que ainda esperava na fila volta a ela, na ordem de envio.
     */
    suspend fun recover() {
        val pending = stateLock.withLock {
            store.messagesIn(setOf(MessageState.Interpreting)).forEach { store.saveMessage(it.copy(state = MessageState.Interrupted)) }
            store.messagesIn(setOf(MessageState.Applying)).sortedBy { it.createdAtMillis }.forEach { applyRegister(it.id) }
            store.messagesIn(setOf(MessageState.Sent)).sortedBy { it.createdAtMillis }.map { it.id }
        }
        pending.forEach { queue.send(it) }
    }

    // ---- fila ----

    private suspend fun process(id: String) {
        val message = store.message(id) ?: return
        if (message.state != MessageState.Sent) return

        ChatValidator.prefilter(message.text)?.let { decision ->
            stateLock.withLock { handle(id, decision, started = nowMillis(), attemptId = null, modelVersion = null) }
            return
        }
        val active = interpreters.current()
        if (active == null) {
            store.saveMessage(message.copy(state = MessageState.NoModel))
            return
        }

        val attemptId = newId()
        val started = nowMillis()
        val context = stateLock.withLock {
            store.saveMessage(message.copy(state = MessageState.Interpreting, attempts = message.attempts + 1))
            buildContext()
        }
        val attempt = scope.async {
            try {
                Output.Text(active.interpreter.interpret(InterpretRequest(message.text, context)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Output.Failure(e)
            }
        }
        running[id] = attempt
        val output = try {
            attempt.await()
        } catch (e: CancellationException) {
            if (id !in canceled) throw e
            null
        } finally {
            running.remove(id)
        }

        stateLock.withLock {
            val wasCanceled = id in canceled
            canceled -= id
            when {
                output == null || wasCanceled -> {
                    // Resultado de tentativa cancelada nunca chega ao validador.
                    store.saveMessage(requireMessage(id).copy(state = MessageState.Canceled))
                    recordAttempt(attemptId, id, active.modelVersion, started, "canceled")
                }
                output is Output.Failure -> {
                    val kind = (output.error as? InterpreterFailure)?.kind?.name ?: "Engine"
                    markFailed(id, kind)
                    recordAttempt(attemptId, id, active.modelVersion, started, "failed")
                }
                else -> {
                    val raw = (output as Output.Text).raw
                    val fresh = buildContext()
                    handle(id, ChatValidator.evaluate(message.text, raw, fresh), started, attemptId, active.modelVersion)
                }
            }
        }
    }

    private suspend fun markFailed(id: String, kind: String) {
        val message = store.message(id) ?: return
        store.saveMessage(message.copy(state = MessageState.Failed, failure = kind))
    }

    private suspend fun recordAttempt(attemptId: String?, messageId: String, modelVersion: String?, started: Long, outcome: String) {
        if (attemptId == null) return
        store.addAttempt(ChatAttempt(attemptId, messageId, modelVersion, started, nowMillis() - started, outcome))
    }

    private suspend fun requireMessage(id: String) = checkNotNull(store.message(id)) { "Mensagem $id sumiu" }

    // ---- decisões ----

    private suspend fun handle(id: String, decision: ChatDecision, started: Long, attemptId: String?, modelVersion: String?) {
        val message = requireMessage(id)
        val outcome: String = when (decision) {
            is ChatDecision.Register -> {
                closeUndo()
                val partial = PartialEntryOf.from(decision.entry)
                store.saveMessage(
                    message.copy(
                        state = MessageState.Applying,
                        operationId = message.operationId ?: newId(),
                        fields = DraftCodec.toFields(partial),
                        categoryHints = decision.categoryHints,
                    ),
                )
                applyRegister(id)
                "applied"
            }
            is ChatDecision.Ask -> {
                closeUndo()
                ask(message, decision.draft, decision.question)
                "question"
            }
            is ChatDecision.Correct -> {
                perform(message, decision.plan)
                "applied"
            }
            is ChatDecision.AskCorrection -> {
                askCorrection(message, decision.request, decision.question)
                "question"
            }
            is ChatDecision.Answer -> {
                store.saveMessage(message.copy(state = MessageState.Applied))
                answerWith(decision.questionId, decision.value)
                "applied"
            }
            is ChatDecision.Reply -> {
                if (decision.reply !in KEEPS_UNDO) closeUndo()
                store.saveMessage(
                    message.copy(
                        state = MessageState.Replied,
                        reply = decision.reply,
                        fields = decision.prefill?.let(DraftCodec::toFields).orEmpty(),
                    ),
                )
                "replied"
            }
            is ChatDecision.OpenEdit -> {
                store.saveMessage(message.copy(state = MessageState.Replied, editEntryId = decision.entryId))
                "replied"
            }
            is ChatDecision.Refuse -> {
                closeUndo()
                store.saveMessage(
                    message.copy(
                        state = MessageState.Refused,
                        refuse = decision.reason,
                        fields = decision.prefill?.let(DraftCodec::toFields).orEmpty(),
                    ),
                )
                "refused"
            }
        }
        recordAttempt(attemptId, id, modelVersion, started, outcome)
    }

    /** A próxima mensagem fecha o Desfazer dos recibos anteriores (decisão de 24/10/2026). */
    private suspend fun closeUndo() {
        store.snapshot().receipts.filter { !it.undoClosed }.forEach { store.saveReceipt(it.copy(undoClosed = true)) }
    }

    // ---- registro ----

    /** Repete com segurança: o identificador de operação já está gravado na mensagem. */
    private suspend fun applyRegister(messageId: String) {
        val message = requireMessage(messageId)
        val operationId = checkNotNull(message.operationId)
        val context = buildContext()
        val partial = DraftCodec.fromFields(message.fields)
        val decision = EntryCompleter.complete(partial, context)
        if (decision !is ChatDecision.Register) {
            // A conta ou o cartão deixaram de existir entre a gravação e a repetição.
            store.saveMessage(message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected))
            return
        }
        val command = ChatCommands.register(decision.entry, clock.today())
        val envelope = Envelope(CommandId(operationId), Origin.Chat, draftId = message.draftId)
        when (val result = space.execute(envelope, command)) {
            is CommandResult.Applied -> afterRegister(message, decision.entry, result.receipt, context)
            is CommandResult.AlreadyApplied -> afterRegister(message, decision.entry, result.receipt, context)
            is CommandResult.Rejected -> {
                store.saveMessage(
                    message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected, note = result.errors.firstOrNull()?.message),
                )
            }
            is CommandResult.DecisionNeeded, is CommandResult.StaleVersion ->
                store.saveMessage(message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected))
        }
    }

    private suspend fun afterRegister(message: ChatMessage, entry: CompleteEntry, receipt: Receipt, context: ChatContext) {
        onFinanceChanged()
        val entryId = receipt.changes.first { it.type in ENTRY_TYPES }.id
        val existing = store.receiptByOperation(receipt.operationId)
        if (existing == null) {
            val summary = summaryOf(entryId, null)
            store.saveReceipt(
                ChatReceiptRecord(
                    id = newId(),
                    messageId = message.id,
                    operationId = receipt.operationId,
                    entryId = entryId,
                    summary = summary,
                    kind = ReceiptKind.Registered,
                    state = ReceiptState.Active,
                    undoClosed = false,
                    supersedes = null,
                    createdAtMillis = nowMillis(),
                ),
            )
        }
        closeQuestionsOf(message.id, QuestionState.Answered)
        val toClassify = entry.nature == Nature.Expense && entry.categoryId == null
        store.saveMessage(
            message.copy(state = if (toClassify) MessageState.ToClassify else MessageState.Applied, draftId = null, categoryHints = message.categoryHints),
        )
        if (toClassify && store.snapshot().questions.none { it.link == QuestionLink.Entry(entryId) }) {
            val summary = summaryOf(entryId, null)
            store.saveQuestion(
                ChatQuestionRecord(
                    id = newId(),
                    messageId = message.id,
                    field = QuestionField.Category,
                    reason = ChatQuestion.Reason.Missing,
                    options = EntryCompleter.categoryOptions(context, message.categoryHints.mapNotNull { id -> context.categoryById(id)?.ref }),
                    link = QuestionLink.Entry(entryId),
                    description = summary.description,
                    amountCents = summary.totalCents,
                    state = QuestionState.Open,
                    createdAtMillis = nowMillis(),
                ),
            )
        }
    }

    private suspend fun closeQuestionsOf(messageId: String, state: QuestionState) {
        store.snapshot().questions.filter { it.messageId == messageId && it.state == QuestionState.Open && it.link is QuestionLink.Draft }
            .forEach { store.saveQuestion(it.copy(state = state)) }
    }

    // ---- perguntas ----

    private suspend fun ask(message: ChatMessage, draft: PartialEntry, question: ChatQuestion) {
        closeQuestionsOf(message.id, QuestionState.Answered)
        val saved = space.saveDraft(
            LocalDraft(
                id = message.draftId.orEmpty(),
                source = DraftSource.Chat,
                fields = DraftCodec.toFields(draft),
                missing = DraftCodec.missing(question),
                reason = "chat",
                updatedAtMillis = nowMillis(),
            ),
        ).get()
        store.saveMessage(
            message.copy(
                state = MessageState.Asked,
                draftId = saved,
                operationId = message.operationId ?: newId(),
                fields = DraftCodec.toFields(draft),
            ),
        )
        val options = if (question.field == QuestionField.Category) {
            EntryCompleter.categoryOptions(buildContext(), emptyList())
        } else {
            question.options
        }
        store.saveQuestion(
            ChatQuestionRecord(
                id = newId(),
                messageId = message.id,
                field = question.field,
                reason = question.reason,
                options = options,
                link = QuestionLink.Draft(saved),
                description = draft.description,
                amountCents = draft.amount?.cents,
                state = QuestionState.Open,
                createdAtMillis = nowMillis(),
            ),
        )
    }

    private suspend fun askCorrection(message: ChatMessage, request: CorrectionRequest, question: ChatQuestion) {
        store.saveMessage(message.copy(state = MessageState.Asked))
        store.saveQuestion(
            ChatQuestionRecord(
                id = newId(),
                messageId = message.id,
                field = question.field,
                reason = question.reason,
                options = question.options,
                link = QuestionLink.Correction(request),
                description = null,
                amountCents = null,
                state = QuestionState.Open,
                createdAtMillis = nowMillis(),
            ),
        )
    }

    /** Aplica uma resposta (toque ou digitada) à pergunta e segue o mesmo caminho da frase original. */
    private suspend fun answerWith(questionId: String, value: AnswerValue) {
        val question = store.question(questionId) ?: return
        if (question.state != QuestionState.Open) return
        when (val link = question.link) {
            is QuestionLink.Draft -> answerDraft(question, link, value)
            is QuestionLink.Entry -> answerClassification(question, link, value)
            is QuestionLink.Correction -> answerCorrection(question, link, value)
        }
    }

    private suspend fun answerDraft(question: ChatQuestionRecord, link: QuestionLink.Draft, value: AnswerValue) {
        val draft = space.drafts().get().firstOrNull { it.id == link.draftId }
        if (draft == null) {
            store.saveQuestion(question.copy(state = QuestionState.Discarded))
            return
        }
        val message = requireMessage(question.messageId)
        val applied = EntryCompleter.apply(DraftCodec.fromFields(draft.fields), question.field, value)
        val context = buildContext()
        when (val decision = EntryCompleter.complete(applied, context)) {
            is ChatDecision.Register -> {
                store.saveQuestion(question.copy(state = QuestionState.Answered))
                store.saveMessage(
                    message.copy(
                        state = MessageState.Applying,
                        fields = DraftCodec.toFields(PartialEntryOf.from(decision.entry)),
                        operationId = message.operationId ?: newId(),
                    ),
                )
                closeUndo()
                applyRegister(message.id)
            }
            is ChatDecision.Ask -> ask(message, decision.draft, decision.question)
            is ChatDecision.Refuse -> store.saveMessage(message.copy(refuse = decision.reason))
            else -> Unit
        }
    }

    private suspend fun answerClassification(question: ChatQuestionRecord, link: QuestionLink.Entry, value: AnswerValue) {
        val category = ((value as? AnswerValue.Option)?.value as? OptionValue.Category) ?: return
        val detail = space.entry(link.entryId).get()
        if (detail == null || detail.voided) {
            store.saveQuestion(question.copy(state = QuestionState.Discarded))
            return
        }
        if (detail.classification != app.accord.core.space.Classification.ToClassify) {
            // Classificado em outra tela, ou pela outra pessoa: a opção antiga não sobrescreve.
            store.saveQuestion(question.copy(state = QuestionState.ResolvedElsewhere))
            return
        }
        val command = ChatCommands.correct(EntryChange.Category(category.id), detail)
        val message = requireMessage(question.messageId)
        when (val result = space.execute(Envelope(CommandId(newId()), Origin.Chat), command)) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                val receipt = receiptOf(result)
                onFinanceChanged()
                store.saveQuestion(question.copy(state = QuestionState.Answered))
                supersedeAndSave(
                    entryId = link.entryId, newEntryId = link.entryId, receipt = receipt, message = message,
                    kind = ReceiptKind.Classified, scope = null,
                )
                store.saveMessage(message.copy(state = MessageState.Applied))
            }
            is CommandResult.StaleVersion -> {
                val fresh = space.entry(link.entryId).get()
                store.saveQuestion(question.copy(state = if (fresh != null && fresh.classification != app.accord.core.space.Classification.ToClassify) QuestionState.ResolvedElsewhere else QuestionState.Open))
            }
            else -> store.saveMessage(message.copy(refuse = RefuseReason.Rejected, note = (result as? CommandResult.Rejected)?.errors?.firstOrNull()?.message))
        }
    }

    private suspend fun answerCorrection(question: ChatQuestionRecord, link: QuestionLink.Correction, value: AnswerValue) {
        val option = (value as? AnswerValue.Option)?.value ?: return
        val message = requireMessage(question.messageId)
        val context = buildContext()
        val request = CorrectionCompleter.apply(link.request, context, option)
        store.saveQuestion(question.copy(state = QuestionState.Answered))
        when (val decision = CorrectionCompleter.complete(request, context)) {
            is ChatDecision.Correct -> perform(message, decision.plan)
            is ChatDecision.AskCorrection -> askCorrection(message, decision.request, decision.question)
            is ChatDecision.OpenEdit -> store.saveMessage(message.copy(state = MessageState.Replied, editEntryId = decision.entryId))
            is ChatDecision.Refuse -> store.saveMessage(message.copy(state = MessageState.Refused, refuse = decision.reason))
            else -> Unit
        }
    }

    // ---- correções ----

    private suspend fun perform(message: ChatMessage, plan: CorrectionPlan) {
        val detail = space.entry(plan.entryId).get()
        if (detail == null || detail.voided) {
            store.saveMessage(message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected))
            return
        }
        val command = ChatCommands.correct(plan.change, detail)
        when (val result = space.execute(Envelope(CommandId(newId()), Origin.Chat), command)) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                val receipt = receiptOf(result)
                onFinanceChanged()
                val newEntryId = receipt.changes.firstOrNull { it.type == EntityType.Movement && it.id != plan.entryId && plan.change is EntryChange.Payment }?.id
                    ?: plan.entryId
                val scope = (plan.change as? EntryChange.Category ?: plan.change as? EntryChange.Description)
                    ?.let { detail.purchase?.installments?.size?.takeIf { count -> count > 1 } }
                supersedeAndSave(plan.entryId, newEntryId, receipt, message, ReceiptKind.Corrected, scope)
                if (plan.change is EntryChange.Category) {
                    store.snapshot().questions.filter { it.link == QuestionLink.Entry(plan.entryId) && it.state == QuestionState.Open }
                        .forEach { store.saveQuestion(it.copy(state = QuestionState.ResolvedElsewhere)) }
                }
                store.saveMessage(message.copy(state = MessageState.Applied))
            }
            is CommandResult.StaleVersion ->
                store.saveMessage(message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected, note = null))
            is CommandResult.Rejected ->
                store.saveMessage(
                    message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected, note = result.errors.firstOrNull()?.message),
                )
            is CommandResult.DecisionNeeded -> store.saveMessage(message.copy(state = MessageState.Refused, refuse = RefuseReason.Rejected))
        }
    }

    private fun receiptOf(result: CommandResult): Receipt = when (result) {
        is CommandResult.Applied -> result.receipt
        is CommandResult.AlreadyApplied -> result.receipt
        else -> error("Sem recibo")
    }

    /** Grava o recibo novo e marca "alterado depois" os anteriores do lançamento. */
    private suspend fun supersedeAndSave(
        entryId: String,
        newEntryId: String,
        receipt: Receipt,
        message: ChatMessage,
        kind: ReceiptKind,
        scope: Int?,
    ) {
        if (store.receiptByOperation(receipt.operationId) != null) return
        val previous = store.receiptsOfEntry(entryId).filter { it.state == ReceiptState.Active }
        previous.forEach { store.saveReceipt(it.copy(state = ReceiptState.ChangedLater, undoClosed = true)) }
        store.saveReceipt(
            ChatReceiptRecord(
                id = newId(),
                messageId = message.id,
                operationId = receipt.operationId,
                entryId = newEntryId,
                summary = summaryOf(newEntryId, scope),
                kind = kind,
                state = ReceiptState.Active,
                undoClosed = false,
                supersedes = previous.maxByOrNull { it.createdAtMillis }?.id,
                createdAtMillis = nowMillis(),
            ),
        )
    }

    // ---- contexto e resumo ----

    private suspend fun summaryOf(entryId: String, scope: Int?): ReceiptSummary {
        val detail = checkNotNull(space.entry(entryId).get()) { "Lançamento $entryId sumiu" }
        return ReceiptSummaries.from(detail, space.categories().get(), space.accounts().get(), scope)
    }

    private suspend fun buildContext(): ChatContext {
        val categories = space.categories().get()
        val accounts = space.accounts().get()
        val card = space.card().get()
        val openQuestion = store.openQuestions().maxByOrNull { it.createdAtMillis }
        val receipts = store.activeReceipts(RECENT_RECEIPTS)
        return ChatContext(
            today = clock.today(),
            categories = categories.mapIndexed { index, node -> CategoryItem("c${index + 1}", node.id, node.name, node.parentId) },
            hasCard = true,
            cardLabel = card.nickname,
            accounts = accounts.accounts.mapIndexed { index, account ->
                AccountItem("a${index + 1}", account.id, ReceiptSummaries.accountLabel(bankName(account.bank), account.nickname))
            },
            myDefaultAccountId = accounts.myDefaultAccountId,
            members = members(),
            question = openQuestion?.let { OpenQuestion("q1", it.id, it.field, it.options) },
            receipts = receipts.mapIndexed { index, receipt ->
                ReceiptItem(
                    "r${index + 1}", receipt.id, receipt.entryId, ReceiptSummaries.label(receipt.summary), receipt.summary.installments,
                    hasAccount = receipt.summary.accountLabel != null,
                )
            },
        )
    }

    private fun bankName(bank: app.accord.core.space.BankRef) = when (bank) {
        is app.accord.core.space.BankRef.Catalog -> bank.name
        is app.accord.core.space.BankRef.Other -> bank.name
    }

    private fun <T> QueryResult<T>.get(): T = when (this) {
        is QueryResult.Data -> value
        is QueryResult.Failure -> throw cause
    }

    private companion object {
        const val RECENT_RECEIPTS = 3
        val RETRYABLE = setOf(MessageState.Failed, MessageState.Interrupted, MessageState.Canceled, MessageState.NoModel, MessageState.Refused)
        val ENTRY_TYPES = setOf(EntityType.CardPurchase, EntityType.Movement, EntityType.Income)

        /** Respostas que não fecham o Desfazer: o botão que elas oferecem precisa continuar funcionando. */
        val KEEPS_UNDO = setOf(StandardReply.UndoByText, StandardReply.NoRecentReceipt)
    }
}

/** Lançamento completo de volta a campos normalizados, para gravar o que será aplicado. */
internal object PartialEntryOf {
    fun from(entry: CompleteEntry) = PartialEntry(
        nature = entry.nature,
        description = entry.description,
        amount = entry.amount,
        role = entry.role,
        installments = entry.installments,
        date = entry.date,
        categoryId = entry.categoryId,
        payment = entry.payment,
        accountId = entry.accountId,
        spentBy = entry.spentBy,
    )
}
