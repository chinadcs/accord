package app.accord.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.CATEGORY_PALETTE_SIZE
import app.accord.ui.CategoryColorNames
import app.accord.ui.categoryPaletteColor
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** O que o painel mostra, vindo de [CategoryDialog.Create] ou [CategoryDialog.Edit]. */
internal data class PanelState(
    val title: Int,
    val isMain: Boolean,
    val parentName: String?,
    val name: String,
    val iconKey: String,
    /** Ícone de partida: se não está na lista de escolhas, aparece como "Ícone atual". */
    val originalIcon: String,
    val colorIndex: Int,
    val nameError: Boolean,
    val message: String?,
    val working: Boolean,
    val changed: Boolean,
    val fallbackName: String,
    val creating: Boolean,
)

/**
 * Painel único de criar e editar de categoria (nome, ícone e cor), que sobe de baixo como o seletor de categoria do registro. Une o antigo
 * "Renomear" e a tela "Editar". Reclassificar e remover ficam só no ⋮ da lista (spec 02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryEditSheet(
    state: PanelState,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    // Com alteração, fechar pergunta antes; sem ela, fecha direto. O arrasto para baixo é recusado com alteração para o painel não sumir sem a pergunta.
    val requestClose = { if (state.changed) asking = true else onClose() }
    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !state.changed }),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        EditSheetBody(state, onName, onIcon, onColor, onSave, requestClose)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.categories_discard_title)) },
            confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.categories_discard_confirm)) } },
            dismissButton = { TextButton(onClick = { asking = false }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.categories_discard_keep)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditSheetBody(
    state: PanelState,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.creating) focus.requestFocus() }
    val tint = categoryPaletteColor(state.colorIndex)
    // Cabeçalho e Salvar ficam fixos; só o miolo (nome, cor, ícones) rola, para Salvar nunca ficar depois de 70 ícones.
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(state.title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.register_picker_close))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Preview(state, tint)
            OutlinedTextField(
                value = state.name, onValueChange = onName, singleLine = true,
                label = { Text(stringResource(R.string.categories_name)) },
                isError = state.nameError || state.message != null,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.categories_name_required)) }) else null,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (state.isMain) {
                Text(stringResource(R.string.category_edit_color), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                ) {
                    for (index in 0 until CATEGORY_PALETTE_SIZE) {
                        ColorOption(index, selected = index == state.colorIndex) { onColor(index) }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.category_edit_sub_color, state.parentName.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.category_edit_icon), style = MaterialTheme.typography.titleSmall)
            val choices = if (CategoryIconChoices.none { it.key == state.originalIcon }) {
                listOf(IconChoice(state.originalIcon, stringResource(R.string.category_edit_icon_current))) + CategoryIconChoices
            } else {
                CategoryIconChoices
            }
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
            ) {
                choices.forEach { choice ->
                    IconOption(choice, selected = choice.key == state.iconKey, tint = tint) { onIcon(choice.key) }
                }
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = onSave, enabled = !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(R.string.categories_save)) }
    }
}

@Composable
private fun Preview(state: PanelState, tint: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Surface(color = tint.copy(alpha = 0.16f), shape = CircleShape) {
                Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
                    DecorativeIcon(lucideRes(state.iconKey), tint = tint)
                }
            }
            Text(state.name.ifBlank { state.fallbackName }, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IconOption(choice: IconChoice, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val description = stringResource(R.string.category_edit_icon_option, choice.label)
    val chosen = stringResource(R.string.state_selected)
    Surface(
        color = if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(2.dp, tint) else null,
        modifier = Modifier
            .size(AccordSpacing.touchMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                if (selected) stateDescription = chosen
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            DecorativeIcon(lucideRes(choice.key), tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColorOption(index: Int, selected: Boolean, onClick: () -> Unit) {
    val color = categoryPaletteColor(index)
    val description = stringResource(R.string.category_edit_color_option, CategoryColorNames[index])
    val chosen = stringResource(R.string.state_selected)
    Box(
        Modifier
            .size(AccordSpacing.touchMin)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                if (selected) stateDescription = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    painterResource(Lucide.drawable.lucide_ic_check), contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
