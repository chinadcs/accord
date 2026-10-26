package app.accord

import android.graphics.Bitmap
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.AccordApp
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Activity real: o IME do sistema não é substituído pelo serviço de texto da regra Compose. */
class KeyboardLayoutUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    @Volatile private var keyboardVisible = false
    private var pixelsPerDp = 1f

    @After fun close() { scenario?.close() }

    @Test
    fun chatPreservaCampoEnvioEManualComTexto200ETecladoReal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAccordApplication).container.theme.set(ThemePreference.Dark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .clearPrimaryClip()
            val container = (activity.application as TestAccordApplication).container
            activity.setContent {
                val density = LocalDensity.current
                val imeBottom = WindowInsets.ime.getBottom(density)
                SideEffect { keyboardVisible = imeBottom > 0; pixelsPerDp = density.density }
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    AccordTheme(ThemePreference.Dark) { AccordApp(container) }
                }
            }
        }
        compose.onNodeWithText("Mensagem do Chat").performClick().performTextInput("mercado 35")
        compose.waitUntil(10_000) { keyboardVisible }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(directory, "charcoal-chat-escuro-fonte200-teclado.png").outputStream().use {
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Mensagem do Chat").assertIsDisplayed()
        compose.onNodeWithText("Enviar").assertIsDisplayed()
        compose.onNodeWithText("Registrar manualmente").assertIsDisplayed()
        val manual = compose.onNodeWithText("Registrar manualmente").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("root-navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("A ação manual deve manter 56 dp completos (atual: ${manual.height / pixelsPerDp})", manual.height >= 56f * pixelsPerDp - 1f)
        assertTrue("A navegação não pode cobrir a ação manual", manual.bottom <= navigation.top)
    }
}
