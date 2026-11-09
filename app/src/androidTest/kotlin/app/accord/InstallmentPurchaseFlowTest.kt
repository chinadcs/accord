package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
 * Parcela n/N sem compra registrada (accord-z724), de ponta a ponta: a dúvida guardada de um documento de cartão oferece "Cadastrar a compra parcelada",
 * a pessoa confirma o total, aplica, e a compra nasce com todas as parcelas, as anteriores nos meses passados. A categoria padrão do lote é escolhida
 * pela sessão (o seletor tem teste próprio); o resto passa pela interface.
 */
@RunWith(AndroidJUnit4::class)
class InstallmentPurchaseFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val sofa get() = "SOFA $tag"
    private val invoice = YearMonth.now().plusMonths(1)
    private var batchId = ""

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val card = (container.space().cards() as QueryResult.Data).value.first().id
        val day = LocalDate.now()
        fun line(ordinal: Int, cents: Long, name: String, hint: InstallmentHint?) =
            ExtractedMovement(ordinal, listOf("LINE" to "$name $cents"), day, cents, "BRL", name, hint, emptySet())
        val extraction = ExtractionResult(
            "sha-$tag", DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, DocumentKind.Card, null, null, null),
            listOf(line(1, -20000, sofa, InstallmentHint(3, 4)), line(2, -1000, "PADARIA $tag", null)), 0, emptyList(),
        )
        val imports = container.imports()
        val started = (imports.start(StartImport("inst-$tag", "fatura-$tag.pdf", 10, extraction, BatchSource(DocumentKind.Card, card, invoice))) as QueryResult.Data).value
        batchId = (started as StartOutcome.Started).info.id
        val groceries = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        imports.decide(batchId, ImportDecision.DefaultCategory(groceries), 1)
        val token = (imports.review(batchId) as QueryResult.Data).value!!.token
        check((imports.confirm(batchId, "inst-confirm-$tag", token) as QueryResult.Data).value is ConfirmOutcome.Applied)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun cadastrarACompraParceladaCriaTodasAsParcelasEFechaADuvida() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revisar pendências")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revisar pendências").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(sofa, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(sofa, substring = true).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cadastrar a compra parcelada")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parcela 3/4 de uma compra que você ainda não registrou").performScrollTo().assertIsDisplayed()
        // Sem o total, nada é aplicado.
        compose.onNodeWithText("Aplicar decisão").assertIsDisplayed()
        compose.onNodeWithText("Cadastrar a compra parcelada").performScrollTo().performClick()

        // O total sugerido é N × a parcela (800,00); um total que não divide igual à linha é recusado na hora.
        compose.onNodeWithText("Compra parcelada: parcela 3 de 4").assertIsDisplayed()
        compose.onNodeWithText("Total da compra (R\$)").performTextClearance()
        compose.onNodeWithText("Total da compra (R\$)").performTextInput("790,00")
        compose.onNodeWithText("Com este total", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Total da compra (R\$)").performTextClearance()
        compose.onNodeWithText("Total da compra (R\$)").performTextInput("800,00")
        compose.onNodeWithText("A parcela 3 fica em", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usar este total").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Faturas de", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cadastra a compra em 4 parcelas e vincula esta linha à parcela 3.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(sofa, substring = true)).fetchSemanticsNodes().isEmpty() }

        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val item = runBlocking { (container.imports().result(batchId) as QueryResult.Data).value!!.items.first { it.ordinal == 1 } }
        val purchase = runBlocking { (container.space().purchase(item.targetId!!) as QueryResult.Data).value!! }
        assertEquals(80000, purchase.total.cents)
        assertEquals((0..3).map { invoice.minusMonths(2).plusMonths(it.toLong()) }, purchase.installments.map { it.invoiceMonth })
    }
}
