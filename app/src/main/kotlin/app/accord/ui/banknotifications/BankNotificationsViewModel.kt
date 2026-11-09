package app.accord.ui.banknotifications

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.banknotifications.BankNotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Um app instalado com ícone de abertura; só o nome visível e o pacote são lidos. */
data class InstalledApp(val packageName: String, val label: String)

data class BankNotificationsUiState(
    val loading: Boolean = true,
    val accessGranted: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
) {
    /** Escolhidos primeiro, depois os demais por nome; a busca ignora maiúsculas. */
    val shown: List<InstalledApp>
        get() = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<InstalledApp> { it.packageName in selected })
}

/**
 * Escolha dos apps cujas notificações o Accord lê (ADR 0069). Nada vem marcado; o acesso às notificações é concedido nos
 * ajustes do Android, nunca por aqui. A lista de apps vem do sistema e não é guardada.
 */
class BankNotificationsViewModel(
    private val settings: BankNotificationSettings,
    private val loadApps: suspend () -> List<InstalledApp>,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val access = MutableStateFlow(settings.listenerAccessGranted())
    private val query = MutableStateFlow("")

    val state: StateFlow<BankNotificationsUiState> = combine(apps, access, settings.packages, query) { list, granted, selected, q ->
        BankNotificationsUiState(loading = list == null, accessGranted = granted, apps = list.orEmpty(), selected = selected, query = q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BankNotificationsUiState())

    init {
        viewModelScope.launch { apps.value = withContext(Dispatchers.IO) { runCatching { loadApps() }.getOrDefault(emptyList()) } }
    }

    /** Voltar dos ajustes do sistema traz a situação real do acesso. */
    fun refresh() = access.update { settings.listenerAccessGranted() }

    fun setQuery(value: String) = query.update { value }

    fun setAllowed(packageName: String, allowed: Boolean) = settings.setAllowed(packageName, allowed)

    fun clearAll() = settings.clear()

    companion object {
        /** Apps com ícone de abertura (a consulta é declarada no manifesto), sem o próprio Accord. */
        fun installedApps(context: Context): List<InstalledApp> {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
            return pm.queryIntentActivities(launcher, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }
}
