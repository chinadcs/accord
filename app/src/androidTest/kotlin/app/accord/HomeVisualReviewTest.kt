package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * Capturas da Início completa (accord-u8zo, ADR 0078): cada cenário rola do topo ao rodapé e grava uma imagem por tela, em
 * `screens/u8zo`. Não afirma nada além de o rodapé ser alcançado; a inspeção é visual. Dados fictícios.
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

    private fun shoot(name: String, scenario: Scenario, theme: ThemePreference, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(theme) { Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(PaddingValues(), scenario.pending, {}, {}, allowance = scenario.allowance, upcoming = scenario.upcoming,
                        projection = scenario.projection, cashFlow = scenario.cashFlow, balance = scenario.balance)
                } }
            }
        }
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/u8zo").apply { mkdirs() }
        var step = 1
        while (true) {
            compose.waitForIdle()
            File(folder, "$name-$step.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val atEnd = runCatching { compose.onNodeWithText(footer).assertIsDisplayed() }.isSuccess
            if (atEnd || step >= 8) break
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.30f) }
            step++
        }
    }

    @Test fun cheioClaro() = shoot("cheio-claro", full(), ThemePreference.Light)
    @Test fun cheioEscuro() = shoot("cheio-escuro", full(), ThemePreference.Dark)
    @Test fun faltaClaro() = shoot("falta-claro", shortfall(), ThemePreference.Light)
    @Test fun tudoEmDiaClaro() = shoot("tudo-em-dia-claro", allClear(), ThemePreference.Light)
    @Test fun tudoEmDiaEscuro() = shoot("tudo-em-dia-escuro", allClear(), ThemePreference.Dark)
    @Test fun falhaParcialClaro() = shoot("falha-claro", partialFailure(), ThemePreference.Light)
    @Test fun falhaParcialEscuro() = shoot("falha-escuro", partialFailure(), ThemePreference.Dark)
    @Test fun cargaClaro() = shoot("carga-claro", loading(), ThemePreference.Light)
    @Test fun cargaEscuro() = shoot("carga-escuro", loading(), ThemePreference.Dark)
    @Test fun fonte150Claro() = shoot("fonte150-claro", full(), ThemePreference.Light, fontScale = 1.5f)
    @Test fun fonte150Escuro() = shoot("fonte150-escuro", shortfall(), ThemePreference.Dark, fontScale = 1.5f)
}
