package app.accord.ui.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.sync.ConflictChoice
import app.accord.core.sync.ConflictEntity
import app.accord.core.sync.ConflictVersion
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.categoryPath
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.fullLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ConflictRoute(viewModel: ConflictViewModel, padding: PaddingValues, onFinished: () -> Unit, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    LaunchedEffect(ui.finished) { if (ui.finished) onFinished() }
    BackHandler(onBack = onBack)
    ConflictScreen(ui, padding, onSelect = viewModel::select, onApply = viewModel::apply, onDefer = viewModel::defer, onBack = onBack)
}

@Composable
fun ConflictScreen(
    ui: ConflictUi,
    padding: PaddingValues,
    onSelect: (ConflictChoice) -> Unit,
    onApply: () -> Unit,
    onDefer: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.conflict_title), onBack)
        val view = ui.view
        if (view == null) {
            if (ui.loading) Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            ui.message?.let { MessageBanner(it) }
            return@Column
        }
        Text(stringResource(R.string.conflict_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.stale) MessageBanner(ScreenMessage(MessageKind.Attention, R.string.conflict_stale))
        if (ui.offline) MessageBanner(ScreenMessage(MessageKind.Neutral, R.string.conflict_offline))

        VersionCard(
            title = R.string.conflict_mine, subtitle = stringResource(R.string.conflict_mine_when),
            entities = view.entities, side = Side.Mine, lookup = ui.lookup,
            selected = ui.selected == ConflictChoice.Local, enabled = !ui.working, onKeep = { onSelect(ConflictChoice.Local) },
        )
        VersionCard(
            title = R.string.conflict_theirs,
            subtitle = view.entities.firstNotNullOfOrNull { it.remote?.serviceTimeMillis }
                ?.let { stringResource(R.string.conflict_theirs_when, syncTimeLabel(it)) }.orEmpty(),
            entities = view.entities, side = Side.Theirs, lookup = ui.lookup,
            selected = ui.selected == ConflictChoice.Remote, enabled = !ui.working, onKeep = { onSelect(ConflictChoice.Remote) },
        )
        if (view.heldOperationIds.isNotEmpty()) {
            Text(stringResource(R.string.conflict_held_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ui.selected == null) {
            Text(stringResource(R.string.conflict_pick_hint), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onApply, enabled = ui.selected != null && !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(if (ui.working) R.string.conflict_applying else R.string.conflict_apply))
        }
        OutlinedButton(onClick = onDefer, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.conflict_later))
        }
        ui.message?.let { MessageBanner(it) }
    }
}

private enum class Side { Mine, Theirs }

@Composable
private fun VersionCard(
    title: Int,
    subtitle: String,
    entities: List<ConflictEntity>,
    side: Side,
    lookup: ConflictLookup,
    selected: Boolean,
    enabled: Boolean,
    onKeep: () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = border, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entities.forEach { entity ->
                val version: ConflictVersion? = if (side == Side.Mine) entity.local else entity.remote
                EntityBlock(entity, version, lookup, side, showName = entities.size > 1)
            }
            // O texto e o marcador mudam juntos: a seleção não depende só da cor.
            Row(
                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onKeep),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Text(
                    stringResource(if (selected) R.string.conflict_keeping else R.string.conflict_keep),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = AccordSpacing.s2),
                )
            }
        }
    }
}

@Composable
private fun EntityBlock(entity: ConflictEntity, version: ConflictVersion?, lookup: ConflictLookup, side: Side, showName: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        if (showName) Text(stringResource(ConflictFormat.entityLabel(entity.ref.type)), style = MaterialTheme.typography.labelLarge)
        if (version == null) {
            Text(stringResource(R.string.conflict_missing_remote), style = MaterialTheme.typography.bodyMedium)
            return
        }
        ConflictFormat.lines(entity.ref.type, version.state, entity.differingFields).forEach { line ->
            val label = stringResource(line.label)
            val value = fieldText(line.value, lookup)
            val differs = stringResource(R.string.conflict_differs)
            Surface(
                color = if (line.differs) Accord.colors.attentionContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (line.differs) Accord.colors.onAttentionContainer else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(label, value, differs.takeIf { line.differs }).joinToString(", ")
                },
            ) {
                Row(Modifier.padding(horizontal = AccordSpacing.s2, vertical = AccordSpacing.s1), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                    // O destaque tem texto, não só cor.
                    if (line.differs) Text(differs, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        if (side == Side.Mine && version.tombstone) {
            Text(stringResource(R.string.conflict_deleted), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Valor do campo em português, com nomes no lugar de identificadores. */
@Composable
fun fieldText(value: FieldValue, lookup: ConflictLookup): String {
    val none = stringResource(R.string.value_none)
    return when (value) {
        is FieldValue.Text -> value.text?.takeIf { it.isNotBlank() } ?: none
        is FieldValue.Cents -> value.cents?.let { Money(it).format() } ?: none
        is FieldValue.Day -> value.epochDay?.let { LocalDate.ofEpochDay(it).fullLabel() } ?: none
        is FieldValue.MonthValue -> value.month?.let { runCatching { YearMonth.parse(it).label() }.getOrDefault(it) } ?: none
        is FieldValue.CategoryRef -> value.id?.let { id ->
            categoryPath(lookup.categories, id) ?: stringResource(R.string.value_unknown)
        } ?: stringResource(R.string.situation_to_classify)
        is FieldValue.AccountRef -> value.id?.let { id ->
            lookup.accounts[id]?.let { "${it.nickname}" } ?: stringResource(R.string.value_unknown)
        } ?: none
        is FieldValue.Flag -> stringResource(if (value.value) R.string.value_yes else R.string.value_no)
        is FieldValue.Deleted -> stringResource(if (value.deleted) R.string.conflict_deleted else R.string.conflict_present)
        is FieldValue.Installments ->
            if (value.uniformCents != null) {
                pluralStringResource(R.plurals.installments_summary_uniform, value.count, value.count, Money(value.uniformCents).format())
            } else {
                pluralStringResource(R.plurals.installments_summary_mixed, value.count, value.count)
            }
        is FieldValue.Count -> pluralStringResource(R.plurals.items_count, value.count, value.count)
        is FieldValue.Limits -> value.lines.joinToString("\n") { line ->
            val month = runCatching { YearMonth.parse(line.month).label() }.getOrDefault(line.month)
            val total = line.totalCents?.let { Money(it).format() } ?: none
            val split = line.split.joinToString("; ") { (id, cents) -> "${categoryName(lookup.categories, id)} ${Money(cents).format()}" }
            listOfNotNull("$month: $total", split.takeIf { it.isNotEmpty() }).joinToString(" · ")
        }.ifEmpty { none }
    }
}

private fun categoryName(categories: Map<String, CategoryNode>, id: String): String = categories[id]?.name ?: "—"
