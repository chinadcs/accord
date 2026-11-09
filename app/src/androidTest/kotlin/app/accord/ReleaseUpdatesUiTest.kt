package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.ui.theme.AccordTheme
import app.accord.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider

class ReleaseUpdatesUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("0.1.1", 101,
        "https://github.com/chinadcs/accord/releases/download/v0.1.1/accord.apk", true)

    @Test fun manualCheckAndBrowserFailureAreRecoverable() {
        var checks = 0
        compose.setContent { AccordTheme {
            UpdatePanel(UpdateState(available = release), { checks++ }, { false })
        } }
        compose.onNodeWithText("Verificar atualizações").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
        compose.onNodeWithText("Accord 0.1.1 · pré-release de teste").assertIsDisplayed()
        compose.onNodeWithText("Abrir download").performClick()
        compose.onNodeWithText("Não foi possível abrir o navegador.", substring = true).assertIsDisplayed()
    }

    @Test fun noticeRequiresExplicitDownloadAndCanBeDismissed() {
        var downloads = 0
        var dismissals = 0
        compose.setContent { AccordTheme {
            UpdateNotice(UpdateState(available = release, showNotice = true), { dismissals++ }, { downloads++; true })
        } }
        compose.runOnIdle { assertEquals(0, downloads) }
        compose.onNodeWithText("Agora não").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, downloads) }
        compose.onNodeWithText("Abrir download").performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(2, dismissals) }
    }

    @Test fun checkingDisablesDuplicateManualActionAndFailurePreservesUse() {
        compose.setContent { AccordTheme { UpdatePanel(UpdateState(checking = true), {}, { false }) } }
        compose.onNodeWithText("Verificar atualizações").assertIsNotEnabled()
        compose.onNodeWithText("Verificando versões no GitHub…").assertIsDisplayed()
    }

    @Test fun downloadIntentIsExplicitlyRequestedAndRejectsOtherOrigins() {
        var launched: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        assertFalse(openReleaseDownload(context, release.copy(downloadUrl = "https://example.invalid/accord.apk")))
        assertNull(launched)
        assertTrue(openReleaseDownload(context, release))
        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals(release.downloadUrl, launched?.dataString)
    }
}
