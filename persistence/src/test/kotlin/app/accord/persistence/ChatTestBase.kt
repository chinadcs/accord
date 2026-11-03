package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.chat.ActiveInterpreter
import app.accord.core.chat.ChatCoordinator
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.DateExpression
import app.accord.core.chat.FakeInterpreter
import app.accord.core.chat.InterpreterSource
import app.accord.core.chat.MessageState
import app.accord.core.chat.Proposal
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.fail

/**
 * Cenário comum dos testes do Chat: fachada real em memória, histórico em Room, interpretador fake e
 * relógio de 22/10/2026 em America/Sao_Paulo, salvo indicação. O motor nunca entra nos testes.
 */
abstract class ChatTestBase : SpaceTestBase() {
    private val scopes = mutableListOf<CoroutineScope>()

    protected val oct22: Instant = Instant.parse("2026-10-22T15:00:00Z")

    protected inner class Env(
        val handle: Handle,
        val database: TestDatabase,
        val fake: FakeInterpreter,
        instant: Instant,
        val suggester: app.accord.core.chat.CategorySuggester? = null,
    ) {
        val store = RoomChatStore(database.db)
        val budgetClock = BudgetClock.of(Clock.fixed(instant, ZoneOffset.UTC))
        var modelInstalled = true
        var interpretation: app.accord.core.chat.Interpreter = fake
        var memberItems: List<app.accord.core.chat.MemberItem> = emptyList()
        var financeChanges = 0
        val pilotEvents = kotlinx.coroutines.channels.Channel<app.accord.core.chat.CategoryPilotEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        private val ownScopes = mutableListOf<CoroutineScope>()
        fun stop() { ownScopes.forEach { it.cancel() } }
        val space get() = handle.space
        private var counter = 0

        fun newCoordinator(eventSink: ((app.accord.core.chat.CategoryPilotEvent) -> Unit)? = null): ChatCoordinator {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it; ownScopes += it }
            return ChatCoordinator(
                space = handle.space,
                store = store,
                interpreters = InterpreterSource { if (modelInstalled) ActiveInterpreter(interpretation, "fake-1") else null },
                clock = budgetClock,
                scope = scope,
                newId = { "id-${handle.person}-${database.hashCode()}-${counter++}" },
                onFinanceChanged = { financeChanges++ },
                members = { memberItems },
                categorySuggester = suggester,
                onCategoryPilotEvent = eventSink ?: { pilotEvents.trySend(it); Unit },
            )
        }

        val coordinator = newCoordinator()

        suspend fun categoryRef(seedKey: String): String {
            val ids = handle.space.categories().value()
            return "c${ids.indexOfFirst { it.id == seed(seedKey) } + 1}"
        }

        suspend fun message(id: String) = checkNotNull(store.message(id))

        /** Envia e espera a mensagem sair do processamento (aplicada, pergunta, resposta, recusa, falha...). */
        suspend fun say(text: String, vararg settled: MessageState): ChatMessage {
            val id = checkNotNull(coordinator.send(text))
            return wait(id, *settled)
        }

        suspend fun wait(id: String, vararg settled: MessageState): ChatMessage {
            val until = if (settled.isEmpty()) TERMINAL else settled.toSet()
            return waitFor("mensagem $id em $until") { message(id).takeIf { it.state in until } }
        }
    }

    protected fun env(instant: Instant = oct22, person: String = "ana", withAccounts: Boolean = true, defaultAccount: Boolean = true, suggester: app.accord.core.chat.CategorySuggester? = null): Env {
        val database = newDatabase()
        val handle = open(person = person, database = database, now = instant)
        val env = Env(handle, database, FakeInterpreter(), instant, suggester)
        if (withAccounts) runBlocking {
            val a = handle.space.execute(handle.env(), AddAccount(BankRef.Other("Nubank"), "Conta")).applied().changes.single().id
            handle.space.execute(handle.env(), AddAccount(BankRef.Other("Itaú"), "Salário")).applied()
            if (defaultAccount) handle.space.execute(handle.env(), SetMyDefaultAccount(a)).applied()
        }
        return env
    }

    protected fun reg(
        description: String? = null,
        amount: String? = null,
        role: app.accord.core.chat.AmountRole? = null,
        installments: String? = null,
        date: DateExpression? = null,
        category: String? = null,
        payment: ChatPayment? = null,
        account: String? = null,
        nature: app.accord.core.chat.Nature? = null,
    ) = Proposal.Register(nature, description, amount, role, installments, date, category, payment, account)

    protected suspend fun <T : Any> waitFor(what: String, timeoutMillis: Long = 8_000, probe: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            probe()?.let { return it }
            delay(15)
        }
        fail("Esperava $what")
    }

    @AfterTest
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
    }

    protected companion object {
        val TERMINAL = setOf(
            MessageState.Applied, MessageState.ToClassify, MessageState.Asked, MessageState.Refused, MessageState.Failed,
            MessageState.Canceled, MessageState.Interrupted, MessageState.NoModel, MessageState.ResolvedManually, MessageState.Replied,
        )
    }
}
