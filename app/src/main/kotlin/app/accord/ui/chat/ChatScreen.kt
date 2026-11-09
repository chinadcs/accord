package app.accord.ui.chat

import app.accord.ui.register.CategoryTile
import app.accord.ui.register.CategoryDialog
import app.accord.ui.colorKey
import app.accord.ui.categoryColor
import app.accord.core.space.CategoryNode
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.vendors.ChooseIconFlow
import app.accord.ui.vendors.IconRefLogo
import app.accord.ui.vendors.LocalIconActions
import app.accord.ui.vendors.LocalIconChoices
import app.accord.ui.vendors.rememberIconLibrary
import app.accord.ui.components.AccordMark
import app.accord.core.Money
import app.accord.core.chat.ChatFeedback
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.FeedbackField
import app.accord.core.chat.FeedbackVote
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.MessageState
import app.accord.core.chat.Nature
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.chat.RefuseReason
import app.accord.core.chat.StandardReply
import app.accord.core.label
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.fullLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import com.composables.icons.lucide.R as Lucide

/** Destinos que o Chat abre, todos fora da tela: ela só decide qual deles e entrega o identificador. */
class ChatActions(
    val onOpenModel: () -> Unit,
    val onOpenEntry: (String) -> Unit,
    val onEditEntry: (String) -> Unit,
    /** Rota do registro manual, já com o que o Chat entendeu. */
    val onManual: (String) -> Unit,
    val onOpenBudget: () -> Unit,
    val onOpenEntries: () -> Unit,
    val onOpenCard: () -> Unit,
    /** Lista de Lançamentos (tela 07) com os mesmos filtros da consulta. */
    val onOpenFilteredEntries: (YearMonth, String?, app.accord.core.space.DateCriterion) -> Unit = { _, _, _ -> },
    /** Compromissos do mês (tela 10) e cronograma de uma compra (tela 11). */
    val onOpenCommitments: (YearMonth) -> Unit = {},
    val onOpenCommitment: (String) -> Unit = {},
    /** Recalcula a resposta de uma consulta pela fachada, sem chamar o interpretador. */
    val onRefreshQuery: (String) -> Unit = {},
    /** Envia como mensagem a pergunta completa de uma opção (categoria ou compra). */
    val onSendQuestion: (String) -> Unit = {},
)

