package app.accord.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.CATEGORY_PALETTE_SIZE
import app.accord.ui.CategoryColorNames
import app.accord.ui.categoryColor
import app.accord.ui.categoryPaletteColor
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.lucideRes
import app.accord.ui.ownColorKey
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun CategoryEditRoute(
    viewModel: CategoryEditViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onMove: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    CategoryEditScreen(
        content, padding, onBack,
        onName = viewModel::setName, onIcon = viewModel::setIcon, onColor = viewModel::setColor, onSave = viewModel::save,
        onMove = { onMove(viewModel.categoryId) }, onRemove = { onRemove(viewModel.categoryId) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(
    content: CategoryEditContent,
    padding: PaddingValues,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
    onSave: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.category_edit_title), onBack)
        when (content) {
            CategoryEditContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CategoryEditContent.Missing -> Text(stringResource(R.string.category_edit_missing), style = MaterialTheme.typography.bodyLarge)
            is CategoryEditContent.Ready -> {
                val tint = if (content.isMain) categoryPaletteColor(content.colorIndex) else categoryColor((content.parent ?: content.node).ownColorKey())
                Preview(content, tint)
                OutlinedTextField(
                    value = content.name, onValueChange = onName, singleLine = true,
                    label = { Text(stringResource(R.string.categories_name)) },
                    isError = content.nameError || content.message != null,
                    supportingText = if (content.nameError) ({ Text(stringResource(R.string.categories_name_required)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (content.isMain) {
                    Text(stringResource(R.string.category_edit_color), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                    ) {
                        for (index in 0 until CATEGORY_PALETTE_SIZE) {
                            ColorOption(index, selected = index == content.colorIndex) { onColor(index) }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.category_edit_sub_color, content.parent?.name.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.category_edit_icon), style = MaterialTheme.typography.titleSmall)
                val choices = if (CategoryIconChoices.none { it.key == content.node.iconKey }) {
                    listOf(IconChoice(content.node.iconKey, stringResource(R.string.category_edit_icon_current))) + CategoryIconChoices
                } else {
                    CategoryIconChoices
                }
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                ) {
                    choices.forEach { choice ->
                        IconOption(choice, selected = choice.key == content.iconKey, tint = tint) { onIcon(choice.key) }
                    }
                }
                content.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = onSave, enabled = !content.working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.categories_save)) }
                OutlinedButton(onClick = onMove, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    DecorativeIcon(lucideRes("arrow-left-right"))
                    Text(stringResource(R.string.category_edit_move_entries))
                }
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Icon(painterResource(lucideRes("trash-2")), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.category_edit_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Preview(content: CategoryEditContent.Ready, tint: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Surface(color = tint.copy(alpha = 0.16f), shape = CircleShape) {
                Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
                    DecorativeIcon(lucideRes(content.iconKey), tint = tint)
                }
            }
            Text(content.name.ifBlank { content.node.name }, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IconOption(choice: IconChoice, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val description = stringResource(R.string.category_edit_icon_option, choice.label)
    val chosen = stringResource(R.string.state_selected)
    Surface(
        color = if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
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
