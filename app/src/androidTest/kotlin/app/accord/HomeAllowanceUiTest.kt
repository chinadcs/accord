package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.accord.core.Money
import app.accord.core.SpendingAllowance
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQueryResult
import app.accord.core.chat.ConsultOutcome
import app.accord.core.chat.ConsultRequest
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.chat.ConsultType
import app.accord.core.chat.MessageState
import app.accord.core.chat.StandardReply
import app.accord.core.space.DateCriterion
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.QueryCard
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Margem para gastar na Início e a mesma resposta no Chat (accord-jpj7; renomeada e resumida em accord-xj7j, ADR 0078). */
class HomeAllowanceUiTest {
    @get:Rule val compose = createComposeRule()

    // 9/11/2026 é segunda-feira: a semana vai até domingo, 15/11. Limite total R$ 1.000,00, líquido R$ 560,00 (56%).
    private val value = SpendingAllowance(
        "2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false,
        limitTotalCents = 100_000, netSpentCents = 56_000,
    )
    private fun brl(cents: Long) = Money(cents).format()

    private fun home(state: HomeAllowanceState, fontScale: Float = 1f, theme: ThemePreference = ThemePreference.System,
                     onBudget: () -> Unit = {}, onRetry: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(theme) { Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                        allowance = state, onAllowanceRetry = onRetry, onBudget = onBudget)
                } }
            }
        }
    }

    private fun capture(name: String) {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/xj7j").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun respostaSemanalDoChatMostraMesmoValorEPermiteAtualizarEAbrirOrcamento() {
        var budgets = 0
        var refreshes = 0
        val request = ConsultRequest(ConsultType.WeekAllowance, YearMonth.of(2026, 11), true, DateCriterion.BudgetMonth, true)
        val result = ChatQueryResult(ChatQueryResult.Kind.WeekAllowance, "2026-11", true, DateCriterion.BudgetMonth.name, true, allowance = value)
        val item = ChatItem.Query(ChatMessage("m1", "quanto posso gastar esta semana?", 1, MessageState.Replied, reply = StandardReply.Consult),
            ConsultSnapshot.of(request, ConsultOutcome.Result(result), 1_000))
        val actions = ChatActions(onOpenModel = {}, onOpenEntry = {}, onEditEntry = {}, onManual = {},
            onOpenBudget = { budgets++ }, onOpenEntries = {}, onOpenCard = {}, onRefreshQuery = { refreshes++ })
        compose.setContent { AccordTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { QueryCard(item, actions, {}) }
        } }
        // O Chat usa o corpo compartilhado, só com o rótulo novo; fórmula, catálogo e botões não mudam.
        compose.onNodeWithText("Margem para gastar").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Margem do orçamento").assertCountEquals(0)
        compose.onNodeWithText("Até o fim desta semana: ${brl(14_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hoje:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver orçamento").performScrollTo().performClick()
        compose.onNodeWithText("Atualizar").performScrollTo().performClick()
        assertEquals(1, budgets)
        assertEquals(1, refreshes)
    }

    @Test fun cargaNaoMostraNumeroNemAlvoEDescreveOQueCarrega() {
        home(HomeAllowanceState.Loading)
        compose.onNodeWithContentDescription("Calculando a margem para gastar…").assertExists()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Hoje").assertCountEquals(0)
        compose.onAllNodesWithText("Ver limites").assertCountEquals(0)
        // Só o estado pronto tem botão de informação e alvo do cartão.
        compose.onAllNodesWithContentDescription("Como é calculado").assertCountEquals(0)
    }

    @Test fun falhaDizQueNaoEZeroENaoMostraValor() {
        var retries = 0
        home(HomeAllowanceState.Failed, onRetry = { retries++ })
        compose.onNodeWithText("Não foi possível calcular a margem. Este valor não é zero.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Hoje").assertCountEquals(0)
        compose.onNodeWithText("Tentar de novo").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }

    @Test fun prontaMostraHojeEstaSemanaBarraDeUsoECoberturaNosDoisTemas() {
        val theme = mutableStateOf(ThemePreference.Light)
        var opens = 0
        compose.setContent { AccordTheme(theme.value) { Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                allowance = HomeAllowanceState.Ready(value), onBudget = { opens++ })
        } } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { theme.value = choice }
            compose.onNode(hasText("Margem para gastar") and isHeading(), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Pelo orçamento de novembro, não do banco").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Hoje").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(brl(2_000)).assertIsDisplayed()
            compose.onNodeWithText("Esta semana").assertIsDisplayed()
            compose.onNodeWithText(brl(14_000)).assertIsDisplayed()
            compose.onNodeWithText("até dom., 15/11").assertIsDisplayed()
            compose.onNodeWithText("${brl(56_000)} de ${brl(100_000)} usados (56%)").assertIsDisplayed()
            compose.onNodeWithText("22 dias restantes, contando hoje").assertIsDisplayed()
            compose.onNodeWithText("Inclui 2 categorias com limite.").assertIsDisplayed()
            compose.onNodeWithText("3 lançamentos a classificar ficam fora do cálculo").assertIsDisplayed()
            compose.onAllNodesWithText("Provisório", substring = true).assertCountEquals(0)
            // O texto da barra é o equivalente; a barra tem a mesma descrição e fica fora da leitura do cartão.
            compose.onNodeWithContentDescription("${brl(56_000)} de ${brl(100_000)} usados (56%)", useUnmergedTree = true).assertExists()
            capture("margem-pronta-${choice.name}")
        }
        // O toque no cartão (rótulo "Ver limites") abre o Orçamento.
        compose.onNodeWithText("Ver limites").performScrollTo().performClick()
        assertEquals(1, opens)
    }

    @Test fun semanaQueTerminaNoFimDoMesNaoDizDomingo() {
        // 30/11/2026 é segunda e o último dia do mês: a janela acaba ali, sem chegar ao domingo.
        home(HomeAllowanceState.Ready(value.copy(date = "2026-11-30", weekEnd = "2026-11-30", daysRemaining = 1, weekDays = 1)))
        compose.onNodeWithText("1 dia restante, contando hoje").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("até o fim do mês").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("até dom.", substring = true).assertCountEquals(0)
    }

    @Test fun botaoDeInformacaoAbreComoECalculadoSemAbrirOOrcamento() {
        var opens = 0
        home(HomeAllowanceState.Ready(value.copy(provisional = true)), onBudget = { opens++ })
        compose.onNodeWithContentDescription("Como é calculado").performScrollTo()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Como é calculado").assertIsDisplayed()
        compose.onNodeWithText("Restante do mês dividido pelos dias restantes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Entram só as categorias principais com limite cadastrado", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Inclui 2 categorias com limite. Fora do cálculo: 4 categorias sem limite e 3 lançamentos a classificar.").assertIsDisplayed()
        compose.onNodeWithText("Lançamentos a classificar ficam fora do cálculo até receberem categoria.").assertIsDisplayed()
        compose.onNodeWithText("provisória quando parte do valor ainda está a conferir", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usa os registros deste aparelho", substring = true).assertIsDisplayed()
        compose.onNodeWithText("não saldo bancário", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Fechar").assertHeightIsAtLeast(48.dp).performClick()
        compose.onAllNodesWithText("Fechar").assertCountEquals(0)
        assertEquals(0, opens)
    }

    @Test fun excessoMostraMargemZeroComIconeETextoEBarraCheia() {
        val over = value.copy(todayCents = 0, weekCents = 0, remainingCents = -123, excessCents = 123, netSpentCents = 100_123)
        home(HomeAllowanceState.Ready(over))
        compose.onNodeWithText("Passou ${brl(123)} do orçamento considerado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(brl(0), substring = true).assertExists()
        compose.onNodeWithText("${brl(100_123)} de ${brl(100_000)} usados (100%)").assertIsDisplayed()
        capture("margem-excesso")
    }

    @Test fun semLimitesNaoViraZeroNemMostraBarra() {
        val none = value.copy(limitedCategories = 0, todayCents = null, weekCents = null, remainingCents = null, limitTotalCents = null, netSpentCents = null)
        var opens = 0
        home(HomeAllowanceState.Ready(none), onBudget = { opens++ })
        compose.onNodeWithText("Cadastre limites para calcular a margem para gastar.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Hoje").assertCountEquals(0)
        compose.onAllNodesWithText("usados", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("dias restantes", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Ver limites").performScrollTo().performClick()
        assertEquals(1, opens)
        capture("margem-sem-limites")
    }

    @Test fun provisorioFicaExplicitoComIconeETexto() {
        home(HomeAllowanceState.Ready(value.copy(provisional = true)))
        compose.onNodeWithText("Provisório: parte do valor ainda está a conferir").performScrollTo().assertIsDisplayed()
    }

    @Test fun semBarraQuandoOLimiteTotalOuOLiquidoNaoExistem() {
        home(HomeAllowanceState.Ready(value.copy(limitTotalCents = null, netSpentCents = null)))
        compose.onNodeWithText("Hoje").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("usados", substring = true).assertCountEquals(0)
    }

    @Test fun fonteDuasVezesEmpilhaHojeEEstaSemanaESemQuebrarONumero() {
        val big = value.copy(todayCents = 12_345_678, weekCents = 86_419_746, remainingCents = 86_419_746, limitTotalCents = 90_000_000, netSpentCents = 3_580_254)
        home(HomeAllowanceState.Ready(big), fontScale = 2f)
        val today = compose.onNodeWithText(brl(12_345_678), useUnmergedTree = true)
        val week = compose.onNodeWithText(brl(86_419_746), useUnmergedTree = true)
        for (node in listOf(today, week)) {
            node.performScrollTo().assertIsDisplayed()
            val results = mutableListOf<TextLayoutResult>()
            node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.firstOrNull()
            assertNotNull("sem layout de texto", layout)
            assertEquals("o valor ocupa uma linha só", 1, layout!!.lineCount)
            assertFalse("o valor não pode vazar da largura", layout.didOverflowWidth)
        }
        // Empilhados: Esta semana começa abaixo de Hoje e na mesma margem esquerda.
        val todayBounds = compose.onNodeWithText("Hoje", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val weekBounds = compose.onNodeWithText("Esta semana", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Esta semana deve vir abaixo de Hoje", weekBounds.top > todayBounds.bottom)
        assertEquals(todayBounds.left, weekBounds.left)
        capture("margem-fonte-2")
    }
}
