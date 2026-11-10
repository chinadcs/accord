package app.accord

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.accord.core.MonthCashFlow
import app.accord.core.SpendingAllowance
import org.junit.Assert.assertTrue
import app.accord.ui.home.AllowanceSummary
import app.accord.ui.home.ForecastSummary
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.theme.AccordTheme
import org.junit.Rule
import org.junit.Test

/**
 * Salto de altura dos cartões Previsão e Margem entre a carga e o estado pronto típico (accord-qjlo, ADR 0078): sem a reserva
 * de altura na carga, o cartão crescia 170 a 260 dp quando o valor chegava e empurrava o resto da tela. Falha e "sem dados"
 * continuam menores (uma frase), o que é aceito. A faixa Pede atenção não foi alterada: a carga reserva três linhas
 * (280 dp a 100%) e o caso comum "tudo em dia" (164 dp) encolhe, porque quantas linhas aparecem só se sabe depois da leitura. Dados fictícios.
 */
class HomeHeightStabilityUiTest {
    @get:Rule val compose = createComposeRule()

    private val current = mutableStateOf<Pair<Float, @Composable () -> Unit>>(1f to {})
    private var density = 1f
    private var started = false

    private fun heightDp(scale: Float, content: @Composable () -> Unit): Float {
        if (!started) {
            started = true
            compose.setContent {
                val (fontScale, body) = current.value
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    density = LocalDensity.current.density
                    AccordTheme { Box(Modifier.fillMaxWidth().padding(16.dp)) { Box(Modifier.testTag("m")) { body() } } }
                }
            }
        }
        compose.runOnIdle { current.value = scale to content }
        compose.waitForIdle()
        return compose.onNodeWithTag("m").fetchSemanticsNode().size.height / density
    }

    private val flow = MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false)
    private val allowance = HomeAllowanceState.Ready(SpendingAllowance(
        "2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false,
        limitTotalCents = 100_000, netSpentCents = 56_000,
    ))

    /** Carga entre 60% e 100% da altura do pronto típico: sem salto de centenas de dp e sem um vazio maior que o conteúdo. */
    private fun assertCloseToReady(name: String, scale: Float, loading: Float, ready: Float) {
        android.util.Log.i("HEIGHT", "$name@$scale carga=$loading pronto=$ready")
        assertTrue("$name@$scale: carga $loading dp x pronto $ready dp", loading >= 0.6f * ready && loading <= ready)
    }

    @Test fun previsaoNaoSaltaCentenasDeDpDaCargaParaOPronto() {
        for (scale in listOf(1f, 1.3f, 1.5f)) {
            val loading = heightDp(scale) { ForecastSummary(HomeCashFlowState.Loading, {}) }
            val ready = heightDp(scale) { ForecastSummary(HomeCashFlowState.Ready(flow), {}) }
            assertCloseToReady("previsao", scale, loading, ready)
        }
    }

    @Test fun margemNaoSaltaCentenasDeDpDaCargaParaOPronto() {
        for (scale in listOf(1f, 1.3f, 1.5f)) {
            val loading = heightDp(scale) { AllowanceSummary(HomeAllowanceState.Loading, {}) }
            val ready = heightDp(scale) { AllowanceSummary(allowance, {}) }
            assertCloseToReady("margem", scale, loading, ready)
        }
    }
}
