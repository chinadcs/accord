package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * "Previsão até o fim do mês" na Início (ADR 0078, accord-u8zo): resultado com ícone e palavra, duas barras na mesma escala
 * com texto equivalente, chips só quando há, carga e falha sem zero e sem botão próprio (o "Tentar de novo" é do banner).
 */
class HomeCashFlowUiTest {
    @get:Rule val compose = createComposeRule()

    private fun money(cents: Long) = Money(cents).format()

    // Entradas R$ 5.000,00 (3.000 recebidos + 2.000 a receber); saídas R$ 2.050,00 (1.850 comprometidos, dos quais 1.500 pagos,
    // + 200 estimados). Só com o recebido sobram R$ 950,00; com a renda prevista, R$ 2.950,00.
    private val value = MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false)

    private fun home(
        state: HomeCashFlowState,
        fontScale: Float = 1f,
        theme: ThemePreference = ThemePreference.System,
        onBudget: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            AccordTheme(theme) { Surface(color = MaterialTheme.colorScheme.background) {
                HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {},
                    cashFlow = state, onCashFlowRetry = onRetry, onBudget = onBudget)
            } }
        }
    }

    private fun capture(name: String) {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/u8zo").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun barsDescription(inflows: String, outflows: String) = "$inflows $outflows"
    private val valueBars = barsDescription(
        "Entradas ${money(500_000)}, dos quais ${money(300_000)} já recebidos.",
        "Saídas ${money(205_000)}, dos quais ${money(185_000)} comprometidos e ${money(20_000)} estimados.",
    )

    private fun hasClickLabel(label: String) = SemanticsMatcher("clickLabel=$label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test fun cargaNaoMostraValorNemAlvoEDescreveOQueCarrega() {
        home(HomeCashFlowState.Loading)
        compose.onNodeWithContentDescription("Calculando a previsão até o fim do mês…").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Sobra", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Falta", substring = true).assertCountEquals(0)
        // Só o estado pronto tem alvo de toque e o link para o Orçamento.
        compose.onAllNodesWithText("Ver o mês no Orçamento").assertCountEquals(0)
    }

    @Test fun falhaDizQueNaoEZeroSemBotaoProprio() {
        var retries = 0
        home(HomeCashFlowState.Failed, onRetry = { retries++ })
        compose.onNodeWithText("Não foi possível calcular a previsão. Este valor não é zero.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Sobra", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Ver o mês no Orçamento").assertCountEquals(0)
        // O único botão de repetir da tela é o do banner; o cartão da previsão não tem outro nem alvo de toque.
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNode(hasText("Não foi possível calcular a previsão", substring = true) and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("Tentar de novo").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }

    @Test fun sobraMostraPalavraValorSemSinalESegundaLinhaComARendaPrevista() {
        home(HomeCashFlowState.Ready(value))
        compose.onNode(hasText("Previsão até o fim do mês") and isHeading(), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Só com a renda recebida").assertIsDisplayed()
        compose.onNodeWithText("Sobra ${money(95_000)}").assertIsDisplayed()
        compose.onNodeWithText("Se a renda prevista chegar: sobra ${money(295_000)}").assertIsDisplayed()
        compose.onAllNodesWithText("Falta", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("-", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Há valores provisórios", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("sem histórico", substring = true).assertCountEquals(0)
    }

    @Test fun faltaDizFaltaComValorSemSinalENaoSoPelaCor() {
        // Recebido R$ 100,00 contra saídas de R$ 2.050,00: falta R$ 1.950,00 nas duas leituras.
        home(HomeCashFlowState.Ready(value.copy(receivedCents = 10_000, toReceiveCents = 0)))
        compose.onNodeWithText("Falta ${money(195_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Se a renda prevista chegar: falta ${money(195_000)}").assertIsDisplayed()
        compose.onAllNodesWithText("Sobra", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("-", substring = true).assertCountEquals(0)
    }

    @Test fun resultadosDiferentesFaltaNoPrimeiroESobraNoSegundo() {
        home(HomeCashFlowState.Ready(value.copy(receivedCents = 100_000, toReceiveCents = 200_000)))
        compose.onNodeWithText("Falta ${money(105_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Se a renda prevista chegar: sobra ${money(95_000)}").assertIsDisplayed()
    }

    @Test fun zeroExatoNaoDizSobraNemFalta() {
        home(HomeCashFlowState.Ready(value.copy(receivedCents = 205_000, toReceiveCents = 0)))
        compose.onNodeWithText("Fica em ${money(0)}, sem sobra nem falta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Se a renda prevista chegar: fica em ${money(0)}, sem sobra nem falta").assertIsDisplayed()
        compose.onAllNodesWithText("Sobra", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Falta", substring = true).assertCountEquals(0)
    }

    @Test fun duasBarrasTemTextoEquivalenteCompletoELegendaDeCheioETracejado() {
        home(HomeCashFlowState.Ready(value))
        compose.onNodeWithText("Cheio: já confirmado · tracejado: previsto").performScrollTo().assertIsDisplayed()
        // O pago (R$ 1.500,00) é parte do comprometido: não aparece como valor próprio nem entra no total das saídas.
        compose.onNodeWithContentDescription(valueBars, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText(money(150_000), substring = true).assertCountEquals(0)
        // Os totais visíveis (Entradas e Saídas) acompanham a descrição.
        compose.onNodeWithText("Entradas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(money(500_000), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Saídas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(money(205_000), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun semRendaNaoHaBarraDeEntradasMasOTotalZeroContinuaEscrito() {
        home(HomeCashFlowState.Ready(value.copy(receivedCents = 0, toReceiveCents = 0)))
        compose.onNodeWithText("Falta ${money(205_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(
            barsDescription("Entradas: nenhuma renda no mês.",
                "Saídas ${money(205_000)}, dos quais ${money(185_000)} comprometidos e ${money(20_000)} estimados."),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithText(money(0), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun semDadosDizQueAindaNaoHaComoPreverSemSobraNemFalta() {
        home(HomeCashFlowState.Ready(MonthCashFlow(0, 0, 0, 0, 0, 0, 0, false)))
        compose.onNodeWithText("Ainda não há dados para prever o mês").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Sobra", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Falta", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Fica em", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Cheio:", substring = true).assertCountEquals(0)
        // Mesmo assim o cartão leva ao Orçamento, onde o mês se organiza.
        compose.onNodeWithText("Ver o mês no Orçamento").assertIsDisplayed()
    }

    @Test fun semEstimativaEProvisorioSoAparecemQuandoHa() {
        home(HomeCashFlowState.Ready(value.copy(categoriesWithoutEstimate = 2, provisional = true)))
        compose.onNodeWithText("2 categorias sem histórico", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Há valores provisórios", substring = true).assertIsDisplayed()
    }

    @Test fun umaCategoriaSemEstimativaFicaNoSingular() {
        home(HomeCashFlowState.Ready(value.copy(categoriesWithoutEstimate = 1)))
        compose.onNodeWithText("1 categoria sem histórico", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Há valores provisórios", substring = true).assertCountEquals(0)
    }

    @Test fun cartaoInteiroEUmBotaoQueAbreOOrcamentoComRotuloDoDestino() {
        var opens = 0
        home(HomeCashFlowState.Ready(value), onBudget = { opens++ })
        val card = compose.onNode(hasText("Ver o mês no Orçamento") and hasClickAction()).performScrollTo()
        card.assert(hasClickLabel("Ver o mês no Orçamento"))
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        card.performClick()
        assertEquals(1, opens)
    }

    @Test fun cartaoNaoRepeteORodapeDaTela() {
        home(HomeCashFlowState.Ready(value))
        // A frase "nada aqui é saldo bancário" é só do rodapé único.
        compose.onAllNodesWithText("Cálculos com os registros deste aparelho; nada aqui é saldo bancário.").assertCountEquals(1)
        compose.onAllNodesWithText("saldo bancário", substring = true).assertCountEquals(1)
    }

    @Test fun textoAmpliadoMantemResultadoBarrasELinkVisiveis() {
        home(HomeCashFlowState.Ready(value.copy(categoriesWithoutEstimate = 2, provisional = true)), fontScale = 2f)
        compose.onNodeWithText("Sobra ${money(95_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Se a renda prevista chegar: sobra ${money(295_000)}").assertIsDisplayed()
        compose.onNodeWithContentDescription(valueBars, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Cheio: já confirmado · tracejado: previsto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver o mês no Orçamento").performScrollTo().assertIsDisplayed()
    }

    @Test fun faltaEmTextoAmpliadoNaoQuebraONumero() {
        home(HomeCashFlowState.Ready(value.copy(receivedCents = 10_000, toReceiveCents = 0)), fontScale = 2f)
        compose.onNodeWithText("Falta ${money(195_000)}").performScrollTo().assertIsDisplayed()
    }

    @Test fun sobraEFaltaNosDoisTemasComCapturas() {
        val theme = mutableStateOf(ThemePreference.Light)
        val state = mutableStateOf<HomeCashFlowState>(HomeCashFlowState.Ready(value.copy(categoriesWithoutEstimate = 2, provisional = true)))
        compose.setContent { AccordTheme(theme.value) { Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, cashFlow = state.value)
        } } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { theme.value = choice }
            compose.runOnIdle { state.value = HomeCashFlowState.Ready(value.copy(categoriesWithoutEstimate = 2, provisional = true)) }
            compose.onNode(hasText("Previsão até o fim do mês") and isHeading(), useUnmergedTree = true).performScrollTo()
            compose.onNodeWithText("Sobra ${money(95_000)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Ver o mês no Orçamento").performScrollTo()
            capture("previsao-sobra-${choice.name}")
            compose.runOnIdle { state.value = HomeCashFlowState.Ready(value.copy(receivedCents = 10_000, toReceiveCents = 0)) }
            compose.onNodeWithText("Falta ${money(195_000)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Ver o mês no Orçamento").performScrollTo()
            capture("previsao-falta-${choice.name}")
        }
    }
}
