package app.accord

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.alerts.AndroidNotifications
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Previsões e alertas de ponta a ponta no banco do aparelho (S5.6): o toque numa notificação abre Orçamento, Previsões e o detalhe da categoria do evento; os
 * atalhos do Orçamento levam às mesmas telas. A categoria e o evento nascem só para este teste (chave própria), sem tocar nos dados de outros.
 */
@RunWith(AndroidJUnit4::class)
class ForecastFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val categoryName get() = "Previsao $tag"
    private var eventId = ""
    private var categoryId = ""

    @Before
    fun seed() = runBlocking<Unit> {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val created = space.execute(Envelope(CommandId("fc-cat-$tag")), CreateCategory(categoryName)) as CommandResult.Applied
        categoryId = created.receipt.changes.first { it.type == app.accord.core.space.EntityType.Category }.id
        val month = container.clock.currentMonth()
        space.execute(
            Envelope(CommandId("fc-limit-$tag")),
            DefineLimits(month, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(categoryId, LimitEdit(total = TotalChange.Set(Money(100)))))),
        )
        space.execute(
            Envelope(CommandId("fc-pay-$tag")),
            RegisterDirectPayment(DirectPaymentKind.Cash, "gasto $tag", Classification.Category(categoryId), container.clock.today(), Money(10_000)),
        )
        // Gastar acima do limite grava o evento na hora (avaliação interna da fachada).
        eventId = (space.alertStatuses() as QueryResult.Data).value.single { it.categoryId == categoryId }.id
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitForText(text: String) {
        if (text == "Categorias") {
            // O gráfico ocupa o início do orçamento; o cabeçalho é um item lazy abaixo dele.
            // Voltar das previsões também pode restaurar uma posição mais baixa da lista.
            // Com a lista ainda em "Carregando" (dois itens) o cabeçalho não existe e a rolagem falha: repete até a lista carregar.
            compose.waitUntil(15_000) {
                runCatching { compose.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }.isSuccess
            }
        }
        if (text == "$categoryName · excesso já ocorrido") {
            compose.waitUntil(15_000) {
                runCatching { compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasText(text, substring = true)) }.isSuccess
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tocarNoAvisoAbreODetalheDaCategoriaEVoltarLevaAsPrevisoesEDepoisAoOrcamento() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(AndroidNotifications.EXTRA_ALERT_EVENT, eventId)
        scenario = ActivityScenario.launch(intent)

        waitForText("$categoryName · previsão do mês")
        compose.onNodeWithText("Você abriu pelo aviso de", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Excesso já ocorrido").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Voltar às previsões").performScrollTo().performClick()
        waitForText("Previsões e alertas")
        waitForText("$categoryName · excesso já ocorrido")

        // Voltar das previsões chega ao Orçamento, onde a pilha começou.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        waitForText("Categorias")
    }

    @Test
    fun orcamentoLevaAsPrevisoesEAoDetalheEOPrincipalConfereOsMovimentosDaCategoria() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onAllNodesWithText("Orçamento")[0].performClick()
        waitForText("Categorias")
        // O atalho fica no fim da lista do mês: a lista só compõe o que está perto da tela.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Previsões e alertas"))
        compose.onNodeWithText("Previsões e alertas").performClick()

        waitForText("$categoryName · excesso já ocorrido")
        compose.onNodeWithText("$categoryName · excesso já ocorrido", substring = true).performScrollTo().performClick()
        waitForText("$categoryName · previsão do mês")
        // Sem aviso tocado, não há faixa do aviso nem botão de voltar às previsões.
        assertTrue(compose.onAllNodesWithText("Você abriu pelo aviso", substring = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Conferir movimentos").performScrollTo().performClick()
        waitForText("gasto $tag")
    }
}
