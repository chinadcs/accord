package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.Money
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.UnavailableReason
import app.accord.ui.components.MoneyChart
import app.accord.ui.components.MoneyChartValue
import app.accord.ui.forecast.ForecastChart
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Synthetic money only; exercise the real Charty Canvas and exact accessible alternative. */
class MoneyChartUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun valoresExatosNegativosZeroEAusenciaComAcaoEAtualizacao() {
        var opened = 0
        val amount = mutableStateOf(Money(16_777_217)) // cents are beyond Float's exact integer range
        compose.setContent {
            AccordTheme(ThemePreference.Light) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        MoneyChart("Categorias", "Outubro · mês do orçamento · R$", listOf(
                            MoneyChartValue("Casa", amount.value, onClick = { opened++ }),
                            MoneyChartValue("Crédito líquido", Money(-12345)),
                            MoneyChartValue("Zero confirmado", Money.ZERO),
                            MoneyChartValue("Setembro", null, "Dados indisponíveis"),
                        ))
                    }
                }
            }
        }
        compose.onNodeWithText("1. Casa: ${Money(16_777_217).format()}").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("2. Crédito líquido: ${Money(-12345).format()}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3. Zero confirmado: ${Money.ZERO.format()}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4. Setembro: Dados indisponíveis").performScrollTo().assertIsDisplayed()
        chartScreenshot(compose, "exact-negative-zero-missing-light")
        compose.runOnIdle { amount.value = Money(98000) }
        compose.onNodeWithText("1. Casa: ${Money(98000).format()}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1. Casa: ${Money(16_777_217).format()}").assertDoesNotExist()
    }

    @Test fun previsaoIndisponivelEEncerradaNaoViramZeroNoTemaEscuroComFonteAmpliada() {
        val categories = listOf(
            forecast("food", 50000, Estimate.Value(15000), 65000),
            forecast("home", -12345, Estimate.Unavailable(UnavailableReason.NoCompletePeriod), -12345),
            forecast("pet", 30000, Estimate.NotApplicable, 30000),
        )
        compose.setContent {
            AccordTheme(ThemePreference.Dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            ForecastChart(YearMonth.of(2026, 10), listOf("Alimentação" to categories[0], "Casa" to categories[1], "Pet" to categories[2]))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Conhecidos × previsão").assertIsDisplayed()
        chartScreenshot(compose, "forecast-dark-font200-top")
        compose.onNodeWithText("1. Alimentação · conhecidos: ${Money(50000).format()}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2. Alimentação · total previsto: ${Money(65000).format()}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4. Casa · total previsto: Previsão indisponível").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6. Pet · total previsto: Mês encerrado · sem projeção").performScrollTo().assertIsDisplayed()
        chartScreenshot(compose, "forecast-dark-font200-list")
    }

    private fun forecast(id: String, known: Long, estimate: Estimate, total: Long) =
        CategoryForecast(id, null, known, estimate, total, Excess.NoLimit, Confidence.NoKnownCaveats, emptyList())

}

internal fun chartScreenshot(compose: ComposeTestRule, name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "charts-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
