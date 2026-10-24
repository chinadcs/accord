package app.accord

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Aviso curto depois de uma ação (ex.: "Salvo no aparelho"), com Desfazer quando a operação é
 * reversível. Telas publicam; a raiz do aplicativo mostra, para o aviso sobreviver à troca de tela.
 */
data class FeedbackEvent(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
    /** Operação a desfazer se a pessoa tocar em Desfazer; nulo não oferece a ação. */
    val undoOperationId: String? = null,
)

class FeedbackBus {
    private val _events = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FeedbackEvent> = _events.asSharedFlow()

    fun post(event: FeedbackEvent) {
        _events.tryEmit(event)
    }

    private val _undone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Um Desfazer foi aplicado: telas abertas recarregam, porque o que mostram pode ter voltado ao que era. */
    val undone: SharedFlow<Unit> = _undone.asSharedFlow()

    fun notifyUndone() {
        _undone.tryEmit(Unit)
    }
}

enum class UndoOutcome { Done, ChangedAfter, Failed }