@Composable
fun ChatRoute(viewModel: ChatViewModel, padding: PaddingValues, today: LocalDate, actions: ChatActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val openManual: (ChatMessage?) -> Unit = { message -> scope.launch { actions.onManual(viewModel.manualRoute(message)) } }
    ChatScreen(
        state, today, padding, actions,
        onComposerChange = viewModel::onComposerChange,
        onSend = viewModel::send,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onAnswer = viewModel::answer,
        onAnswerAmount = viewModel::answerAmount,
        onUndo = viewModel::undo,
        onDiscard = viewModel::discardQuestion,
        onExample = viewModel::insertExample,
        onManual = openManual,
        onRate = viewModel::rate,
        onCreateConversation = viewModel::createConversation,
        onSelectConversation = viewModel::selectConversation,
        onRenameConversation = viewModel::renameConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onPosition = viewModel::savePosition,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    today: LocalDate,
    padding: PaddingValues,
    actions: ChatActions,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
    onAnswerAmount: (ChatQuestionRecord, String, () -> Unit) -> Unit,
    onUndo: (String) -> Unit,
    onDiscard: (ChatQuestionRecord) -> Unit,
    onExample: (String) -> Unit,
    onManual: (ChatMessage?) -> Unit,
    onRate: (String, FeedbackVote, FeedbackField?) -> Unit = { _, _, _ -> },
    onCreateConversation: () -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    onPosition: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    var conversationsOpen by rememberSaveable { mutableStateOf(false) }
    if (conversationsOpen) {
        ConversationsScreen(state.conversations, padding,
            onBack = { conversationsOpen = false },
            onCreate = { onCreateConversation(); conversationsOpen = false },
            onSelect = { onSelectConversation(it); conversationsOpen = false },
            onRename = onRenameConversation, onDelete = onDeleteConversation)
        return
    }
    val conversation = state.activeConversation
    val listState = remember(conversation?.id) { androidx.compose.foundation.lazy.LazyListState(conversation?.scrollIndex ?: 0, conversation?.scrollOffset ?: 0) }
    DisposableEffect(listState) {
        onDispose { conversation?.let { onPosition(it.id, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) ->
            conversation?.let { onPosition(it.id, index, offset) }
        }
    }
    val hasViewport by remember(listState) { derivedStateOf { listState.layoutInfo.viewportSize.height > 0 } }
    var previousLast by remember(conversation?.id) { mutableStateOf(state.items.lastOrNull()?.key) }
    // A conversa acompanha o fim quando chega algo novo, sem mover o foco do campo de mensagem.
    LaunchedEffect(state.items.lastOrNull()?.key, hasViewport) {
        val last = state.items.lastOrNull()?.key
        if (last != previousLast && state.items.isNotEmpty() && hasViewport) listState.animateScrollToItem(state.items.size)
        previousLast = last
    }
    Column(
        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { conversationsOpen = true }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.chat_conversations))
            }
            Text(conversation?.title.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            TextButton(onClick = onCreateConversation, enabled = state.loaded, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.chat_conversation_new))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            item(key = "chat-header") {
                ChatHeader(state.badge, actions.onOpenModel, actions.onOpenEntries, Modifier.padding(vertical = AccordSpacing.s2))
            }
            if (state.loaded && state.items.isEmpty()) {
                item(key = "chat-welcome") { EmptyChat(onExample, pilot = state.badge == ModelBadge.FakePilot) }
            } else {
                items(state.items, key = { it.key }) { item ->
                    ChatItemView(item, today, state, actions, onCancel, onRetry, onAnswer, onAnswerAmount, onUndo, onDiscard, onManual, onRate)
                }
            }
        }
        Composer(state.composer, onComposerChange, onSend)
    }
}

@Composable
private fun ChatHeader(badge: ModelBadge, onOpenModel: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Paisagem (spec 04): marca, título, estado do modelo, sincronização e pesquisa numa só linha.
    if (isCompactHeight() && !keyboardOpen) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            AccordMark()
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            ModelBadgeButton(badge, onOpenModel, wrap = true)
            Box(Modifier.weight(1f)) {}
            IconButton(onClick = onSearch, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_search), contentDescription = stringResource(R.string.search_entries))
            }
        }
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (!keyboardOpen) AccordMark()
            if (keyboardOpen) {
                Box(Modifier.weight(1f)) { ModelBadgeButton(badge, onOpenModel) }
            } else {
                Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_search), contentDescription = stringResource(R.string.search_entries))
            }
        }
        if (!keyboardOpen) ModelBadgeButton(badge, onOpenModel)
    }
}

