package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.core.SpendingAllowance
import app.accord.core.UpcomingEvent
import app.accord.core.forecast.ExceedProjection
import app.accord.core.space.CardInfo
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.core.space.DraftSource
import app.accord.core.space.EntrySummary
import app.accord.core.space.LocalDraft
import app.accord.core.space.TotalBalance
import app.accord.ui.AccordApp
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.home.ProjectionRow
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.QueueItem
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Capturas da Início completa (accord-u8zo e accord-qjlo, ADR 0078): cada cenário rola do topo ao rodapé e grava uma imagem por
 * tela, em `screens/qjlo`. Só afirma que o rodapé é alcançado e fica acima da barra inferior; a inspeção é visual. Dados fictícios.
 */
class HomeVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private val card = CardInfo("c1", null, "Itaú", 12, 20)
    private val footer = "Cálculos com os registros deste aparelho; nada aqui é saldo bancário."

    private val balance = HomeBalanceState.Ready(TotalBalance(
        EntrySummary(Money(30_000), Money(150_000), Money(5_000), Money.ZERO, receivedIncomes = Money(100_000)),
        EntrySummary(Money(12_000), Money(40_000), Money(2_000), Money.ZERO, receivedIncomes = Money(40_000)),
    ))
    private val allowance = HomeAllowanceState.Ready(SpendingAllowance(
        "2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false,
        limitTotalCents = 100_000, netSpentCents = 56_000,
    ))
    private val flow = MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 2, true)

    private fun pendings(n: Int) = PendingQueueUiState(loading = false, items = (1..n).map {
        QueueItem.BankNotification(LocalDraft(id = "d$it", source = DraftSource.Notification, fields = emptyMap(), reason = "x"))
    })

    private val events = HomeUpcomingState.Ready(listOf(
        UpcomingEvent.BillDue(Commitment("b1", CommitmentNature.Bill, "Internet", null, Money(15_000), LocalDate.parse("2026-11-12"), null, null, emptySet(), null)),
        UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-20")),
    ))
    private val exceed = HomeProjectionState.Ready(listOf(
        ProjectionRow("Mercado", ExceedProjection("Mercado", LocalDate.parse("2026-11-14"), 4, 100_000, 5_000, false)),
    ))

    private class Scenario(
        val pending: PendingQueueUiState, val balance: HomeBalanceState, val upcoming: HomeUpcomingState,
        val projection: HomeProjectionState, val allowance: HomeAllowanceState, val cashFlow: HomeCashFlowState,
    )

    private fun full() = Scenario(pendings(3), balance, events, exceed, allowance, HomeCashFlowState.Ready(flow))
    private fun allClear() = Scenario(PendingQueueUiState(loading = false), balance, HomeUpcomingState.Ready(emptyList()),
        HomeProjectionState.Ready(emptyList()), allowance, HomeCashFlowState.Ready(flow.copy(categoriesWithoutEstimate = 0, provisional = false)))
    private fun partialFailure() = Scenario(pendings(3), HomeBalanceState.Failed, events, HomeProjectionState.Failed,
        HomeAllowanceState.Failed, HomeCashFlowState.Failed)
    private fun loading() = Scenario(PendingQueueUiState(), HomeBalanceState.Loading, HomeUpcomingState.Loading,
        HomeProjectionState.Loading, HomeAllowanceState.Loading, HomeCashFlowState.Loading)
    private fun shortfall() = full().let {
        Scenario(it.pending, it.balance, it.upcoming, it.projection, it.allowance, HomeCashFlowState.Ready(flow.copy(receivedCents = 10_000, toReceiveCents = 0)))
    }

    private var theme by mutableStateOf(ThemePreference.Light)
    private var fontScale by mutableFloatStateOf(1f)
    private var scenario by mutableStateOf(full())
    private var scenarioId by mutableStateOf("")

    private fun capture(folder: File, name: String) {
        compose.waitForIdle()
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Rola do topo ao rodapé gravando uma imagem por tela; [maxSteps] cobre a fonte a 200%. */
    private fun scrollAndShoot(folder: File, name: String, maxSteps: Int = 12) {
        var step = 1
        while (true) {
            capture(folder, "$name-$step")
            val atEnd = runCatching { compose.onNodeWithText(footer).assertIsDisplayed() }.isSuccess
            if (atEnd || step >= maxSteps) {
                check(atEnd) { "$name: o rodapé não foi alcançado em $maxSteps telas" }
                break
            }
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.30f) }
            step++
        }
    }

    /**
     * Matriz final (accord-qjlo): quatro cenários (cheio, tudo em dia, falha parcial, carga) em claro e escuro, nas fontes 100%,
     * 130%, 150% e 200% (200% é só informativo; o critério vigente é retrato e fonte padrão, accord-rub9). O Início inteiro é
     * rolado até o rodapé em cada combinação. Com a escala de animação do sistema em 0 (o AVD rápido), `Accord.motion.reduced`
     * é verdadeiro e o esqueleto fica parado; isso não valida movimento reduzido em aparelho.
     */
    @Test fun matrizFinal() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(theme) { Surface(color = MaterialTheme.colorScheme.background) {
                    key(scenarioId) {
                        HomeScreen(PaddingValues(), scenario.pending, {}, {}, allowance = scenario.allowance, upcoming = scenario.upcoming,
                            projection = scenario.projection, cashFlow = scenario.cashFlow, balance = scenario.balance)
                    }
                } }
            }
        }
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/qjlo").apply { mkdirs() }
        val scenarios = mapOf<String, () -> Scenario>(
            "cheio" to ::full, "falta" to ::shortfall, "emdia" to ::allClear, "falha" to ::partialFailure, "carga" to ::loading,
        )
        for ((themeName, choice) in listOf("claro" to ThemePreference.Light, "escuro" to ThemePreference.Dark)) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            for ((name, build) in scenarios) {
                compose.runOnIdle { theme = choice; fontScale = scale; scenario = build(); scenarioId = "$name-$themeName-$scale" }
                val id = "$name-$themeName-f${(scale * 100).toInt()}"
                if (name == "carga") capture(folder, "$id-1")
                else scrollAndShoot(folder, id)
            }
        }
    }

    /**
     * O Início real (AccordApp, banco do aparelho de teste, barra e botão de registro) em claro e escuro e nas quatro fontes: o último
     * bloco e o rodapé precisam ficar acima da barra e do botão ao rolar até o fim.
     */
    @Test fun inicioRealComBarraEBotao() {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(theme) { AccordApp(app.container) }
            }
        }
        val folder = File(app.getExternalFilesDir(null), "screens/qjlo").apply { mkdirs() }
        for ((themeName, choice) in listOf("claro" to ThemePreference.Light, "escuro" to ThemePreference.Dark)) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            compose.runOnIdle { theme = choice; fontScale = scale }
            compose.onNodeWithTag("tab-Home").performClick()
            val id = "app-$themeName-f${(scale * 100).toInt()}"
            capture(folder, "$id-1")
            var swipes = 0
            while (swipes < 14 && runCatching { compose.onNodeWithText(footer).assertIsDisplayed() }.isFailure) {
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.30f) }
                swipes++
            }
            capture(folder, "$id-fim")
            val footerBottom = compose.onNodeWithText(footer).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.bottom
            val navTop = compose.onNodeWithTag("root-navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            check(footerBottom <= navTop) { "$id: o rodapé ($footerBottom) fica sob a barra inferior ($navTop)" }
        }
    }
}
