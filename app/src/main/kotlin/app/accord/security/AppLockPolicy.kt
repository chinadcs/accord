package app.accord.security

/** Estado efêmero: uma nova instância sempre exige autenticação, salvo desativação explícita. */
data class AppLockState(val enabled: Boolean = true, val locked: Boolean = true)

class AppLockPolicy(enabled: Boolean = true) {
    var state = AppLockState(enabled, enabled)
        private set
    private var backgroundAt: Long? = null

    fun background(nowElapsed: Long) {
        if (backgroundAt == null) backgroundAt = nowElapsed
    }

    fun foreground(nowElapsed: Long, deviceLocked: Boolean) {
        val leftAt = backgroundAt
        if (state.enabled && (deviceLocked || (leftAt != null &&
                (nowElapsed < leftAt || nowElapsed - leftAt >= TIMEOUT_MILLIS)))) {
            state = state.copy(locked = true)
        }
        backgroundAt = null
    }

    fun screenOff() {
        if (state.enabled) state = state.copy(locked = true)
    }

    /** Chamado exclusivamente após sucesso do autenticador do sistema. */
    fun authenticated() {
        state = state.copy(locked = false)
        backgroundAt = null
    }

    /** A UI exige autenticação antes de desativar, exceto na escolha explícita sem credencial. */
    fun setEnabled(value: Boolean) {
        state = AppLockState(value, value)
        backgroundAt = null
    }

    companion object { const val TIMEOUT_MILLIS = 60_000L }
}