@Composable
private fun ModelBadgeButton(badge: ModelBadge, onClick: () -> Unit, wrap: Boolean = false) {
    // ADR 0036: sem modelo no P0, não há aviso nem caminho para a tela do modelo.
    if (badge == ModelBadge.LocalRules) return
    val text = when (badge) {
        ModelBadge.LocalRules -> return
        ModelBadge.FakePilot -> stringResource(R.string.chat_fake_pilot)
        ModelBadge.NotInstalled -> stringResource(R.string.chat_model_not_installed)
        is ModelBadge.Downloading -> badge.percent?.let { stringResource(R.string.chat_model_downloading_percent, it) } ?: stringResource(R.string.chat_model_downloading)
        ModelBadge.Working -> stringResource(R.string.chat_model_working)
        ModelBadge.Installed -> stringResource(R.string.chat_model_installed)
        ModelBadge.InstalledNoEngine -> stringResource(R.string.chat_model_no_engine)
        ModelBadge.Problem -> stringResource(R.string.chat_model_problem)
    }
    val description = stringResource(R.string.chat_model_badge_description, text, stringResource(if (badge == ModelBadge.FakePilot) R.string.chat_fake_pilot_open else R.string.chat_model_open))
    var showPilot by rememberSaveable { mutableStateOf(false) }
    if (showPilot) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPilot = false },
            title = { Text(stringResource(R.string.chat_fake_pilot)) },
            text = { Text(stringResource(R.string.chat_fake_pilot_help), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPilot = false }) { Text(stringResource(R.string.chat_categories_close)) } },
        )
    }
    TextButton(
        onClick = { if (badge == ModelBadge.FakePilot) showPilot = true else onClick() },
        modifier = (if (wrap) Modifier else Modifier.fillMaxWidth()).heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = description },
    ) {
        Row(if (wrap) Modifier else Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(
                if (badge == ModelBadge.Installed) Lucide.drawable.lucide_ic_circle_check else Lucide.drawable.lucide_ic_cpu,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = AccordSpacing.s1).then(if (wrap) Modifier else Modifier.weight(1f)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onExample: (String) -> Unit, modifier: Modifier = Modifier, pilot: Boolean = false) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s5)) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.chat_welcome_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (pilot) R.string.chat_fake_pilot_welcome else R.string.chat_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.chat_examples_heading), style = MaterialTheme.typography.titleMedium)
            val examples = if (pilot) listOf(R.string.chat_pilot_pet, R.string.chat_pilot_fuel, R.string.chat_pilot_shirt, R.string.chat_pilot_amazon)
                else listOf(R.string.chat_example_expense, R.string.chat_example_pix, R.string.chat_example_income)
            examples.forEach { res ->
                val example = stringResource(res)
                Surface(onClick = { onExample(example) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).padding(horizontal = AccordSpacing.s3, vertical = AccordSpacing.s2),
                        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                        Text(example, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(text: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = AccordSpacing.s1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            val input: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    modifier = modifier.heightIn(min = AccordSpacing.touchMin),
                    label = { Text(stringResource(R.string.chat_composer_label)) },
                    placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                )
            }
            val send: @Composable (Modifier) -> Unit = { modifier ->
                Button(onClick = onSend, enabled = text.isNotBlank(), modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.chat_send))
                }
            }
            if (largeText) {
                input(Modifier.fillMaxWidth())
                send(Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    input(Modifier.weight(1f))
                    send(Modifier)
                }
            }
        }
    }
}

@Composable
private fun ChatItemView(
    item: ChatItem,
    today: LocalDate,
    state: ChatUiState,
    actions: ChatActions,
    onCancel: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
    onAnswerAmount: (ChatQuestionRecord, String, () -> Unit) -> Unit,
    onUndo: (String) -> Unit,
    onDiscard: (ChatQuestionRecord) -> Unit,
    onManual: (ChatMessage?) -> Unit,
    onRate: (String, FeedbackVote, FeedbackField?) -> Unit,
) {
    when (item) {
        is ChatItem.User -> UserBubble(item.message.text)
        is ChatItem.Working -> WorkingCard(item.message, onCancel = { onCancel(item.message) }, onManual = { onManual(item.message) })
        is ChatItem.Receipt -> ReceiptCard(item, today, actions, state.categories, onAnswer, onUndo, onRate)
        is ChatItem.Question -> QuestionCard(item, onAnswer, onAnswerAmount, onDiscard, onManual = { onManual(item.message) })
        is ChatItem.Query -> QueryCard(item, actions, onChoose = actions.onSendQuestion)
        is ChatItem.Notice -> NoticeCard(item.message, state, actions, onRetry = { onRetry(item.message) }, onManual = { onManual(item.message) })
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val spoken = stringResource(R.string.chat_user_message, text)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Text(
                text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(AccordSpacing.s3),
            )
        }
    }
}

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun WorkingCard(message: ChatMessage, onCancel: () -> Unit, onManual: () -> Unit) {
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                // Sem animação contínua quando a redução de movimento está ativa.
                if (Accord.motion.reduced) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_loader)
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                        when (message.state) {
                            MessageState.Applying -> R.string.chat_applying
                            MessageState.Sent -> R.string.chat_waiting
                            else -> R.string.chat_working
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(stringResource(R.string.chat_listing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionRow {
                OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_cancel)) }
                TextButton(onClick = onManual, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_manual)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) { content() }
}

