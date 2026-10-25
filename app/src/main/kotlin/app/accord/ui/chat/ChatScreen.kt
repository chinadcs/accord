package app.accord.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
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
import app.accord.ui.sync.SyncIndicator
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch
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
) {
    val listState = rememberLazyListState()
    // A conversa acompanha o fim quando chega algo novo, sem mover o foco do campo de mensagem.
    LaunchedEffect(state.items.lastOrNull()?.key) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }
    Column(
        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
    ) {
        ChatHeader(state.badge, actions.onOpenModel, Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2))
        if (state.loaded && state.items.isEmpty()) {
            EmptyChat(onExample, Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                items(state.items, key = { it.key }) { item ->
                    ChatItemView(item, today, state, actions, onCancel, onRetry, onAnswer, onAnswerAmount, onUndo, onDiscard, onManual)
                }
            }
        }
        Composer(state.composer, onComposerChange, onSend, onManual = { onManual(null) })
    }
}

@Composable
private fun ChatHeader(badge: ModelBadge, onOpenModel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            ModelBadgeButton(badge, onOpenModel)
        }
        SyncIndicator()
    }
}

@Composable
private fun ModelBadgeButton(badge: ModelBadge, onClick: () -> Unit) {
    val text = when (badge) {
        ModelBadge.NotInstalled -> stringResource(R.string.chat_model_not_installed)
        is ModelBadge.Downloading -> badge.percent?.let { stringResource(R.string.chat_model_downloading_percent, it) } ?: stringResource(R.string.chat_model_downloading)
        ModelBadge.Working -> stringResource(R.string.chat_model_working)
        ModelBadge.Installed -> stringResource(R.string.chat_model_installed)
        ModelBadge.InstalledNoEngine -> stringResource(R.string.chat_model_no_engine)
        ModelBadge.Problem -> stringResource(R.string.chat_model_problem)
    }
    val description = stringResource(R.string.chat_model_open)
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = "$text. $description" },
    ) {
        DecorativeIcon(
            if (badge == ModelBadge.Installed) Lucide.drawable.lucide_ic_circle_check else Lucide.drawable.lucide_ic_cpu,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = AccordSpacing.s1))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onExample: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chat_empty_body), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            listOf(R.string.chat_example_expense, R.string.chat_example_pix, R.string.chat_example_income).forEach { res ->
                val example = stringResource(res)
                OutlinedButton(onClick = { onExample(example) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(example) }
            }
        }
    }
}

@Composable
private fun Composer(text: String, onChange: (String) -> Unit, onSend: () -> Unit, onManual: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin),
                    label = { Text(stringResource(R.string.chat_composer_label)) },
                    placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                )
                Button(onClick = onSend, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.chat_send))
                }
            }
            TextButton(onClick = onManual, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.chat_manual))
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
) {
    when (item) {
        is ChatItem.User -> UserBubble(item.message.text)
        is ChatItem.Working -> WorkingCard(item.message, onCancel = { onCancel(item.message) }, onManual = { onManual(item.message) })
        is ChatItem.Receipt -> ReceiptCard(item, today, actions, onAnswer, onUndo)
        is ChatItem.Question -> QuestionCard(item, onAnswer, onAnswerAmount, onDiscard, onManual = { onManual(item.message) })
        is ChatItem.Notice -> NoticeCard(item.message, state, actions, onRetry = { onRetry(item.message) }, onManual = { onManual(item.message) })
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(
                text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(AccordSpacing.s3),
                // O TalkBack lê quem falou antes do texto.
            )
        }
    }
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
private fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) { content() }
}

// ---- recibo ----

@Composable
private fun ReceiptCard(
    item: ChatItem.Receipt,
    today: LocalDate,
    actions: ChatActions,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
    onUndo: (String) -> Unit,
) {
    val receipt = item.receipt
    val summary = receipt.summary
    val active = receipt.state == ReceiptState.Active
    // Atualização anunciada de forma educada: o foco continua no campo de mensagem.
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            ReceiptLines(summary, today)
            if (receipt.state == ReceiptState.ChangedLater) StateTag(stringResource(R.string.receipt_changed_later), Lucide.drawable.lucide_ic_pencil)
            if (receipt.state == ReceiptState.Undone) StateTag(stringResource(R.string.receipt_undone), Lucide.drawable.lucide_ic_undo_2)
            Text(stringResource(R.string.receipt_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.question != null) {
                CategoryChoices(item.question, summary, onAnswer)
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
                    if (item.undoAvailable) {
                        TextButton(onClick = { onUndo(receipt.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.receipt_action_undo))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptLines(summary: ReceiptSummary, today: LocalDate) {
    val total = Money(summary.totalCents).format()
    val date = LocalDate.parse(summary.dateIso)
    val title = when {
        summary.nature == Nature.Income && summary.expectedIncome -> stringResource(R.string.receipt_income_expected, total, date.fullLabel())
        summary.nature == Nature.Income -> stringResource(R.string.receipt_income, total)
        else -> stringResource(R.string.receipt_expense, total)
    }
    Text(title, style = MaterialTheme.typography.titleMedium)

    val payment = paymentText(summary)
    if (summary.nature == Nature.Expense) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (summary.toClassify) {
                StateTag(stringResource(R.string.receipt_to_classify), Lucide.drawable.lucide_ic_tag)
                if (payment != null) Text(payment, style = MaterialTheme.typography.bodyMedium)
            } else {
                val category = listOfNotNull(summary.parentName, summary.categoryName).joinToString(" > ")
                Text(
                    if (payment != null) stringResource(R.string.receipt_category_payment, category, payment) else category,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
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
        ChatPayment.Card -> stringResource(R.string.receipt_payment_card)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChoices(
    question: ChatQuestionRecord,
    summary: ReceiptSummary,
    onAnswer: (ChatQuestionRecord, app.accord.core.chat.QuestionOption) -> Unit,
) {
    Text(
        stringResource(R.string.question_category, "${summary.description} ${Money(summary.totalCents).format()}"),
        style = MaterialTheme.typography.bodyLarge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        question.options.forEach { option ->
            OutlinedButton(
                onClick = { onAnswer(question, option) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = option.label },
            ) { Text(option.label) }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; invalid = false },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.question_amount_field)) },
            isError = invalid,
            supportingText = if (invalid) ({ Text(stringResource(R.string.question_amount_error)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        )
        Button(
            onClick = { onAnswerAmount(question, value) { invalid = true } },
            enabled = value.isNotBlank(),
            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(R.string.question_amount_answer)) }
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
                null -> R.string.reply_query
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
                message.reply == StandardReply.Query -> {
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
            OutlinedButton(onClick = onRetry, modifier = min) { Text(stringResource(R.string.notice_action_retry)) }
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
