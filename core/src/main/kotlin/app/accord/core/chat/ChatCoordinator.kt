package app.accord.core.chat

import app.accord.core.BudgetClock
import app.accord.core.analysis.ProductPhase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
    private val categorySuggester: CategorySuggester? = null,
    private val onCategoryPilotEvent: (CategoryPilotEvent) -> Unit = {},
    /** Fase do produto (S6): no P1 o Chat responde as consultas do catálogo; no P0 só orienta. */
    private val phase: ProductPhase = ProductPhase.P0,
) {
    val snapshot: Flow<ChatSnapshot> get() = store.observe()

    private val queryRunner = ChatQueryRunner(space, clock)

    private val queue = Channel<String>(Channel.UNLIMITED)
    private val stateLock = Mutex()
    private val running = java.util.concurrent.ConcurrentHashMap<String, Deferred<Output>>()
    private val canceled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private sealed interface Output {
        data class Text(val raw: String) : Output
        data class Failure(val error: Throwable) : Output
    }

    init {
        if (categorySuggester != null) scope.launch(Dispatchers.Default) {
            try {
                val loadMillis = categorySuggester.prepare()
                pilotEvent(CategoryPilotEvent(UUID.randomUUID().toString(), CategoryPilotEventKind.Loaded, loadMillis = loadMillis))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
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

    suspend fun createConversation() = store.createConversation()
    suspend fun selectConversation(id: String) = store.selectConversation(id)
    suspend fun renameConversation(id: String, title: String) = store.renameConversation(id, title)
    suspend fun saveConversationDraft(id: String, text: String) = store.saveConversationDraft(id, text)
    suspend fun saveConversationPosition(id: String, index: Int, offset: Int) = store.saveConversationPosition(id, index, offset)

    /** Grava a mensagem e a enfileira. Textos iguais em mensagens distintas são intenções distintas. */
    suspend fun send(text: String, conversationId: String? = null): String? = stateLock.withLock {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withLock null
        val conversation = if (conversationId == null) store.ensureActiveConversation() else store.conversation(conversationId) ?: return@withLock null
        val id = newId()
        store.saveMessage(ChatMessage(id = id, text = trimmed, createdAtMillis = nowMillis(), conversationId = conversation.id))
        store.touchConversation(conversation.id)
        queue.send(id)
        id
    }

    suspend fun deleteConversation(id: String): Boolean = stateLock.withLock {
        detachQuestions(id)
        val ids = store.snapshot().messages.filter { it.conversationId == id && it.state in setOf(MessageState.Sent,MessageState.Interpreting,MessageState.Applying) }.map { it.id }
        canceled.addAll(ids)
        ids.forEach { running[it]?.cancel() }
        store.deleteConversation(id)
    }

    suspend fun clearHistory() = stateLock.withLock {
        detachQuestions(null)
        val ids = store.snapshot().messages.filter { it.state in setOf(MessageState.Sent,MessageState.Interpreting,MessageState.Applying) }.map { it.id }
        canceled.addAll(ids)
        ids.forEach { running[it]?.cancel() }
        store.clear()
    }

    /** Antes de apagar os recibos, preserva apenas os alvos financeiros estruturados das dúvidas. */
    private suspend fun detachQuestions(conversationId: String?) {
        val snapshot = store.snapshot()
        val messages = snapshot.messages.filter { conversationId == null || it.conversationId == conversationId }.map { it.id }.toSet()
        val receipts = snapshot.receipts.associateBy { it.id }
        snapshot.questions.filter { it.messageId in messages && it.state == QuestionState.Open }.forEach { q ->
            val request = (q.link as? QuestionLink.Correction)?.request ?: return@forEach
            val targets = listOfNotNull(request.entryId, request.receiptId?.let { receipts[it]?.entryId }) +
                q.options.mapNotNull { (it.value as? OptionValue.Receipt)?.receiptId?.let { id -> receipts[id]?.entryId } }
            store.saveQuestion(q.copy(detachedEntryIds = (q.detachedEntryIds + targets).distinct()))
        }
    }

    /** Cancela só a tentativa: o texto fica e nada é aplicado; resultado tardio é descartado. */
    fun cancel(messageId: String) {
        scope.launch {
            stateLock.withLock {
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
    }

    /** "Tentar de novo" e "Interpretar agora": nova tentativa explícita, nunca automática. */
    suspend fun retry(messageId: String) = stateLock.withLock {
        val message = store.message(messageId) ?: return@withLock
        if (message.state !in RETRYABLE) return@withLock
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
            val message = store.message(messageId)
            if (message != null) store.saveMessage(message.copy(state = MessageState.ResolvedManually))
            store.snapshot().questions.filter { it.messageId == messageId && it.state == QuestionState.Open }
                .forEach {
                    store.saveQuestion(it.copy(state = QuestionState.Discarded))
                    (it.link as? QuestionLink.Draft)?.let { link -> space.discardDraft(link.draftId) }
                }
            message?.draftId?.let { space.discardDraft(it) }
        }
    }

    /**
     * Joinha do recibo (accord-8q5). Tocar de novo no voto vigente, sem campo, o retira; escolher o campo do joinha para baixo
     * completa o mesmo voto. Só o ID do recibo, o voto, o campo e o instante são guardados; nada sai do aparelho.
     */
    suspend fun rate(receiptId: String, vote: FeedbackVote, field: FeedbackField? = null) = stateLock.withLock {
        val receipt = store.receipt(receiptId) ?: return@withLock
        val current = store.feedback().firstOrNull { it.receiptId == receiptId }
            ?.takeIf { it.assessedOperationId == null || it.assessedOperationId == receipt.operationId }
        if (current?.vote == vote && field == null) store.clearFeedback(receiptId)
        else {
            val fields = if (vote != FeedbackVote.Down) emptySet() else if (field == null) emptySet() else {
                val previous = current?.takeIf { it.vote == FeedbackVote.Down }?.fields.orEmpty()
                if (field in previous) previous - field else previous + field
            }
            store.saveFeedback(ChatFeedback(receiptId, vote, fields.minByOrNull { it.ordinal }, nowMillis(),fields,receipt.operationId))
        }
    }

    /** Retém os votos por 30 dias; os mais antigos são apagados. */
    suspend fun purgeExpiredFeedback() = store.purgeFeedbackBefore(nowMillis() - FEEDBACK_RETENTION_MILLIS)

    /** Marca o recibo como desfeito, reativa a versão que ele substituiu e ajusta as perguntas do lançamento. */
    private suspend fun markUndone(receipt: ChatReceiptRecord, questions: List<ChatQuestionRecord>) {
        store.saveReceipt(receipt.copy(state = ReceiptState.Undone))
        val supersedes = receipt.supersedes?.let { store.receipt(it) }
        if (supersedes != null && supersedes.state == ReceiptState.ChangedLater) {
            store.saveReceipt(supersedes.copy(state = ReceiptState.Active))
        }
        questions.filter { it.link == QuestionLink.Entry(receipt.entryId) }.forEach { question ->
            val next = when {
                question.state == QuestionState.Open -> QuestionState.Discarded
                receipt.kind == ReceiptKind.Classified && question.state == QuestionState.Answered -> QuestionState.Open
                else -> question.state
            }
            if (next != question.state) store.saveQuestion(question.copy(state = next))
        }
    }

    /**
     * Uma edição feita fora do Chat (botão "Editar" do recibo, ou qualquer tela) mudou o lançamento: o card passa a mostrar
     * a versão atual no lugar do original, marcado como corrigido. Lançamento sem recibo ativo no Chat não é assunto daqui.
     */
    suspend fun entryEdited(entryId: String, operationId: String) = stateLock.withLock {
        if (store.receiptByOperation(operationId) != null) return@withLock
        val original = store.receiptsOfEntry(entryId).filter { it.state == ReceiptState.Active }.maxByOrNull { it.createdAtMillis }
            ?: return@withLock
        val detail = space.entry(entryId).get() ?: return@withLock
        if (detail.voided) return@withLock
        val summary = summaryOf(entryId, null)
        if (summary == original.summary) return@withLock
        store.saveReceipt(original.copy(state = ReceiptState.ChangedLater, undoClosed = true))
        store.saveReceipt(
            ChatReceiptRecord(
                id = newId(), messageId = original.messageId, operationId = operationId, entryId = entryId, summary = summary,
                kind = ReceiptKind.Corrected, state = ReceiptState.Active, undoClosed = false, supersedes = original.id,
                createdAtMillis = nowMillis(),
            ),
        )
    }

    /** Um Desfazer de fora do Chat (aviso depois de editar) desfez a operação deste recibo: o card volta à versão anterior. */
    suspend fun operationUndone(operationId: String) = stateLock.withLock {
        val receipt = store.receiptByOperation(operationId)?.takeIf { it.state == ReceiptState.Active } ?: return@withLock
        markUndone(receipt, store.snapshot().questions)
    }

    /** Desfaz a operação do recibo mais recente. Se algo mudou depois, a fachada recusa e nada é apagado. */
    suspend fun undo(receiptId: String): UndoResult = stateLock.withLock {
        val snapshot = store.snapshot()
        val target = snapshot.receipts.firstOrNull { it.id == receiptId } ?: return UndoResult.NotAvailable
        val conversationId = snapshot.messages.firstOrNull { it.id == target.messageId }?.conversationId ?: return UndoResult.NotAvailable
        val receipt = snapshot.forConversation(conversationId).undoableReceipt()?.takeIf { it.id == receiptId } ?: return UndoResult.NotAvailable
        when (val result = space.execute(Envelope(CommandId(newId()), Origin.Chat), Undo(receipt.operationId))) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                onFinanceChanged()
                markUndone(receipt, snapshot.questions)
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
            store.ensureActiveConversation()
            store.messagesIn(setOf(MessageState.Interpreting)).forEach { store.saveMessage(it.copy(state = MessageState.Interrupted)) }
            store.messagesIn(setOf(MessageState.Applying)).sortedBy { it.createdAtMillis }.forEach { applyRegister(it.id) }
            store.messagesIn(setOf(MessageState.Sent)).sortedBy { it.createdAtMillis }.map { it.id }
        }
        pending.forEach { queue.send(it) }
    }

    // ---- fila ----

    private suspend fun process(id: String) {
        val message = store.message(id) ?: run { canceled -= id; return }
        if (message.state != MessageState.Sent) return

        ChatValidator.prefilter(message.text)?.let { decision ->
            stateLock.withLock { if (store.message(id)!=null) handle(id, decision, started = nowMillis(), attemptId = null, modelVersion = null) }
            return
        }
        val active = interpreters.current()
        if (active == null) {
            stateLock.withLock { store.message(id)?.let { store.saveMessage(it.copy(state = MessageState.NoModel)) } }
            return
        }

        val attemptId = newId()
        val started = nowMillis()
        val context = stateLock.withLock {
            val current=store.message(id) ?: return@withLock null
            store.saveMessage(current.copy(state = MessageState.Interpreting, attempts = current.attempts + 1))
            buildContext(current.conversationId)
        } ?: return
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
        if (id in canceled) attempt.cancel()
        val output = try {
            attempt.await()
        } catch (e: CancellationException) {
            if (id !in canceled) throw e
            null
        } finally {
            running.remove(id)
        }

        stateLock.withLock {
            if (store.message(id)==null) { canceled -= id; return@withLock }
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
                    val fresh = buildContext(message.conversationId)
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

    private suspend fun saveConsult(message: ChatMessage, snapshot: ConsultSnapshot) {
        store.saveMessage(
            message.copy(
                state = MessageState.Replied,
                reply = if (snapshot.status == ConsultSnapshot.Status.Ask) StandardReply.ConsultAsk else StandardReply.Consult,
                fields = ConsultSnapshot.toFields(snapshot),
            ),
        )
    }

    /**
     * "Atualizar" numa resposta de consulta: recalcula pela fachada com os parâmetros já validados, sem chamar o interpretador, e troca o
     * instante do cálculo. Não cria operação nem pendência.
     */
    suspend fun refreshQuery(id: String): Unit = stateLock.withLock {
        val message = store.message(id) ?: return@withLock
        val snapshot = ConsultSnapshot.fromFields(message.fields) ?: return@withLock
        if (snapshot.status == ConsultSnapshot.Status.Ask) return
        val request = snapshot.request()
        saveConsult(message, ConsultSnapshot.of(request, queryRunner.run(request), nowMillis()))
    }

    private suspend fun requireMessage(id: String) = checkNotNull(store.message(id)) { "Mensagem $id sumiu" }

    // ---- decisões ----

    private suspend fun handle(id: String, decision: ChatDecision, started: Long, attemptId: String?, modelVersion: String?) {
        val message = requireMessage(id)
        val outcome: String = when (decision) {
            is ChatDecision.Register -> {
                closeUndo(message.conversationId)
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
                closeUndo(message.conversationId)
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
                if (decision.reply !in KEEPS_UNDO) closeUndo(message.conversationId)
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
            is ChatDecision.Consult -> {
                // S6: leitura pela fachada. Sem operação na fila, sem pendência, sem lançamento; só o histórico local muda.
                closeUndo(message.conversationId)
                val outcome = queryRunner.run(decision.request)
                saveConsult(message, ConsultSnapshot.of(decision.request, outcome, nowMillis()))
                "replied"
            }
            is ChatDecision.ConsultAsk -> {
                closeUndo(message.conversationId)
                saveConsult(message, ConsultSnapshot.ofAsk(null, decision.ask, nowMillis()))
                "replied"
            }
            is ChatDecision.Refuse -> {
                closeUndo(message.conversationId)
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
    private suspend fun closeUndo(conversationId: String) {
        store.snapshot().forConversation(conversationId).receipts.filter { !it.undoClosed }.forEach { store.saveReceipt(it.copy(undoClosed = true)) }
    }

    // ---- registro ----

    /** Repete com segurança: o identificador de operação já está gravado na mensagem. */
    private suspend fun applyRegister(messageId: String) {
        val message = requireMessage(messageId)
        val operationId = checkNotNull(message.operationId)
        val context = buildContext(message.conversationId)
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
            startCategorySuggestion(message, entryId, context)
        }
        // Estado terminal somente depois do recibo e da pergunta persistidos.
        store.saveMessage(
            message.copy(state = if (toClassify) MessageState.ToClassify else MessageState.Applied, draftId = null, categoryHints = message.categoryHints),
        )
    }

    /** Capture under stateLock, infer outside it, then check again before publishing local hints. */
    private suspend fun startCategorySuggestion(message: ChatMessage, entryId: String, context: ChatContext) {
        val suggester = categorySuggester ?: return
        val question = store.openQuestions().firstOrNull { it.link == QuestionLink.Entry(entryId) } ?: return
        val captured = try {
            (space.entry(entryId).get()?.revision ?: return) to space.categories().get()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return }
        scope.launch {
            try {
                val result = try {
                    withContext(Dispatchers.Default) { suggester.suggest(CategorySuggestionRequest(question.id, message.text, context)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    CategorySuggestion()
                }
                ensureActive()
                pilotEvent(CategoryPilotEvent(question.id, CategoryPilotEventKind.Evaluated, result.reason, result.loadMillis, result.inferenceMillis))
                stateLock.withLock {
                    ensureActive()
                    val fresh = space.entry(entryId).get()
                    val currentMessage = store.message(message.id)
                    if (store.question(question.id) != question || currentMessage?.state != MessageState.ToClassify ||
                        currentMessage.text != message.text || fresh == null || fresh.voided || fresh.revision != captured.first ||
                        fresh.classification != app.accord.core.space.Classification.ToClassify || space.categories().get() != captured.second
                    ) {
                        pilotEvent(CategoryPilotEvent(question.id, CategoryPilotEventKind.Stale))
                        return@withLock
                    }
                    val hints = result.categoryIds.distinct().take(3)
                    if (result.reason != CategorySuggestionReason.Suggested || hints.isEmpty() ||
                        hints.any { context.categoryById(it) == null }
                    ) return@withLock
                    store.saveMessage(currentMessage.copy(categoryHints = hints))
                    store.saveQuestion(question.copy(options = EntryCompleter.categoryOptions(context, hints.mapNotNull { context.categoryById(it)?.ref })))
                    pilotEvent(CategoryPilotEvent(question.id, CategoryPilotEventKind.Published))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The space/store may have closed during sign-out; never revive a previous session.
                pilotEvent(CategoryPilotEvent(question.id, CategoryPilotEventKind.Stale))
            }
        }
    }

    private fun pilotEvent(event: CategoryPilotEvent) {
        // Diagnostics can never interrupt a financial operation. No exception details are logged.
        try { onCategoryPilotEvent(event) } catch (_: Exception) { }
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
            EntryCompleter.categoryOptions(buildContext(message.conversationId), emptyList())
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
        val message = store.message(question.messageId)
        val applied = EntryCompleter.apply(DraftCodec.fromFields(draft.fields), question.field, value)
        if (message == null) return // Sem conversa, o rascunho é retomado pelo formulário em Revisar.
        val context = buildContext(message.conversationId)
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
                closeUndo(message.conversationId)
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
        val message = store.message(question.messageId)
        when (val result = space.execute(Envelope(CommandId(newId()), Origin.Chat), command)) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                val receipt = receiptOf(result)
                onFinanceChanged()
                store.saveQuestion(question.copy(state = QuestionState.Answered))
                if (message == null) return
                supersedeAndSave(
                    entryId = link.entryId, newEntryId = link.entryId, receipt = receipt, messageId = message.id,
                    kind = ReceiptKind.Classified, scope = null,
                )
                store.saveMessage(message.copy(state = MessageState.Applied))
                if (categorySuggester != null) {
                    pilotEvent(CategoryPilotEvent(question.id, if (message.categoryHints.firstOrNull() == category.id)
                        CategoryPilotEventKind.Accepted else CategoryPilotEventKind.OtherChoice))
                }
            }
            is CommandResult.StaleVersion -> {
                val fresh = space.entry(link.entryId).get()
                store.saveQuestion(question.copy(state = if (fresh != null && fresh.classification != app.accord.core.space.Classification.ToClassify) QuestionState.ResolvedElsewhere else QuestionState.Open))
            }
            else -> if (message != null) store.saveMessage(message.copy(refuse = RefuseReason.Rejected, note = (result as? CommandResult.Rejected)?.errors?.firstOrNull()?.message))
        }
    }

    private suspend fun answerCorrection(question: ChatQuestionRecord, link: QuestionLink.Correction, value: AnswerValue) {
        val option = (value as? AnswerValue.Option)?.value ?: return
        val message = requireMessage(question.messageId)
        val context = buildContext(message.conversationId)
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
                supersedeAndSave(plan.entryId, newEntryId, receipt, message.id, ReceiptKind.Corrected, scope)
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
        messageId: String,
        kind: ReceiptKind,
        scope: Int?,
    ) {
        if (store.receiptByOperation(receipt.operationId) != null) return
        val previous = store.receiptsOfEntry(entryId).filter { it.state == ReceiptState.Active }
        previous.forEach { store.saveReceipt(it.copy(state = ReceiptState.ChangedLater, undoClosed = true)) }
        store.saveReceipt(
            ChatReceiptRecord(
                id = newId(),
                messageId = messageId,
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
        return ReceiptSummaries.from(detail, space.categories().get(), space.accounts().get(), scope).copy(
            cardLabel = detail.cardId?.let { id -> space.cards().get().firstOrNull { it.id == id }?.nickname },
        )
    }

    private suspend fun buildContext(conversationId: String): ChatContext {
        val categories = space.categories().get()
        val accounts = space.accounts().get()
        val card = space.card().get()
        val cards = space.cards().get()
        val scoped = store.snapshot().forConversation(conversationId)
        val openQuestion = scoped.questions.filter { it.state == QuestionState.Open }.maxByOrNull { it.createdAtMillis }
        val receipts = scoped.receipts.filter { it.state == ReceiptState.Active }.sortedByDescending { it.createdAtMillis }.take(RECENT_RECEIPTS)
        return ChatContext(
            today = clock.today(),
            categories = categories.mapIndexed { index, node -> CategoryItem("c${index + 1}", node.id, node.name, node.parentId) },
            hasCard = true,
            cardLabel = card.nickname,
            cards = cards.filter { it.configured || cards.none { c -> c.configured } }.mapIndexed { index, c -> AccountItem("card${index+1}", c.id, c.nickname) },
            myDefaultCardId = card.id,
            cardAliases = cards.associate { it.id to listOfNotNull(it.bank?.let(::bankName)) },
            accounts = accounts.accounts.mapIndexed { index, account ->
                AccountItem("a${index + 1}", account.id, ReceiptSummaries.accountLabel(bankName(account.bank), account.nickname))
            },
            myDefaultAccountId = accounts.myDefaultAccountId,
            members = members(),
            question = openQuestion?.let { OpenQuestion("q1", it.id, it.field, it.options) },
            phase = phase,
            seedKeys = categories.mapNotNull { node -> node.seedKey?.let { node.id to it } }.toMap(),
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
        const val FEEDBACK_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
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
        cardId = entry.cardId,
        spentBy = entry.spentBy,
    )
}
