package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.core.model.FailureCause
import app.accord.core.model.ModelFile
import app.accord.core.model.ModelInstall
import app.accord.core.model.ModelManifest
import app.accord.core.model.ModelStatus
import app.accord.ui.alerts.AlertPreferencesScreen
import app.accord.ui.alerts.AlertPreferencesUiState
import app.accord.ui.model.ModelActions
import app.accord.ui.model.ModelScreen
import app.accord.ui.model.ModelUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test

/** Estados sintéticos difíceis de reproduzir por rede/serviço, identificados como tais no manifest. */
class UiAuditStateCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val theme = when (InstrumentationRegistry.getArguments().getString("auditTheme")) {
        "dark" -> ThemePreference.Dark
        else -> ThemePreference.Light
    }

    private val manifest = ModelManifest(
        version = "teste-1", engine = "motor de teste", engineVersion = "1",
        files = listOf(ModelFile("modelo.bin", 4_000, "0".repeat(64))), installBytes = 16_000,
    )

    @Test fun modeloDisponivelBaixandoEFalha() {
        val state = mutableStateOf(ModelUiState(ModelStatus(ModelInstall.Available(manifest), null), engineAvailable = false))
        compose.setContent {
            AccordTheme(theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ModelScreen(
                        state.value, PaddingValues(), ModelActions({}, {}, {}),
                        onDownload = {}, onCancel = {}, onRetry = {}, onAskRemove = {},
                        onAskRemoveAndInstall = {}, onDismissDialog = {}, onConfirmRemove = {},
                        onConfirmRemoveAndInstall = {}, onDownloadNow = {}, onWaitForWifi = {}, pendingBytes = 4_000,
                    )
                }
            }
        }
        UiAuditSyntheticEvidence.capture(compose, "25_modelo_disponivel", theme)
        state.value = ModelUiState(ModelStatus(ModelInstall.Downloading(manifest, 2_000, 4_000, false), null), false)
        UiAuditSyntheticEvidence.capture(compose, "25_modelo_baixando", theme)
        state.value = ModelUiState(ModelStatus(ModelInstall.Failed(manifest, FailureCause.Storage), null), false)
        UiAuditSyntheticEvidence.capture(compose, "25_modelo_falha", theme)
        state.value = ModelUiState(ModelStatus(ModelInstall.Interrupted(manifest, 2_000, 4_000, true), null), false)
        UiAuditSyntheticEvidence.capture(compose, "25_modelo_interrompido", theme)
    }

    @Test fun preferenciasSemPermissaoECanalBloqueado() {
        val state = mutableStateOf(AlertPreferencesUiState(
            loading = false, saved = AlertPreference.Enabled, access = NotificationAccess.PermissionDenied,
        ))
        compose.setContent {
            AccordTheme(theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AlertPreferencesScreen(state.value, PaddingValues(), {}, {}, {}, {})
                }
            }
        }
        UiAuditSyntheticEvidence.capture(compose, "27_permissao_negada", theme)
        state.value = state.value.copy(access = NotificationAccess.ChannelDisabled)
        UiAuditSyntheticEvidence.capture(compose, "27_canal_desativado", theme)
    }
}