// ---- recibo ----

@Composable
private fun ReceiptCard(
    item: ChatItem.Receipt,
    today: LocalDate,
    actions: ChatActions,
    categories: Map<String, CategoryNode>,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
    onUndo: (String) -> Unit,
    onRate: (String, FeedbackVote, FeedbackField?) -> Unit,
) {
    val receipt = item.receipt
    val summary = receipt.summary
    val active = receipt.state == ReceiptState.Active
    val library by rememberIconLibrary()
    val choices = LocalIconChoices.current
    val icon = remember(summary.description, summary.nature, receipt.entryId, choices, library) {
        library?.takeIf { summary.nature == Nature.Expense }?.let { lib ->
            (choices.forEntry(receipt.entryId, summary.description) ?: lib.matcher.match(summary.description))?.takeIf(lib::isKnown)?.let { it to lib }
        }
    }
    var choosingIcon by remember { mutableStateOf(false) }
    if (choosingIcon) ChooseIconFlow(receipt.entryId, summary.description) { choosingIcon = false }
    // Atualização anunciada de forma educada: o foco continua no campo de mensagem.
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            icon?.let { (ref, lib) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                    IconRefLogo(ref, lib)
                    Text(summary.description, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            ReceiptLines(summary, today)
            if (item.corrected && receipt.state == ReceiptState.Active) StateTag(stringResource(R.string.receipt_corrected), Lucide.drawable.lucide_ic_pencil)
            if (receipt.state == ReceiptState.ChangedLater) StateTag(stringResource(R.string.receipt_changed_later), Lucide.drawable.lucide_ic_pencil)
            if (receipt.state == ReceiptState.Undone) StateTag(stringResource(R.string.receipt_undone), Lucide.drawable.lucide_ic_undo_2)
            if (receipt.state != ReceiptState.Undone) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_circle_check, modifier = Modifier.size(16.dp), tint = Accord.colors.success)
                    Text(stringResource(R.string.receipt_saved), style = MaterialTheme.typography.bodySmall, color = Accord.colors.success)
                }
            }
            if (item.question != null) {
                CategoryChoices(item.question, summary, item.suggestedRefs, categories, onAnswer)
            }
            if (receipt.state != ReceiptState.Undone) {
                ActionRow {
                    TextButton(onClick = { actions.onOpenEntry(receipt.entryId) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.receipt_action_view))
                    }
                    if (active) {
                        TextButton(onClick = { actions.onEditEntry(receipt.entryId) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.receipt_action_edit))
                        }
                    }
                    if (active && summary.nature == Nature.Expense && LocalIconActions.current != null) {
                        TextButton(onClick = { choosingIcon = true }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.icon_receipt_action))
                        }
                    }
                    if (item.undoAvailable) {
                        TextButton(onClick = { onUndo(receipt.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.receipt_action_undo))
                        }
                    }
                }
                FeedbackRow(item.feedback) { vote, field -> onRate(receipt.id, vote, field) }
            }
        }
    }
}

