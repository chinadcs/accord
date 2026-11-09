package app.accord

import androidx.compose.ui.test.onNodeWithContentDescription

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.onNodeWithContentDescription("Chat").performClick()
        compose.onNodeWithText("Mensagem do Chat").performClick().performTextInput("mercado 35")
        compose.waitUntil(10_000) { keyboardVisible }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(directory, "charcoal-chat-escuro-fonte200-teclado.png").outputStream().use {
            // Captura só o app; não lê nem apaga a área de transferência pessoal do teclado.
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Mensagem do Chat").assertIsDisplayed()
        compose.onNodeWithText("Enviar").assertIsDisplayed()
        val send = compose.onNodeWithText("Enviar").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("root-navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Enviar deve manter 48 dp completos (atual: ${send.height / pixelsPerDp})", send.height >= 48f * pixelsPerDp - 1f)
        assertTrue("A navegação não pode cobrir o envio", send.bottom <= navigation.top)
    }
}
