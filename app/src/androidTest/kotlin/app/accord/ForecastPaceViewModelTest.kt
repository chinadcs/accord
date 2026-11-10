package app.accord

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.analysis.ProductPhase
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Channel
import app.accord.core.forecast.ChannelExplanation
import app.accord.core.forecast.ChannelState
import app.accord.core.forecast.ClosureSource
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.InvoiceClosure
import app.accord.core.forecast.MonthForecast
import app.accord.core.forecast.Period
import app.accord.core.forecast.ToClassifySummary
import app.accord.core.space.*
import app.accord.persistence.*
import app.accord.ui.forecast.ForecastPaceState
import app.accord.ui.forecast.ForecastsContent
import app.accord.ui.forecast.ForecastsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * O "Ritmo atual" de Previsões (ADR 0078, accord-6nmc) é lido à parte do resto da tela: mantém o pronto na releitura, uma falha
 * dele não derruba a previsão (nem o inverso apresenta valor antigo) e só existe para o mês em andamento. Room real em memória;
 * a previsão é sintética (com data de estouro conhecida) para não depender de meses de histórico.
 *
 * Hoje é 08/11/2026. Quatro categorias com possível excesso, de datas projetadas 08/11, 09/11, 10/11 e 13/11 (a quinta já estourou
 * e a sexta não tem limite: ficam de fora).
 */
