package app.accord.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.ProblemKind
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.ServiceState
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncStage
import app.accord.core.sync.SyncStatus
import app.accord.ui.access.MessageKind
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.composables.icons.lucide.R as Lucide

private val DISPLAY_ZONE = ZoneId.of("America/Sao_Paulo")
private val SYNC_TIME = DateTimeFormatter.ofPattern("dd/MM, HH'h'mm", Locale.forLanguageTag("pt-BR"))
private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

/** `22/10, 10h30`: dia e hora da última sincronização concluída, só para exibição. */
fun syncTimeLabel(millis: Long): String = SYNC_TIME.format(Instant.ofEpochMilli(millis).atZone(DISPLAY_ZONE))

/** Aviso fixo da tela, escolhido pelo estado real do motor: a causa da parada quando é conhecida. */
sealed interface SyncNotice {
    val kind: MessageKind
    val title: Int
    val body: Int?

    data object Update : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_update_title; override val body = R.string.sync_update_body }
    data object OtherAccount : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_other_account_title; override val body = R.string.sync_other_account_body }
    data object Session : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_session_title; override val body = R.string.sync_session_body }
    data object NotMember : SyncNotice { override val kind = MessageKind.Error; override val title = R.string.sync_not_member_title; override val body = R.string.sync_not_member_body }
    data object Paused : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_paused_title; override val body = R.string.sync_paused_body }
    data object Unavailable : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_service_title; override val body = R.string.sync_service_body }
    data object Quota : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_quota_title; override val body = R.string.sync_quota_body }
    data object Offline : SyncNotice { override val kind = MessageKind.Attention; override val title = R.string.sync_offline_title; override val body = null }
    data object Failed : SyncNotice { override val kind = MessageKind.Error; override val title = R.string.sync_failed_title; override val body = R.string.sync_failed_body }
}

fun SyncStatus.notice(): SyncNotice? = when {
    updateRequired -> SyncNotice.Update
    session == SessionState.OtherAccount -> SyncNotice.OtherAccount
    session == SessionState.NeedsRenewal || session == SessionState.SignedOut -> SyncNotice.Session
    lastFailure == RemoteFailure.NotMember -> SyncNotice.NotMember
    service == ServiceState.Paused -> SyncNotice.Paused
    service == ServiceState.QuotaExceeded -> SyncNotice.Quota
    service == ServiceState.Unavailable -> SyncNotice.Unavailable
    connection == ConnectionState.Offline -> SyncNotice.Offline
    lastFailure != null -> SyncNotice.Failed
    else -> null
}

/** A visão do casal só é dada como possivelmente desatualizada quando há motivo; nunca há selo de "em dia". */
fun SyncStatus.viewMayBeOutdated(nowMillis: Long): Boolean =
    summary.awaitingOperations > 0 || summary.openConflicts > 0 || summary.rejectedOperations > 0 ||
        lastFailure != null || summary.lastSyncMillis == null || nowMillis - summary.lastSyncMillis!! > WEEK_MILLIS

@Composable
fun SyncRoute(
    viewModel: SyncViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenConflict: (String) -> Unit,
    onRenew: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SyncScreen(
        status, ui, padding, System.currentTimeMillis(),
        onBack = onBack, onSyncNow = viewModel::syncNow, onOpenEntry = onOpenEntry, onOpenConflict = onOpenConflict, onRenew = onRenew,
        onRetry = viewModel::retry, onAskDiscard = viewModel::askDiscard, onDiscard = viewModel::discard, onCancelDiscard = viewModel::cancelDiscard,
    )
}

@Composable
fun SyncScreen(
    status: SyncStatus?,
    ui: SyncUi,
    padding: PaddingValues,
    nowMillis: Long,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenConflict: (String) -> Unit,
    onRenew: () -> Unit,
    onRetry: (String) -> Unit,
    onAskDiscard: (String) -> Unit,
    onDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.sync_title), onBack)
        if (status == null) {
            Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        status.notice()?.let { notice -> NoticeCard(notice, onRenew = if (notice == SyncNotice.Session) onRenew else null) }
        SummaryCard(status, nowMillis)
        SyncNowButton(status, ui, onSyncNow)
        ui.message?.let { MessageBanner(it) }
        if (ui.conflicts.isNotEmpty()) ConflictsCard(ui, onOpenConflict)
        if (ui.attention.isNotEmpty()) AttentionCard(ui, onOpenEntry, onRetry, onAskDiscard)
        Text(stringResource(R.string.sync_received_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (ui.confirmDiscard != null) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text(stringResource(R.string.sync_op_discard_title)) },
            text = { Text(stringResource(R.string.sync_op_discard_body)) },
            confirmButton = { TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.sync_op_discard_confirm)) } },
            dismissButton = { TextButton(onClick = onCancelDiscard, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.sync_op_discard_cancel)) } },
        )
    }
}

@Composable
private fun NoticeCard(notice: SyncNotice, onRenew: (() -> Unit)?) {
    val (container, content, icon) = when (notice.kind) {
        MessageKind.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Lucide.drawable.lucide_ic_circle_alert)
        else -> Triple(Accord.colors.attentionContainer, Accord.colors.onAttentionContainer, Lucide.drawable.lucide_ic_triangle_alert)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3), verticalAlignment = Alignment.Top) {
                DecorativeIcon(icon, tint = content)
                Text(stringResource(notice.title), style = MaterialTheme.typography.titleSmall)
            }
            notice.body?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            onRenew?.let {
                OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.sync_session_action)) }
            }
        }
    }
}

