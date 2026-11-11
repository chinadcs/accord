package app.accord

import android.content.Context
import app.accord.ui.theme.ThemePreference
import app.accord.ui.theme.CornerStyle
import app.accord.ui.theme.ThemePalette
import app.accord.core.space.DateCriterion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferência de tema deste aparelho (tela 22): fica nas preferências locais, não é dado financeiro,
 * não sincroniza e não altera nenhum lançamento. Sem escolha salva, segue o sistema.
 */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _entriesDateCriterion = MutableStateFlow(
        runCatching { DateCriterion.valueOf(prefs.getString(KEY_ENTRIES_DATE_CRITERION, null) ?: "") }
            .getOrDefault(DateCriterion.PurchaseDate),
    )
    val entriesDateCriterion: StateFlow<DateCriterion> = _entriesDateCriterion.asStateFlow()

    private val _includeExpectedIncomeInHomeSummary = MutableStateFlow(
        prefs.getBoolean(KEY_INCLUDE_EXPECTED_INCOME_IN_HOME_SUMMARY, true),
    )
    val includeExpectedIncomeInHomeSummary: StateFlow<Boolean> = _includeExpectedIncomeInHomeSummary.asStateFlow()

    fun setIncludeExpectedIncomeInHomeSummary(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_EXPECTED_INCOME_IN_HOME_SUMMARY, value).apply()
        _includeExpectedIncomeInHomeSummary.value = value
    }

    fun setEntriesDateCriterion(value: DateCriterion) {
        prefs.edit().putString(KEY_ENTRIES_DATE_CRITERION, value.name).apply()
        _entriesDateCriterion.value = value
    }

    private val _preference = MutableStateFlow(read())
    val preference: StateFlow<ThemePreference> = _preference.asStateFlow()
    private val _palette = MutableStateFlow(readPalette())
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()
    private val _corners = MutableStateFlow(
        runCatching { CornerStyle.valueOf(prefs.getString(KEY_CORNERS, null).orEmpty()) }.getOrDefault(CornerStyle.Rounded),
    )
    val corners: StateFlow<CornerStyle> = _corners.asStateFlow()

    fun setCorners(value: CornerStyle) {
        prefs.edit().putString(KEY_CORNERS, value.name).apply()
        _corners.value = value
    }

    private fun read(): ThemePreference =
        runCatching { ThemePreference.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemePreference.System)

    fun set(value: ThemePreference) {
        prefs.edit().putString(KEY, value.name).apply()
        _preference.value = value
    }

    private fun readPalette(): ThemePalette =
        runCatching { ThemePalette.valueOf(prefs.getString(KEY_PALETTE, null) ?: "") }.getOrDefault(ThemePalette.Accord)

    fun setPalette(value: ThemePalette) {
        prefs.edit().putString(KEY_PALETTE, value.name).apply()
        _palette.value = value
    }

    private companion object {
        const val FILE = "accord_local_preferences"
        const val KEY = "theme"
        const val KEY_CORNERS = "corners"
        const val KEY_PALETTE = "palette"
        const val KEY_ENTRIES_DATE_CRITERION = "entries_date_criterion"
        const val KEY_INCLUDE_EXPECTED_INCOME_IN_HOME_SUMMARY = "include_expected_income_in_home_summary"
    }
}
