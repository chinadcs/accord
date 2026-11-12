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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import app.accord.ui.components.SkeletonBlock
import app.accord.ui.sync.LocalSyncStatus
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
    val awaitingSync = (LocalSyncStatus.current?.summary?.awaitingCategoryOperations ?: 0) > 0
    CategoriesScreen(
        content, rows, dialog, reordering, tab, padding, onBack, onRetry = { viewModel.load() },
        onTab = viewModel::selectTab,
        onToggle = viewModel::toggle, onToggleReorder = viewModel::toggleReorder, onMove = viewModel::move,
        onDragStart = viewModel::startDrag, onDragTo = viewModel::dragTo, onDragEnd = viewModel::endDrag, onDragCancel = viewModel::cancelDrag,
        onCreate = viewModel::startCreate, onEdit = viewModel::startEdit, onEditIncome = viewModel::startEditIncome,
        onMoveSub = viewModel::startMove,
        onName = viewModel::setName, onIcon = viewModel::setIcon, onColor = viewModel::setColor,
        onDestination = viewModel::setDestination, onAnswer = viewModel::answer,
        onSubmit = viewModel::submit, onClose = viewModel::close,
        onMoveEntries = { id -> viewModel.close(); onMoveEntries(id) },
        onRemove = { id -> viewModel.close(); onRemove(id) },
        awaitingSync = awaitingSync,
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
    onDragStart: (CategoryNode) -> Unit,
    onDragTo: (CategoryNode, Int) -> Unit,
    onDragEnd: (CategoryNode) -> Unit,
    onDragCancel: () -> Unit,
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
    awaitingSync: Boolean = false,
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
        // Só alteração de categoria ainda não reconhecida pelo serviço; sem estado por linha (ADR 0084).
        if (awaitingSync) {
            Text(
                stringResource(R.string.categories_pending_sync), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(AccordSpacing.s3),
            )
        }
        when (content) {
            CategoriesContent.Loading -> {
                // Esqueleto estático com movimento reduzido (SkeletonBlock); um nó só para o TalkBack.
                val loading = stringResource(R.string.categories_loading)
                Column(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = loading },
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                ) { repeat(6) { SkeletonBlock(height = 64.dp) } }
            }
            CategoriesContent.Failed -> {
                Text(stringResource(R.string.categories_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is CategoriesContent.Ready -> if (rows.isEmpty()) {
                val income = tab == CategoryTab.Income
                Column(Modifier.fillMaxWidth().padding(vertical = AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(stringResource(if (income) R.string.categories_empty_income_title else R.string.categories_empty_expense_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (income) R.string.categories_empty_income_body else R.string.categories_empty_expense_body),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onCreate(null) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.categories_new_label))
                    }
                }
            } else {
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
                val listState = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = AccordSpacing.s4)) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        CategoryListRow(
                            row, rows, listState, first = index == 0, last = index == rows.lastIndex, reordering = reordering,
                            onToggle = onToggle, onMove = onMove, onDragStart = onDragStart, onDragTo = onDragTo, onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel, onCreate = onCreate,
                            onEdit = onEdit, onEditIncome = onEditIncome, onMoveSub = onMoveSub,
                            onMoveEntries = onMoveEntries, onRemove = onRemove,
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
    dialog?.let { CategoryDialogs(it, mains, onName, onIcon, onColor, onDestination, onAnswer, onSubmit, onClose) }
}

/** Estado do arraste de uma linha: [total] é o deslocamento do dedo e [start] o `offset` da linha quando a alça foi segurada. */
private class DragState {
    var active by mutableStateOf(false)
    var total by mutableFloatStateOf(0f)
    var start = 0
}

/** Uma linha do contêiner agrupado: o canto arredondado só na primeira e na última, divisor recuado entre as demais. */
// announceForAccessibility é o anúncio pontual da nova posição; shortcut: API depreciada, trocar por live region se o TalkBack não anunciar.
@Suppress("DEPRECATION")
@Composable
private fun CategoryListRow(
    row: CategoryRow,
    rows: List<CategoryRow>,
    listState: LazyListState,
    first: Boolean,
    last: Boolean,
    reordering: Boolean,
    onToggle: (String) -> Unit,
    onMove: (CategoryNode, Int) -> Unit,
    onDragStart: (CategoryNode) -> Unit,
    onDragTo: (CategoryNode, Int) -> Unit,
    onDragEnd: (CategoryNode) -> Unit,
    onDragCancel: () -> Unit,
    onCreate: (String?) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val square = CornerSize(0.dp)
    val medium = MaterialTheme.shapes.medium
    val shape = medium.copy(
        topStart = if (first) medium.topStart else square, topEnd = if (first) medium.topEnd else square,
        bottomStart = if (last) medium.bottomStart else square, bottomEnd = if (last) medium.bottomEnd else square,
    )
    val node = when (row) { is CategoryRow.Main -> row.expense; is CategoryRow.Sub -> row.expense; else -> null }
    val pos = when (row) { is CategoryRow.Main -> row.pos; is CategoryRow.Sub -> row.pos; else -> 1 }
    val total = when (row) { is CategoryRow.Main -> row.total; is CategoryRow.Sub -> row.total; else -> 1 }
    val drag = remember { DragState() }
    val currentRows by rememberUpdatedState(rows)
    val currentRow by rememberUpdatedState(row)
    val view = LocalView.current
    val upLabel = stringResource(R.string.categories_move_up)
    val downLabel = stringResource(R.string.categories_move_down)
    val announce = { delta: Int ->
        node?.let { view.announceForAccessibility(view.context.getString(R.string.categories_position, it.name, pos + delta, total)) }
    }
    // "Subir"/"Descer" em toda linha de despesa, inclusive fora do modo; omitidas na primeira e na última irmã.
    val actions = buildList {
        if (node != null && pos > 1) add(CustomAccessibilityAction(upLabel) { onMove(node, -1); announce(-1); true })
        if (node != null && pos < total) add(CustomAccessibilityAction(downLabel) { onMove(node, 1); announce(1); true })
    }
    val handle: (@Composable () -> Unit)? = if (reordering && node != null) ({
        val handleModifier = Modifier.size(AccordSpacing.touchMin).pointerInput(row.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    drag.start = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.offset ?: 0
                    drag.total = 0f
                    drag.active = true
                    onDragStart(node)
                },
                onDrag = { change, delta ->
                    change.consume()
                    drag.total += delta.y
                    val items = listState.layoutInfo.visibleItemsInfo
                    val me = items.firstOrNull { it.key == row.key } ?: return@detectDragGesturesAfterLongPress
                    val center = drag.start + drag.total + me.size / 2
                    val keys = siblingKeys(currentRow, currentRows)
                    val target = items.firstOrNull { it.key != row.key && it.key in keys && center >= it.offset && center < it.offset + it.size }
                    if (target != null) onDragTo(node, keys.indexOf(target.key))
                },
                onDragEnd = { drag.active = false; onDragEnd(node) },
                onDragCancel = { drag.active = false; onDragCancel() },
            )
        }
        Box(handleModifier, contentAlignment = Alignment.Center) {
            Icon(
                painterResource(Lucide.drawable.lucide_ic_grip_vertical),
                contentDescription = stringResource(R.string.categories_drag_handle, node.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }) else null
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape,
        // Borda de 2 dp e elevação tonal identificam o item arrastado, sem escala nem depender de cor.
        tonalElevation = if (drag.active) 8.dp else 0.dp,
        border = if (drag.active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            // Mantém a linha sob o dedo mesmo quando a lista a realoca ao trocar de lugar.
            if (drag.active) {
                val now = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.offset ?: drag.start
                translationY = drag.total - (now - drag.start)
            }
        },
    ) {
        Column {
            when (row) {
                is CategoryRow.Main -> MainLine(row, handle, actions, onToggle, onCreate, onEdit, onEditIncome, onMoveEntries, onRemove)
                is CategoryRow.Sub -> SubLine(row, handle, actions, onEdit, onEditIncome, onMoveSub, onMoveEntries, onRemove)
                is CategoryRow.NewSub -> NewSubLine(row, onCreate)
            }
            if (!last) HorizontalDivider(Modifier.padding(start = AccordSpacing.s4), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Chaves das linhas irmãs de [row] na ordem atual: principais entre si; subcategorias da mesma principal. */
private fun siblingKeys(row: CategoryRow, rows: List<CategoryRow>): List<String> = when (row) {
    is CategoryRow.Main -> rows.filterIsInstance<CategoryRow.Main>().map { it.key }
    is CategoryRow.Sub -> rows.filterIsInstance<CategoryRow.Sub>().filter { it.expense?.parentId == row.expense?.parentId }.map { it.key }
    else -> emptyList()
}

@Composable
private fun MainLine(
    row: CategoryRow.Main,
    handle: (@Composable () -> Unit)?,
    actions: List<CustomAccessibilityAction>,
    onToggle: (String) -> Unit,
    onCreate: (String?) -> Unit,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val color = categoryColor(row.colorKey)
    val state = stringResource(if (row.expanded) R.string.categories_state_expanded else R.string.categories_state_collapsed)
    val toggleLabel = stringResource(if (row.expanded) R.string.categories_action_collapse else R.string.categories_action_expand)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = toggleLabel) { onToggle(row.id) }
            .semantics(mergeDescendants = true) { stateDescription = state; customActions = actions }
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
        if (handle != null) {
            handle()
        } else {
            RowMenu(row.name) { close ->
                MenuItem(R.string.categories_menu_edit) { close(); row.expense?.let(onEdit) ?: row.income?.let(onEditIncome) }
                MenuItem(R.string.categories_new_sub) { close(); onCreate(row.id) }
                if (row.expense != null) {
                    MenuItem(R.string.categories_menu_reclassify) { close(); onMoveEntries(row.id) }
                    HorizontalDivider()
                    MenuItem(R.string.categories_menu_remove) { close(); onRemove(row.id) }
                }
            }
        }
    }
}

@Composable
private fun SubLine(
    row: CategoryRow.Sub,
    handle: (@Composable () -> Unit)?,
    actions: List<CustomAccessibilityAction>,
    onEdit: (CategoryNode) -> Unit,
    onEditIncome: (IncomeCategoryNode) -> Unit,
    onMoveSub: (CategoryNode) -> Unit,
    onMoveEntries: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val color = categoryColor(row.colorKey)
    val editLabel = stringResource(R.string.categories_action_edit)
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium)
            .clickable(onClickLabel = editLabel) { row.expense?.let(onEdit) ?: row.income?.let(onEditIncome) }
            .semantics(mergeDescendants = true) { customActions = actions }
            .padding(start = AccordSpacing.s6 + AccordSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DecorativeIcon(lucideRes(row.iconKey), tint = color)
        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = AccordSpacing.s2))
        if (handle != null) {
            handle()
        } else if (row.expense != null) {
            RowMenu(row.name) { close ->
                MenuItem(R.string.categories_menu_edit) { close(); onEdit(row.expense) }
                MenuItem(R.string.categories_menu_pass) { close(); onMoveSub(row.expense) }
                MenuItem(R.string.categories_menu_reclassify) { close(); onMoveEntries(row.id) }
                HorizontalDivider()
                MenuItem(R.string.categories_menu_remove) { close(); onRemove(row.id) }
            }
        } else {
            Box(Modifier.size(AccordSpacing.touchMin))
        }
    }
}

@Composable
private fun NewSubLine(row: CategoryRow.NewSub, onCreate: (String?) -> Unit) {
    if (row.noSubs) {
        Text(
            stringResource(R.string.categories_no_subs_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AccordSpacing.s6 + AccordSpacing.s3, top = AccordSpacing.s2),
        )
    }
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
) {
    when (dialog) {
        is CategoryDialog.Create -> CategoryEditSheet(
            PanelState(
                title = if (dialog.parentId == null) R.string.categories_create_title else R.string.categories_create_sub_title,
                isMain = dialog.parentId == null, parentName = dialog.parentName, name = dialog.name, iconKey = dialog.iconKey,
                originalIcon = dialog.iconKey, colorIndex = dialog.colorIndex, nameError = dialog.nameError, message = dialog.message,
                working = dialog.working, changed = dialog.changed, fallbackName = "", creating = true,
            ),
            onName, onIcon, onColor, onSave = onSubmit, onClose = onClose,
        )
        is CategoryDialog.Edit -> CategoryEditSheet(
            PanelState(
                title = if (dialog.isMain) R.string.category_edit_title else R.string.category_edit_sub_title,
                isMain = dialog.isMain, parentName = dialog.parentName, name = dialog.name, iconKey = dialog.iconKey,
                originalIcon = dialog.originalIcon, colorIndex = dialog.colorIndex, nameError = dialog.nameError, message = dialog.message,
                working = dialog.working, changed = dialog.changed, fallbackName = dialog.originalName, creating = false,
            ),
            onName, onIcon, onColor, onSave = onSubmit, onClose = onClose,
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
