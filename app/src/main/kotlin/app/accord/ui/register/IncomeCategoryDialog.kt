package app.accord.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.accord.R
import androidx.compose.ui.graphics.Color
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.categoryColor
import app.accord.ui.ownColorKey
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordSpacing

/**
 * Escolha da categoria de renda (ADR 0038, ADR 0082): a principal e, recuadas abaixo dela, as subcategorias, todas escolhíveis
 * (a principal vale sozinha), com "Sem categoria" e a opção de criar outra, que já fica escolhida no formulário. Ao criar, a pessoa
 * escolhe se é principal ou subcategoria de qual principal. A recusa do nome (vazio ou repetido entre irmãos) volta em português
 * da fachada e o diálogo continua aberto.
 */
@Composable
internal fun IncomeCategoryDialog(
    categories: List<IncomeCategoryNode>,
    selected: String?,
    onPick: (String?) -> Unit,
    onCreate: (name: String, parentId: String?, done: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** Falso na edição: o comando de correção troca a categoria de renda, mas não a remove. */
    allowNone: Boolean = true,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (creating) R.string.register_income_category_new else R.string.register_income_category)) },
        text = {
            val mains = categories.filter { it.parentId == null }
            if (creating) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text(stringResource(R.string.register_income_category_name)) },
                        singleLine = true, isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.register_income_category_parent),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = AccordSpacing.s2),
                    )
                    IncomeCategoryRow(stringResource(R.string.register_income_category_parent_none), parentId == null, null, null) { parentId = null; error = null }
                    mains.forEach { main ->
                        IncomeCategoryRow(main.name, parentId == main.id, main.iconKey, categoryColor(main.ownColorKey())) { parentId = main.id; error = null }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (allowNone) IncomeCategoryRow(stringResource(R.string.register_income_category_none), selected == null, null, null) { onPick(null) }
                    mains.forEach { main ->
                        val color = categoryColor(main.ownColorKey())
                        IncomeCategoryRow(main.name, selected == main.id, main.iconKey, color) { onPick(main.id) }
                        categories.filter { it.parentId == main.id }.sortedBy { it.order }.forEach { sub ->
                            IncomeCategoryRow(
                                sub.name, selected == sub.id, sub.iconKey, color,
                                modifier = Modifier.padding(start = AccordSpacing.s4),
                                description = main.name + " › " + sub.name,
                            ) { onPick(sub.id) }
                        }
                    }
                    TextButton(onClick = { creating = true }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.register_income_category_new))
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = !working,
                    onClick = {
                        working = true
                        onCreate(name, parentId) { message ->
                            working = false
                            if (message == null) onDismiss() else error = message
                        }
                    },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.register_income_category_create)) }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_picker_close))
                }
            }
        },
        dismissButton = if (creating) {
            {
                TextButton(onClick = { creating = false; error = null }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_income_category_cancel))
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun IncomeCategoryRow(
    label: String,
    selected: Boolean,
    iconKey: String?,
    color: Color?,
    modifier: Modifier = Modifier,
    /** Leitura de uma subcategoria: o nome sozinho se repete ("Outros"), então o leitor de tela diz o caminho. */
    description: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (iconKey != null && color != null) DecorativeIcon(lucideRes(iconKey), tint = color)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
    }
}