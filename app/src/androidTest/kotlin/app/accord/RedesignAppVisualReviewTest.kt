package app.accord

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.AccordApp
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captura o AccordApp completo, incluindo a barra inferior real, para inspeção manual. */
class RedesignAppVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private var fontScale by mutableFloatStateOf(1f)

    @Test
    fun cincoRaizesComBarraInferiorNormalETexto130() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as TestAccordApplication).container
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(ThemePreference.Light) { AccordApp(container) }
            }
        }
        val roots = listOf(
            "Chat" to "chat",
            "Orçamento" to "orcamento",
            "Lançamentos" to "lancamentos",
            "Revisar" to "revisar",
            "Mais" to "mais",
        )
        for ((scale, suffix) in listOf(1f to "normal", 1.3f to "130")) {
            fontScale = scale
            compose.waitForIdle()
            for ((label, slug) in roots) {
                // O Chat é o ícone central sem rótulo visual; os demais destinos têm texto.
                if (label == "Chat") compose.onNodeWithContentDescription("Chat").performClick()
                else compose.onNode(hasText(label) and hasClickAction()).performClick()
                compose.waitForIdle()
                val directory = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
                File(directory, "redesign-app-$slug-$suffix.png").outputStream().use { stream ->
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }
    }
}
