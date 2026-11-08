package app.accord.ui.simulator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.simulation.Scenario
import app.accord.core.simulation.ScenarioSimulator
import app.accord.core.simulation.SimulationResult
import app.accord.core.space.CategoryNode
import app.accord.core.space.QueryResult
import app.accord.ui.register.FormError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class SimulatorField { Total, Installments }

/** Rascunho do cenário: texto como digitado, nunca um número inventado. Vive só no estado da tela (nunca sincroniza). */
data class SimulatorForm(
    val totalText: String = "",
    val installmentsText: String = "",
    val categoryId: String? = null,
    /** Escolha explícita da primeira fatura; nulo enquanto vale a sugestão do motor. */
    val firstMonth: YearMonth? = null,
)

sealed interface SimulatorOutcome {
    data object Idle : SimulatorOutcome

    /** "Calculando": os parâmetros ficam e um segundo pedido é ignorado. */
    data object Calculating : SimulatorOutcome
    data class Ready(val result: SimulationResult.Ready, val scenario: Scenario) : SimulatorOutcome
    data object Unavailable : SimulatorOutcome
}

data class SimulatorUiState(
    val form: SimulatorForm = SimulatorForm(),
    val errors: Map<SimulatorField, FormError> = emptyMap(),
    val outcome: SimulatorOutcome = SimulatorOutcome.Idle,
    val categories: Map<String, CategoryNode> = emptyMap(),
    /** Fatura que o motor estimaria para uma compra hoje; é só sugestão. */
    val suggestedMonth: YearMonth? = null,
) {
    /** Mês efetivo da primeira fatura: a escolha do casal ou, sem ela, a sugestão. */
    val firstMonth: YearMonth? get() = form.firstMonth ?: suggestedMonth
    val firstMonthIsEstimate: Boolean get() = form.firstMonth == null
}

/**
 * Simulador de compra (tela 36). Só lê: nunca executa comando, não grava nada e não toca a fila. O rascunho sobrevive à volta
 * de outras telas e à recriação do processo; o resultado não, e cada alteração dos parâmetros descarta o resultado anterior
 * para não mostrar números de um cenário que já não é o digitado.
 */
class SimulatorViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SimulatorUiState(
            form = SimulatorForm(
                totalText = saved[KEY_TOTAL] ?: "",
                installmentsText = saved[KEY_INSTALLMENTS] ?: "",
                categoryId = saved[KEY_CATEGORY],
                firstMonth = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse),
            ),
        ),
    )
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    /** Muda a cada edição dos parâmetros; um cálculo que termina com outra geração não mostra resultado. */
    private var generation = 0

    init {
        viewModelScope.launch {
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }.orEmpty()
            val suggested = ScenarioSimulator(space, provider.clock).suggestedFirstMonth()
            _state.update { it.copy(categories = categories, suggestedMonth = suggested) }
        }
    }

    fun setTotal(text: String) = edit(SimulatorField.Total) { it.copy(totalText = text) }.also { saved[KEY_TOTAL] = text }

    fun setInstallments(text: String) = edit(SimulatorField.Installments) { it.copy(installmentsText = text) }.also { saved[KEY_INSTALLMENTS] = text }

    fun setCategory(id: String?) {
        edit(null) { it.copy(categoryId = id) }
        saved[KEY_CATEGORY] = id
    }

    fun shiftFirstMonth(months: Long) {
        val base = _state.value.firstMonth ?: return
        val next = base.plusMonths(months)
        edit(null) { it.copy(firstMonth = next) }
        saved[KEY_MONTH] = next.toString()
    }

    fun useSuggestedMonth() {
        edit(null) { it.copy(firstMonth = null) }
        saved[KEY_MONTH] = null
    }

    private fun edit(field: SimulatorField?, change: (SimulatorForm) -> SimulatorForm) {
        _state.update {
            it.copy(
                form = change(it.form),
                errors = if (field == null) it.errors else it.errors - field,
                // Resultado de outro cenário não fica na tela; um cálculo em curso é descartado ao terminar (geração mudou).
                outcome = SimulatorOutcome.Idle,
            )
        }
        generation++
    }

    fun simulate() {
        val current = _state.value
        if (current.outcome is SimulatorOutcome.Calculating) return
        val errors = linkedMapOf<SimulatorField, FormError>()
        val total = when (val r = Money.parse(current.form.totalText)) {
            is MoneyParse.Ok -> r.money.also { if (it.cents <= 0) errors[SimulatorField.Total] = FormError.MustBePositive }
            is MoneyParse.Error -> {
                errors[SimulatorField.Total] = when (r.reason) {
                    MoneyParse.Reason.Empty -> FormError.AmountEmpty
                    MoneyParse.Reason.Negative -> FormError.AmountNegative
                    MoneyParse.Reason.Ambiguous -> FormError.AmountAmbiguous
                    MoneyParse.Reason.TooLarge -> FormError.AmountTooLarge
                    MoneyParse.Reason.Invalid -> FormError.AmountInvalid
                }
                null
            }
        }
        val installments = current.form.installmentsText.trim().toIntOrNull()
        if (installments == null) errors[SimulatorField.Installments] = FormError.InstallmentsInvalid
        val first = current.firstMonth
        if (total == null || installments == null || first == null || errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, outcome = SimulatorOutcome.Idle) }
            return
        }
        val scenario = Scenario(total, installments, first, current.form.categoryId)
        _state.update { it.copy(errors = emptyMap(), outcome = SimulatorOutcome.Calculating) }
        val requested = generation
        viewModelScope.launch {
            val result = ScenarioSimulator(provider.space(), provider.clock).simulate(scenario)
            if (requested != generation) return@launch
            _state.update { latest ->
                when (result) {
                    is SimulationResult.Ready -> latest.copy(outcome = SimulatorOutcome.Ready(result, scenario))
                    is SimulationResult.Invalid -> latest.copy(
                        errors = result.errors.mapNotNull { e ->
                            when (e.field) {
                                "total" -> SimulatorField.Total
                                "installments" -> SimulatorField.Installments
                                else -> null
                            }?.let { it to FormError.FromFacade(e.message) }
                        }.toMap(),
                        outcome = SimulatorOutcome.Idle,
                    )
                    SimulationResult.Unavailable -> latest.copy(outcome = SimulatorOutcome.Unavailable)
                }
            }
        }
    }

    companion object {
        private const val KEY_TOTAL = "simTotal"
        private const val KEY_INSTALLMENTS = "simInstallments"
        private const val KEY_CATEGORY = "simCategory"
        private const val KEY_MONTH = "simFirstMonth"
    }
}
