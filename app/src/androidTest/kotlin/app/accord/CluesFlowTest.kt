package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.analysis.ProductPhase
import app.accord.core.clues.*
import app.accord.core.space.*
import app.accord.persistence.*
import app.accord.ui.clues.*
import app.accord.ui.theme.AccordTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** Actual Room + ViewModel + Compose, isolated from the app's account and financial database. */
class CluesFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AccordDatabase
    private lateinit var space: RoomFinanceSpace
    private val day = LocalDate.of(2026,11,8)
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC))
    private val provider = object : SpaceProvider {
        override val clock get() = this@CluesFlowTest.clock
        override val phase = ProductPhase.P2
        override val personId = "ana"
        override val feedback = FeedbackBus()
        override fun memberNames() = mapOf("ana" to "Ana")
        override suspend fun space(): FinanceSpace = space
        override suspend fun imports() = space.importSession()
        override suspend fun undo(operationId: String): UndoOutcome = error("Not used")
    }
    private fun <T> QueryResult<T>.data() = (this as QueryResult.Data).value
    private fun envelope(id: String) = Envelope(CommandId(id))

    @Before fun prepare() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AccordDatabase>(InstrumentationRegistry.getInstrumentation().targetContext,
            factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        space = RoomFinanceSpace.open(db, clock, { Session("clue-flow", "ana") })
        repeat(2) { index ->
            assertTrue(space.execute(envelope("purchase-$index"), RegisterCardPurchase("Mercado",
                Classification.Category(DeterministicIds.seedCategory("clue-flow","alimentacao.mercado")),
                day.minusDays(1), Money(5000), null, 1, invoice = YearMonth.of(2026,12))) is CommandResult.Applied)
        }
    }

    @After fun close() { db.close() }

    @Test fun changedEvidenceRequiresAnExplicitNewAnalysis() {
        val clue = runBlocking { ClueDetector.analyze(space.clueSource(day.minusDays(56),day).data(),day).clues.single() }
        val saved = SavedStateHandle(mapOf("clueKey" to clue.key, "reference" to day.toEpochDay().toString()))
        lateinit var vm: CluesViewModel
        compose.setContent { AccordTheme {
            vm = remember { CluesViewModel(provider,saved) }
            ClueDetailRoute(vm, PaddingValues(), {}, {})
        } }
        compose.waitUntil(10_000) { vm.state.value.selected != null }
        runBlocking {
            clue.evidence.forEachIndexed { index,e ->
                assertTrue(space.execute(envelope("edit-$index"), CorrectEntry(e.entry.id, description = "Mercado novo")) is CommandResult.Applied)
            }
        }
        compose.runOnIdle { vm.refresh() }
        compose.waitUntil(10_000) { vm.state.value.changed }
        compose.onNodeWithTag("clue-detail-list").performScrollToNode(hasText("Dispensar pista: compras legítimas"))
        compose.onNodeWithText("Dispensar pista: compras legítimas").assertIsNotEnabled()
        compose.runOnIdle { vm.updateAnalysis() }
        compose.waitUntil(10_000) { !vm.state.value.changed && vm.state.value.seen?.evidence?.firstOrNull()?.description == "Mercado novo" }
        assertTrue(runBlocking { space.clueEvaluations().data().isEmpty() })
    }

    @Test fun dismissalPersistsAcrossRecreationAndLeavesMoneyAndVersionsUntouched() {
        val clue = runBlocking { ClueDetector.analyze(space.clueSource(day.minusDays(56),day).data(),day).clues.single() }
        val before = runBlocking { space.budget(YearMonth.of(2026,12)).data() }
        val revisions = runBlocking { clue.evidence.map { space.entry(it.entry.id).data()!!.revision } }
        val saved = SavedStateHandle(mapOf("clueKey" to clue.key, "reference" to day.toEpochDay().toString()))
        lateinit var vm: CluesViewModel
        compose.setContent { AccordTheme {
            vm = remember { CluesViewModel(provider,saved) }
            ClueDetailRoute(vm, PaddingValues(), {}, {})
        } }
        compose.waitUntil(10_000) { vm.state.value.selected != null }
        compose.onNodeWithTag("clue-detail-list").performScrollToNode(hasText("Dispensar pista: compras legítimas"))
        compose.onNodeWithText("Dispensar pista: compras legítimas").performClick()
        compose.waitUntil(10_000) { vm.state.value.selected?.evaluated == true }
        runBlocking {
            assertEquals(1, space.clueEvaluations().data().size)
            assertEquals(before, space.budget(YearMonth.of(2026,12)).data())
            assertEquals(revisions, clue.evidence.map { space.entry(it.entry.id).data()!!.revision })
        }
        lateinit var restored: CluesViewModel
        compose.runOnIdle {
            restored = CluesViewModel(provider, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        }
        compose.waitUntil(10_000) { restored.state.value.selected?.evaluated == true }
        assertEquals(1, runBlocking { space.clueEvaluations().data().size })
    }
}
