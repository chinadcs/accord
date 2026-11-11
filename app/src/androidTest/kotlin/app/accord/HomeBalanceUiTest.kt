package app.accord

import androidx.compose.foundation.layout.PaddingValues
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.accord.ui.entries.BALANCE_ICON_TAG_PREFIX
import app.accord.ui.theme.ThemePreference
import com.composables.icons.lucide.R as Lucide
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import app.accord.core.Money
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Saldo total no Início e esqueletos de carga (accord-wecg, ADR 0071). Dados fictícios. */
class HomeBalanceUiTest {
    @get:Rule val compose = createComposeRule()

    private fun brl(reais: Long) = Money(reais * 100)
    private fun home(balance: HomeBalanceState, onBalanceRetry: () -> Unit = {}, fontScale: Float = 1f, theme: ThemePreference = ThemePreference.System, countExpectedIncome: Boolean = false) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            AccordTheme(theme) { HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, balance = balance, includeExpectedIncome = countExpectedIncome, onBalanceRetry = onBalanceRetry) }
        }
    }

    private fun iconTag(res: Int) = BALANCE_ICON_TAG_PREFIX + res
    private val month = EntrySummary(brl(120), brl(400), brl(20), Money.ZERO, receivedIncomes = brl(400))
    private val total = EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000))

    @Test fun saldoTotalAcumuladoComReceitaEDespesaSoDoMes() {
        val total = EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000))
        val month = EntrySummary(brl(120), brl(400), brl(20), Money.ZERO, receivedIncomes = brl(400))
        home(HomeBalanceState.Ready(TotalBalance(total, month)))
        compose.onNodeWithText("Saldo total").assertIsDisplayed()
        compose.onNodeWithText(brl(750).format()).assertIsDisplayed() // 1000 − (300 − 50)
        compose.onNodeWithText("Receitas do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(400).format()).assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(100).format()).assertIsDisplayed() // do mês: 120 − 20
    }

    @Test fun subtituloDizAcumuladoAteOFimDoMesETituloEhCabecalho() {
        home(HomeBalanceState.Ready(TotalBalance(total, month)))
        // O acumulado vai até o fim do mês atual (ADR 0071), por isso o subtítulo não diz "até hoje".
        compose.onNodeWithText("acumulado até o fim do mês").assertIsDisplayed()
        compose.onAllNodesWithText("acumulado até hoje").assertCountEquals(0)
        compose.onNode(hasText("Saldo total") and isHeading()).assertIsDisplayed()
    }

    @Test fun receitaEDespesaTemIconeDecorativoEOTextoEEquivalente() {
        home(HomeBalanceState.Ready(TotalBalance(total, month)))
        compose.onNodeWithTag(iconTag(Lucide.drawable.lucide_ic_arrow_down_left), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(iconTag(Lucide.drawable.lucide_ic_arrow_up_right), useUnmergedTree = true).assertIsDisplayed()
        // Os ícones não têm descrição própria: o rótulo "Receitas do mês"/"Despesa do mês" é que é lido.
        compose.onNodeWithTag(iconTag(Lucide.drawable.lucide_ic_arrow_down_left), useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithText("Receitas do mês").assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
    }

    @Test fun cartaoEUmNoSoComTodosOsValoresNaDescricao() {
        home(HomeBalanceState.Ready(TotalBalance(total, month)))
        compose.onNodeWithContentDescription(
            "Saldo total, acumulado até o fim do mês: saldo de ${brl(750).format()}. " +
                "Receitas do mês: ${brl(400).format()}. Despesa do mês: ${brl(100).format()}.",
        ).assertIsDisplayed()
    }

    @Test fun contandoPrevistasSaldoEReceitasSomamRecebidaEPrevista() {
        val total = EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000))
        val month = EntrySummary(brl(120), brl(900), brl(20), Money.ZERO, receivedIncomes = brl(400))
        home(HomeBalanceState.Ready(TotalBalance(total, month)), countExpectedIncome = true)
        compose.onNodeWithText("Receitas do mês").assertIsDisplayed() // o rótulo é o mesmo com a opção ligada
        compose.onNodeWithText(brl(1250).format()).assertIsDisplayed() // 1500 − (300 − 50)
        compose.onNodeWithText(brl(900).format()).assertIsDisplayed()
    }

    @Test fun saldoNegativoELidoPorExtenso() {
        val negative = TotalBalance(EntrySummary(brl(400), Money.ZERO, Money.ZERO, Money.ZERO, receivedIncomes = brl(100)),
            EntrySummary(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))
        home(HomeBalanceState.Ready(negative))
        compose.onNodeWithContentDescription("saldo negativo de ${brl(300).format()}", substring = true).assertIsDisplayed()
        compose.onNodeWithText(Money(-30_000).format()).assertIsDisplayed()
    }

    @Test fun textoAmpliadoEmpilhaReceitaEDespesaComOsIcones() {
        home(HomeBalanceState.Ready(TotalBalance(total, month)), fontScale = 2f)
        compose.onNodeWithText("Receitas do mês").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(750).format()).assertIsDisplayed()
        compose.onNodeWithTag(iconTag(Lucide.drawable.lucide_ic_arrow_up_right), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun saldoNosDoisTemasComCapturas() {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val theme = mutableStateOf(ThemePreference.Light)
        compose.setContent { AccordTheme(theme.value) { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, balance = HomeBalanceState.Ready(TotalBalance(total, month)))
        } } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { theme.value = choice }
            compose.onNodeWithText("acumulado até o fim do mês").assertIsDisplayed()
            val folder = File(app.getExternalFilesDir(null), "screens/xj7j").apply { mkdirs() }
            File(folder, "saldo-${choice.name}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun rendaPrevistaNaoEntraEOSaldoNegativoMostraOSinal() {
        home(HomeBalanceState.Ready(TotalBalance(EntrySummary(brl(400), Money.ZERO, Money.ZERO, Money.ZERO, receivedIncomes = brl(100)), EntrySummary(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))))
        compose.onNodeWithText(Money(-30_000).format()).assertIsDisplayed()
    }

    @Test fun carregandoMostraEsqueletoSemNumerosEComDescricaoParaOLeitorDeTela() {
        home(HomeBalanceState.Loading)
        compose.onNodeWithContentDescription("Calculando o saldo total…").assertIsDisplayed()
        compose.onAllNodesWithText("Saldo total").assertCountEquals(0)
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun falhaNaoViraZeroEOBannerUnicoPermiteRepetir() {
        var retries = 0
        home(HomeBalanceState.Failed, onBalanceRetry = { retries++ })
        compose.onNodeWithText("Não foi possível calcular o saldo total.").assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        // O cartão só diz a frase; o "Tentar de novo" é um só, no banner, e refaz a leitura do saldo.
        compose.onNodeWithText("Não foi possível carregar: saldo. Isso não significa valor zero nem que não há itens.").assertIsDisplayed()
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }

    @Test fun inicioNaoTemMaisOBotaoRegistrarManualmenteNemASincronizacao() {
        home(HomeBalanceState.Loading)
        compose.onAllNodesWithText("Registrar manualmente").assertCountEquals(0)
        compose.onAllNodesWithText("Salvo no aparelho").assertCountEquals(0)
        compose.onAllNodesWithText("Dados sincronizados", substring = true).assertCountEquals(0)
    }
}