/**
 * Joinha do recibo (accord-8q5): "Entendi certo?" com dois botões de 48 dp. O joinha para baixo oferece o campo errado.
 * Mede utilidade percebida, não correção, e fica só neste aparelho; Editar e Desfazer continuam na linha de ações.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackRow(feedback: ChatFeedback?, onRate: (FeedbackVote, FeedbackField?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.feedback_prompt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VoteButton(Lucide.drawable.lucide_ic_thumbs_up, stringResource(R.string.feedback_up), feedback?.vote == FeedbackVote.Up) { onRate(FeedbackVote.Up, null) }
            VoteButton(Lucide.drawable.lucide_ic_thumbs_down, stringResource(R.string.feedback_down), feedback?.vote == FeedbackVote.Down) { onRate(FeedbackVote.Down, null) }
        }
        if (feedback?.vote == FeedbackVote.Down) {
            Text(stringResource(R.string.feedback_field_prompt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                FeedbackField.entries.forEach { field ->
                    FilterChip(
                        selected = field in feedback.fields,
                        onClick = { onRate(FeedbackVote.Down, field) },
                        label = { Text(stringResource(feedbackFieldLabel(field))) },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteButton(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (selected) R.string.feedback_selected else R.string.feedback_not_selected)
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(AccordSpacing.touchMin).semantics {
            contentDescription = description
            stateDescription = state
            this.selected = selected
        },
    ) {
        Icon(
            painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun feedbackFieldLabel(field: FeedbackField): Int = when (field) {
    FeedbackField.Value -> R.string.feedback_field_value
    FeedbackField.Category -> R.string.feedback_field_category
    FeedbackField.Date -> R.string.feedback_field_date
    FeedbackField.Payment -> R.string.feedback_field_payment
    FeedbackField.Understood -> R.string.feedback_field_understood
    FeedbackField.NotEntry -> R.string.feedback_field_not_entry
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptLines(summary: ReceiptSummary, today: LocalDate) {
    val total = Money(summary.totalCents).format()
    val date = LocalDate.parse(summary.dateIso)
    val title = when {
        summary.nature == Nature.Income && summary.expectedIncome -> stringResource(R.string.receipt_income_expected, total, date.fullLabel())
        summary.nature == Nature.Income -> stringResource(R.string.receipt_income, total)
        else -> stringResource(R.string.receipt_expense, total)
    }
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

    val payment = paymentText(summary)
    if (summary.nature == Nature.Expense) {
        if (summary.toClassify) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                StateTag(stringResource(R.string.receipt_to_classify), Lucide.drawable.lucide_ic_tag)
                if (payment != null) Text(payment, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val category = listOfNotNull(summary.parentName, summary.categoryName).joinToString(" > ")
            Text(
                if (payment != null) stringResource(R.string.receipt_category_payment, category, payment) else category,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else if (payment != null) {
        Text(payment, style = MaterialTheme.typography.bodyMedium)
    }
    if (date != today) Text(stringResource(R.string.receipt_date, date.fullLabel()), style = MaterialTheme.typography.bodyMedium)

    if (summary.installments > 1) {
        val each = Money(summary.installmentCents).format()
        val last = Money(summary.lastInstallmentCents).format()
        Text(
            if (summary.installmentCents == summary.lastInstallmentCents) {
                stringResource(R.string.receipt_installments, summary.installments, each, total)
            } else {
                stringResource(R.string.receipt_installments_rounded, summary.installments, each, last, total)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    summary.invoiceMonth?.let { month ->
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.receipt_invoice, YearMonth.parse(month).label()), style = MaterialTheme.typography.bodyMedium)
            if (summary.invoiceToConfirm) StateTag(stringResource(R.string.receipt_to_confirm), Lucide.drawable.lucide_ic_triangle_alert)
        }
    }
    summary.budgetMonth?.let { month ->
        Text(stringResource(R.string.receipt_budget_month, YearMonth.parse(month).label()), style = MaterialTheme.typography.bodyMedium)
    }
    summary.scopeInstallments?.let { Text(stringResource(R.string.receipt_scope, it), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun paymentText(summary: ReceiptSummary): String? {
    val method = when (summary.payment) {
        ChatPayment.Card -> summary.cardLabel ?: stringResource(R.string.receipt_payment_card)
        ChatPayment.Pix -> stringResource(R.string.receipt_payment_pix)
        ChatPayment.Debit -> stringResource(R.string.receipt_payment_debit)
        ChatPayment.Cash -> stringResource(R.string.receipt_payment_cash)
        null -> null
    }
    val account = summary.accountLabel
    return when {
        method != null && account != null -> stringResource(R.string.receipt_account, method, account)
        else -> method ?: account
    }
}

/** Selo com ícone e texto: o estado nunca depende só da cor (tokens §7). */
@Composable
private fun StateTag(text: String, icon: Int) {
    Surface(color = Accord.colors.attentionContainer, contentColor = Accord.colors.onAttentionContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = AccordSpacing.s2, vertical = AccordSpacing.s1),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            DecorativeIcon(icon, Modifier.size(16.dp), tint = Accord.colors.onAttentionContainer)
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Pergunta de categoria do recibo "a classificar": até três sugestões no mesmo quadro (ícone na cor da categoria e nome)
 * do seletor do registro manual, e "Ver todas" abre esse mesmo seletor. Nada é aplicado sem o toque.
 */
@Composable
private fun CategoryChoices(
    question: ChatQuestionRecord,
    summary: ReceiptSummary,
    suggestedRefs: List<String>,
    categories: Map<String, CategoryNode>,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
) {
    Text(
        stringResource(R.string.question_category, "${summary.description} ${Money(summary.totalCents).format()}"),
        style = MaterialTheme.typography.bodyLarge,
    )
    val suggestedRef = suggestedRefs.firstOrNull()
    var showAll by rememberSaveable(question.id) { mutableStateOf(false) }
    if (suggestedRef != null) {
        Text(stringResource(R.string.chat_category_suggested_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val suggestions = suggestedRefs.take(3).mapNotNull { ref -> question.options.firstOrNull { it.ref == ref } }
    if (suggestions.isNotEmpty()) {
        // Com texto ampliado três colunas não cabem: os quadros passam a uma coluna, como o campo de mensagem.
        val stacked = LocalDensity.current.fontScale >= 1.3f
        val tile: @Composable (app.accord.core.chat.QuestionOption, Modifier) -> Unit = { option, tileModifier ->
            val node = (option.value as? app.accord.core.chat.OptionValue.Category)?.id?.let(categories::get)
            val isSuggested = option.ref == suggestedRef
            if (node != null) {
                val description = if (isSuggested) stringResource(R.string.chat_category_suggested_desc, option.label) else option.label
                CategoryTile(
                    name = node.name, iconKey = node.iconKey, color = categoryColor(node.colorKey(categories)), selected = isSuggested,
                    modifier = tileModifier.semantics(mergeDescendants = true) { contentDescription = description },
                    caption = node.parentId?.let(categories::get)?.name, compact = !stacked,
                ) { onAnswer(question, option) }
            } else {
                // Catálogo ainda não carregado: o rótulo completo continua escolhível.
                OutlinedButton(onClick = { onAnswer(question, option) }, modifier = tileModifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(if (isSuggested) stringResource(R.string.chat_category_suggested_tag) + ": " + option.label else option.label)
                }
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                suggestions.forEach { tile(it, Modifier) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                suggestions.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
                // Três colunas iguais, como na grade do registro manual, mesmo com menos de três sugestões.
                repeat(3 - suggestions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    OutlinedButton(onClick = { showAll = true }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.chat_categories_all))
    }
    if (showAll) {
        if (categories.isNotEmpty()) {
            CategoryDialog(
                categories = categories, selected = null,
                onPick = { id ->
                    showAll = false
                    question.options.firstOrNull { (it.value as? app.accord.core.chat.OptionValue.Category)?.id == id }?.let { onAnswer(question, it) }
                },
                onDismiss = { showAll = false },
            )
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAll = false },
                title = { Text(stringResource(R.string.chat_categories_all)) },
                text = {
                    val allOptions = question.options.sortedBy { if (" > " in it.label) 1 else 0 }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.testTag("chat-category-catalog")) {
                        items(allOptions.size) { index ->
                            val option = allOptions[index]
                            TextButton(onClick = { showAll = false; onAnswer(question, option) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                                Text(option.label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(R.string.chat_categories_close)) } },
            )
        }
    }
}

// ---- perguntas ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(
    item: ChatItem.Question,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
    onAnswerAmount: (ChatQuestionRecord, String, () -> Unit) -> Unit,
    onDiscard: (ChatQuestionRecord) -> Unit,
    onManual: () -> Unit,
) {
    val question = item.question
    val isDraft = question.link is QuestionLink.Draft
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (isDraft) StateTag(stringResource(R.string.question_not_saved), Lucide.drawable.lucide_ic_triangle_alert)
            Text(questionText(question), style = MaterialTheme.typography.bodyLarge)
            if (question.field == QuestionField.Amount) {
                AmountAnswer(question, onAnswerAmount)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    question.options.forEach { option ->
                        OutlinedButton(
                            onClick = { onAnswer(question, option) },
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = option.label },
                        ) { Text(option.label) }
                    }
                }
            }
            ActionRow {
                TextButton(onClick = onManual, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_manual)) }
                TextButton(onClick = { onDiscard(question) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.question_discard))
                }
            }
        }
    }
}

