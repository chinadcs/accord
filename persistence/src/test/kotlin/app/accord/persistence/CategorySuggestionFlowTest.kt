package app.accord.persistence

import app.accord.core.chat.*
import app.accord.core.space.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class CategorySuggestionFlowTest : ChatTestBase() {
    private suspend fun Env.event(kind: CategoryPilotEventKind) = withTimeout(5_000) {
        while (pilotEvents.receive().kind != kind) { }
    }
    private class Delayed : CategorySuggester {
        val started = CompletableDeferred<CategorySuggestionRequest>()
        val result = CompletableDeferred<CategorySuggestion>()
        val finished = CompletableDeferred<Unit>()
        override suspend fun suggest(request: CategorySuggestionRequest): CategorySuggestion {
            started.complete(request)
            return try { result.await() } finally { finished.complete(Unit) }
        }
        fun finish(vararg ids: String) = result.complete(CategorySuggestion(ids.toList(), CategorySuggestionReason.Suggested))
    }

    private suspend fun Env.register(): String {
        fake.on("ração 80 no cartão", reg("ração", "80", payment = ChatPayment.Card))
        say("ração 80 no cartão")
        return store.snapshot().receipts.single().entryId
    }

    @Test fun `receipt and manual choice available while suggestions wait then only tap classifies same entry`() = runBlocking<Unit> {
        val delayed = Delayed()
        val e = env(suggester = delayed)
        val id = e.register()
        delayed.started.await()
        assertEquals(Classification.ToClassify, e.space.entry(id).value()!!.classification)
        assertTrue(e.store.openQuestions().single().options.isNotEmpty())
        val financialWrites = e.financeChanges
        delayed.finish(seed("pet.alimentacao"), seed("pet"), seed("alimentacao"))
        waitFor("hints") { e.store.snapshot().messages.single().takeIf { it.categoryHints.size == 3 } }
        assertEquals(financialWrites, e.financeChanges)
        assertEquals(Classification.ToClassify, e.space.entry(id).value()!!.classification)
        val q = e.store.openQuestions().single()
        assertEquals("Pet > Alimentação", q.options.first().label)
        e.coordinator.answer(q.id, q.options.first())
        assertEquals(Classification.Category(seed("pet.alimentacao")), e.space.entry(id).value()!!.classification)
        assertEquals(1, e.store.snapshot().receipts.map { it.entryId }.distinct().size)
    }

    @Test fun `manual answer before inference cannot be overwritten or reopened`() = runBlocking<Unit> {
        val delayed = Delayed()
        val e = env(suggester = delayed)
        val id = e.register()
        delayed.started.await()
        val q = e.store.openQuestions().single()
        e.coordinator.answer(q.id, q.options.first { it.label == "Casa" })
        delayed.finish(seed("pet"))
        e.event(CategoryPilotEventKind.Stale)
        assertEquals(Classification.Category(seed("casa")), e.space.entry(id).value()!!.classification)
        assertTrue(e.store.openQuestions().isEmpty())
        assertTrue(e.store.snapshot().messages.single().categoryHints.isEmpty())
    }

    @Test fun `edited entry discards stale suggestion`() = runBlocking<Unit> {
        val delayed = Delayed()
        val e = env(suggester = delayed)
        val id = e.register()
        delayed.started.await()
        e.space.execute(e.handle.env(), CorrectEntry(id, description = "outra compra")).applied()
        delayed.finish(seed("pet"))
        e.event(CategoryPilotEventKind.Stale)
        assertTrue(e.store.snapshot().messages.single().categoryHints.isEmpty())
    }

    @Test fun `unknown category and adapter failure preserve manual question`() = runBlocking<Unit> {
        for (fail in listOf(false, true)) {
            val e = env(suggester = CategorySuggester {
                if (fail) error("private input must never reach diagnostics")
                CategorySuggestion(listOf("unknown"), CategorySuggestionReason.Suggested)
            })
            val id = e.register()
            e.event(CategoryPilotEventKind.Evaluated)
            assertTrue(e.store.snapshot().messages.single().categoryHints.isEmpty())
            assertEquals(Classification.ToClassify, e.space.entry(id).value()!!.classification)
            assertTrue(e.store.openQuestions().single().options.isNotEmpty())
        }
    }

    @Test fun `explicit category never calls suggester`() = runBlocking<Unit> {
        var calls = 0
        val e = env(suggester = CategorySuggester { calls++; CategorySuggestion() })
        e.fake.on("ração 80 no cartão", reg("ração", "80", category = e.categoryRef("pet"), payment = ChatPayment.Card))
        val message = e.say("ração 80 no cartão")
        assertEquals(MessageState.Applied, message.state)
        assertTrue(message.categoryHints.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun `catalog change and undo discard pending suggestions`() = runBlocking<Unit> {
        for (undo in listOf(false, true)) {
            val delayed = Delayed()
            val e = env(suggester = delayed)
            e.register()
            delayed.started.await()
            if (undo) e.coordinator.undo(e.store.snapshot().receipts.single().id)
            else e.space.execute(e.handle.env(), RenameCategory(seed("pet"), "Bichinhos")).applied()
            delayed.finish(seed("pet"))
            e.event(CategoryPilotEventKind.Stale)
            assertTrue(e.store.snapshot().messages.single().categoryHints.isEmpty())
        }
    }

    @Test fun `leaving runtime cancels inference and cannot publish into next session`() = runBlocking<Unit> {
        val delayed = Delayed()
        val e = env(suggester = delayed)
        e.register()
        delayed.started.await()
        e.stop()
        delayed.finish(seed("pet"))
        delayed.finished.await()
        assertTrue(e.store.snapshot().messages.single().categoryHints.isEmpty())
        val next = env()
        assertTrue(next.store.snapshot().messages.isEmpty())
    }

    @Test fun `diagnostic failure cannot block suggestions or financial confirmation`() = runBlocking<Unit> {
        val e = env(suggester = CategorySuggester { CategorySuggestion(listOf(seed("pet")), CategorySuggestionReason.Suggested) })
        val coordinator = e.newCoordinator(eventSink = { error("diagnostics unavailable") })
        e.fake.on("ração 80 no cartão", reg("ração", "80", payment = ChatPayment.Card))
        val messageId = coordinator.send("ração 80 no cartão")!!
        e.wait(messageId)
        waitFor("suggestion despite diagnostic failure") { e.message(messageId).takeIf { it.categoryHints.isNotEmpty() } }
        val q = e.store.openQuestions().single()
        val entryId = e.store.snapshot().receipts.single().entryId
        coordinator.answer(q.id, q.options.first())
        assertEquals(Classification.Category(seed("pet")), e.space.entry(entryId).value()!!.classification)
        assertEquals(MessageState.Applied, e.message(messageId).state)
    }
}
