@file:Suppress("DEPRECATION")

package app.accord

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.security.AppLockScreen
import app.accord.ui.theme.AccordTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Autenticador simulado: verifica o disparo da UI, não biometria/PIN do Android. */
@RunWith(AndroidJUnit4::class)
class AppLockAutoPromptTest {
    @get:Rule val compose = createComposeRule()

    @Test fun abreAutomaticamenteECancelamentoPermiteRepetirSemLoop() {
        var attempts = 0
        var unlocked = 0
        lateinit var result: (Boolean) -> Unit
        compose.setContent {
            AccordTheme {
                AppLockScreen(true, { attempts++; result = it }, { unlocked++ }, {}, {})
            }
        }
        compose.runOnIdle {
            assertEquals(1, attempts)
            result(false)
        }
        compose.runOnIdle {
            assertEquals(1, attempts)
            assertEquals(0, unlocked)
        }
        compose.onNodeWithText("Desbloquear").performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            result(true)
        }
        compose.runOnIdle { assertEquals(1, unlocked) }
    }

    @Test fun novaEntradaAbreOutraTentativaAutomatica() {
        val visible = mutableStateOf(true)
        var attempts = 0
        compose.setContent {
            AccordTheme {
                if (visible.value) AppLockScreen(true, { attempts++ }, {}, {}, {})
            }
        }
        compose.runOnIdle {
            assertEquals(1, attempts)
            visible.value = false
        }
        compose.runOnIdle { visible.value = true }
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun semCredencialNaoTentaEConfigurarDisparaAutomaticamente() {
        val configured = mutableStateOf(false)
        var attempts = 0
        compose.setContent {
            AccordTheme {
                AppLockScreen(configured.value, { attempts++ }, {}, {}, {})
            }
        }
        compose.runOnIdle {
            assertEquals(0, attempts)
            configured.value = true
        }
        compose.runOnIdle { assertEquals(1, attempts) }
    }

    @Test fun esperaRetomadaESoRetomarNaoRepetePromptCancelado() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        var attempts = 0
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AccordTheme {
                    AppLockScreen(true, { attempts++; it(false) }, {}, {}, {})
                }
            }
        }
        compose.runOnIdle {
            assertEquals(0, attempts)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle {
            assertEquals(1, attempts)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, attempts) }
    }
}
