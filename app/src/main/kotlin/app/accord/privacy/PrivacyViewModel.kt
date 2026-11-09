package app.accord.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.AppContainer
import app.accord.core.sync.DataExportScope
import app.accord.core.sync.RemoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream

data class PrivacyExportState(
    val busy: Boolean = false,
    val bundle: DataExportBundle? = null,
    val confirmLocalOnly: Boolean = false,
    val error: Boolean = false,
    val saved: Boolean = false,
)

class PrivacyViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(PrivacyExportState())
    val state = mutableState.asStateFlow()

    fun prepare(scope: DataExportScope) {
        if (mutableState.value.busy) return
        mutableState.value = PrivacyExportState(busy = true)
        viewModelScope.launch {
            try {
                val account = container.access.status.value.account ?: error("NoAccount")
                val local = container.access.runtime.value?.dataExport?.snapshot(scope) ?: JsonObject(emptyMap())
                val profile = buildJsonObject {
                    put("accountId", account.accountId)
                    if (scope == DataExportScope.Personal) {
                        put("email", account.email)
                        put("theme", container.theme.preference.value.name)
                        put("palette", container.theme.palette.value.name)
                        put("corners", container.theme.corners.value.name)
                        put("entriesDateCriterion", container.theme.entriesDateCriterion.value.name)
                        put("appLockEnabled", container.appLock.state.value.enabled)
                    }
                }
                val service = container.access.exportData(scope)
                val remote = (service as? RemoteResult.Ok)?.value?.let { Json.parseToJsonElement(it).jsonObject }
                // Revalidar identidade após a chamada evita oferecer arquivo de outra sessão.
                check(container.access.status.value.account?.accountId == account.accountId)
                val bundle = DataExportBundle(scope, System.currentTimeMillis(), profile, local, remote)
                mutableState.value = PrivacyExportState(bundle = bundle, confirmLocalOnly = remote == null)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.value = PrivacyExportState(error = true) }
        }
    }

    fun confirmLocal() { mutableState.value = mutableState.value.copy(confirmLocalOnly = false) }
    fun cancel() { if (!mutableState.value.busy) mutableState.value = PrivacyExportState() }

    fun save(openOutput: () -> OutputStream?) {
        val bundle = mutableState.value.bundle ?: run {
            mutableState.value = PrivacyExportState(error = true)
            return
        }
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = openOutput() ?: error("NoOutput")
                    output.use { DataArchive.write(it, bundle) }
                }
                mutableState.value = PrivacyExportState(saved = true)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.value = PrivacyExportState(error = true) }
        }
    }
}
