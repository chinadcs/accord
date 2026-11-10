package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import app.accord.ui.components.RootNavigationBar
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.core.SpendingAllowance
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.nav.Root
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import app.accord.ui.AccordApp
import app.accord.ui.theme.ThemePreference
import java.io.File
import android.graphics.Bitmap

class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cargaNaoDizQueNaoHaPendencias() {
        show(PendingQueueUiState())
        compose.onNodeWithContentDescription("Consultando o que pede atenção…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").assertDoesNotExist()
    }

    @Test fun falhaOfereceRepetirNoBannerERevisarNaLinha() {
        var retries = 0
        var reviews = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false, failed = true), { retries++ }, { reviews++ })
        } }
        compose.onNodeWithText("Não foi possível consultar as pendências", substring = true).performScrollTo().assertIsDisplayed()
        // Um só "Tentar de novo", no banner; a linha de Revisar em falha continua abrindo a fila.
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        compose.onNodeWithText("Não foi possível consultar as pendências", substring = true).performClick()
        assertEquals(1, retries)
        assertEquals(1, reviews)
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").assertDoesNotExist()
    }

    // Dados fictícios do estado pronto de todos os blocos.
    private val readyBalance = HomeBalanceState.Ready(TotalBalance(
        EntrySummary(Money(30_000), Money(150_000), Money(5_000), Money.ZERO, receivedIncomes = Money(100_000)),
        EntrySummary(Money(12_000), Money(40_000), Money(2_000), Money.ZERO, receivedIncomes = Money(40_000)),
    ))
    private val readyAllowance = HomeAllowanceState.Ready(SpendingAllowance(
        "2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false,
        limitTotalCents = 100_000, netSpentCents = 56_000,
    ))
    private val readyFlow = HomeCashFlowState.Ready(MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false))

    private fun showAll(
        pending: PendingQueueUiState = PendingQueueUiState(loading = false),
        balance: HomeBalanceState = readyBalance,
        upcoming: HomeUpcomingState = HomeUpcomingState.Ready(emptyList()),
        projection: HomeProjectionState = HomeProjectionState.Ready(emptyList()),
        allowance: HomeAllowanceState = readyAllowance,
        cashFlow: HomeCashFlowState = readyFlow,
        retries: MutableList<String> = mutableListOf(),
    ) = compose.setContent { AccordTheme {
        HomeScreen(PaddingValues(), pending, { retries += "pendencias" }, {},
            allowance = allowance, onAllowanceRetry = { retries += "margem" },
            upcoming = upcoming, onUpcomingRetry = { retries += "datas" },
            projection = projection, onProjectionRetry = { retries += "estouro" },
            cashFlow = cashFlow, onCashFlowRetry = { retries += "previsao" },
            balance = balance, onBalanceRetry = { retries += "saldo" })
    } }

    @Test fun semFalhaNaoHaBannerNemBotaoDeRepetir() {
        showAll()
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
    }

    @Test fun cargaNaoConfundeComFalhaENaoMostraBanner() {
        showAll(PendingQueueUiState(), HomeBalanceState.Loading, HomeUpcomingState.Loading, HomeProjectionState.Loading,
            HomeAllowanceState.Loading, HomeCashFlowState.Loading)
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun variasFalhasTemUmBannerUmBotaoEORetryChamaSoOQueFalhou() {
        val retries = mutableListOf<String>()
        showAll(balance = HomeBalanceState.Failed, allowance = HomeAllowanceState.Failed, cashFlow = HomeCashFlowState.Failed, retries = retries)
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(1)
        compose.onNodeWithText("Não foi possível carregar: saldo, margem e previsão. Isso não significa valor zero nem que não há itens.")
            .assertIsDisplayed()
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("saldo", "margem", "previsao"), retries)
        // Cada bloco falho diz a sua frase curta e nenhum mostra valor.
        compose.onNodeWithText("Não foi possível calcular o saldo total.").assertIsDisplayed()
        compose.onNodeWithText("Não foi possível calcular a margem. Este valor não é zero.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Não foi possível calcular a previsão. Este valor não é zero.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun todasAsLeiturasFalhandoAindaTemUmUnicoBotaoQueRepeteCadaUma() {
        val retries = mutableListOf<String>()
        showAll(PendingQueueUiState(loading = false, failed = true), HomeBalanceState.Failed, HomeUpcomingState.Failed,
            HomeProjectionState.Failed, HomeAllowanceState.Failed, HomeCashFlowState.Failed, retries)
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(listOf("saldo", "pendencias", "datas", "estouro", "margem", "previsao"), retries)
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Nada nos próximos 7 dias", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Revisar: nenhuma pendência neste aparelho").assertCountEquals(0)
    }

    @Test fun bannerSomeQuandoAFalhaSeResolveESubstituiPeloValor() {
        val flow = mutableStateOf<HomeCashFlowState>(HomeCashFlowState.Failed)
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, allowance = readyAllowance, balance = readyBalance,
                upcoming = HomeUpcomingState.Ready(emptyList()), projection = HomeProjectionState.Ready(emptyList()), cashFlow = flow.value)
        } }
        compose.onNodeWithText("Parte das informações não carregou").assertIsDisplayed()
        compose.runOnIdle { flow.value = readyFlow }
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
        compose.onNodeWithText("Sobra ${Money(95_000).format()}").performScrollTo().assertIsDisplayed()
    }

    @Test fun bannerVemAntesDoSaldoEEOUnicoAvisoDinamicoDaTela() {
        showAll(balance = HomeBalanceState.Failed, cashFlow = HomeCashFlowState.Failed)
        val banner = compose.onNodeWithText("Parte das informações não carregou").fetchSemanticsNode().boundsInRoot.top
        val balance = compose.onNodeWithText("Não foi possível calcular o saldo total.").fetchSemanticsNode().boundsInRoot.top
        assertTrue("banner ($banner) acima do saldo ($balance)", banner < balance)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite), useUnmergedTree = true).assertExists()
    }

    @Test fun ordemDeLeituraDaTelaCompleta() {
        showAll()
        val titles = listOf("Saldo total", "Pede atenção", "Margem para gastar", "Previsão até o fim do mês")
        titles.forEach { compose.onNode(hasText(it) and isHeading(), useUnmergedTree = true).assertExists() }
        val tops = titles.map { compose.onNode(hasText(it) and isHeading(), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y }
        val footer = compose.onNodeWithText("Cálculos com os registros deste aparelho; nada aqui é saldo bancário.", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("ordem visual $tops, rodapé $footer", (tops + footer) == (tops + footer).sorted())
        compose.onAllNodes(isHeading() and hasText("Início"), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun filaVaziaTemTextoLocal() {
        show(PendingQueueUiState(loading = false))
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").performScrollTo().assertIsDisplayed()
    }

    @Test fun indicadoresDeInicioEMaisTemDescricaoAcessivel() {
        compose.setContent { AccordTheme { RootNavigationBar(Root.Home, {}, hasPending = true) } }
        listOf("Home", "More").forEach {
            compose.onNodeWithTag("tab-$it").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, "Há pendências neste aparelho"))
        }
        compose.onNodeWithTag("tab-Chat").assertContentDescriptionEquals("Chat")
    }

    @Test fun inicioMaisERevisarNosDoisTemas() {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val theme = mutableStateOf(ThemePreference.Light)
        compose.setContent { AccordTheme(theme.value) { AccordApp(app.container) } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            theme.value = choice
            compose.onNodeWithTag("tab-Home").performClick()
            compose.onNodeWithText("Pede atenção").assertIsDisplayed()
            capture("inicio-${choice.name}")
            compose.onNodeWithTag("tab-More").performClick()
            compose.onNode(hasText("Revisar") and hasClickAction()).assertIsDisplayed()
            capture("mais-${choice.name}")
            compose.onNode(hasText("Revisar") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Voltar").assertIsDisplayed()
            capture("revisar-${choice.name}")
            compose.onNodeWithContentDescription("Voltar").performClick()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/b6og").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun show(state: PendingQueueUiState) {
        compose.setContent { AccordTheme { HomeScreen(PaddingValues(), state, {}, {}) } }
    }
}
