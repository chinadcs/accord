package app.accord.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.AppContainer
import app.accord.core.model.DownloadPolicy
import app.accord.core.model.ModelInstall
import app.accord.core.model.ModelManifest
import app.accord.core.model.ModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelDialog { None, MeteredChoice, ConfirmRemove, ConfirmRemoveAndInstall }

data class ModelUiState(
    val status: ModelStatus,
    val engineAvailable: Boolean,
    val dialog: ModelDialog = ModelDialog.None,
)

/**
 * Tela 25. O estado vem do gerenciador do modelo, que continua trabalhando enquanto a tela está fechada:
 * sair e voltar mostra o estado real sem reiniciar o download (S3 §11).
 */
class ModelViewModel(private val container: AppContainer) : ViewModel() {
    private val manager = container.models.manager
    private val dialog = MutableStateFlow(ModelDialog.None)

    val state: StateFlow<ModelUiState> = combine(manager.status, dialog) { status, dialog ->
        ModelUiState(status, container.models.engineAvailable, dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelUiState(manager.status.value, container.models.engineAvailable))

    init {
        viewModelScope.launch { manager.refreshCatalog() }
    }

    fun retry() {
        viewModelScope.launch { manager.refreshCatalog() }
    }

    /** Fora do Wi-Fi, pergunta antes: baixar agora ou esperar a rede não tarifada (decisão de 23/10/2026). */
    fun download() {
        if (container.models.isMetered()) dialog.value = ModelDialog.MeteredChoice else start(DownloadPolicy.Now)
    }

    fun chooseDownloadNow() {
        dialog.value = ModelDialog.None
        start(DownloadPolicy.Now)
    }

    fun chooseWaitForWifi() {
        dialog.value = ModelDialog.None
        start(DownloadPolicy.WifiOnly)
    }

    private fun start(policy: DownloadPolicy) {
        viewModelScope.launch { manager.download(policy) }
    }

    fun cancel() {
        viewModelScope.launch { manager.cancel() }
    }

    fun askRemove() = dialog.update { ModelDialog.ConfirmRemove }

    fun askRemoveAndInstall() = dialog.update { ModelDialog.ConfirmRemoveAndInstall }

    fun dismissDialog() = dialog.update { ModelDialog.None }

    fun confirmRemove() {
        dialog.value = ModelDialog.None
        viewModelScope.launch { manager.remove() }
    }

    fun confirmRemoveAndInstall() {
        dialog.value = ModelDialog.None
        val policy = if (container.models.isMetered()) DownloadPolicy.WifiOnly else DownloadPolicy.Now
        viewModelScope.launch { manager.removeCurrentAndInstall(policy) }
    }

    /** Tamanho real do download que a pessoa está prestes a aceitar, para o aviso de dados móveis. */
    fun pendingDownloadBytes(): Long? = when (val install = manager.status.value.install) {
        is ModelInstall.Available -> install.manifest.downloadBytes
        is ModelInstall.Interrupted -> install.manifest.downloadBytes - install.receivedBytes
        is ModelInstall.Paused -> install.manifest.downloadBytes - install.receivedBytes
        else -> null
    }

    fun manifest(): ModelManifest? = when (val install = manager.status.value.install) {
        is ModelInstall.Available -> install.manifest
        else -> null
    }
}
