@file:Suppress("DEPRECATION") // Mesma API de teste das demais suítes; migração v2 é independente.

package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import android.view.WindowManager
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        container.appLock.setScreenshotsAllowed(false)
        container.appLock.setEnabled(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
        container.appLock.setEnabled(false)
        container.appLock.setScreenshotsAllowed(false)
    }

    @Test fun permitirPrintsLiberaAJanelaEVoltarAProtegeNaHora() {
        fun secure() = run {
            var secure = false
            scenario!!.onActivity { secure = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
            secure
        }
        assertTrue(secure())
        container.appLock.setScreenshotsAllowed(true)
        compose.waitUntil(5_000) { !secure() }
        scenario!!.recreate()
        assertTrue(!secure())
        container.appLock.setScreenshotsAllowed(false)
        compose.waitUntil(5_000) { secure() }
    }

    @Test fun bloqueadoNaoExpoeNavegacaoNaArvoreAcessivel() {
        compose.onNodeWithText("Accord bloqueado").assertIsDisplayed()
        compose.onNodeWithText("Lançamentos").assertDoesNotExist()
    }

    @Test fun bloquearTelaProtegeMesmoDepoisDeAutenticar() {
        // Simula SOMENTE o callback de sucesso; não comprova biometria/PIN do aparelho.
        compose.runOnIdle { container.appLock.authenticated() }
        compose.onNodeWithText("Accord bloqueado").assertDoesNotExist()
        compose.runOnIdle { container.appLock.onScreenOff() }
        compose.onNodeWithText("Accord bloqueado").assertIsDisplayed()
        compose.onNodeWithText("Lançamentos").assertDoesNotExist()
    }

    @Test fun recriarActivityNaoApagaBloqueio() {
        scenario!!.recreate()
        compose.onNodeWithText("Accord bloqueado").assertIsDisplayed()
    }

    @Test fun janelaProtegeCapturaMesmoDesbloqueadaEAposRecriacao() {
        fun secureWindow() {
            scenario!!.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
        secureWindow()
        compose.runOnIdle { container.appLock.authenticated() }
        secureWindow()
        scenario!!.recreate()
        secureWindow()
    }
}
