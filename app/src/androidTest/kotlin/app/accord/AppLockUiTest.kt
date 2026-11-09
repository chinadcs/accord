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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        container.appLock.setEnabled(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
        container.appLock.setEnabled(false)
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
}
