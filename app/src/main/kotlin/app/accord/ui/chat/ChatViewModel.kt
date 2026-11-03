package app.accord.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.AppContainer
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.core.chat.ChatCoordinator
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.MessageState
import app.accord.core.chat.PartialEntry
import app.accord.core.chat.QuestionOption
import app.accord.core.chat.UndoResult
import app.accord.core.model.ModelInstall
import app.accord.core.model.ModelStatus
import app.accord.core.space.CategoryNode
import app.accord.core.space.QueryResult
import app.accord.ui.register.FormCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Situação do modelo no cabeçalho do Chat (tela 04 §4): discreta, abre a tela 25. */
sealed interface ModelBadge {
    data object FakePilot : ModelBadge
    data object LocalRules : ModelBadge
    data object NotInstalled : ModelBadge
    data class Downloading(val percent: Int?) : ModelBadge
    data object Working : ModelBadge
    data object Installed : ModelBadge

    /** Arquivos instalados, mas nenhum motor integrado para interpretar: o Chat segue em "sem modelo". */
    data object InstalledNoEngine : ModelBadge
    data object Problem : ModelBadge
}

data class ChatUiState(
    val loaded: Boolean = false,
    val items: List<ChatItem> = emptyList(),
    val composer: String = "",
    val badge: ModelBadge = ModelBadge.NotInstalled,
    /** Há modelo pronto para "Interpretar agora" nas mensagens guardadas sem modelo. */
    val canInterpretNow: Boolean = false,
    /** Categorias do espaço, para as sugestões e o seletor de categoria do recibo "a classificar". */
    val categories: Map<String, CategoryNode> = emptyMap(),
)

/**
 * Tela do Chat (04). A conversa vive no histórico local e o rascunho da mensagem sobrevive à troca de
 * aba e à recriação do processo. Enviar grava a mensagem e esvazia o campo; o campo não é bloqueado
 * durante a interpretação, e tocar duas vezes em Enviar com o campo já vazio não envia nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val composer = MutableStateFlow(saved.get<String>(KEY_COMPOSER).orEmpty())

    private val snapshot: StateFlow<ChatSnapshot?> = container.chat
        .flatMapLatest { coordinator -> coordinator?.snapshot ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Catálogo do espaço aberto, recarregado a cada mudança da conversa (o catálogo muda raramente). */
    private val categories: StateFlow<Map<String, CategoryNode>> = snapshot.mapLatest { snap ->
        if (snap == null) emptyMap() else try {
            (container.space().categories() as? QueryResult.Data)?.value?.associateBy { it.id }.orEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val state: StateFlow<ChatUiState> = combine(snapshot, container.models.manager.status, composer, categories) { snap, model, text, categoryMap ->
        // ADR 0036: com regras locais não há modelo a instalar nem aviso de modelo.
        ChatUiState(
            loaded = snap != null,
            items = snap?.let(::buildChatItems).orEmpty(),
            composer = text,
            badge = when {
                container.categoryPilot.replacesInterpreter -> ModelBadge.FakePilot
                container.usesRuleInterpreter -> ModelBadge.LocalRules
                else -> badgeOf(model, container.models.engineAvailable)
            },
            categories = categoryMap,
            canInterpretNow = container.categoryPilot.enabled || container.usesRuleInterpreter || (model.active != null && container.models.engineAvailable),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private val coordinator: ChatCoordinator? get() = container.chat.value

    fun onComposerChange(text: String) {
        composer.value = text
        saved[KEY_COMPOSER] = text
    }

    fun send() {
        val text = composer.value
        if (text.isBlank()) return
        // Esvazia antes de gravar: um segundo toque em Enviar encontra o campo vazio e não envia nada.
        onComposerChange("")
        viewModelScope.launch {
            val sent = coordinator?.send(text)
            if (sent == null) onComposerChange(text)
        }
    }

    fun cancel(message: ChatMessage) {
        coordinator?.cancel(message.id)
    }

    fun retry(message: ChatMessage) {
        viewModelScope.launch { coordinator?.retry(message.id) }
    }

    fun answer(question: ChatQuestionRecord, option: QuestionOption) {
        viewModelScope.launch { coordinator?.answer(question.id, option) }
    }

    /** Resposta digitada num campo de valor da própria pergunta; o app lê o valor, sem passar pelo modelo. */
    fun answerAmount(question: ChatQuestionRecord, text: String, onInvalid: () -> Unit) {
        viewModelScope.launch {
            if (coordinator?.answerAmount(question.id, text) != true) onInvalid()
        }
    }

    fun undo(receiptId: String) {
        viewModelScope.launch {
            val message = when (coordinator?.undo(receiptId)) {
                UndoResult.Done -> R.string.undo_done
                UndoResult.ChangedAfter -> R.string.undo_changed_after
                UndoResult.NotAvailable, UndoResult.Failed, null -> R.string.undo_failed
            }
            container.feedback.post(FeedbackEvent(message))
            if (message == R.string.undo_done) container.feedback.notifyUndone()
        }
    }

    fun discardQuestion(question: ChatQuestionRecord) {
        viewModelScope.launch { coordinator?.discardQuestion(question.id) }
    }

    fun insertExample(text: String) = onComposerChange(text)

    /**
     * Rota do registro manual com o que o Chat já entendeu ainda sem salvar. Sem mensagem, abre em branco.
     * A mensagem acompanha o formulário: ao salvar, ela deixa de oferecer "Tentar de novo".
     */
    suspend fun manualRoute(message: ChatMessage?): String {
        val prefill: PartialEntry? = message?.let { coordinator?.manualPrefill(it.id) }
        val query = buildList {
            prefill?.let { add("form=${Uri.encode(FormCodec.encode(ChatPrefill.toForm(it, container.clock.today())))}") }
            message?.takeIf { it.state != MessageState.ResolvedManually }?.let { add("chatMessage=${Uri.encode(it.id)}") }
        }
        return if (query.isEmpty()) "register" else "register?${query.joinToString("&")}"
    }

    private fun badgeOf(model: ModelStatus, engineAvailable: Boolean): ModelBadge = when (val install = model.install) {
        is ModelInstall.Downloading -> ModelBadge.Downloading(percentOf(install.receivedBytes, install.totalBytes))
        is ModelInstall.Verifying, is ModelInstall.Installing -> ModelBadge.Working
        is ModelInstall.Failed -> ModelBadge.Problem
        else -> when {
            model.active == null -> ModelBadge.NotInstalled
            engineAvailable -> ModelBadge.Installed
            else -> ModelBadge.InstalledNoEngine
        }
    }

    private fun percentOf(received: Long, total: Long): Int? = if (total <= 0) null else (received * 100 / total).toInt()

    private companion object {
        const val KEY_COMPOSER = "composer"
    }
}
