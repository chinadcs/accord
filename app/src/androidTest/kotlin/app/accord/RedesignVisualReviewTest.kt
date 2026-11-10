package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.access.AccessPanel
import app.accord.ui.access.AccessScreen
import app.accord.ui.access.AccessUi
import app.accord.ui.more.AppearanceScreen
import app.accord.ui.more.MoreActions
import app.accord.ui.more.MoreScreen
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.CornerStyle
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Capturas sintéticas para inspeção visual manual. Não substituem a revisão nos dois celulares. */
class RedesignVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private data class Spec(val theme: ThemePreference, val fontScale: Float, val content: @Composable () -> Unit)
    private var spec by mutableStateOf<Spec?>(null)
    private var started = false

    private fun capture(name: String, theme: ThemePreference, fontScale: Float = 1f, scrollTo: String? = null, content: @Composable () -> Unit) {
        spec = Spec(theme, fontScale, content)
        if (!started) {
            started = true
            compose.setContent {
                spec?.let { selected ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, selected.fontScale)) {
                        AccordTheme(selected.theme) {
                            Surface(color = MaterialTheme.colorScheme.background) { selected.content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (scrollTo != null) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(scrollTo, substring = true))
            compose.waitForIdle()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private val moreActions = MoreActions()

    private val more = @Composable { MoreScreen(PaddingValues(), moreActions) }
    private val login = @Composable { access(AccessUi()) }
    private val create = @Composable { access(AccessUi(panel = AccessPanel.Create)) }
    private val appearance = @Composable {
        AppearanceScreen(PaddingValues(), ThemePreference.System, CornerStyle.Rounded, {}, {}, {})
    }

    @Composable
    private fun access(ui: AccessUi) {
        AccessScreen(
            ui, PaddingValues(),
            onEmail = {}, onPassword = {}, onToggleShow = {}, onSignIn = {}, onCancel = {},
            onOpenCreate = {}, onOpenHelp = {}, onClosePanel = {}, onCreateEmail = {},
            onCreatePassword = {}, onCreateConfirmation = {}, onToggleShowPasswords = {}, onSignUp = {},
        )
    }

    @Test fun mais() {
        capture("redesign-mais-claro", ThemePreference.Light, content = more)
        capture("redesign-mais-escuro", ThemePreference.Dark, content = more)
        capture("redesign-mais-texto-130", ThemePreference.Light, 1.3f, content = more)
        capture("redesign-mais-final-escuro", ThemePreference.Dark, scrollTo = "Aparência", content = more)
    }

    @Test fun acesso() {
        capture("redesign-acesso-claro", ThemePreference.Light, content = login)
        capture("redesign-acesso-escuro", ThemePreference.Dark, content = login)
        capture("redesign-acesso-texto-130", ThemePreference.Light, 1.3f, content = login)
        capture("redesign-criar-acesso-130", ThemePreference.Light, 1.3f, content = create)
    }

    @Test fun aparencia() {
        capture("redesign-aparencia-claro", ThemePreference.Light, content = appearance)
        capture("redesign-aparencia-escuro", ThemePreference.Dark, content = appearance)
        capture("redesign-aparencia-texto-130", ThemePreference.Light, 1.3f, content = appearance)
    }
}
