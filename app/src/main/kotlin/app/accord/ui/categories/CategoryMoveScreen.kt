package app.accord.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.ui.categoryColor
import app.accord.ui.colorKey
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordSpacing

@Composable
fun CategoryMoveRoute(viewModel: CategoryMoveViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    CategoryMoveScreen(
        content, padding, onBack,
        onDestination = viewModel::setDestination, onScope = viewModel::setScope, onAlsoRemove = viewModel::setAlsoRemove,
        onConfirmDiscard = viewModel::setConfirmDiscard, onConfirm = viewModel::confirm,
    )
}

@Composable
fun CategoryMoveScreen(
    content: CategoryMoveContent,
    padding: PaddingValues,
    onBack: () -> Unit,
    onDestination: (String) -> Unit,
    onScope: (ScopeChoice) -> Unit,
    onAlsoRemove: (Boolean) -> Unit,
    onConfirmDiscard: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        val title = if ((content as? CategoryMoveContent.Ready)?.removeMode == true) R.string.category_remove_title else R.string.category_move_title
        DetailHeader(stringResource(title), onBack)
        when (content) {
            CategoryMoveContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CategoryMoveContent.Missing -> Text(stringResource(R.string.category_edit_missing), style = MaterialTheme.typography.bodyLarge)
            is CategoryMoveContent.Ready -> Body(content, onDestination, onScope, onAlsoRemove, onConfirmDiscard, onConfirm)
        }
    }
}

@Composable
private fun Body(
    s: CategoryMoveContent.Ready,
    onDestination: (String) -> Unit,
    onScope: (ScopeChoice) -> Unit,
    onAlsoRemove: (Boolean) -> Unit,
    onConfirmDiscard: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        if (s.subcategoryCount > 0) stringResource(R.string.category_move_source_with_subs, s.source.name, s.subcategoryCount)
        else stringResource(R.string.category_move_source, s.source.name),
        style = MaterialTheme.typography.titleMedium,
    )

    if (s.removeMode && s.nothingToMove) {
        Text(stringResource(R.string.category_remove_empty, s.source.name), style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(stringResource(R.string.category_move_destination), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            s.options.forEach { option -> DestinationRow(option, s.categories, option.id == s.destinationId) { onDestination(option.id) } }
        }
        if (s.destinationError) {
            Text(stringResource(R.string.categories_move_pick), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (!s.removing) {
            Text(stringResource(R.string.category_move_scope), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                ChoiceRow(stringResource(R.string.category_move_scope_all), s.scope == ScopeChoice.All) { onScope(ScopeChoice.All) }
                ChoiceRow(stringResource(R.string.category_move_scope_from), s.scope == ScopeChoice.FromThisMonth) { onScope(ScopeChoice.FromThisMonth) }
            }
        }
        if (!s.removeMode) {
            ToggleRow(stringResource(R.string.category_move_also_remove, s.source.name), s.alsoRemove, enabled = s.scope == ScopeChoice.All, onChange = onAlsoRemove)
        }
        Impact(s)
    }

    if (s.limitMonths.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(
                    stringResource(R.string.category_remove_limits, s.limitMonths.joinToString(", ") { it.label() }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                )
                ToggleRow(stringResource(R.string.category_remove_limits_confirm), s.confirmDiscard, enabled = true, onChange = onConfirmDiscard)
            }
        }
    }

    Text(stringResource(R.string.category_move_known_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    val label = when {
        s.removeMode && s.nothingToMove -> R.string.category_remove_confirm
        s.removing -> R.string.category_move_and_remove_confirm
        else -> R.string.category_move_confirm
    }
    Button(
        onClick = onConfirm, enabled = !s.working,
        colors = if (s.removing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
    ) { Text(stringResource(label)) }
}

@Composable
private fun Impact(s: CategoryMoveContent.Ready) {
    val impact = s.impact ?: return
    val text = if (impact.entries == 0) {
        stringResource(R.string.category_move_impact_none)
    } else {
        pluralStringResource(R.plurals.category_move_impact, impact.entries, impact.entries, impact.months.joinToString(", ") { it.label() })
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(AccordSpacing.s3))
    }
}

@Composable
private fun DestinationRow(option: CategoryNode, categories: Map<String, CategoryNode>, selected: Boolean, onClick: () -> Unit) {
    val indent = if (option.parentId == null) 0 else 1
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).padding(start = AccordSpacing.s4 * indent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        RadioButton(selected = selected, onClick = null)
        DecorativeIcon(lucideRes(option.iconKey), tint = categoryColor(option.colorKey(categories)))
        Text(option.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}
