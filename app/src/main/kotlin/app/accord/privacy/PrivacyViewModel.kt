package app.accord.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.AppContainer
import app.accord.core.sync.DataExportScope
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.PrivacyAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

/** Relatório dos últimos sete dias do Chat neste aparelho: só texto com contagens (accord-8q5). */
data class UsageReportState(val busy: Boolean = false, val text: String? = null, val error: Boolean = false)

data class SpaceDeletionUi(val id: String, val status: String, val members: List<String>, val approvals: Set<String>)
data class PrivacyLifecycleState(
    val busy: Boolean = false, val available: Boolean = false, val error: Boolean = false,
    val wrongPassword: Boolean = false, val requests: List<SpaceDeletionUi> = emptyList(),
)

class PrivacyViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(PrivacyExportState())
    val state = mutableState.asStateFlow()
    private val usageState = MutableStateFlow(UsageReportState())
    val usage = usageState.asStateFlow()
    private val lifecycleState = MutableStateFlow(PrivacyLifecycleState())
    val lifecycle = lifecycleState.asStateFlow()
    val currentPerson get() = container.access.status.value.account?.accountId
    val hasSpace get() = container.access.status.value.space != null
    fun memberName(id: String): String? = container.memberNames()[id]

    init { refreshLifecycle() }

    fun refreshLifecycle() {
        if (lifecycleState.value.busy) return
        lifecycleState.value = lifecycleState.value.copy(busy = true, error = false, wrongPassword = false)
        viewModelScope.launch {
            try {
                when (val result = container.access.reconcilePrivacy()) {
                    is RemoteResult.Failed -> lifecycleState.value = lifecycleState.value.copy(busy = false, available = false, error = true)
                    is RemoteResult.Ok -> {
                        val data = Json.parseToJsonElement(result.value).jsonObject
                        val requests = (data["requests"] as? JsonArray).orEmpty().mapNotNull { element ->
                            val request = element as? JsonObject ?: return@mapNotNull null
                            fun strings(key: String) = (request[key] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
                            SpaceDeletionUi(request["requestId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                                request["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null, strings("members"), strings("approvals").toSet())
                        }
                        lifecycleState.value = PrivacyLifecycleState(available = true, requests = requests)
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { lifecycleState.value = lifecycleState.value.copy(busy = false, available = false, error = true) }
        }
    }

    fun deleteAccount(password: String) {
        if (lifecycleState.value.busy || password.isBlank()) return
        lifecycleState.value = lifecycleState.value.copy(busy = true, error = false, wrongPassword = false)
        viewModelScope.launch {
            val result = container.access.prepareAccountDeletion(password)
            lifecycleState.value = lifecycleState.value.copy(busy = false,
                wrongPassword = result is RemoteResult.Failed && result.failure == RemoteFailure.InvalidCredentials,
                error = result is RemoteResult.Failed && result.failure != RemoteFailure.InvalidCredentials)
        }
    }

    fun spaceAction(action: PrivacyAction, id: String, password: String? = null) {
        if (lifecycleState.value.busy) return
        lifecycleState.value = lifecycleState.value.copy(busy = true, error = false, wrongPassword = false)
        viewModelScope.launch {
            val result = container.access.privacySpaceAction(action, id, password)
            lifecycleState.value = lifecycleState.value.copy(busy = false,
                wrongPassword = result is RemoteResult.Failed && result.failure == RemoteFailure.InvalidCredentials,
                error = result is RemoteResult.Failed && result.failure != RemoteFailure.InvalidCredentials)
            if (result is RemoteResult.Ok) refreshLifecycle()
        }
    }

    fun prepare(scope: DataExportScope) {
        if (mutableState.value.busy) return
        mutableState.value = PrivacyExportState(busy = true)
        viewModelScope.launch {
            try {
                val account = container.access.status.value.account ?: error("NoAccount")
                val local = container.access.runtime.value?.dataExport?.snapshot(scope) ?: JsonObject(emptyMap())
                val archived = if (scope == DataExportScope.Personal) JsonArray(container.access.status.value.archivedSpaces.mapNotNull { space ->
                    container.access.archivedChatData(space.spaceId)?.let { text -> buildJsonObject {
                        put("spaceId", space.spaceId); put("name", space.name); put("history", Json.parseToJsonElement(text))
                    } }
                }) else JsonArray(emptyList())
                val profile = buildJsonObject {
                    put("accountId", account.accountId)
                    if (scope == DataExportScope.Personal) {
                        put("email", account.email)
                        put("theme", container.theme.preference.value.name)
                        put("palette", container.theme.palette.value.name)
                        put("corners", container.theme.corners.value.name)
                        put("entriesDateCriterion", container.theme.entriesDateCriterion.value.name)
                        put("appLockEnabled", container.appLock.state.value.enabled)
                        put("intentionDrafts", container.intentionDraftExport())
                    }
                }
                val service = container.access.exportData(scope)
                val remote = (service as? RemoteResult.Ok)?.value?.let { Json.parseToJsonElement(it).jsonObject }
                // Revalidar identidade após a chamada evita oferecer arquivo de outra sessão.
                check(container.access.status.value.account?.accountId == account.accountId)
                val bundle = DataExportBundle(scope, System.currentTimeMillis(), profile,
                    if (scope == DataExportScope.Personal) JsonObject(local + ("archivedSpaces" to archived)) else local, remote)
                mutableState.value = PrivacyExportState(bundle = bundle, confirmLocalOnly = remote == null)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.value = PrivacyExportState(error = true) }
        }
    }

    fun openUsageReport(deviceLabel: String) {
        if (usageState.value.busy) return
        usageState.value = UsageReportState(busy = true)
        viewModelScope.launch {
            try {
                val text = container.chatUsageReport(deviceLabel)
                usageState.value = if (text == null) UsageReportState(error = true) else UsageReportState(text = text)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { usageState.value = UsageReportState(error = true) }
        }
    }

    fun closeUsageReport() { usageState.value = UsageReportState() }

    fun confirmLocal() { mutableState.value = mutableState.value.copy(confirmLocalOnly = false) }
    fun cancel() { if (!mutableState.value.busy) mutableState.value = PrivacyExportState() }

    fun save(openOutput: () -> OutputStream?) {
        val bundle = mutableState.value.bundle ?: run {
            mutableState.value = PrivacyExportState(error = true)
            return
        }
        if (container.access.status.value.account?.accountId != bundle.profile["accountId"]?.jsonPrimitive?.contentOrNull) {
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
