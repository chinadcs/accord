package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import app.accord.ui.components.RootNavigationBar
import app.accord.ui.home.HomeScreen
import app.accord.ui.nav.Root
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import app.accord.ui.AccordApp
import app.accord.ui.theme.ThemePreference
import java.io.File
import android.graphics.Bitmap

class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cargaNaoDizQueNaoHaPendencias() {
        show(PendingQueueUiState())
        compose.onNodeWithText("Consultando pendências…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nenhuma pendência neste aparelho").assertDoesNotExist()
    }

    @Test fun falhaOfereceRepetirERevisar() {
        var retries = 0
        var reviews = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false, failed = true), { retries++ }, {}, {}, { reviews++ })
        } }
        compose.onNodeWithText("Não foi possível consultar as pendências").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        compose.onNodeWithText("Abrir Revisar").performScrollTo().performClick()
        assertEquals(1, retries)
        assertEquals(1, reviews)
        compose.onNodeWithText("Nenhuma pendência neste aparelho").assertDoesNotExist()
    }

    @Test fun filaVaziaTemTextoLocal() {
        show(PendingQueueUiState(loading = false))
        compose.onNodeWithText("Nenhuma pendência neste aparelho").performScrollTo().assertIsDisplayed()
    }

    @Test fun indicadoresDeInicioEMaisTemDescricaoAcessivel() {
        compose.setContent { AccordTheme { RootNavigationBar(Root.Home, {}, hasPending = true) } }
        listOf("Home", "More").forEach {
            compose.onNodeWithTag("tab-$it").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, "Há pendências neste aparelho"))
        }
        compose.onNodeWithTag("tab-Chat").assertContentDescriptionEquals("Chat")
    }

    @Test fun inicioMaisERevisarNosDoisTemas() {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val theme = mutableStateOf(ThemePreference.Light)
        compose.setContent { AccordTheme(theme.value) { AccordApp(app.container) } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            theme.value = choice
            compose.onNodeWithTag("tab-Home").performClick()
            compose.onNodeWithText("Conversar no Chat").assertIsDisplayed()
            capture("inicio-${choice.name}")
            compose.onNodeWithTag("tab-More").performClick()
            compose.onNode(hasText("Revisar") and hasClickAction()).assertIsDisplayed()
            capture("mais-${choice.name}")
            compose.onNode(hasText("Revisar") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Voltar").assertIsDisplayed()
            capture("revisar-${choice.name}")
            compose.onNodeWithContentDescription("Voltar").performClick()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val folder = File(app.getExternalFilesDir(null), "screens/b6og").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun show(state: PendingQueueUiState) {
        compose.setContent { AccordTheme { HomeScreen(PaddingValues(), state, {}, {}, {}, {}) } }
    }
}
