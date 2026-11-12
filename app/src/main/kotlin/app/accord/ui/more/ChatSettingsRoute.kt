package app.accord.ui.more

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordDialogColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.AppContainer
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.core.chat.ChatImportResult
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.R as Lucide

/**
 * Ajustes do Chat (ADR 0077): hub com rota própria, para o Voltar do sistema fechá-lo. Reúne o modelo no aparelho, exportar, importar e
 * limpar o histórico e, quando houver, os chats de espaços encerrados. Os seletores de arquivo e os diálogos de confirmação moram aqui:
 * o aviso ainda lembra que o arquivo exportado guarda as conversas, sem senha (plano §14). O diálogo aberto sobrevive à rotação.
 */
@Composable
internal fun ChatSettingsRoute(
    container: AppContainer,
    padding: PaddingValues,
    onBack: () -> Unit,
    onModel: () -> Unit,
    onArchivedChats: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val access by container.access.status.collectAsStateWithLifecycle()
    var chatDialog by rememberSaveable { mutableStateOf<ChatHistoryDialog?>(null) }
    val resolver = LocalContext.current.contentResolver
    // O lançador é registrado por chave estável e o retorno usa o escopo da composição atual: depois de uma rotação o resultado chega a esta tela.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = container.exportChat()
            val saved = text != null && runCatching {
                withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("sem saída") }
            }.isSuccess
            container.feedback.post(FeedbackEvent(if (saved) R.string.chat_exported else R.string.chat_export_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } } }.getOrNull()
            val result = text?.let { container.importChat(it) } ?: ChatImportResult.Unreadable
            container.feedback.post(
                when (result) {
                    is ChatImportResult.Imported ->
                        if (result.messages == 0) FeedbackEvent(R.string.chat_imported_none) else FeedbackEvent(R.string.chat_imported_count, listOf(result.messages))
                    ChatImportResult.OtherOwner -> FeedbackEvent(R.string.chat_import_other_owner)
                    ChatImportResult.Unreadable -> FeedbackEvent(R.string.chat_import_unreadable)
                },
            )
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.chat_settings_title), onBack)
        AccordCard {
            Column {
                MoreRow(R.string.more_chat_model, Lucide.drawable.lucide_ic_cpu, onModel)
                MoreRow(R.string.more_chat_export, Lucide.drawable.lucide_ic_download, { chatDialog = ChatHistoryDialog.Export })
                MoreRow(R.string.more_chat_import, Lucide.drawable.lucide_ic_upload, { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                MoreRow(R.string.more_chat_clear, Lucide.drawable.lucide_ic_trash_2, { chatDialog = ChatHistoryDialog.Clear })
                if (access.archivedSpaces.isNotEmpty()) {
                    MoreRow(R.string.privacy_archived_chats, Lucide.drawable.lucide_ic_history, onArchivedChats)
                }
            }
        }
    }

    chatDialog?.let { dialog ->
        ChatHistoryDialogView(
            dialog,
            onConfirm = {
                chatDialog = null
                when (dialog) {
                    ChatHistoryDialog.Export -> exportLauncher.launch("historico-do-chat.json")
                    ChatHistoryDialog.Clear -> scope.launch {
                        container.clearChat()
                        container.feedback.post(FeedbackEvent(R.string.chat_cleared))
                    }
                }
            },
            onDismiss = { chatDialog = null },
        )
    }
}

/** Confirmação aberta nos Ajustes do Chat; guardada com `rememberSaveable` (enum é serializável) para não sumir ao girar a tela. */
internal enum class ChatHistoryDialog { Export, Clear }

@Composable
private fun ChatHistoryDialogView(dialog: ChatHistoryDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val export = dialog == ChatHistoryDialog.Export
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (export) R.string.chat_export_title else R.string.chat_clear_title)) },
        text = { Text(stringResource(if (export) R.string.chat_export_body else R.string.chat_clear_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (export) R.string.chat_export_confirm else R.string.chat_clear_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_dialog_cancel)) } },
    )
}
