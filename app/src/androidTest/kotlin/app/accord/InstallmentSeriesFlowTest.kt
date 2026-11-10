package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.importing.BatchSource
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.InstallmentHint
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.DeterministicIds
import app.accord.core.space.QueryResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * accord-qid0 (ADR 0076), de ponta a ponta pela interface: um documento de cartão com as duas linhas de um refinanciamento (n/N na mesma data, como no
 * CSV) e um crédito sem a compra. A pessoa registra a compra parcelada UMA vez e as duas linhas saem da fila; no crédito, escolhe "Não achei a compra:
 * registrar e estornar" e a fatura recebe o estorno. A categoria padrão do lote vem da sessão (o seletor tem teste próprio).
 */
@RunWith(AndroidJUnit4::class)
class InstallmentSeriesFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val loan get() = "REFINANCIAMENTO $tag"
    private val credit get() = "CANCELAMENTO $tag"
    private val invoice = YearMonth.now().plusMonths(1)
    private var batchId = ""
    private var cardId = ""

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        cardId = (container.space().cards() as QueryResult.Data).value.first().id
        val day = LocalDate.now()
        fun line(ordinal: Int, cents: Long, name: String, hint: InstallmentHint?) =
            ExtractedMovement(ordinal, listOf("LINE" to "$name $cents"), day, cents, "BRL", name, hint, emptySet())
        val extraction = ExtractionResult(
            "sha-qid0-$tag", DeclaredMetadata(SourceFormat.Csv, null, "BRL", null, null, DocumentKind.Card, null, null, null),
            listOf(
                line(1, -97277, loan, InstallmentHint(2, 3)), line(2, 4000, credit, null),
                line(3, -97277, loan, InstallmentHint(3, 3)), line(4, -1000, "PADARIA $tag", null),
            ),
            0, emptyList(),
        )
        val imports = container.imports()
        val started = (imports.start(StartImport("qid0-$tag", "fatura-$tag.csv", 10, extraction, BatchSource(DocumentKind.Card, cardId, invoice))) as QueryResult.Data).value
        batchId = (started as StartOutcome.Started).info.id
        val groceries = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        imports.decide(batchId, ImportDecision.DefaultCategory(groceries), 1)
        val token = (imports.review(batchId) as QueryResult.Data).value!!.token
        check((imports.confirm(batchId, "qid0-confirm-$tag", token) as QueryResult.Data).value is ConfirmOutcome.Applied)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun openQueue() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revisar pendências")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revisar pendências").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(credit, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun umaDecisaoCadastraACompraParceladaEAsDuasLinhasSaemDaFila() {
        openQueue()
        compose.onAllNodes(hasText(loan, substring = true))[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cadastrar a compra parcelada")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cadastrar a compra parcelada").performScrollTo().performClick()
        // O total sugerido já vem preenchido (as parcelas que faltam pelo mesmo valor): é só confirmar.
        compose.onNodeWithText("Usar este total").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Faturas de", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aplicar decisão").performClick()
        // As duas linhas da compra saem da fila com uma só decisão.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(loan, substring = true)).fetchSemanticsNodes().isEmpty() }

        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val items = runBlocking { (container.imports().result(batchId) as QueryResult.Data).value!!.items }
        val first = items.first { it.ordinal == 1 }
        val second = items.first { it.ordinal == 3 }
        assertEquals(first.targetId, second.targetId)
        val purchase = runBlocking { (container.space().purchase(first.targetId!!) as QueryResult.Data).value!! }
        assertEquals(3 * 97277L, purchase.total.cents)
        assertEquals(3, purchase.installments.size)
    }

    @Test
    fun creditoSemCompraRegistraACompraEEstornaElaMesma() {
        openQueue()
        compose.onNodeWithText(credit, substring = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("No cartão, todo valor positivo é estorno")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("É estorno de uma compra: escolher a compra").performScrollTo().performClick()
        compose.onNodeWithText("Não achei a compra: registrar e estornar").assertIsDisplayed()
        compose.onNodeWithText("Não achei a compra: registrar e estornar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Registra uma compra do mesmo valor e data do crédito", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aplicar decisão").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(credit, substring = true)).fetchSemanticsNodes().isEmpty() }

        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        // O documento declara a fatura: a compra registrada e o estorno ficam nela, e o crédito é todo da fatura.
        val credits = runBlocking { (container.space().invoice(invoice, cardId) as QueryResult.Data).value.credits.cents }
        assertEquals(4000L, credits)
    }
}
