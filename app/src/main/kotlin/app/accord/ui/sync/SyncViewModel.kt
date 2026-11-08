package app.accord.ui.sync

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import app.accord.core.sync.ProblemKind
import app.accord.core.sync.ProblemOperation
import app.accord.core.sync.SyncOutcome
import app.accord.persistence.SyncController
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nome da operação para a pessoa: o tipo de comando em português, sem termos técnicos. */
@StringRes
fun commandLabel(commandType: String): Int = when (commandType) {
    "RegisterDirectPayment", "RegisterCardPurchase" -> R.string.op_new_expense
    "RegisterBoleto" -> R.string.op_new_boleto
    "RegisterIncome" -> R.string.op_new_income
    "CreateRecurrence", "EditRecurrence", "DeleteRecurrence", "PayOccurrence" -> R.string.op_new_recurrence
    "CorrectEntry", "CorrectInvoiceAssignment", "CorrectOccurrenceInvoice", "EditAccount", "ChangePaymentMethod" -> R.string.op_correction
    "DeleteEntry" -> R.string.op_delete
    "DefineLimits" -> R.string.op_limits
    "PayInvoice" -> R.string.op_pay_invoice
    "RegisterInvoiceTotal" -> R.string.op_invoice_total
    "PayBoleto" -> R.string.op_pay_boleto
    "ReceiveIncome" -> R.string.op_receive_income
    "RegisterRefund", "ConfirmRefund" -> R.string.op_refund
    "RequestInstallmentCancellation", "ConfirmNonCharge" -> R.string.op_cancellation
    "CreateCategory", "RenameCategory", "ReorderCategories", "MoveSubcategory" -> R.string.op_category
    "AddAccount", "SetMyDefaultAccount" -> R.string.op_account
    "ConfigureCard" -> R.string.op_card
    "Undo" -> R.string.op_undo
    "ClassifyEntry" -> R.string.op_classify
    "ConfirmImportBatch" -> R.string.op_import
    "ResolveConflict" -> R.string.op_resolve
    else -> R.string.op_other
}

data class ConflictRowUi(val operationId: String, @StringRes val kind: Int, val subject: String?, val deferred: Boolean)

data class AttentionRow(
    val operationId: String,
    val kind: ProblemKind,
    @StringRes val title: Int,
    val subject: String?,
    val failure: String?,
    /** Lançamento que a operação alterou, quando há uma tela de detalhe para ele. */
    val entryId: String?,
)

data class SyncUi(
    val conflicts: List<ConflictRowUi> = emptyList(),
    val attention: List<AttentionRow> = emptyList(),
    val working: Boolean = false,
    val message: ScreenMessage? = null,
    /** Operação recusada que a pessoa está prestes a descartar (confirmação). */
    val confirmDiscard: String? = null,
)

/** Tela 26: estado honesto da fila, "Sincronizar agora", conflitos e operações com problema. */
class SyncViewModel(private val provider: SpaceProvider, private val sync: SyncController) : ViewModel() {
    private val _ui = MutableStateFlow(SyncUi())
    val ui: StateFlow<SyncUi> = _ui.asStateFlow()

    /** Estado da fila, da sessão e do serviço, vindo do motor. */
    val status = sync.status

    fun refresh() {
        viewModelScope.launch {
            sync.refresh()
            val conflicts = sync.conflicts()
            val problems = sync.problems()
            val space = runCatching { provider.space() }.getOrNull()
            suspend fun subject(ref: EntityRef?): String? = ref?.let { describe(space, it) }
            _ui.update {
                it.copy(
                    conflicts = conflicts.map { c ->
                        ConflictRowUi(c.operationId, commandLabel(c.commandType), subject(c.entities.firstOrNull()?.ref), c.deferred)
                    },
                    // O conflito aparece na própria seção; aqui entram os demais problemas (retidas e recusadas).
                    attention = problems.filter { p -> p.kind != ProblemKind.Conflict }.map { p -> attention(p, space) },
                )
            }
        }
    }

    private suspend fun attention(p: ProblemOperation, space: FinanceSpace?): AttentionRow {
        val first = p.entities.firstOrNull()
        return AttentionRow(
            operationId = p.operationId, kind = p.kind, title = commandLabel(p.commandType),
            subject = first?.let { describe(space, it) }, failure = p.failure,
            entryId = first?.takeIf { it.type in ENTRY_TYPES }?.id,
        )
    }

    private suspend fun describe(space: FinanceSpace?, ref: EntityRef): String? {
        space ?: return null
        return when (ref.type) {
            in ENTRY_TYPES -> (space.entry(ref.id) as? QueryResult.Data)?.value?.description
            EntityType.Category -> (space.categories() as? QueryResult.Data)?.value?.firstOrNull { it.id == ref.id }?.name
            EntityType.IncomeCategory -> (space.incomeCategories() as? QueryResult.Data)?.value?.firstOrNull { it.id == ref.id }?.name
            EntityType.Account -> (space.accounts() as? QueryResult.Data)?.value?.accounts?.firstOrNull { it.id == ref.id }?.nickname
            else -> null
        }
    }

    fun syncNow() {
        if (_ui.value.working) return
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = sync.syncNow()
            _ui.update { it.copy(working = false) }
            refresh()
            // Só um ciclo concluído é anunciado como sucesso; a causa de uma parada já está no aviso fixo da tela.
            val message = (outcome as? SyncOutcome.Completed)?.let {
                ScreenMessage(MessageKind.Success, if ((sync.summary()?.openConflicts ?: 0) > 0) R.string.sync_done_conflict else R.string.sync_done)
            }
            _ui.update { it.copy(message = message) }
        }
    }

    fun retry(operationId: String) {
        viewModelScope.launch {
            sync.retryRejected(operationId)
            refresh()
            sync.syncNow()
            refresh()
        }
    }

    fun askDiscard(operationId: String) = _ui.update { it.copy(confirmDiscard = operationId) }
    fun cancelDiscard() = _ui.update { it.copy(confirmDiscard = null) }

    fun discard() {
        val id = _ui.value.confirmDiscard ?: return
        _ui.update { it.copy(confirmDiscard = null) }
        viewModelScope.launch {
            sync.discardRejected(id)
            refresh()
        }
    }

    private companion object {
        val ENTRY_TYPES = setOf(EntityType.Movement, EntityType.CardPurchase, EntityType.Boleto, EntityType.Income)
    }
}
