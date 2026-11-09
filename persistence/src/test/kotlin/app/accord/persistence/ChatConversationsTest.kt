package app.accord.persistence

import app.accord.core.chat.*
import app.accord.core.space.QueryResult
import kotlinx.coroutines.*
import kotlin.test.*

class ChatConversationsTest : ChatTestBase() {
    @Test fun `rascunho posicao nome e ultima selecao sao independentes e persistidos`() = runBlocking<Unit> {
        val e = env()
        val a = e.store.createConversation("Casa")
        e.store.saveConversationDraft(a.id, "mercado")
        e.store.saveConversationPosition(a.id, 7, 31)
        val b = e.store.createConversation("Viagem")
        e.store.saveConversationDraft(b.id, "hotel")
        e.store.renameConversation(b.id, "Férias")
        e.store.selectConversation(a.id)
        val restored = RoomChatStore(e.database.db)
        assertEquals(a.id, restored.ensureActiveConversation().id)
        assertEquals("mercado", restored.conversation(a.id)!!.draftText)
        assertEquals(7 to 31, restored.conversation(a.id)!!.let { it.scrollIndex to it.scrollOffset })
        assertEquals("hotel", restored.conversation(b.id)!!.draftText)
        assertEquals("Férias", restored.conversation(b.id)!!.title)
        assertFalse(restored.selectConversation("ausente"))
        assertEquals(a.id, restored.ensureActiveConversation().id)
    }

    @Test fun `recibos e perguntas de outra conversa nao entram no contexto`() = runBlocking<Unit> {
        val e = env()
        val a = e.store.createConversation("A")
        e.fake.on("compra 68 no cartão", reg("compra", "68", category = categoryRef(e), payment = ChatPayment.Card))
        assertEquals(MessageState.Applied, e.say("compra 68 no cartão").state)
        val b = e.store.createConversation("B")
        e.say("despesa desconhecida 20")
        assertTrue(e.fake.requests.last().context.receipts.isEmpty())
        assertNull(e.fake.requests.last().context.question)
        e.store.selectConversation(a.id)
        e.say("outra compra 30")
        assertEquals(1, e.fake.requests.last().context.receipts.size)
        assertEquals(1, e.store.snapshot().forConversation(b.id).messages.size)
        assertEquals(2, e.store.snapshot().forConversation(a.id).messages.size)
    }

    private suspend fun categoryRef(e: Env) = e.categoryRef("alimentacao.delivery")

    @Test fun `excluir conversa cancela tentativa e ignora resposta tardia sem recriar historico`() = runBlocking<Unit> {
        val e = env()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        e.interpretation = Interpreter {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            ProposalCodec.encode(reg("tardia", "50", payment = ChatPayment.Card))
        }
        val a = e.store.createConversation("Apagar")
        val id = e.coordinator.send("compra demorada", a.id)!!
        withTimeout(8_000) { started.await() }
        assertTrue(e.coordinator.deleteConversation(a.id))
        release.complete(Unit)
        e.interpretation = e.fake
        e.say("terminou") // Barreira da fila serial depois da resposta tardia.
        assertNull(e.store.message(id))
        assertNull(e.store.conversation(a.id))
        assertTrue(e.store.snapshot().receipts.isEmpty())
        assertEquals(1, e.store.snapshot().conversations.size)
    }

    @Test fun `excluir preserva lancamento pergunta e classificacao manual sem ressuscitar texto`() = runBlocking<Unit> {
        val e = env()
        val a = e.store.createConversation("Apagar")
        e.fake.on("objeto 25 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("objeto", "25", payment = ChatPayment.Card)))
        val m = e.say("objeto 25 no cartão")
        val q = e.store.openQuestions().single()
        val entry = e.store.snapshot().receipts.single().entryId
        e.coordinator.rate(e.store.snapshot().receipts.single().id, FeedbackVote.Down, FeedbackField.NotEntry)
        e.coordinator.deleteConversation(a.id)
        assertNull(e.store.message(m.id))
        assertTrue(e.store.feedback().isEmpty())
        assertEquals(q, e.store.openQuestions().single())
        assertNotNull((e.space.entry(entry) as QueryResult.Data).value)
        e.coordinator.answer(q.id, q.options.first { it.value is OptionValue.Category })
        assertEquals(QuestionState.Answered, e.store.question(q.id)!!.state)
        assertTrue(e.store.snapshot().messages.isEmpty())
        assertTrue(e.store.snapshot().receipts.isEmpty())
    }

    @Test fun `rascunho estruturado sobrevive a exclusao e pode ser encerrado pelo formulario`() = runBlocking<Unit> {
        val e = env()
        val a = e.store.createConversation("Apagar")
        e.fake.on("gastei no mercado", reg("mercado"))
        val m = e.say("gastei no mercado")
        val q = e.store.openQuestions().single()
        e.coordinator.deleteConversation(a.id)
        assertEquals(1, (e.space.drafts() as QueryResult.Data).value.size)
        e.coordinator.resolveManually(m.id)
        assertEquals(QuestionState.Discarded, e.store.question(q.id)!!.state)
        assertTrue((e.space.drafts() as QueryResult.Data).value.isEmpty())
        assertTrue(e.store.snapshot().messages.isEmpty())
    }

    @Test fun `arquivo v2 preserva conversas sem reaplicar financeiro e aceita v1`() = runBlocking<Unit> {
        val e = env()
        val a = e.store.createConversation("Casa")
        e.store.saveConversationDraft(a.id, "rascunho")
        e.fake.on("objeto 25 no cartão", reg("objeto", "25", category = categoryRef(e), payment = ChatPayment.Card))
        e.say("objeto 25 no cartão")
        val text = e.store.exportText("mesma-pessoa|espaco")
        e.coordinator.clearHistory()
        assertEquals(ChatImportResult.Imported(1), e.store.importText(text, "mesma-pessoa|espaco"))
        assertEquals("rascunho", e.store.conversation(a.id)!!.draftText)
        assertEquals(ChatImportResult.Imported(0), e.store.importText(text, "mesma-pessoa|espaco"))
        val old = """{"format":"accord-chat-history","version":1,"owner":"mesma-pessoa|espaco","messages":[],"attempts":[],"questions":[],"receipts":[]}"""
        assertEquals(ChatImportResult.Imported(0), e.store.importText(old, "mesma-pessoa|espaco"))
    }
}
