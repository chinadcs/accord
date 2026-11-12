package app.accord.privacy

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.AppContainer
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Leitor sem coordenador, campos de envio ou comandos financeiros. Dados só do titular autenticado. */
@Composable
fun ArchivedChatsScreen(padding: PaddingValues, container: AppContainer, onBack: () -> Unit) {
    val access by container.access.status.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<JsonObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, access.account?.accountId) {
        history = null; failed = false
        selected?.let { id ->
            try { history = container.access.archivedChatData(id)?.let { Json.parseToJsonElement(it).jsonObject } }
            catch (_: Exception) { failed = true }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        DetailHeader(stringResource(R.string.privacy_archived_chats), onBack)
        Text(stringResource(R.string.privacy_archived_read_only))
        access.archivedSpaces.forEach { space ->
            AccordOutlinedButton(onClick = { selected = space.spaceId }, modifier = Modifier.fillMaxWidth()) { Text(space.name) }
        }
        if (failed) Text(stringResource(R.string.privacy_archive_failed), color = MaterialTheme.colorScheme.error)
        history?.let { data ->
            val messages = (data["chat_message"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val receipts = (data["chat_receipt"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val conversations = (data["chat_conversation"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            if (messages.isEmpty()) Text(stringResource(R.string.privacy_archive_empty))
            messages.groupBy { it["conversationId"]?.jsonPrimitive?.contentOrNull ?: "legacy" }.forEach { (conversationId, thread) ->
            Text(conversations.firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == conversationId }?.get("title")?.jsonPrimitive?.contentOrNull ?: "Histórico anterior", style = MaterialTheme.typography.titleMedium)
            thread.sortedBy { it["createdAtMillis"]?.jsonPrimitive?.longOrNull }.forEach { message ->
                AccordCard {
                    Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Text(message["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        val id = message["id"]?.jsonPrimitive?.contentOrNull
                        receipts.filter { it["messageId"]?.jsonPrimitive?.contentOrNull == id }.forEach { receipt ->
                            val summary = receipt["summary"]?.jsonPrimitive?.contentOrNull?.let {
                                runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
                            }
                            summary?.get("description")?.jsonPrimitive?.contentOrNull?.let { Text(it) }
                            summary?.get("totalCents")?.jsonPrimitive?.longOrNull?.let { cents ->
                                Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2)))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