class ForecastPaceViewModelTest {
    private lateinit var db: AccordDatabase
    private lateinit var space: RoomFinanceSpace
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC))
    private val nov = YearMonth.of(2026, 11)
    private val ids = mutableMapOf<String, String>()

    @Volatile private var failForecasts = false

    /** Falha só a leitura do ritmo (a previsão da tela chama `forecasts` de outro lugar). */
    @Volatile private var failPaceOnly = false
    private val paceReads = AtomicInteger()
    private val mainReads = AtomicInteger()

    private val provider = object : SpaceProvider {
        override val clock get() = this@ForecastPaceViewModelTest.clock
        override val phase = ProductPhase.P2
        override val personId = "ana"
        override val feedback = FeedbackBus()
        override suspend fun space(): FinanceSpace {
            val real = space
            return object : FinanceSpace by real {
                override suspend fun forecasts(month: YearMonth): QueryResult<MonthForecast> {
                    val pace = Throwable().stackTrace.any { it.methodName == "loadProjectionRows" }
                    (if (pace) paceReads else mainReads).incrementAndGet()
                    if (failForecasts || (pace && failPaceOnly)) return QueryResult.Failure(IllegalStateException("previsão indisponível"))
                    return QueryResult.Data(synthetic(month))
                }
            }
        }
        override suspend fun imports() = space.importSession()
        override suspend fun undo(operationId: String): UndoOutcome = error("Not used")
    }

    @Before fun prepare() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AccordDatabase>(InstrumentationRegistry.getInstrumentation().targetContext,
            factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        space = RoomFinanceSpace.open(db, clock, { Session("pace-vm", "ana") })
        for (name in listOf("Ritmo Mercado", "Ritmo Lazer", "Ritmo Transporte", "Ritmo Casa", "Ritmo Ocorrida", "Ritmo SemLimite")) {
            val applied = space.execute(Envelope(CommandId("pace-$name")), CreateCategory(name)) as CommandResult.Applied
            ids[name] = applied.receipt.changes.first { it.type == EntityType.Category }.id
        }
    }

    @After fun close() { db.close() }

    private fun channel(estimate: Long, remaining: Int) = ChannelExplanation(
        Channel.Other, Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), ChannelState.Estimated, remaining, 3, emptyList(), estimate,
    )

    private fun possible(name: String, limit: Long, known: Long, estimate: Long, remaining: Int, low: Boolean = false) = CategoryForecast(
        ids.getValue(name), limit, known, Estimate.Value(estimate), known + estimate, Excess.Possible(known + estimate - limit),
        if (low) Confidence.Low(listOf(ConfidenceReason.InsufficientHistory)) else Confidence.NoKnownCaveats, listOf(channel(estimate, remaining)),
    )

    private fun synthetic(month: YearMonth) = MonthForecast(
        month, "forecast-v1", InvoiceClosure(false, ClosureSource.Configuration),
        listOf(
            possible("Ritmo Casa", 100_000, 60_000, 80_000, 10), // estoura no dia 6: 13/11
            possible("Ritmo Lazer", 50_000, 49_000, 20_000, 20, low = true), // dia 2: 09/11
            possible("Ritmo Mercado", 100_000, 99_500, 5_000, 5), // dia 1: hoje
            possible("Ritmo Transporte", 10_000, 5_000, 30_000, 15), // dia 3: 10/11
            CategoryForecast(ids.getValue("Ritmo Ocorrida"), 10_000, 12_000, Estimate.Value(0), 12_000, Excess.Occurred(2_000), Confidence.NoKnownCaveats, emptyList()),
            CategoryForecast(ids.getValue("Ritmo SemLimite"), null, 5_000, Estimate.Value(1_000), 6_000, Excess.NoLimit, Confidence.NoKnownCaveats, emptyList()),
        ),
        ToClassifySummary(0, 0), calculatedAtMillis = 0,
    )

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail(message)
            Thread.sleep(20)
        }
    }

    private fun create(saved: SavedStateHandle = SavedStateHandle()): ForecastsViewModel {
        lateinit var vm: ForecastsViewModel
        onMain { vm = ForecastsViewModel(provider, saved) }
        return vm
    }

    @Test fun ritmoComecaEmCargaEFicaProntoComTodasAsCategoriasDaMaisProximaAMaisDistante() {
        val vm = create()
        assertTrue(vm.state.value.pace is ForecastPaceState.Loading)
        waitFor("ritmo pronto") { vm.state.value.pace is ForecastPaceState.Ready }
        val rows = (vm.state.value.pace as ForecastPaceState.Ready).rows
        // Sem o corte de três da Início; a já ocorrida e a sem limite não entram.
        assertEquals(listOf("Ritmo Mercado", "Ritmo Lazer", "Ritmo Transporte", "Ritmo Casa"), rows.map { it.categoryName })
        assertEquals(listOf(0, 1, 2, 5), rows.map { it.projection.daysFromToday })
        assertEquals(
            listOf("2026-11-08", "2026-11-09", "2026-11-10", "2026-11-13"), rows.map { it.projection.date.toString() },
        )
        assertEquals(listOf(false, true, false, false), rows.map { it.projection.lowConfidence })
        // Ícone e cor vêm da categoria.
        assertTrue(rows.all { it.iconKey != null })
        waitFor("tela pronta") { vm.state.value.content is ForecastsContent.Ready }
    }

    @Test fun releituraMantemORitmoProntoSemRegravarCarga() {
        val vm = create()
        waitFor("ritmo pronto") { vm.state.value.pace is ForecastPaceState.Ready }
        val first = vm.state.value.pace
        onMain { vm.refresh() }
        assertTrue(vm.state.value.pace is ForecastPaceState.Ready)
        repeat(20) { Thread.sleep(10); assertTrue(vm.state.value.pace is ForecastPaceState.Ready) }
        assertEquals(first, vm.state.value.pace)
    }

    @Test fun falhaSoDoRitmoNaoDerrubaAPrevisaoETentarDeNovoRecuperaSemRecarregarATela() {
        failPaceOnly = true
        val vm = create()
        waitFor("ritmo falho") { vm.state.value.pace is ForecastPaceState.Failed }
        waitFor("previsão pronta") { vm.state.value.content is ForecastsContent.Ready }
        val content = vm.state.value.content
        failPaceOnly = false
        val mainBefore = mainReads.get()
        onMain { vm.retryPace() }
        // Volta pela carga, não por valor antigo, e termina pronto.
        waitFor("ritmo recuperado") { vm.state.value.pace is ForecastPaceState.Ready }
        assertSame("a previsão não foi recarregada nem regravada", content, vm.state.value.content)
        assertEquals(mainBefore, mainReads.get())
    }

    @Test fun releituraComFalhaPassaDoProntoParaFalhaESemNuncaApresentarVazio() {
        val vm = create()
        waitFor("ritmo pronto") { vm.state.value.pace is ForecastPaceState.Ready }
        failForecasts = true
        onMain { vm.refresh() }
        waitFor("ritmo falho") { vm.state.value.pace is ForecastPaceState.Failed }
        // Falha nunca vira "nenhuma categoria estoura": o estado não é Ready de lista vazia.
        assertFalse(vm.state.value.pace is ForecastPaceState.Ready)
        waitFor("previsão falha com a última visão") { vm.state.value.content is ForecastsContent.Failed }
        failForecasts = false
        onMain { vm.retry() }
        waitFor("tudo pronto") { vm.state.value.pace is ForecastPaceState.Ready && vm.state.value.content is ForecastsContent.Ready }
    }

    @Test fun outroMesNaoLeOsDadosDoRitmoEVoltarAoMesAtualRelePelaCarga() {
        val vm = create()
        waitFor("ritmo pronto") { vm.state.value.pace is ForecastPaceState.Ready }
        val reads = paceReads.get()
        onMain { vm.nextMonth() }
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
        assertTrue(vm.state.value.pace is ForecastPaceState.OtherMonth)
        waitFor("previsão de dezembro") { vm.state.value.content is ForecastsContent.Ready }
        assertEquals("o ritmo de outro mês não faz leitura", reads, paceReads.get())
        onMain { vm.previousMonth() }
        // Voltar ao mês atual não reaproveita o resultado antigo: passa pela carga.
        assertTrue(vm.state.value.pace is ForecastPaceState.Loading)
        waitFor("ritmo de novo") { vm.state.value.pace is ForecastPaceState.Ready }
    }

    @Test fun mesDaRotaOuSalvoQueNaoEOAtualComecaSemProjecao() {
        val vm = create(SavedStateHandle(mapOf(ForecastsViewModel.ARG_MONTH to "2026-10")))
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertTrue(vm.state.value.pace is ForecastPaceState.OtherMonth)
        waitFor("previsão de outubro") { vm.state.value.content is ForecastsContent.Ready }
        assertEquals(0, paceReads.get())
    }
}