@Composable
private fun AmountAnswer(question: ChatQuestionRecord, onAnswerAmount: (ChatQuestionRecord, String, () -> Unit) -> Unit) {
    var value by rememberSaveable(question.id) { mutableStateOf("") }
    var invalid by remember(question.id) { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val field: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; invalid = false },
            modifier = modifier,
            label = { Text(stringResource(R.string.question_amount_field)) },
            isError = invalid,
            supportingText = if (invalid) ({ Text(stringResource(R.string.question_amount_error)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        )
    }
    val answer: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = { onAnswerAmount(question, value) { invalid = true } },
            enabled = value.isNotBlank(),
            modifier = modifier.heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(R.string.question_amount_answer)) }
    }
    if (largeText) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            field(Modifier.fillMaxWidth())
            answer(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            field(Modifier.weight(1f))
            answer(Modifier)
        }
    }
}

@Composable
private fun questionText(question: ChatQuestionRecord): String {
    val what = question.description
    val amount = question.amountCents?.let { Money(it).format() }
    val subject = listOfNotNull(what, amount).joinToString(" ")
    return when (question.field) {
        QuestionField.Amount -> when (question.reason) {
            ChatQuestion.Reason.Ambiguous -> stringResource(R.string.question_amount_ambiguous)
            ChatQuestion.Reason.Invalid -> stringResource(R.string.question_amount_invalid)
            else -> if (what != null) stringResource(R.string.question_amount, what) else stringResource(R.string.question_amount_plain)
        }
        QuestionField.AmountRole -> if (subject.isNotEmpty()) stringResource(R.string.question_role, subject) else stringResource(R.string.question_role_plain)
        QuestionField.Date -> when (question.reason) {
            ChatQuestion.Reason.FutureExpense -> stringResource(R.string.question_date_future)
            ChatQuestion.Reason.Invalid -> stringResource(R.string.question_date_invalid)
            else -> stringResource(R.string.question_date)
        }
        QuestionField.Payment -> if (subject.isNotEmpty()) stringResource(R.string.question_payment, subject) else stringResource(R.string.question_payment_plain)
        QuestionField.Account -> if (question.reason == ChatQuestion.Reason.Invalid) stringResource(R.string.question_account_invalid) else stringResource(R.string.question_account)
        QuestionField.Category -> if (subject.isNotEmpty()) stringResource(R.string.question_category, subject) else stringResource(R.string.question_category_plain)
        QuestionField.Target -> stringResource(R.string.question_target)
    }
}

