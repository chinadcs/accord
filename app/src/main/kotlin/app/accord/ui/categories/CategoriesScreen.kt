package app.accord.ui.categories

import app.accord.ui.categoryColor
import app.accord.ui.ownColorKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.DetailHeader
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun CategoriesRoute(viewModel: CategoriesViewModel, padding: PaddingValues, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val reordering by viewModel.reordering.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CategoriesScreen(
        content, dialog, expanded, reordering, padding, onBack, onRetry = { viewModel.load() },
        onToggle = viewModel::toggle, onToggleReorder = viewModel::toggleReorder, onMove = viewModel::move,
        onCreate = viewModel::startCreate, onRename = viewModel::startRename, onMoveSub = viewModel::startMove,
        onName = viewModel::setName, onDestination = viewModel::setDestination, onAnswer = viewModel::answer,
        onSubmit = viewModel::submit, onClose = viewModel::close, onEdit = onEdit,
    )
}

@Composable
fun CategoriesScreen(
    content: CategoriesContent,
    dialog: CategoryDialog?,
    expanded: Set<String>,
    reordering: Boolean,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleReorder: () -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onCreate: (String?) -> Unit,
    onRename: (CategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
    onName: (String) -> Unit,
    onDestination: (String) -> Unit,
    onAnswer: (java.time.YearMonth, PartChoice?, String?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.categories_screen_title), onBack)
        OutlinedButton(onClick = { onCreate(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            DecorativeIcon(Lucide.drawable.lucide_ic_plus)
            Text(stringResource(R.string.categories_new))
        }
        when (content) {
            CategoriesContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CategoriesContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is CategoriesContent.Ready -> {
                content.mains.forEach { main ->
                    MainRow(main, main.node.id in expanded, reordering, onToggle, onMove, onCreate, onRename, onMoveSub, onEdit)
                }
                OutlinedButton(onClick = onToggleReorder, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(if (reordering) R.string.categories_reorder_done else R.string.categories_reorder))
                }
                Text(stringResource(R.string.categories_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val mains = (content as? CategoriesContent.Ready)?.mains.orEmpty()
    dialog?.let { CategoryDialogs(it, mains, onName, onDestination, onAnswer, onSubmit, onClose) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainRow(
    main: MainCategory,
    expanded: Boolean,
    reordering: Boolean,
    onToggle: (String) -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onCreate: (String?) -> Unit,
    onRename: (CategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
    onEdit: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                    Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
                        DecorativeIcon(lucideRes(main.node.iconKey), tint = categoryColor(main.node.ownColorKey()))
                    }
                }
                Text(main.node.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (reordering) ReorderButtons(main.node, onMove)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
            ) {
                TextButton(onClick = { onToggle(main.node.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Icon(painterResource(if (expanded) Lucide.drawable.lucide_ic_chevron_up else Lucide.drawable.lucide_ic_chevron_down), contentDescription = null)
                    Spacer(Modifier.width(AccordSpacing.s1))
                    Text(stringResource(if (expanded) R.string.categories_collapse else R.string.categories_expand, main.children.size))
                }
                TextButton(onClick = { onRename(main.node) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_pencil), contentDescription = null)
                    Spacer(Modifier.width(AccordSpacing.s1))
                    Text(stringResource(R.string.categories_rename))
                }
                TextButton(onClick = { onEdit(main.node.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Icon(painterResource(lucideRes("palette")), contentDescription = null)
                    Spacer(Modifier.width(AccordSpacing.s1))
                    Text(stringResource(R.string.categories_edit))
                }
            }
            if (expanded) {
                main.children.forEach { sub ->
                    Row(Modifier.padding(start = AccordSpacing.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        DecorativeIcon(lucideRes(sub.iconKey), tint = categoryColor(main.node.ownColorKey()))
                        Text(sub.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (reordering) ReorderButtons(sub, onMove)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = AccordSpacing.s4),
                        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                    ) {
                        TextButton(onClick = { onRename(sub) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Icon(painterResource(Lucide.drawable.lucide_ic_pencil), contentDescription = null)
                            Spacer(Modifier.width(AccordSpacing.s1))
                            Text(stringResource(R.string.categories_rename))
                        }
                        TextButton(onClick = { onEdit(sub.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Icon(painterResource(lucideRes("palette")), contentDescription = null)
                            Spacer(Modifier.width(AccordSpacing.s1))
                            Text(stringResource(R.string.categories_edit))
                        }
                        TextButton(onClick = { onMoveSub(sub) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Icon(painterResource(lucideRes("arrow-left-right")), contentDescription = null)
                            Spacer(Modifier.width(AccordSpacing.s1))
                            Text(stringResource(R.string.categories_move))
                        }
                    }
                }
                TextButton(onClick = { onCreate(main.node.id) }, modifier = Modifier.padding(start = AccordSpacing.s4).heightIn(min = AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_plus), contentDescription = null)
                    Spacer(Modifier.width(AccordSpacing.s1))
                    Text(stringResource(R.string.categories_new_sub))
                }
            }
        }
    }
}

@Composable
private fun ReorderButtons(node: CategoryNode, onMove: (CategoryNode, Int) -> Unit) {
    Row {
        IconButton(onClick = { onMove(node, -1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_chevron_up), contentDescription = stringResource(R.string.categories_move_up, node.name))
        }
        IconButton(onClick = { onMove(node, 1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_chevron_down), contentDescription = stringResource(R.string.categories_move_down, node.name))
        }
    }
}

@Composable
private fun CategoryDialogs(
    dialog: CategoryDialog,
    mains: List<MainCategory>,
    onName: (String) -> Unit,
    onDestination: (String) -> Unit,
    onAnswer: (java.time.YearMonth, PartChoice?, String?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    when (dialog) {
        is CategoryDialog.Create -> NameDialog(
            title = R.string.categories_create_title, name = dialog.name, error = dialog.nameError, message = dialog.message,
            working = dialog.working, onName = onName, onSubmit = onSubmit, onClose = onClose,
            note = if (dialog.parentId == null) null else R.string.categories_create_sub_note,
        )
        is CategoryDialog.Rename -> NameDialog(
            title = R.string.categories_rename_title, name = dialog.name, error = dialog.nameError, message = dialog.message,
            working = dialog.working, onName = onName, onSubmit = onSubmit, onClose = onClose, note = R.string.categories_rename_note,
        )
        is CategoryDialog.Move -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.categories_move_title, dialog.sub.name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    val origin = mains.firstOrNull { it.node.id == dialog.sub.parentId }?.node?.name.orEmpty()
                    Text(stringResource(R.string.categories_move_from, origin), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.categories_move_to), style = MaterialTheme.typography.titleSmall)
                    mains.filter { it.node.id != dialog.sub.parentId }.forEach { main ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                                .selectable(selected = dialog.destinationId == main.node.id, role = Role.RadioButton) { onDestination(main.node.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = dialog.destinationId == main.node.id, onClick = null)
                            Text(main.node.name, modifier = Modifier.padding(start = AccordSpacing.s2))
                        }
                    }
                    if (dialog.destinationError) Text(stringResource(R.string.categories_move_pick), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.categories_move_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dialog.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onSubmit, enabled = !dialog.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.linked_confirm)) }
            },
            dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) } },
        )
        is CategoryDialog.ReviewParts -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.categories_parts_title, dialog.sub.name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                    val destination = mains.firstOrNull { it.node.id == dialog.destinationId }?.node?.name.orEmpty()
                    Text(stringResource(R.string.categories_parts_body, destination), style = MaterialTheme.typography.bodyMedium)
                    dialog.answers.forEach { a ->
                        Column(Modifier.selectableGroup()) {
                            Text(stringResource(R.string.categories_parts_month, a.month.label(), a.part.format()), style = MaterialTheme.typography.titleSmall)
                            ChoiceRow(stringResource(R.string.categories_parts_carry, a.part.format()), a.choice == PartChoice.Carry) { onAnswer(a.month, PartChoice.Carry, null) }
                            ChoiceRow(stringResource(R.string.categories_parts_drop), a.choice == PartChoice.Drop) { onAnswer(a.month, PartChoice.Drop, null) }
                            ChoiceRow(stringResource(R.string.categories_parts_custom), a.choice == PartChoice.Custom) { onAnswer(a.month, PartChoice.Custom, null) }
                            if (a.choice == PartChoice.Custom) {
                                OutlinedTextField(
                                    value = a.customText, onValueChange = { onAnswer(a.month, null, it) }, singleLine = true,
                                    label = { Text(stringResource(R.string.categories_parts_value)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (dialog.missing) Text(stringResource(R.string.categories_parts_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    dialog.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onSubmit, enabled = !dialog.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.categories_parts_confirm)) }
            },
            dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) } },
        )
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
private fun NameDialog(
    title: Int,
    name: String,
    error: Boolean,
    message: String?,
    working: Boolean,
    onName: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    note: Int?,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                OutlinedTextField(
                    value = name, onValueChange = onName, singleLine = true, label = { Text(stringResource(R.string.categories_name)) },
                    isError = error || message != null,
                    supportingText = when {
                        error -> ({ Text(stringResource(R.string.categories_name_required)) })
                        message != null -> ({ Text(message) })
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.categories_save)) }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) } },
    )
}
