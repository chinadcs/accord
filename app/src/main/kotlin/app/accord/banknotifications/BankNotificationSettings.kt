package app.accord.banknotifications

import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Apps cujas notificações o Accord pode ler neste aparelho (ADR 0069). Nenhum vem marcado: a lista só cresce por escolha
 * explícita da pessoa, app a app. É preferência do aparelho, como o tema; não sincroniza e não é dado financeiro.
 */
class BankNotificationSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _packages = MutableStateFlow(read())

    /** Pacotes escolhidos; vazio por padrão. */
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    fun isAllowed(packageName: String): Boolean = packageName in _packages.value

    fun setAllowed(packageName: String, allowed: Boolean) {
        val next = if (allowed) _packages.value + packageName else _packages.value - packageName
        prefs.edit().putStringSet(KEY_PACKAGES, next).apply()
        _packages.value = next
    }

    /** Desliga todos os apps de uma vez (a pessoa retira a escolha, sem precisar revogar o acesso do sistema). */
    fun clear() {
        prefs.edit().remove(KEY_PACKAGES).apply()
        _packages.value = emptySet()
    }

    /** O acesso às notificações é uma permissão especial do sistema, concedida pela pessoa nos ajustes do Android. */
    fun listenerAccessGranted(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Para o Android reconectar o serviço depois de a pessoa mudar a lista ou conceder o acesso. */
    fun listenerComponent() = ComponentName(context, BankNotificationListener::class.java)

    private fun read(): Set<String> = prefs.getStringSet(KEY_PACKAGES, null).orEmpty().toSet()

    private companion object {
        const val FILE = "accord_bank_notifications"
        const val KEY_PACKAGES = "packages"
    }
}
