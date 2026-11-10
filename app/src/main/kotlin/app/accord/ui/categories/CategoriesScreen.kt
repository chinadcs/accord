package app.accord.ui.categories

import app.accord.ui.categoryColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.DetailHeader
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
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
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val reordering by viewModel.reordering.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CategoriesScreen(
        content, rows, dialog, reordering, tab, padding, onBack, onRetry = { viewModel.load() },
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

/**
 * Categorias (tela 23, ADR 0084): cabeçalho e abas fixos e, abaixo, uma lista densa agrupada. O toque na principal expande ou
 * recolhe; o ⋮ guarda as ações da linha. A lista chega pronta em [rows] (ver [buildRows]).
 */
@Composable
fun CategoriesScreen(
    content: CategoriesContent,
    rows: List<CategoryRow>,
    dialog: CategoryDialog?,
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
        Modifier.fillMaxSize().padding(padding).padding(horizontal = AccordSpacing.s4).padding(top = AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(
            stringResource(R.string.categories_screen_title), onBack,
            action = {
                val label = stringResource(if (tab == CategoryTab.Income) R.string.categories_new_income_label else R.string.categories_new_label)
                OutlinedButton(
                    onClick = { onCreate(null) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = label },
                ) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_plus)
                    Text(stringResource(R.string.categories_new), modifier = Modifier.padding(start = AccordSpacing.s1))
                }
            },
        )
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
        when (content) {
            CategoriesContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CategoriesContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is CategoriesContent.Ready -> {
                if (tab == CategoryTab.Expense) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            pluralStringResource(R.plurals.categories_count, content.mains.size, content.mains.size),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onToggleReorder, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(if (reordering) R.string.categories_reorder_done else R.string.categories_reorder))
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = AccordSpacing.s4)) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        CategoryListRow(
                            row, first = index == 0, last = index == rows.lastIndex, reordering = reordering,
                            onToggle = onToggle, onMove = onMove, onCreate = onCreate,
                            onEdit = onEdit, onEditIncome = onEditIncome, onMoveSub = onMoveSub,
                        )
                    }
                    item(key = "note") {
                        Text(
                            stringResource(if (tab == CategoryTab.Expense) R.string.categories_note else R.string.categories_income_note),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AccordSpacing.s3),
                        )
                    }
                }
            }
        }
    }
    val mains = (content as? CategoriesContent.Ready)?.mains.orEmpty()
    dialog?.let { CategoryDialogs(it, mains, onName, onIcon, onColor, onDestination, onAnswer, onSubmit, onClose, onMoveEntries, onRemove) }
}

/** Uma linha do contêiner agrupado: o canto arredondado só na primeira e na última, divisor recuado entre as demais. */
@Composable
private fun CategoryListRow(
    row: CategoryRow,
    first: Boolean,
    last: Boolean,
    reordering: Boolean,
    onToggle: (String) -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onCreate: (String?) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
) {
    val square = CornerSize(0.dp)
    val medium = MaterialTheme.shapes.medium
    val shape = medium.copy(
        topStart = if (first) medium.topStart else square, topEnd = if (first) medium.topEnd else square,
        bottomStart = if (last) medium.bottomStart else square, bottomEnd = if (last) medium.bottomEnd else square,
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column {
            when (row) {
                is CategoryRow.Main -> MainLine(row, reordering, onToggle, onMove, onEdit, onEditIncome)
                is CategoryRow.Sub -> SubLine(row, reordering, onMove, onEdit, onEditIncome, onMoveSub)
                is CategoryRow.NewSub -> NewSubLine(row, onCreate)
            }
            if (!last) HorizontalDivider(Modifier.padding(start = AccordSpacing.s4), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MainLine(
    row: CategoryRow.Main,
    reordering: Boolean,
    onToggle: (String) -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
) {
    val color = categoryColor(row.colorKey)
    val state = stringResource(if (row.expanded) R.string.categories_state_expanded else R.string.categories_state_collapsed)
    val toggleLabel = stringResource(if (row.expanded) R.string.categories_action_collapse else R.string.categories_action_expand)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = toggleLabel) { onToggle(row.id) }
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(start = AccordSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        CategoryBadge(row.iconKey, color)
        Column(Modifier.weight(1f).padding(vertical = AccordSpacing.s2)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(subCountText(row.subCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(if (row.expanded) Lucide.drawable.lucide_ic_chevron_up else Lucide.drawable.lucide_ic_chevron_down),
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reordering && row.expense != null) {
            ReorderButtons(row.expense, onMove)
        } else {
            RowMenu(row.name) { close ->
                MenuItem(R.string.categories_menu_edit) { close(); row.expense?.let(onEdit) ?: row.income?.let(onEditIncome) }
            }
        }
    }
}

@Composable
private fun SubLine(
    row: CategoryRow.Sub,
    reordering: Boolean,
    onMove: (CategoryNode, Int) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
) {
    val color = categoryColor(row.colorKey)
    val editLabel = stringResource(R.string.categories_action_edit)
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium)
            .clickable(onClickLabel = editLabel) { row.expense?.let(onEdit) ?: row.income?.let(onEditIncome) }
            .semantics(mergeDescendants = true) {}
            .padding(start = AccordSpacing.s6 + AccordSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DecorativeIcon(lucideRes(row.iconKey), tint = color)
        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = AccordSpacing.s2))
        if (reordering && row.expense != null) {
            ReorderButtons(row.expense, onMove)
        } else if (row.expense != null) {
            RowMenu(row.name) { close ->
                MenuItem(R.string.categories_menu_edit) { close(); onEdit(row.expense) }
                MenuItem(R.string.categories_menu_pass) { close(); onMoveSub(row.expense) }
            }
        } else {
            Box(Modifier.size(AccordSpacing.touchMin))
        }
    }
}

@Composable
private fun NewSubLine(row: CategoryRow.NewSub, onCreate: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .clickable { onCreate(row.parentId) }
            .padding(start = AccordSpacing.s6 + AccordSpacing.s3, end = AccordSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DecorativeIcon(Lucide.drawable.lucide_ic_plus, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.categories_new_sub), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun subCountText(count: Int): String =
    if (count == 0) stringResource(R.string.categories_subs_none) else pluralStringResource(R.plurals.categories_subs_count, count, count)

/** ⋮ de 48 × 48 dp; o nome da categoria vai no rótulo de acessibilidade ("Mais ações de …"). */
@Composable
private fun RowMenu(name: String, items: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_ellipsis_vertical), contentDescription = stringResource(R.string.categories_menu_more, name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
}

/** Ícone da categoria na cor escolhida, sobre um disco de 40 dp da própria cor esmaecida (o mesmo desenho do painel de edição). */
@Composable
private fun CategoryBadge(iconKey: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = CircleShape) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            DecorativeIcon(lucideRes(iconKey), tint = color)
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
            title = if (dialog.income && dialog.parentId == null) R.string.categories_create_income_title else R.string.categories_create_title,
            name = dialog.name, error = dialog.nameError, message = dialog.message,
            working = dialog.working, onName = onName, onSubmit = onSubmit, onClose = onClose,
            note = when {
                dialog.parentId != null -> R.string.categories_create_sub_note
                dialog.income -> R.string.categories_create_income_note
                else -> null
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
