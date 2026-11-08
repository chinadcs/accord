package app.accord.ui.space

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.sync.AccessAction
import app.accord.core.sync.AccessState
import app.accord.core.sync.InviteCode
import app.accord.core.sync.InviteView
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.composables.icons.lucide.R as Lucide

private val SERVICE_ZONE = ZoneId.of("America/Sao_Paulo")
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

/** `22/10/2026 10:00`, no fuso de referência do app, a partir do relógio do serviço. */
fun serviceTimeLabel(millis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(SERVICE_ZONE))

/**
 * Tela 02. [firstRun] é o fluxo de entrada (depois do acesso): mostra Continuar para os primeiros
 * passos. Aberta por Mais, só gerencia o espaço.
 */
@Composable
fun SpaceRoute(
    viewModel: SpaceViewModel,
    padding: PaddingValues,
    firstRun: Boolean,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val access by viewModel.accessState.collectAsStateWithLifecycle()
    RefreshOnResume { if (access.space != null) viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.leave() } }
    val clipboard = LocalClipboard.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.space_code_clip_label)
    SpaceScreen(
        ui, access, padding, firstRun,
        onTab = viewModel::selectTab, onName = viewModel::onName, onCode = viewModel::onCode,
        onCreate = viewModel::create, onJoin = viewModel::join, onGenerate = viewModel::generateInvite,
        onRevoke = viewModel::revokeInvite,
        onScan = {
            InviteScanner.scan(context) { result ->
                when (result) {
                    is InviteScanner.Result.Scanned -> viewModel.onScanned(result.raw)
                    InviteScanner.Result.Unavailable -> viewModel.scanUnavailable()
                    InviteScanner.Result.Canceled -> Unit
                }
            }
        },
        onRefresh = { viewModel.refresh(announce = true) },
        onCopy = { code ->
            scope.launch {
                val clip = ClipData.newPlainText(clipLabel, code)
                // O código é um segredo de uso único: o Android esconde a prévia quando suporta.
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                }
                clipboard.setClipEntry(ClipEntry(clip))
                viewModel.codeCopied()
            }
        },
        onContinue = onContinue, onBack = onBack,
    )
}

@Composable
fun SpaceScreen(
    ui: SpaceUi,
    access: AccessState,
    padding: PaddingValues,
    firstRun: Boolean,
    onTab: (SpaceTab) -> Unit,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onGenerate: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
    onRevoke: () -> Unit = {},
    onScan: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
                }
            }
            Text(stringResource(R.string.space_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        }
        if (access.space == null) {
            NoSpaceContent(ui, access, onTab, onName, onCode, onCreate, onJoin, onScan)
        } else {
            ManageContent(ui, access, firstRun, onGenerate, onRefresh, onCopy, onContinue, onRevoke)
        }
        ui.message?.let { MessageBanner(it) }
    }
}

