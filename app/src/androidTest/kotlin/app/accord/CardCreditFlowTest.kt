package app.accord

import androidx.compose.ui.test.assertCountEquals
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
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.DeterministicIds
import app.accord.core.space.QueryResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Crédito num documento de cartão (accord-96km, ADR 0074): todo valor positivo é estorno. A tela de resolver diz isso, não oferece "recebimento" e deixa
 * escolher a compra estornada ou descartar a linha.
 */
@RunWith(AndroidJUnit4::class)
class CardCreditFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val credit get() = "CANCELAMENTO $tag"
    private val invoice = YearMonth.now().plusMonths(1)
    private var batchId = ""

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val card = (container.space().cards() as QueryResult.Data).value.first().id
        val day = LocalDate.now()
        fun line(ordinal: Int, cents: Long, name: String, hint: app.accord.core.importing.InstallmentHint?) =
            ExtractedMovement(ordinal, listOf("LINE" to "$name $cents"), day, cents, "BRL", name, hint, emptySet())
        val extraction = ExtractionResult(
            "sha-cr-$tag", DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, DocumentKind.Card, null, null, null),
            // A compra de R$ 50,00 do mesmo arquivo é uma possível origem do crédito: sem nenhuma compra que pudesse ser a original, o aplicativo registra uma e estorna ela
            // mesma (accord-qid0), e a pergunta "qual compra foi estornada?" nem apareceria.
            listOf(line(1, 5000, credit, null), line(2, -1000, "PADARIA $tag", null), line(3, -5000, "LOJA $tag", null)), 0, emptyList(),
        )
        val imports = container.imports()
        val started = (imports.start(StartImport("cr-$tag", "fatura-$tag.pdf", 10, extraction, BatchSource(DocumentKind.Card, card, invoice))) as QueryResult.Data).value
        batchId = (started as StartOutcome.Started).info.id
        val groceries = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        imports.decide(batchId, ImportDecision.DefaultCategory(groceries), 1)
        val token = (imports.review(batchId) as QueryResult.Data).value!!.token
        check((imports.confirm(batchId, "cr-confirm-$tag", token) as QueryResult.Data).value is ConfirmOutcome.Applied)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun creditoNoCartaoSoPodeSerEstorno() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revisar pendências")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revisar pendências").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(credit, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crédito no cartão: qual compra foi estornada?").assertIsDisplayed()
        compose.onNodeWithText(credit, substring = true).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("No cartão, todo valor positivo é estorno")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("É estorno de uma compra: escolher a compra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Já está registrado: descartar esta linha").performScrollTo().assertIsDisplayed()
        // Recebimento é coisa de conta: no cartão a opção nem aparece.
        compose.onAllNodes(hasText("É um recebimento", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("É estorno de uma compra: escolher a compra").performClick()
        compose.onNodeWithText("Compra ou pagamento estornado").assertIsDisplayed()
    }
}