// ---- respostas do app ----

@Composable
private fun NoticeCard(message: ChatMessage, state: ChatUiState, actions: ChatActions, onRetry: () -> Unit, onManual: () -> Unit) {
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(noticeText(message, state), style = MaterialTheme.typography.bodyLarge)
            ActionRow {
                NoticeActions(message, state, actions, onRetry, onManual)
            }
        }
    }
}

@Composable
private fun noticeText(message: ChatMessage, state: ChatUiState): String = when (message.state) {
    MessageState.Replied -> stringResource(
        when {
            message.editEntryId != null -> R.string.reply_open_edit
            else -> when (message.reply) {
                StandardReply.Intent -> R.string.reply_intent
                StandardReply.Query -> R.string.reply_query
                StandardReply.UnsupportedRecurrence -> R.string.reply_recurrence
                StandardReply.UnsupportedBoleto -> R.string.reply_boleto
                StandardReply.UnsupportedRefund -> R.string.reply_refund
                StandardReply.UnsupportedInvoicePayment -> R.string.reply_invoice_payment
                StandardReply.UnsupportedCancellation -> R.string.reply_cancellation
                StandardReply.UndoByText -> R.string.reply_undo_text
                StandardReply.NoRecentReceipt -> R.string.reply_no_recent_receipt
                StandardReply.ConsultNeedsP1 -> R.string.reply_consult_needs_p1
                StandardReply.ConsultOutOfCatalog -> R.string.reply_consult_out_of_catalog
                StandardReply.MixedRequest -> R.string.reply_mixed_request
                StandardReply.Consult, StandardReply.ConsultAsk, null -> R.string.reply_query
            }
        },
    )
    MessageState.Refused -> when (message.refuse) {
        RefuseReason.InvalidReference -> stringResource(R.string.refuse_reference)
        RefuseReason.NoCard -> stringResource(R.string.refuse_no_card)
        RefuseReason.NoAccounts -> stringResource(R.string.refuse_no_accounts)
        RefuseReason.InstallmentsNeedCard -> stringResource(R.string.refuse_installments_card)
        RefuseReason.Rejected -> message.note?.let { stringResource(R.string.refuse_rejected_reason, it) } ?: stringResource(R.string.refuse_rejected)
        else -> stringResource(R.string.refuse_unclear)
    }
    MessageState.Failed -> stringResource(
        when (message.failure) {
            "Memory" -> R.string.failed_memory
            "Internal" -> R.string.failed_internal
            else -> R.string.failed_engine
        },
    )
    MessageState.Interrupted -> stringResource(R.string.interrupted)
    MessageState.Canceled -> stringResource(R.string.canceled)
    MessageState.NoModel -> stringResource(if (state.badge == ModelBadge.InstalledNoEngine) R.string.no_model_engine else R.string.no_model)
    MessageState.ResolvedManually -> stringResource(R.string.resolved_manually)
    else -> ""
}

