package app.accord.ui.categories

import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.categoryColor
import app.accord.ui.ownColorKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
fun CategoriesRoute(
    viewModel: CategoriesViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val reordering by viewModel.reordering.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CategoriesScreen(
        content, dialog, expanded, reordering, tab, padding, onBack, onRetry = { viewModel.load() },
        onTab = viewModel::selectTab,
        onToggle = viewModel::toggle, onToggleReorder = viewModel::toggleReorder, onMove = viewModel::move,
        onCreate = viewModel::startCreate, onEdit = viewModel::startEdit, onEditIncome = viewModel::startEditIncome,
        onMoveSub = viewModel::startMove,
        onName = viewModel::setName, onIcon = viewModel::setIcon, onColor = viewModel::setColor,
        onDestination = viewModel::setDestination, onAnswer = viewModel::answer,
        onSubmit = viewModel::submit, onClose = viewModel::close,
        onMoveEntries = { id -> viewModel.close(); onMoveEntries(id) },
        onRemove = { id -> viewModel.close(); onRemove(id) },
    )
}

@Composable
fun CategoriesScreen(
    content: CategoriesContent,
    dialog: CategoryDialog?,
    expanded: Set<String>,
    reordering: Boolean,
    tab: CategoryTab,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTab: (CategoryTab) -> Unit,
    onToggle: (String) -> Unit,
    onToggleReorder: () -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onCreate: (String?) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
    onDestination: (String) -> Unit,
    onAnswer: (java.time.YearMonth, PartChoice?, String?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.categories_screen_title), onBack)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.selectableGroup()) {
            CategoryTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { onTab(entry) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(
                        stringResource(if (entry == CategoryTab.Expense) R.string.categories_tab_expense else R.string.categories_tab_income),
                        modifier = Modifier.padding(AccordSpacing.s2),
                    )
                }
            }
        }
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
            is CategoriesContent.Ready -> when (tab) {
                CategoryTab.Expense -> {
                    content.mains.forEach { main ->
                        MainRow(main, main.node.id in expanded, reordering, onToggle, onMove, onCreate, onEdit, onMoveSub)
                    }
                    OutlinedButton(onClick = onToggleReorder, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(if (reordering) R.string.categories_reorder_done else R.string.categories_reorder))
                    }
                    Text(stringResource(R.string.categories_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CategoryTab.Income -> {
                    content.incomes.forEach { income -> IncomeRow(income, onEditIncome) }
                    Text(stringResource(R.string.categories_income_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val mains = (content as? CategoriesContent.Ready)?.mains.orEmpty()
    dialog?.let { CategoryDialogs(it, mains, onName, onIcon, onColor, onDestination, onAnswer, onSubmit, onClose, onMoveEntries, onRemove) }
}

/** Botão só com ícone: o nome da ação e da categoria vai no rótulo de acessibilidade. */
@Composable
private fun ActionIcon(iconKey: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(AccordSpacing.touchMin)) {
        Icon(painterResource(lucideRes(iconKey)), contentDescription = description)
    }
}

@Composable
private fun IncomeRow(node: IncomeCategoryNode, onEdit: (IncomeCategoryNode) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(AccordSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            CategoryBadge(node.iconKey, categoryColor(node.ownColorKey()))
            Text(node.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ActionIcon("pencil", stringResource(R.string.categories_edit_named, node.name)) { onEdit(node) }
        }
    }
}

/** Ícone da categoria na cor escolhida, sobre um disco da própria cor esmaecida (o mesmo desenho do painel de edição). */
@Composable
private fun CategoryBadge(iconKey: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = CircleShape) {
        Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
            DecorativeIcon(lucideRes(iconKey), tint = color)
        }
    }
}

@Composable
private fun MainRow(
    main: MainCategory,
    expanded: Boolean,
    reordering: Boolean,
    onToggle: (String) -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onCreate: (String?) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
) {
    val color = categoryColor(main.node.ownColorKey())
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                CategoryBadge(main.node.iconKey, color)
                Text(main.node.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (reordering) ReorderButtons(main.node, onMove) else ActionIcon("pencil", stringResource(R.string.categories_edit_named, main.node.name)) { onEdit(main.node) }
            }
            TextButton(onClick = { onToggle(main.node.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Icon(painterResource(if (expanded) Lucide.drawable.lucide_ic_chevron_up else Lucide.drawable.lucide_ic_chevron_down), contentDescription = null)
                Spacer(Modifier.width(AccordSpacing.s1))
                Text(stringResource(if (expanded) R.string.categories_collapse else R.string.categories_expand, main.children.size))
            }
            if (expanded) {
                main.children.forEach { sub ->
                    Row(Modifier.padding(start = AccordSpacing.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        DecorativeIcon(lucideRes(sub.iconKey), tint = color)
                        Text(sub.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (reordering) {
                            ReorderButtons(sub, onMove)
                        } else {
                            ActionIcon("pencil", stringResource(R.string.categories_edit_named, sub.name)) { onEdit(sub) }
                            ActionIcon("arrow-left-right", stringResource(R.string.categories_move_named, sub.name)) { onMoveSub(sub) }
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
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
    onDestination: (String) -> Unit,
    onAnswer: (java.time.YearMonth, PartChoice?, String?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    when (dialog) {
        is CategoryDialog.Create -> NameDialog(
            title = if (dialog.income) R.string.categories_create_income_title else R.string.categories_create_title,
            name = dialog.name, error = dialog.nameError, message = dialog.message,
            working = dialog.working, onName = onName, onSubmit = onSubmit, onClose = onClose,
            note = when {
                dialog.income -> R.string.categories_create_income_note
                dialog.parentId == null -> null
                else -> R.string.categories_create_sub_note
            },
        )
        is CategoryDialog.Edit -> CategoryEditSheet(
            dialog, onName, onIcon, onColor, onSave = onSubmit,
            onMoveEntries = { onMoveEntries(dialog.id) }, onRemove = { onRemove(dialog.id) }, onClose = onClose,
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
