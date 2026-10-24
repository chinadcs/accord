package app.accord

import android.content.Context
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferência de tema deste aparelho (tela 22): fica nas preferências locais, não é dado financeiro,
 * não sincroniza e não altera nenhum lançamento. Sem escolha salva, segue o sistema.
 */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _preference = MutableStateFlow(read())
    val preference: StateFlow<ThemePreference> = _preference.asStateFlow()

    private fun read(): ThemePreference =
        runCatching { ThemePreference.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemePreference.System)

    fun set(value: ThemePreference) {
        prefs.edit().putString(KEY, value.name).apply()
        _preference.value = value
    }

    private companion object {
        const val FILE = "accord_local_preferences"
        const val KEY = "theme"
    }
}
