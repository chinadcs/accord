package app.accord

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.UpcomingEvent
import app.accord.core.analysis.ProductPhase
import app.accord.core.space.*
import app.accord.persistence.*
import app.accord.ui.commitments.CommitmentsContent
import app.accord.ui.commitments.CommitmentsUpcomingState
import app.accord.ui.commitments.CommitmentsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * "Próximos 7 dias" de Compromissos (ADR 0078, accord-l6lu) é lido à parte do mês: parte de hoje, não muda ao navegar entre meses nem
 * com o cenário, mantém o pronto na releitura, e a falha de um lado não derruba o outro. Room real em memória.
 *
 * Hoje é 08/11/2026 (janela de 08/11 a 14/11). Cartão com fechamento 9 e vencimento 14; boleto Internet em 10/11; recorrência
 * Streaming a partir de 12/11; boleto Seguro em 20/11 (fora da janela).
 */
class CommitmentsUpcomingViewModelTest {
    private lateinit var db: AccordDatabase
    private lateinit var space: RoomFinanceSpace
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC))

    @Volatile private var failCards = false

    /** Falha só a leitura de compromissos que a tela do mês faz (a dos 7 dias vem de `loadUpcomingEvents`). */
    @Volatile private var failMonthOnly = false
    private val cardReads = AtomicInteger()

    private val provider = object : SpaceProvider {
        override val clock get() = this@CommitmentsUpcomingViewModelTest.clock
        override val phase = ProductPhase.P2
        override val personId = "ana"
        override val feedback = FeedbackBus()
        override suspend fun space(): FinanceSpace {
            val real = space
            return object : FinanceSpace by real {
                override suspend fun cards(): QueryResult<List<CardInfo>> {
                    cardReads.incrementAndGet()
                    if (failCards) return QueryResult.Failure(IllegalStateException("cartões indisponíveis"))
                    return real.cards()
                }

                override suspend fun commitments(month: YearMonth, kind: CommitmentKind): QueryResult<MonthCommitments> {
                    val upcoming = Throwable().stackTrace.any { it.methodName == "loadUpcomingEvents" }
                    if (failMonthOnly && !upcoming) return QueryResult.Failure(IllegalStateException("mês indisponível"))
                    return real.commitments(month, kind)
                }
            }
        }
        override suspend fun imports() = space.importSession()
        override suspend fun undo(operationId: String): UndoOutcome = error("Not used")
    }

    @Before fun prepare() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AccordDatabase>(InstrumentationRegistry.getInstrumentation().targetContext,
            factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        space = RoomFinanceSpace.open(db, clock, { Session("upcoming-vm", "ana") })
        val created = space.execute(Envelope(CommandId("up-cat")), CreateCategory("Casa 7 dias")) as CommandResult.Applied
        val category = created.receipt.changes.first { it.type == EntityType.Category }.id
        space.execute(Envelope(CommandId("up-card")), ConfigureCard(null, "Cartão 7 dias", 9, 14))
        space.execute(
            Envelope(CommandId("up-bill")),
            RegisterBoleto("Internet", Classification.Category(category), LocalDate.of(2026, 11, 10), Money(15_000)),
        )
        space.execute(
            Envelope(CommandId("up-far")),
            RegisterBoleto("Seguro", Classification.Category(category), LocalDate.of(2026, 11, 20), Money(30_000)),
        )
        space.execute(
            Envelope(CommandId("up-rec")),
            CreateRecurrence(
                kind = RecurrenceKind.Expense, description = "Streaming", categoryId = category, amount = Money(3_990),
                frequency = Frequency.Monthly, firstDate = LocalDate.of(2026, 11, 12),
            ),
        )
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

    private fun create(saved: SavedStateHandle = SavedStateHandle()): CommitmentsViewModel {
        lateinit var vm: CommitmentsViewModel
        onMain { vm = CommitmentsViewModel(provider, saved) }
        return vm
    }

    private fun CommitmentsViewModel.ready() = state.value.upcoming as CommitmentsUpcomingState.Ready

    @Test fun comecaEmCargaEFicaProntoComTodosOsEventosDaJanelaEmOrdemSemOsDeFora() {
        val vm = create()
        assertTrue(vm.state.value.upcoming is CommitmentsUpcomingState.Loading)
        waitFor("próximos 7 dias prontos") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        val ready = vm.ready()
        assertEquals(LocalDate.of(2026, 11, 8), ready.from)
        assertEquals(
            listOf("2026-11-09" to "closing", "2026-11-10" to "bill", "2026-11-12" to "recurrence", "2026-11-14" to "due"),
            ready.events.map {
                it.date.toString() to when (it) {
                    is UpcomingEvent.CardClosing -> "closing"
                    is UpcomingEvent.BillDue -> "bill"
                    is UpcomingEvent.RecurrenceDue -> "recurrence"
                    is UpcomingEvent.CardDue -> "due"
                }
            },
        )
        assertEquals("Internet", (ready.events[1] as UpcomingEvent.BillDue).commitment.description)
        assertEquals(15_000L, (ready.events[1] as UpcomingEvent.BillDue).commitment.amount.cents)
        waitFor("mês pronto") { vm.state.value.content is CommitmentsContent.Ready }
    }

    @Test fun mesDaRotaOuNavegarEntreMesesNaoMudaNemReleOsProximos7Dias() {
        val vm = create(SavedStateHandle(mapOf(CommitmentsViewModel.ARG_MONTH to "2027-03")))
        assertEquals(YearMonth.of(2027, 3), vm.state.value.month)
        waitFor("próximos 7 dias prontos") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        val first = vm.state.value.upcoming
        val reads = cardReads.get()
        onMain { vm.nextMonth() }
        onMain { vm.previousMonth() }
        onMain { vm.setKind(CommitmentKind.Bills) }
        waitFor("mês pronto") { vm.state.value.content is CommitmentsContent.Ready }
        assertSame("o resultado dos 7 dias não foi regravado", first, vm.state.value.upcoming)
        assertEquals("trocar mês ou filtro não lê os 7 dias de novo", reads, cardReads.get())
        assertEquals(LocalDate.of(2026, 11, 8), vm.ready().from)
    }

    @Test fun releituraMantemOProntoSemRegravarCarga() {
        val vm = create()
        waitFor("pronto") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        val first = vm.state.value.upcoming
        val reads = cardReads.get()
        onMain { vm.refresh() }
        assertTrue(vm.state.value.upcoming is CommitmentsUpcomingState.Ready)
        repeat(20) { Thread.sleep(10); assertTrue(vm.state.value.upcoming is CommitmentsUpcomingState.Ready) }
        waitFor("releitura feita") { cardReads.get() > reads }
        assertEquals(first, vm.state.value.upcoming)
    }

    @Test fun falhaSoDosProximos7DiasNaoDerrubaOMesETentarDeNovoRecuperaSemRecarregarOMes() {
        failCards = true
        val vm = create()
        waitFor("7 dias falhos") { vm.state.value.upcoming is CommitmentsUpcomingState.Failed }
        waitFor("mês pronto") { vm.state.value.content is CommitmentsContent.Ready }
        val content = vm.state.value.content
        failCards = false
        onMain { vm.retryUpcoming() }
        // Volta pela carga, não por valor antigo, e termina pronto.
        assertTrue(vm.state.value.upcoming is CommitmentsUpcomingState.Loading)
        waitFor("recuperado") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        assertEquals(4, vm.ready().events.size)
        assertSame("o mês não foi recarregado nem regravado", content, vm.state.value.content)
    }

    @Test fun falhaDoMesNaoDerrubaOsProximos7Dias() {
        failMonthOnly = true
        val vm = create()
        waitFor("mês falho") { vm.state.value.content is CommitmentsContent.Failed }
        waitFor("7 dias prontos") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        assertEquals(4, vm.ready().events.size)
        failMonthOnly = false
        onMain { vm.retry() }
        waitFor("mês recuperado") { vm.state.value.content is CommitmentsContent.Ready }
        assertTrue(vm.state.value.upcoming is CommitmentsUpcomingState.Ready)
    }

    @Test fun releituraComFalhaPassaDoProntoParaFalhaSemNuncaApresentarVazio() {
        val vm = create()
        waitFor("pronto") { vm.state.value.upcoming is CommitmentsUpcomingState.Ready }
        failCards = true
        onMain { vm.refresh() }
        waitFor("falho") { vm.state.value.upcoming is CommitmentsUpcomingState.Failed }
        // Falha nunca vira "nada previsto": o estado não é Ready de lista vazia.
        assertFalse(vm.state.value.upcoming is CommitmentsUpcomingState.Ready)
    }
}
