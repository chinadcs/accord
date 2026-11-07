package app.accord.ui.vendors

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.accord.R
import app.accord.core.space.BankRef
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.ui.banks.BankLogo
import app.accord.ui.categories.CategoryIconChoices
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordLogoPlate
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch

private class Choice(val ref: IconRef, val label: String)

private enum class PickerTab(val label: Int) { Brands(R.string.icon_tab_brands), Banks(R.string.icon_tab_banks), Generic(R.string.icon_tab_generic) }

/**
 * Seletor de ícone com busca (ADR 0047): Marcas (Simple Icons), Bancos (logos locais) e Genéricos (Lucide). Cada opção mostra o nome em texto e é um
 * botão para o TalkBack. Fechar mantém o ícone atual; "Voltar ao ícone automático" só aparece quando há uma escolha para remover.
 */
@Composable
fun IconPickerDialog(
    library: IconLibrary?,
    entryName: String,
    canReturnToAutomatic: Boolean,
    onPick: (IconRef) -> Unit,
    onAutomatic: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(PickerTab.Brands) }
    val choices = remember(library, tab, query) { library?.let { choicesFor(it, tab, query) }.orEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) })
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).height(maxHeight.coerceAtMost(640.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.icon_picker_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.icon_picker_close)) }
                    }
                    Text(
                        stringResource(R.string.icon_picker_for, entryName), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.icon_picker_search)) }, modifier = Modifier.fillMaxWidth().testTag("icon-picker-search"),
                    )
                    PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        PickerTab.entries.forEach { entry ->
                            Tab(
                                selected = tab == entry, onClick = { tab = entry },
                                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).testTag("icon-tab-${entry.name.lowercase()}"),
                                text = { Text(stringResource(entry.label), maxLines = 1) },
                            )
                        }
                    }
                    if (canReturnToAutomatic) {
                        TextButton(onClick = onAutomatic, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.icon_picker_automatic))
                        }
                    }
                    when {
                        library == null -> Text(stringResource(R.string.icon_picker_loading), style = MaterialTheme.typography.bodyMedium)
                        choices.isEmpty() -> Text(stringResource(R.string.icon_picker_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("icon-picker-empty"))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(92.dp), modifier = Modifier.weight(1f).testTag("icon-picker-grid"),
                            contentPadding = PaddingValues(vertical = AccordSpacing.s1),
                            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                        ) {
                            items(choices, key = { "${it.ref.source}:${it.ref.key}" }) { choice -> ChoiceCell(choice, library, onPick) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCell(choice: Choice, library: IconLibrary, onPick: (IconRef) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .semantics(mergeDescendants = true) { contentDescription = choice.label }
            .clickable(role = Role.Button) { onPick(choice.ref) }
            .testTag("icon-choice-${choice.ref.source.name.lowercase()}-${choice.ref.key}")
            .padding(AccordSpacing.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        when (choice.ref.source) {
            IconSource.Brand -> library.brands.find(choice.ref.key)?.let { brand -> IconPlate { BrandGlyph(brand) } }
            IconSource.Bank -> BankLogo(BankRef.Catalog(choice.ref.key, choice.label))
            IconSource.Generic -> IconPlate { DecorativeIcon(lucideRes(choice.ref.key), tint = AccordLogoPlate.glyph) }
        }
        Text(choice.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private fun choicesFor(library: IconLibrary, tab: PickerTab, query: String): List<Choice> = when (tab) {
    PickerTab.Brands -> library.brands.search(query).map { Choice(IconRef(IconSource.Brand, it.slug), it.title) }
    PickerTab.Banks -> {
        val q = IconText.fold(query)
        library.banks.entries.filter { it.hasLogo && (q.isEmpty() || q in IconText.fold(it.name)) }
            .sortedBy { IconText.fold(it.name) }.map { Choice(IconRef(IconSource.Bank, it.ispb), it.name) }
    }
    PickerTab.Generic -> {
        val q = IconText.fold(query)
        CategoryIconChoices.filter { q.isEmpty() || q in IconText.fold(it.label) || q in IconText.fold(it.key) }
            .sortedBy { IconText.fold(it.label) }.map { Choice(IconRef(IconSource.Generic, it.key), it.label) }
    }
}

/**
 * Fluxo de escolher o ícone de um lançamento: seletor e, depois de escolher, a pergunta se vale só para ele ou também para os próximos com o mesmo nome.
 * Cancelar em qualquer passo não grava nada. Sem [LocalIconActions] (tela sem fachada) o fluxo não abre.
 */
@Composable
fun ChooseIconFlow(entryId: String, description: String, onDone: () -> Unit) {
    val actions = LocalIconActions.current ?: return
    val choices = LocalIconChoices.current
    val library by rememberIconLibrary()
    var picked by remember { mutableStateOf<IconRef?>(null) }
    val scope = rememberCoroutineScope()
    val chosen = picked
    if (chosen == null) {
        IconPickerDialog(
            library = library, entryName = description,
            canReturnToAutomatic = choices.hasEntryChoice(entryId) || choices.hasNameChoice(description),
            onPick = { picked = it },
            onAutomatic = {
                scope.launch {
                    if (choices.hasEntryChoice(entryId)) actions.clear(IconScope.Entry, entryId) else actions.clear(IconScope.Name, description)
                    onDone()
                }
            },
            onDismiss = onDone,
        )
    } else {
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.icon_scope_title)) },
            text = { Text(stringResource(R.string.icon_scope_body, description)) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Button(
                        onClick = {
                            scope.launch {
                                // A escolha própria do lançamento venceria a regra nova: sai, para este lançamento acompanhar a regra.
                                if (choices.hasEntryChoice(entryId)) actions.clear(IconScope.Entry, entryId)
                                actions.set(IconScope.Name, description, chosen)
                                onDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("icon-scope-all"),
                    ) { Text(stringResource(R.string.icon_scope_all)) }
                    OutlinedButton(
                        onClick = { scope.launch { actions.set(IconScope.Entry, entryId, chosen); onDone() } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("icon-scope-one"),
                    ) { Text(stringResource(R.string.icon_scope_one)) }
                    TextButton(
                        onClick = { picked = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("icon-scope-cancel"),
                    ) { Text(stringResource(R.string.icon_scope_cancel)) }
                }
            },
        )
    }
}
