package app.accord.ui.chat

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordDialogColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.chat.ChatConversation
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing

@Composable
fun ConversationsScreen(
    conversations: List<ChatConversation>, padding: PaddingValues,
    onBack: () -> Unit, onCreate: () -> Unit, onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var renaming by remember { mutableStateOf<ChatConversation?>(null) }
    var deleting by remember { mutableStateOf<ChatConversation?>(null) }
    Column(Modifier.fillMaxSize().padding(padding).padding(AccordSpacing.s4)) {
        DetailHeader(stringResource(R.string.chat_conversations), onBack)
        TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_conversation_new)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            items(conversations, key = { it.id }) { conversation ->
                AccordCard {
                    Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3)) {
                        TextButton(onClick = { onSelect(conversation.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                            Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                        }
                        if (conversation.selected) Text(stringResource(R.string.chat_conversation_current), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { renaming = conversation }) { Text(stringResource(R.string.chat_conversation_rename)) }
                            TextButton(onClick = { deleting = conversation }) { Text(stringResource(R.string.chat_conversation_delete)) }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { conversation ->
        var title by rememberSaveable(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(containerColor = accordDialogColor(), onDismissRequest = { renaming = null }, title = { Text(stringResource(R.string.chat_conversation_rename)) },
            text = { OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.chat_conversation_name)) }, singleLine = true) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onRename(conversation.id, title.trim()); renaming = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    deleting?.let { conversation ->
        AlertDialog(containerColor = accordDialogColor(), onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.chat_conversation_delete_title, conversation.title)) },
            text = { Text(stringResource(R.string.chat_conversation_delete_body)) },
            confirmButton = { TextButton(onClick = { onDelete(conversation.id); deleting = null }) { Text(stringResource(R.string.chat_conversation_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
