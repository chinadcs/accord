package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.ui.theme.AccordTheme
import app.accord.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReleaseUpdatesUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("0.1.1", 101,
        "https://github.com/chinadcs/accord/releases/download/v0.1.1/accord.apk", true, notes = "• Algo novo")

    private class Calls {
        val downloads = mutableListOf<AppRelease>()
        var cancels = 0
        var installs = 0
        var settings = 0
        val actions = UpdateActions(
            download = { downloads += it }, cancel = { cancels++ }, install = { installs++ }, openSettings = { settings++; true },
        )
    }

    @Test fun manualCheckAndDownloadAreExplicit() {
        var checks = 0
        val calls = Calls()
        compose.setContent { AccordTheme {
            UpdatePanel(UpdateState(available = release), InstallState.Idle, { checks++ }, calls.actions)
        } }
        compose.onNodeWithText("Verificar atualizações").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
        compose.onNodeWithText("Accord 0.1.1 · pré-release de teste").assertIsDisplayed()
        compose.runOnIdle { assertTrue(calls.downloads.isEmpty()) }
        compose.onNodeWithText("Baixar atualização").performClick()
        compose.runOnIdle { assertEquals(listOf(release), calls.downloads) }
    }

    @Test fun downloadShowsProgressAndCanBeCancelled() {
        val calls = Calls()
        compose.setContent { AccordTheme {
            UpdatePanel(UpdateState(available = release), InstallState.Downloading(1_000_000, 2_000_000), {}, calls.actions)
        } }
        compose.onNodeWithText("Baixando a atualização…").assertIsDisplayed()
        compose.onNodeWithText("Cancelar download").performClick()
        compose.runOnIdle { assertEquals(1, calls.cancels) }
    }

    @Test fun readyOffersInstallationAndPermissionLeadsToTheSettings() {
        val calls = Calls()
        val install = androidx.compose.runtime.mutableStateOf<InstallState>(InstallState.Ready)
        compose.setContent { AccordTheme { UpdatePanel(UpdateState(available = release), install.value, {}, calls.actions) } }
        compose.onNodeWithText("Instalar agora").performClick()
        compose.runOnIdle { assertEquals(1, calls.installs); install.value = InstallState.NeedsPermission }
        compose.onNodeWithText("Abrir ajustes do Android").performClick()
        compose.runOnIdle { assertEquals(1, calls.settings) }
    }

    @Test fun failureOffersANewAttempt() {
        val calls = Calls()
        compose.setContent { AccordTheme {
            UpdatePanel(UpdateState(available = release), InstallState.Failed(FailureReason.Integrity), {}, calls.actions)
        } }
        compose.onNodeWithText("O arquivo baixado não confere", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Baixar atualização").performClick()
        compose.runOnIdle { assertEquals(1, calls.downloads.size) }
    }

    @Test fun noticeCanBeDismissedAndStartsNothingByItself() {
        val calls = Calls()
        var dismissals = 0
        compose.setContent { AccordTheme {
            UpdateNotice(UpdateState(available = release, showNotice = true), InstallState.Idle, calls.actions) { dismissals++ }
        } }
        compose.runOnIdle { assertTrue(calls.downloads.isEmpty()) }
        compose.onNodeWithText("Agora não").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertTrue(calls.downloads.isEmpty()) }
    }

    @Test fun noticeDownloadsInPlace() {
        val calls = Calls()
        compose.setContent { AccordTheme {
            UpdateNotice(UpdateState(available = release, showNotice = true), InstallState.Idle, calls.actions) {}
        } }
        compose.onNodeWithText("Baixar atualização").performClick()
        compose.runOnIdle { assertEquals(1, calls.downloads.size) }
    }

    @Test fun checkingDisablesDuplicateManualActionAndFailurePreservesUse() {
        compose.setContent { AccordTheme { UpdatePanel(UpdateState(checking = true), InstallState.Idle, {}, Calls().actions) } }
        compose.onNodeWithText("Verificar atualizações").assertIsNotEnabled()
        compose.onNodeWithText("Verificando versões no GitHub…").assertIsDisplayed()
    }

    @Test fun whatsNewShowsChangelogAndCloses() {
        var closed = 0
        compose.setContent { AccordTheme { WhatsNewDialog(release) { closed++ } } }
        compose.onNodeWithText("Accord 0.1.1 instalado").assertIsDisplayed()
        compose.onNodeWithText("• Algo novo").assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