@Composable
private fun NoticeActions(message: ChatMessage, state: ChatUiState, actions: ChatActions, onRetry: () -> Unit, onManual: () -> Unit) {
    val min = Modifier.heightIn(min = AccordSpacing.touchMin)
    fun manual(): @Composable () -> Unit = { TextButton(onClick = onManual, modifier = min) { Text(stringResource(R.string.chat_manual)) } }
    when (message.state) {
        MessageState.Replied -> {
            val edit = message.editEntryId
            when {
                edit != null -> OutlinedButton(onClick = { actions.onEditEntry(edit) }, modifier = min) { Text(stringResource(R.string.reply_action_edit)) }
                message.reply == StandardReply.Query || message.reply == StandardReply.ConsultNeedsP1 || message.reply == StandardReply.ConsultOutOfCatalog -> {
                    OutlinedButton(onClick = actions.onOpenBudget, modifier = min) { Text(stringResource(R.string.reply_action_budget)) }
                    OutlinedButton(onClick = actions.onOpenEntries, modifier = min) { Text(stringResource(R.string.reply_action_entries)) }
                }
                message.reply == StandardReply.UnsupportedInvoicePayment ->
                    OutlinedButton(onClick = actions.onOpenCard, modifier = min) { Text(stringResource(R.string.reply_action_card)) }
                message.reply == StandardReply.UnsupportedCancellation || message.reply == StandardReply.NoRecentReceipt ->
                    OutlinedButton(onClick = actions.onOpenEntries, modifier = min) { Text(stringResource(R.string.reply_action_entries)) }
                message.reply == StandardReply.UndoByText -> Unit
                else -> manual()()
            }
        }
        MessageState.Refused, MessageState.Failed, MessageState.Interrupted, MessageState.Canceled -> {
            // As regras locais são determinísticas: repetir a mesma frase daria a mesma recusa, então só o registro manual resta.
            if (!(message.state == MessageState.Refused && state.badge == ModelBadge.LocalRules)) {
                OutlinedButton(onClick = onRetry, modifier = min) { Text(stringResource(R.string.notice_action_retry)) }
            }
            manual()()
        }
        MessageState.NoModel -> {
            if (state.canInterpretNow) {
                OutlinedButton(onClick = onRetry, modifier = min) { Text(stringResource(R.string.notice_action_interpret_now)) }
            } else {
                OutlinedButton(onClick = actions.onOpenModel, modifier = min) { Text(stringResource(R.string.notice_action_download)) }
            }
            manual()()
        }
        else -> Unit
    }
}
