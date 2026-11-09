package app.accord.security

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferência local; nunca persiste o estado desbloqueado nem credenciais. */
class AppLockStore(context: Context) {
    private val preferences = context.getSharedPreferences("accord_security", Context.MODE_PRIVATE)
    private val policy = AppLockPolicy(preferences.getBoolean("lock_enabled", true))
    private val mutableState = MutableStateFlow(policy.state)
    val state = mutableState.asStateFlow()

    fun onBackground() = update { background(SystemClock.elapsedRealtime()) }
    fun onForeground(deviceLocked: Boolean) = update { foreground(SystemClock.elapsedRealtime(), deviceLocked) }
    fun onScreenOff() = update { screenOff() }
    fun authenticated() = update { authenticated() }

    fun setEnabled(enabled: Boolean) {
        // A desativação só passa a valer depois de a preferência ter sido persistida.
        if (preferences.edit().putBoolean("lock_enabled", enabled).commit()) update { setEnabled(enabled) }
    }

    private fun update(action: AppLockPolicy.() -> Unit) {
        policy.action()
        mutableState.value = policy.state
    }
}
