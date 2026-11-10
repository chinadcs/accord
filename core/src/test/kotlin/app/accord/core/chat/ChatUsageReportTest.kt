package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatUsageReportTest {
    private val since = 1_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun summary() = ReceiptSummary(Nature.Expense, "x", 100, dateIso = "2026-11-10")
    private fun receipt(
        id: String, at: Long = since + 10, kind: ReceiptKind = ReceiptKind.Registered,
        state: ReceiptState = ReceiptState.Active, supersedes: String? = null,
    ) = ChatReceiptRecord(id, "m-$id", "op-$id", "e-$id", summary(), kind, state, false, supersedes, at)

    private fun message(id: String, at: Long = since + 10) = ChatMessage(id = id, text = "frase secreta $id", createdAtMillis = at)
    private fun attempt(id: String, outcome: String, ms: Long, version: String? = null, at: Long = since + 10) =
        ChatAttempt(id, "m-$id", version, at, ms, outcome)

    private fun snapshot(
        receipts: List<ChatReceiptRecord> = emptyList(), feedback: List<ChatFeedback> = emptyList(),
        messages: List<ChatMessage> = emptyList(), questions: List<ChatQuestionRecord> = emptyList(),
    ) = ChatSnapshot(messages, questions, receipts, feedback)

    private fun vote(id: String, vote: FeedbackVote, vararg fields: FeedbackField) =
        ChatFeedback(id, vote, fields.firstOrNull(), since + 20, fields.toSet())

    @Test
    fun `a janela deixa de fora o que veio antes e o fim e exclusivo`() {
        val report = ChatUsageReport.build(
            snapshot(
                receipts = listOf(receipt("antes", at = since - 1), receipt("dentro", at = since), receipt("fim", at = since + 7 * day)),
                messages = listOf(message("a", since - 1), message("b", since), message("c", since + 7 * day)),
            ),
            attempts = listOf(attempt("a", "applied", 5, at = since - 1), attempt("b", "applied", 5, at = since)),
            events = emptyList(), sinceMillis = since,
        )
        assertEquals(1, report.receipts)
        assertEquals(1, report.messagesEligible)
        assertEquals(1, report.attempts)
    }

    @Test
    fun `um recibo com voto negativo e correcao entra uma vez so na taxa combinada`() {
        val original = receipt("r1", state = ReceiptState.ChangedLater)
        val corrected = receipt("r2", kind = ReceiptKind.Corrected, supersedes = "r1")
        val report = ChatUsageReport.build(
            snapshot(
                receipts = listOf(original, corrected, receipt("r3"), receipt("r4", state = ReceiptState.Undone)),
                feedback = listOf(vote("r1", FeedbackVote.Down, FeedbackField.Category), vote("r3", FeedbackVote.Up)),
            ),
            attempts = emptyList(), events = emptyList(), sinceMillis = since,
        )
        assertEquals(4, report.receipts)
        assertEquals(2, report.receiptsVoted)
        assertEquals(2, report.receiptsUnvoted)
        assertEquals(1, report.votesUp)
        assertEquals(1, report.votesDown)
        assertEquals(1, report.receiptsCorrectedLater)
        assertEquals(1, report.receiptsUndone)
        // r1 (voto negativo + corrigido) e r4 (desfeito): dois recibos, não três sinais.
        assertEquals(2, report.receiptsWithAnyErrorSignal)
    }

    @Test
    fun `classificar depois de a classificar nao conta como erro`() {
        val original = receipt("r1", state = ReceiptState.ChangedLater)
        val classified = receipt("r2", kind = ReceiptKind.Classified, supersedes = "r1")
        val report = ChatUsageReport.build(snapshot(receipts = listOf(original, classified)), emptyList(), emptyList(), since)
        assertEquals(1, report.receiptsClassifiedLater)
        assertEquals(0, report.receiptsCorrectedLater)
        assertEquals(0, report.receiptsWithAnyErrorSignal)
    }

    @Test
    fun `motivos do joinha para baixo contam em cada motivo e o voto sem motivo aparece a parte`() {
        val report = ChatUsageReport.build(
            snapshot(
                receipts = listOf(receipt("a"), receipt("b"), receipt("c")),
                feedback = listOf(
                    vote("a", FeedbackVote.Down, FeedbackField.Category, FeedbackField.Date),
                    vote("b", FeedbackVote.Down, FeedbackField.Category),
                    vote("c", FeedbackVote.Down),
                ),
            ),
            emptyList(), emptyList(), since,
        )
        assertEquals(3, report.votesDown)
        assertEquals(2, report.downByField[FeedbackField.Category])
        assertEquals(1, report.downByField[FeedbackField.Date])
        assertEquals(1, report.votesDownWithoutField)
    }

    @Test
    fun `tentativas ficam separadas por versao e a latencia usa o posto mais proximo`() {
        val report = ChatUsageReport.build(
            snapshot(),
            attempts = (1..20).map { attempt("n$it", "applied", it * 10L, version = "rules-1") } +
                listOf(attempt("q", "question", 7, version = "rules-1"), attempt("f", "failed", 9, version = "slm-x"), attempt("c", "canceled", 1, version = null)),
            events = emptyList(), sinceMillis = since,
        )
        assertEquals(23, report.attempts)
        assertEquals(20, report.validResults)
        assertEquals(1, report.questions)
        assertEquals(3, report.byVersion.size)
        val rules = report.byVersion.single { it.modelVersion == "rules-1" }
        assertEquals(21, rules.attempts)
        assertEquals(1, rules.questions)
        assertEquals(200L, rules.latency.max)
        assertEquals(190L, rules.latency.p95)
        assertEquals(LatencyStats.Empty, ChatUsageReport.build(snapshot(), emptyList(), emptyList(), since).byVersion.firstOrNull()?.latency ?: LatencyStats.Empty)
    }

    @Test
    fun `latencia do sugestor separa carga a frio de modelo carregado`() {
        val events = listOf(
            UsageEvent(CategoryPilotEventKind.Loaded, since + 1, loadMillis = 900),
            UsageEvent(CategoryPilotEventKind.Evaluated, since + 2, loadMillis = 800, inferenceMillis = 100),
            UsageEvent(CategoryPilotEventKind.Evaluated, since + 3, inferenceMillis = 40),
            UsageEvent(CategoryPilotEventKind.Evaluated, since + 4, inferenceMillis = 60),
            UsageEvent(CategoryPilotEventKind.Evaluated, since - 5, inferenceMillis = 9_999),
        )
        val report = ChatUsageReport.build(snapshot(), emptyList(), events, since)
        assertEquals(3, report.categoryEvaluated)
        assertEquals(1, report.categoryColdLatency.n)
        assertEquals(900L, report.categoryColdLatency.max)
        assertEquals(2, report.categoryLoadedLatency.n)
        assertEquals(60L, report.categoryLoadedLatency.max)
        assertEquals(900L, report.categoryLoadLatency.p50)
    }

    @Test
    fun `pergunta de categoria respondida sem evento e perda detectavel`() {
        fun question(id: String) = ChatQuestionRecord(
            id, "m", QuestionField.Category, ChatQuestion.Reason.Missing,
            listOf(QuestionOption("c1", "x", OptionValue.Category("cat"))), QuestionLink.Entry("e"), null, null, QuestionState.Answered, since + 5,
        )
        val events = listOf(
            UsageEvent(CategoryPilotEventKind.Published, since + 1),
            UsageEvent(CategoryPilotEventKind.Published, since + 2),
            UsageEvent(CategoryPilotEventKind.Accepted, since + 3),
        )
        val report = ChatUsageReport.build(
            snapshot(questions = listOf(question("q1"), question("q2"))), emptyList(), events, since,
            unreadableEventLines = 3,
        )
        assertEquals(1, report.detectableLostCategoryEvents)
        assertEquals(3, report.unreadableEventLines)
    }

    @Test
    fun `leitura do arquivo descarta linha ilegivel e conta o descarte`() {
        val parsed = ChatUsageReport.parseEvents(
            sequenceOf(
                """{"requestId":"a","atMillis":10,"event":"Evaluated","loadMillis":5,"inferenceMillis":7}""",
                "{quebrado", "",
                """{"requestId":"b","atMillis":11,"event":"Desconhecido"}""",
                """{"requestId":"c","atMillis":12,"event":"Accepted"}""",
            ),
        )
        assertEquals(listOf(CategoryPilotEventKind.Evaluated, CategoryPilotEventKind.Accepted), parsed.events.map { it.kind })
        assertEquals(2, parsed.skipped)
        assertEquals(5L, parsed.events.first().loadMillis)
    }

    @Test
    fun `o texto nao carrega frase, e admite os limites do voto`() {
        val text = ChatUsageReport.build(
            snapshot(messages = listOf(message("a")), receipts = listOf(receipt("a")), feedback = listOf(vote("a", FeedbackVote.Up))),
            listOf(attempt("a", "applied", 3)), emptyList(), since, deviceLabel = "S20 FE",
        ).render()
        assertFalse("secreta" in text)
        assertTrue("utilidade percebida" in text)
        assertTrue("S20 FE" in text)
        assertTrue("outro celular" in text)
        assertNull(ChatUsageReport.build(snapshot(), emptyList(), emptyList(), since).rateNegativeAmongVoted)
    }
}
