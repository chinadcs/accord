package app.accord.ui.detail

import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Formulário de ação vinculada aberto sobre uma tela, com o que a fachada recusou ou o que mudou nesse meio-tempo. */
data class LinkedState(
    val action: LinkedAction,
    val form: LinkedForm,
    val errors: Map<LinkedField, LinkedError> = emptyMap(),
    /** Recusa da fachada, já em português. */
    val message: String? = null,
    val stale: Boolean = false,
    val failed: Boolean = false,
    val working: Boolean = false,
)

/**
 * Ciclo de uma ação vinculada: abrir o formulário, validar, executar e avisar. Usado pelo detalhe do
 * lançamento e pela lista de Rendas. [onChanged] recarrega a tela dona depois de gravar ou quando a
 * versão mudou.
 */
class LinkedActionsController(
    private val provider: SpaceProvider,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    private val _state = MutableStateFlow<LinkedState?>(null)
    val state: StateFlow<LinkedState?> = _state.asStateFlow()

    /** Um ID por abertura do formulário: tocar duas vezes em Confirmar não registra duas operações. */
    private var commandId: CommandId? = null

    fun open(action: LinkedAction) {
        commandId = CommandId(UUID.randomUUID().toString())
        val amountText = (action as? LinkedAction.ConfirmRefundOf)?.refund?.amount?.formatPlain().orEmpty()
        _state.value = LinkedState(action, LinkedForm(date = provider.clock.today(), amountText = amountText))
    }

    fun update(change: (LinkedForm) -> LinkedForm) = _state.update { s ->
        s?.copy(form = change(s.form), errors = emptyMap(), message = null)
    }

    fun close() {
        _state.value = null
        commandId = null
    }

    fun submit() {
        val state = _state.value ?: return
        if (state.working) return
        when (val built = state.action.build(state.form)) {
            is LinkedBuilt.Invalid -> _state.update { it?.copy(errors = built.errors) }
            is LinkedBuilt.Ok -> {
                _state.update { it?.copy(working = true, errors = emptyMap(), message = null) }
                val id = commandId ?: CommandId(UUID.randomUUID().toString()).also { commandId = it }
                scope.launch {
                    when (val result = provider.space().execute(Envelope(id), built.command)) {
                        is CommandResult.Applied -> done(state.action, result.receipt.operationId, result.receipt.undoable)
                        is CommandResult.AlreadyApplied -> done(state.action, result.receipt.operationId, result.receipt.undoable)
                        is CommandResult.Rejected -> _state.update {
                            it?.copy(working = false, message = result.errors.joinToString(" ") { e -> e.message })
                        }
                        is CommandResult.StaleVersion -> {
                            _state.update { it?.copy(working = false, stale = true) }
                            onChanged()
                        }
                        is CommandResult.DecisionNeeded -> _state.update { it?.copy(working = false, failed = true) }
                    }
                }
            }
        }
    }

    /** Impede um segundo toque no interruptor enquanto o primeiro ainda grava. */
    private var toggling = false

    /**
     * Liga ou desliga o interruptor Recebido/Pago. Ligar usa hoje, o valor previsto e a conta padrão; se a fachada recusar (por exemplo,
     * sem conta padrão), abre o formulário completo com o motivo, para a pessoa escolher. Desligar volta ao previsto.
     */
    fun toggleSettle(settle: SettleSwitch, turnOn: Boolean) {
        if (toggling) return
        toggling = true
        scope.launch {
            try {
                val id = CommandId(UUID.randomUUID().toString())
                when (val result = provider.space().execute(Envelope(id), settle.command(turnOn, provider.clock.today()))) {
                    is CommandResult.Applied -> toggled(settle, turnOn, result.receipt.operationId, result.receipt.undoable)
                    is CommandResult.AlreadyApplied -> toggled(settle, turnOn, result.receipt.operationId, result.receipt.undoable)
                    is CommandResult.Rejected -> {
                        if (turnOn) {
                            open(settle.adjustAction())
                            _state.update { it?.copy(message = result.errors.joinToString(" ") { e -> e.message }) }
                        } else {
                            onChanged()
                        }
                    }
                    is CommandResult.StaleVersion -> onChanged()
                    is CommandResult.DecisionNeeded -> onChanged()
                }
            } finally {
                toggling = false
            }
        }
    }

    private fun toggled(settle: SettleSwitch, turnOn: Boolean, operationId: String, undoable: Boolean) {
        val message = when {
            turnOn && settle.receiving -> R.string.feedback_received
            turnOn -> R.string.feedback_paid
            settle.receiving -> R.string.feedback_receipt_undone
            else -> R.string.feedback_payment_undone
        }
        provider.feedback.post(FeedbackEvent(message, undoOperationId = operationId.takeIf { undoable }))
        onChanged()
    }

    private fun done(action: LinkedAction, operationId: String, undoable: Boolean) {
        val message = when (action) {
            is LinkedAction.Settle -> if (action.receiving) R.string.feedback_received else R.string.feedback_paid
            is LinkedAction.NewRefund -> R.string.feedback_refund_registered
            is LinkedAction.ConfirmRefundOf -> R.string.feedback_refund_confirmed
            is LinkedAction.OccurrenceInvoice -> R.string.feedback_occurrence_invoice
            is LinkedAction.Cancellation -> if (action.nonCharge) R.string.feedback_non_charge else R.string.feedback_cancel_requested
        }
        provider.feedback.post(FeedbackEvent(message, undoOperationId = operationId.takeIf { undoable }))
        close()
        onChanged()
    }
}