@Composable
private fun NoSpaceContent(
    ui: SpaceUi,
    access: AccessState,
    onTab: (SpaceTab) -> Unit,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onScan: () -> Unit,
) {
    Text(stringResource(R.string.space_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryTabRow(selectedTabIndex = ui.tab.ordinal, modifier = Modifier.selectableGroup()) {
        Tab(selected = ui.tab == SpaceTab.Create, onClick = { onTab(SpaceTab.Create) }, enabled = !ui.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.space_tab_create), modifier = Modifier.padding(AccordSpacing.s2))
        }
        Tab(selected = ui.tab == SpaceTab.Join, onClick = { onTab(SpaceTab.Join) }, enabled = !ui.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.space_tab_join), modifier = Modifier.padding(AccordSpacing.s2))
        }
    }
    when (ui.tab) {
        SpaceTab.Create -> {
            access.pendingSpaceName?.let { name ->
                MessageBanner(app.accord.ui.access.ScreenMessage(app.accord.ui.access.MessageKind.Attention, R.string.space_pending_creation, listOf(name)))
            }
            OutlinedTextField(
                value = ui.name, onValueChange = onName, singleLine = true, enabled = !ui.working,
                label = { Text(stringResource(R.string.space_name)) },
                placeholder = { Text(stringResource(R.string.space_name_hint)) },
                isError = ui.nameError != null,
                supportingText = ui.nameError?.let { res -> { Text(stringResource(res)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onCreate, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (ui.working || access.busy == AccessAction.CreatingSpace) R.string.space_creating else R.string.space_create_action))
            }
        }
        SpaceTab.Join -> {
            Text(stringResource(R.string.space_join_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onScan, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("space-scan-qr")) {
                Text(stringResource(R.string.space_scan_qr))
            }
            OutlinedTextField(
                value = ui.code, onValueChange = onCode, singleLine = true, enabled = !ui.working,
                label = { Text(stringResource(R.string.space_code)) },
                isError = ui.codeError != null,
                supportingText = ui.codeError?.let { res -> { Text(stringResource(res)) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done, autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onJoin, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (ui.working) R.string.space_joining else R.string.space_join_action))
            }
        }
    }
}

@Composable
private fun ManageContent(
    ui: SpaceUi,
    access: AccessState,
    firstRun: Boolean,
    onGenerate: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onContinue: () -> Unit,
    onRevoke: () -> Unit,
) {
    val space = checkNotNull(access.space)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.space_section_space), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(space.name, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.space_section_participants), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val members = access.participants?.items?.size ?: 1
            Text(
                stringResource(R.string.space_you_connected) + " · " + stringResource(R.string.space_members_count, members, InviteCode.MAX_MEMBERS),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                access.participants?.let { stringResource(R.string.space_last_check, serviceTimeLabel(it.checkedAtMillis)) } ?: stringResource(R.string.space_never_checked),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRefresh, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.space_check_participants))
            }
        }
    }
    InviteBlock(ui, access, onGenerate, onCopy, onRevoke)
    if (firstRun) {
        val bothIn = (access.participants?.items?.size ?: 0) >= 2
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(if (bothIn || access.invite == null) R.string.space_continue_steps else R.string.space_continue_later))
        }
    }
}

@Composable
private fun InviteBlock(ui: SpaceUi, access: AccessState, onGenerate: () -> Unit, onCopy: (String) -> Unit, onRevoke: () -> Unit) {
    val invite: InviteView? = access.invite
    val full = (access.participants?.items?.size ?: 0) >= InviteCode.MAX_MEMBERS
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Text(stringResource(R.string.space_invite_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (full) {
            // Com o espaço lotado, gerar código deixa de ser oferecido.
            Text(stringResource(R.string.space_invite_full), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(stringResource(R.string.space_invite_note), style = MaterialTheme.typography.bodyMedium)
        val slots = InviteCode.MAX_MEMBERS - (access.participants?.items?.size ?: 1)
        Text(pluralStringResource(R.plurals.space_invite_slots, slots, slots), style = MaterialTheme.typography.bodyMedium)
        val code = invite?.code
        if (code != null) {
            // O TalkBack lê o código caractere a caractere, não como uma palavra.
            val spoken = stringResource(R.string.space_code_content_description, code.toList().joinToString(", "))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Text(
                    InviteCode.grouped(code), style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s4).semantics { contentDescription = spoken },
                )
            }
            InviteQrImage(code, Modifier.align(Alignment.CenterHorizontally).testTag("invite-qr"))
            Text(stringResource(R.string.space_invite_shown_once), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onCopy(code) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_copy), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.space_invite_copy), modifier = Modifier.padding(start = AccordSpacing.s2))
            }
        }
        if (invite != null) {
            Text(
                when {
                    invite.expired -> stringResource(R.string.space_invite_expired)
                    code != null -> stringResource(R.string.space_invite_valid_until, serviceTimeLabel(invite.validUntilMillis))
                    else -> stringResource(R.string.space_invite_pending_hidden, serviceTimeLabel(invite.validUntilMillis))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onGenerate, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(
                stringResource(
                    when {
                        ui.working || access.busy == AccessAction.GeneratingInvite -> R.string.space_invite_generating
                        invite != null -> R.string.space_invite_generate_again
                        else -> R.string.space_invite_generate
                    },
                ),
            )
        }
        if (invite != null && !invite.expired) {
            OutlinedButton(onClick = onRevoke, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("invite-revoke")) {
                Text(stringResource(if (access.busy == AccessAction.RevokingInvite) R.string.space_invite_revoking else R.string.space_invite_revoke))
            }
        }
    }
}
