package app.accord.ui.firststeps

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.space.QueryResult
import app.accord.core.sum
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.persistence.AccessCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StepText(@StringRes val res: Int, val args: List<Any> = emptyList())

data class StepRow(
    val id: StepId,
    val status: StepStatus,
    @StringRes val title: Int,
    val detail: StepText,
    /** Nulo quando o item não tem tela própria nesta etapa (o modelo no aparelho). */
    @StringRes val action: Int?,
)

sealed interface StepsContent {
    data object Loading : StepsContent
    data class Ready(val rows: List<StepRow>) : StepsContent
}

/**
 * Tela 03. Cada item vem dos dados persistidos neste aparelho: concluído só depois de salvo, e os que
 * dependem do outro aparelho mostram apenas o que já chegou aqui. Nada é pontuado nem exigido.
 */
class FirstStepsViewModel(private val provider: SpaceProvider, private val access: AccessCenter) : ViewModel() {
    private val _content = MutableStateFlow<StepsContent>(StepsContent.Loading)
    val content: StateFlow<StepsContent> = _content.asStateFlow()

    /** Relê os itens ao voltar de uma tela; os já conhecidos continuam visíveis até o novo resultado. */
    fun refresh() {
        viewModelScope.launch {
            val steps = access.firstSteps()
            if (steps.isEmpty()) return@launch
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value.orEmpty()
            val card = (space.card() as? QueryResult.Data)?.value
            val accounts = (space.accounts() as? QueryResult.Data)?.value
            val limits = (space.limits(provider.clock.currentMonth()) as? QueryResult.Data)?.value.orEmpty()
            val total = limits.mapNotNull { it.setting.total }.sum()

            _content.value = StepsContent.Ready(
                steps.map { step ->
                    val done = step.status == StepStatus.Done
                    when (step.id) {
                        StepId.InitialCategories -> StepRow(
                            step.id, step.status, R.string.steps_categories,
                            if (done) StepText(R.string.steps_categories_done, listOf(categories.count { it.parentId == null })) else StepText(R.string.steps_categories_pending),
                            R.string.steps_categories_action,
                        )
                        StepId.DefaultCard -> StepRow(
                            step.id, step.status, R.string.steps_card,
                            if (done && card != null) StepText(R.string.steps_card_done, listOf(card.closingDay, card.dueDay)) else StepText(R.string.steps_card_pending),
                            if (done) R.string.steps_card_action_done else R.string.steps_card_action_pending,
                        )
                        StepId.AccountsAndMyDefault -> StepRow(
                            step.id, step.status, R.string.steps_accounts,
                            accounts?.accounts?.firstOrNull { it.id == accounts.myDefaultAccountId }?.takeIf { done }
                                ?.let { StepText(R.string.steps_accounts_done, listOf(it.nickname)) } ?: StepText(R.string.steps_accounts_pending),
                            R.string.steps_accounts_action,
                        )
                        StepId.LimitsAndSplit -> StepRow(
                            step.id, step.status, R.string.steps_limits,
                            if (done && total > Money.ZERO) StepText(R.string.steps_limits_done, listOf(total.format())) else StepText(R.string.steps_limits_pending),
                            if (done) R.string.steps_limits_action_done else R.string.steps_limits_action_pending,
                        )
                        StepId.OtherPerson -> StepRow(
                            step.id, step.status, R.string.steps_other,
                            StepText(if (done) R.string.steps_other_done else R.string.steps_other_pending),
                            if (done) R.string.steps_other_action_done else R.string.steps_other_action_pending,
                        )
                        StepId.DeviceModel -> StepRow(step.id, step.status, R.string.steps_model, StepText(R.string.steps_model_unavailable), null)
                    }
                },
            )
        }
    }

    /** "Começar a registrar": encerra a lista neste aparelho; ela continua acessível em Mais. */
    fun start(then: () -> Unit) {
        viewModelScope.launch {
            access.dismissFirstSteps()
            then()
        }
    }
}
