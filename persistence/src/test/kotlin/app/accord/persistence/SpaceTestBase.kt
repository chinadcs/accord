package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.Envelope
import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import app.accord.core.space.Session
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.assertIs

/** Cenário comum dos testes da fachada: banco real em memória e relógio controlável (24/10/2026). */
abstract class SpaceTestBase {
    protected val spaceId = "space-1"
    protected val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneOffset.UTC))
    private val opened = mutableListOf<TestDatabase>()

    protected fun newDatabase() = TestDatabase.create().also { opened += it }

    protected fun open(person: String = "ana", database: TestDatabase = newDatabase(), now: Instant? = null) =
        Handle(database, person, now?.let { BudgetClock.of(Clock.fixed(it, ZoneOffset.UTC)) } ?: clock)

    protected inner class Handle(val database: TestDatabase, val person: String, private val handleClock: BudgetClock) {
        var fault: (() -> Unit)? = null
        val space: FinanceSpace = runBlocking {
            RoomFinanceSpace.open(
                database = database.db,
                clock = handleClock,
                sessions = { Session(spaceId, person) },
                beforeCommit = { fault?.invoke() },
            )
        }
        private var counter = 0
        fun nextId() = CommandId("cmd-$person-${counter++}")
        fun env(id: CommandId = nextId()) = Envelope(id)
    }

    @AfterTest
    fun closeAll() {
        opened.forEach { it.close() }
    }

    protected fun seed(key: String) = DeterministicIds.seedCategory(spaceId, key)
    protected fun brl(reais: Int) = Money(reais * 100L)
    protected fun ym(y: Int, m: Int) = YearMonth.of(y, m)
    protected fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    protected fun <T> QueryResult<T>.value(): T = assertIs<QueryResult.Data<T>>(this).value
    protected fun CommandResult.applied() = assertIs<CommandResult.Applied>(this).receipt
}