@Composable
private fun SummaryCard(status: SyncStatus, nowMillis: Long) {
    val summary = status.summary
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(
                summary.lastSyncMillis?.let { stringResource(R.string.sync_last_done, syncTimeLabel(it)) } ?: stringResource(R.string.sync_last_never),
                style = MaterialTheme.typography.bodyLarge,
            )
            // A contagem é de operações, não de despesas, e nunca fala do outro aparelho.
            if (summary.awaitingOperations > 0) {
                Text(pluralStringResource(R.plurals.sync_pending_ops, summary.awaitingOperations, summary.awaitingOperations), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.sync_count_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.sync_nothing_pending), style = MaterialTheme.typography.titleSmall)
                if (summary.openConflicts == 0 && summary.rejectedOperations == 0) {
                    Text(stringResource(R.string.sync_nothing_pending_note), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (status.viewMayBeOutdated(nowMillis)) {
                Text(
                    stringResource(
                        when {
                            summary.awaitingOperations > 0 -> R.string.sync_view_outdated_queue
                            summary.lastSyncMillis != null && nowMillis - summary.lastSyncMillis!! > WEEK_MILLIS -> R.string.sync_view_outdated_week
                            else -> R.string.sync_view_outdated
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncNowButton(status: SyncStatus, ui: SyncUi, onSyncNow: () -> Unit) {
    val running = ui.working || status.stage != SyncStage.Idle
    // Sem conexão na última tentativa não bloqueia: a pessoa pode tentar de novo, e o reenvio é idempotente.
    val canSync = status.session == SessionState.Valid && !status.updateRequired
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Button(onClick = onSyncNow, enabled = canSync && !running, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.sync_now))
        }
        if (running) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(if (status.stage == SyncStage.Receiving) R.string.sync_running_receive else R.string.sync_running_send),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(stringResource(R.string.sync_running_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (status.session == SessionState.Valid && status.connection == ConnectionState.Offline) {
            Text(stringResource(R.string.sync_unavailable_now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConflictsCard(ui: SyncUi, onOpenConflict: (String) -> Unit) {
    val stackConflictAction = LocalDensity.current.fontScale >= 1.5f
    Surface(color = Accord.colors.attentionContainer, contentColor = Accord.colors.onAttentionContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(
                pluralStringResource(R.plurals.sync_conflicts_count, ui.conflicts.size, ui.conflicts.size),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() },
            )
            ui.conflicts.forEach { row ->
                val itemModifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                    .clickable(role = Role.Button) { onOpenConflict(row.operationId) }
                if (stackConflictAction) {
                    Column(itemModifier, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        ConflictDescription(row)
                        Text(
                            stringResource(R.string.sync_conflict_review),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        itemModifier,
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    ) {
                        ConflictDescription(row, Modifier.weight(1f))
                        Text(stringResource(R.string.sync_conflict_review), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text(stringResource(R.string.sync_conflicts_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConflictDescription(row: ConflictRowUi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(listOfNotNull(stringResource(row.kind), row.subject).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        if (row.deferred) Text(stringResource(R.string.sync_conflict_deferred), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AttentionCard(ui: SyncUi, onOpenEntry: (String) -> Unit, onRetry: (String) -> Unit, onAskDiscard: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.sync_attention_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            ui.attention.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(listOfNotNull(stringResource(row.title), row.subject).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (row.kind == ProblemKind.Rejected) R.string.sync_op_rejected else R.string.sync_op_held),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.kind == ProblemKind.Rejected) {
                        Text(stringResource(R.string.sync_op_rejected_reason, reasonLabel(row.failure)), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.sync_op_held_note), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        row.entryId?.let { id ->
                            OutlinedButton(onClick = { onOpenEntry(id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.sync_attention_open_entry))
                            }
                        }
                        if (row.kind == ProblemKind.Rejected) {
                            OutlinedButton(onClick = { onRetry(row.operationId) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.sync_op_retry))
                            }
                            TextButton(onClick = { onAskDiscard(row.operationId) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.sync_op_discard))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Causa da recusa em palavras, sem valores nem descrições; o código desconhecido não é inventado. */
@Composable
private fun reasonLabel(failure: String?): String {
    val reason = failure?.substringBefore(':')
    return stringResource(
        when (reason) {
            "InvalidEnvelope" -> R.string.sync_reason_InvalidEnvelope
            "TypeNotAllowed" -> R.string.sync_reason_TypeNotAllowed
            "ForeignSpace" -> R.string.sync_reason_ForeignSpace
            "DivergentOperation" -> R.string.sync_reason_DivergentOperation
            "ProtocolUnsupported" -> R.string.sync_reason_ProtocolUnsupported
            "CategoryRemoved" -> R.string.sync_reason_CategoryRemoved
            "CategoryInUse" -> R.string.sync_reason_CategoryInUse
            else -> R.string.sync_reason_other
        },
    )
}
