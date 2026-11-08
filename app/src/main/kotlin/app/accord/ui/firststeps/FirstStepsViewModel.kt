package app.accord.ui.firststeps

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.alerts.AndroidNotifications
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
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

    /** O que o espaço já tem, para quem entrou por convite ("Você entrou em [espaço]"). */
    data class Guest(val spaceName: String, val categoryGroups: Int, val cardConfigured: Boolean, val limitsTotal: String?)

    data class Ready(val rows: List<StepRow>, val guest: Guest? = null) : StepsContent {
        /** O passo de notificações é obrigatório (decisão de 03/11/2026): "Começar a registrar" só vale com a permissão concedida neste aparelho. */
        val canStart: Boolean get() = rows.firstOrNull { it.id == StepId.Notifications }?.status != StepStatus.Pending
    }
}

/**
 * Tela 03. Cada item vem dos dados persistidos neste aparelho: concluído só depois de salvo, e os que
 * dependem do outro aparelho mostram apenas o que já chegou aqui. Nada é pontuado nem exigido.
 */
class FirstStepsViewModel(
    private val provider: SpaceProvider,
    private val access: AccessCenter,
    private val notifications: AndroidNotifications,
) : ViewModel() {
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
            val notificationAccess = notifications.access()
            // Depois do passo o padrão é "ativado" (decisão de 03/11/2026); quem permitiu pelos ajustes do sistema também fica ativado.
            if (notificationAccess == NotificationAccess.Granted && (space.alertPreference() as? QueryResult.Data)?.value == AlertPreference.Undefined) {
                space.setAlertPreference(AlertPreference.Enabled)
            }
            val notificationsRow = StepRow(
                StepId.Notifications,
                if (notificationAccess == NotificationAccess.Granted) StepStatus.Done else StepStatus.Pending,
                R.string.steps_notifications,
                StepText(
                    when (notificationAccess) {
                        NotificationAccess.Granted -> R.string.steps_notifications_done
                        NotificationAccess.ChannelDisabled -> R.string.steps_notifications_channel
                        NotificationAccess.PermissionDenied -> R.string.steps_notifications_pending
                    },
                ),
                if (notificationAccess == NotificationAccess.Granted) R.string.steps_notifications_action_done else R.string.steps_notifications_action_pending,
            )

            val guestName = access.guestSpaceName()
            val guest = guestName?.let {
                StepsContent.Guest(it, categories.count { c -> c.parentId == null }, card?.configured == true, total.takeIf { t -> t > Money.ZERO }?.format())
            }
            _content.value = StepsContent.Ready(guest = guest, rows =
                // ADR 0036: sem modelo no P0, o passo "modelo no aparelho" não aparece na lista.
                steps.filter { it.id != StepId.DeviceModel }.map { step ->
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
                        StepId.Notifications -> notificationsRow
                        StepId.DeviceModel -> StepRow(step.id, step.status, R.string.steps_model, StepText(R.string.steps_model_unavailable), null)
                    }
                }.let { rows -> if (rows.any { it.id == StepId.Notifications }) rows else rows + notificationsRow },
            )
        }
    }

    /** "Começar a registrar": encerra a lista; os ajustes continuam nos destinos próprios de Mais. */
    fun start(then: () -> Unit) {
        viewModelScope.launch {
            access.dismissFirstSteps()
            then()
        }
    }
}
