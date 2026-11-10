package app.accord

import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Movimento da Início em carga (accord-qjlo): o único movimento contínuo é o esqueleto. Com a escala de animação do sistema em 0
 * (`Accord.motion.reduced`), o quadro não muda com o tempo; com ela ligada, muda. O teste afirma o ramo que o aparelho está
 * usando; rodá-lo com a escala em 0 e em 1 (`settings put global animator_duration_scale`) cobre os dois. Isso não substitui a
 * validação de "Remover animações" num aparelho físico.
 */
class HomeMotionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun frame(): IntArray {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    @Test fun esqueletoFicaParadoComMovimentoReduzidoEAnimaSemEle() {
        val resolver = ApplicationProvider.getApplicationContext<AccordApplication>().contentResolver
        val reduced = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AccordTheme { Surface(color = MaterialTheme.colorScheme.background) {
                HomeScreen(PaddingValues(), PendingQueueUiState(), {}, {}, allowance = HomeAllowanceState.Loading, upcoming = HomeUpcomingState.Loading,
                    projection = HomeProjectionState.Loading, cashFlow = HomeCashFlowState.Loading, balance = HomeBalanceState.Loading)
            } }
        }
        compose.mainClock.advanceTimeBy(200)
        val first = frame()
        compose.mainClock.advanceTimeBy(450)
        val second = frame()
        if (reduced) assertArrayEquals("Com movimento reduzido nada pode animar na carga", first, second)
        else assertFalse("Sem movimento reduzido o esqueleto deveria pulsar", first.contentEquals(second))
    }
}
