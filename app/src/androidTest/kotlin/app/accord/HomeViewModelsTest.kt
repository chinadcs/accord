package app.accord

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.analysis.ProductPhase
import app.accord.core.space.*
import app.accord.persistence.*
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeAllowanceViewModel
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeBalanceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

/**
 * Os painéis da Início mantêm o último resultado pronto durante a releitura local e só vão a falha quando a leitura falha
 * (ADR 0078, `accord-vm79`). Room real em memória, isolado da conta e do banco do aplicativo.
 */
class HomeViewModelsTest {
    private lateinit var db: AccordDatabase
    private lateinit var space: RoomFinanceSpace
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC))
    @Volatile private var failing = false
    private val provider = object : SpaceProvider {
        override val clock get() = this@HomeViewModelsTest.clock
        override val phase = ProductPhase.P2
        override val personId = "ana"
        override val feedback = FeedbackBus()
        override suspend fun space(): FinanceSpace {
            if (failing) error("leitura local indisponível")
            return space
        }
        override suspend fun imports() = space.importSession()
        override suspend fun undo(operationId: String): UndoOutcome = error("Not used")
    }

    @Before fun prepare() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AccordDatabase>(InstrumentationRegistry.getInstrumentation().targetContext,
            factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        space = RoomFinanceSpace.open(db, clock, { Session("home-vm", "ana") })
    }

    @After fun close() { db.close() }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail(message)
            Thread.sleep(20)
        }
    }

    @Test fun margemMantemOProntoNaReleituraEVaiAFalhaSoQuandoALeituraFalha() {
        lateinit var vm: HomeAllowanceViewModel
        onMain { vm = HomeAllowanceViewModel(provider) }
        assertTrue(vm.state.value is HomeAllowanceState.Loading)
        onMain { vm.refresh() }
        waitFor("primeira leitura") { vm.state.value is HomeAllowanceState.Ready }
        val first = vm.state.value
        onMain { vm.refresh() }
        // Logo após pedir a releitura o painel continua pronto: não volta ao esqueleto.
        assertTrue(vm.state.value is HomeAllowanceState.Ready)
        repeat(20) { Thread.sleep(10); assertTrue(vm.state.value is HomeAllowanceState.Ready) }
        assertEquals(first, vm.state.value)

        failing = true
        onMain { vm.refresh() }
        waitFor("falha") { vm.state.value is HomeAllowanceState.Failed }

        // Recuperada a leitura, o painel volta a ficar pronto com o resultado novo.
        failing = false
        onMain { vm.refresh() }
        waitFor("nova leitura") { vm.state.value is HomeAllowanceState.Ready }
    }

    @Test fun saldoMantemOProntoEFalhaNuncaApresentaValorAntigoComoAtual() {
        lateinit var vm: HomeBalanceViewModel
        onMain { vm = HomeBalanceViewModel(provider) { DateCriterion.BudgetMonth } }
        onMain { vm.refresh() }
        waitFor("primeira leitura") { vm.state.value is HomeBalanceState.Ready }
        onMain { vm.refresh() }
        assertTrue(vm.state.value is HomeBalanceState.Ready)
        failing = true
        onMain { vm.refresh() }
        waitFor("falha") { vm.state.value is HomeBalanceState.Failed }
        assertFalse(vm.state.value is HomeBalanceState.Ready)
    }
}
