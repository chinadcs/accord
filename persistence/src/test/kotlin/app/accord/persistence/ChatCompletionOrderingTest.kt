package app.accord.persistence

import app.accord.core.chat.*
import kotlinx.coroutines.*
import kotlin.test.*

class ChatCompletionOrderingTest : ChatTestBase() {
    @Test fun `a classificar so fica pronto depois de persistir a pergunta`() = runBlocking<Unit> {
        val e = env()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = object : ChatStore by e.store {
            override suspend fun saveQuestion(question: ChatQuestionRecord) {
                entered.complete(Unit)
                release.await()
                e.store.saveQuestion(question)
            }
        }
        val coordinator = ChatCoordinator(e.space, store, InterpreterSource { ActiveInterpreter(e.fake) }, e.budgetClock, scope)
        try {
            e.fake.on("viagem 300 no cartão", reg("viagem", "300", category = "c999", payment = ChatPayment.Card))
            val id = coordinator.send("viagem 300 no cartão")!!
            withTimeout(8_000) { entered.await() }
            assertEquals(MessageState.Applying, e.store.message(id)!!.state)
            release.complete(Unit)
            waitFor("conclusão com pergunta persistida") { e.store.message(id)?.takeIf { it.state == MessageState.ToClassify } }
            assertEquals(1, e.store.openQuestions().count { it.messageId == id })
        } finally { release.complete(Unit); scope.cancel() }
    }
}
