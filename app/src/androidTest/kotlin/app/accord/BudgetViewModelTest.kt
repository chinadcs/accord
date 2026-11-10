package app.accord

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.analysis.ProductPhase
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.*
import app.accord.persistence.*
import app.accord.ui.budget.BudgetContent
import app.accord.ui.budget.BudgetFlowState
import app.accord.ui.budget.BudgetViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

/**
 * O fluxo do mês do Orçamento (ADR 0078, accord-0xi8) é lido à parte do orçamento: acompanha o mês escolhido, mantém o
 * pronto na releitura e uma falha de um lado não derruba o outro. Room real em memória.
 */
class BudgetViewModelTest {
    private lateinit var db: AccordDatabase
    private lateinit var space: RoomFinanceSpace
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC))
    @Volatile private var throwForecasts = false
    @Volatile private var failForecasts = false
    @Volatile private var failBudget = false
    private val provider = object : SpaceProvider {
        override val clock get() = this@BudgetViewModelTest.clock
        override val phase = ProductPhase.P2
        override val personId = "ana"
        override val feedback = FeedbackBus()
        override suspend fun space(): FinanceSpace {
            val real = space
            return object : FinanceSpace by real {
                override suspend fun forecasts(month: YearMonth): QueryResult<MonthForecast> =
                    if (throwForecasts) error("leitura local indisponível")
                    else if (failForecasts) QueryResult.Failure(IllegalStateException("previsão indisponível")) else real.forecasts(month)

                override suspend fun budget(month: YearMonth): QueryResult<MonthBudget> =
                    if (failBudget) QueryResult.Failure(IllegalStateException("orçamento indisponível")) else real.budget(month)
            }
        }
        override suspend fun imports() = space.importSession()
        override suspend fun undo(operationId: String): UndoOutcome = error("Not used")
    }

    @Before fun prepare() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AccordDatabase>(InstrumentationRegistry.getInstrumentation().targetContext,
            factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        space = RoomFinanceSpace.open(db, clock, { Session("budget-vm", "ana") })
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

    private fun create(saved: SavedStateHandle = SavedStateHandle()): BudgetViewModel {
        lateinit var vm: BudgetViewModel
        onMain { vm = BudgetViewModel(provider, saved) }
        return vm
    }

    @Test fun fluxoComecaEmCargaFicaProntoEAcompanhaOMesEscolhido() {
        val vm = create()
        assertTrue(vm.state.value.flow is BudgetFlowState.Loading)
        waitFor("fluxo pronto") { vm.state.value.flow is BudgetFlowState.Ready }
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        waitFor("orçamento pronto") { vm.state.value.content is BudgetContent.Ready }

        onMain { vm.nextMonth() }
        // Troca de mês: nunca fica o fluxo do mês anterior sob o título do novo.
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
        assertTrue(vm.state.value.flow is BudgetFlowState.Loading)
        waitFor("fluxo do novo mês") { vm.state.value.flow is BudgetFlowState.Ready }
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
    }

    @Test fun releituraMantemOFluxoProntoSemRegravarCarga() {
        val vm = create()
        waitFor("fluxo pronto") { vm.state.value.flow is BudgetFlowState.Ready }
        waitFor("orçamento pronto") { vm.state.value.content is BudgetContent.Ready }
        val first = vm.state.value.flow
        onMain { vm.refresh() }
        assertTrue(vm.state.value.flow is BudgetFlowState.Ready)
        repeat(20) { Thread.sleep(10); assertTrue(vm.state.value.flow is BudgetFlowState.Ready) }
        assertEquals(first, vm.state.value.flow)
        assertTrue(vm.state.value.content is BudgetContent.Ready)
    }

    @Test fun falhaSoDoFluxoNaoDerrubaOOrcamentoENemApresentaValorAntigo() {
        failForecasts = true
        val vm = create()
        waitFor("fluxo falho") { vm.state.value.flow is BudgetFlowState.Failed }
        waitFor("orçamento pronto") { vm.state.value.content is BudgetContent.Ready }

        // Tentar de novo só do fluxo: recupera sem passar o resto da tela por carga.
        failForecasts = false
        onMain { vm.retryFlow() }
        assertTrue(vm.state.value.content is BudgetContent.Ready)
        waitFor("fluxo recuperado") { vm.state.value.flow is BudgetFlowState.Ready }

        // Depois de pronto, uma nova falha troca por falha, sem manter o valor antigo como atual.
        failForecasts = true
        onMain { vm.refresh() }
        waitFor("fluxo falho de novo") { vm.state.value.flow is BudgetFlowState.Failed }
        assertTrue(vm.state.value.content is BudgetContent.Ready)
    }

    @Test fun falhaSoDoOrcamentoNaoDerrubaOFluxo() {
        failBudget = true
        val vm = create()
        waitFor("orçamento falho") { vm.state.value.content is BudgetContent.Failed }
        // O fluxo lê o orçamento do mês por conta própria; sem ele não há resultado, e isso é falha, nunca zero.
        waitFor("fluxo falho") { vm.state.value.flow is BudgetFlowState.Failed }
        failBudget = false
        onMain { vm.retry() }
        waitFor("tudo pronto") { vm.state.value.content is BudgetContent.Ready && vm.state.value.flow is BudgetFlowState.Ready }
    }

    @Test fun excecaoNaLeituraViraFalhaDoFluxo() {
        throwForecasts = true
        val vm = create()
        waitFor("fluxo falho") { vm.state.value.flow is BudgetFlowState.Failed }
        throwForecasts = false
        onMain { vm.retryFlow() }
        waitFor("fluxo pronto") { vm.state.value.flow is BudgetFlowState.Ready }
    }
}
