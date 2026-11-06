package app.accord.ui.banks

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import app.accord.R
import app.accord.core.space.BankRef
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordLogoPlate
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Logo do banco, sempre sobre superfície clara para preservar as cores da marca nos dois temas, sem
 * tintura. Sem logo (ou "Outro banco"), mostra o símbolo genérico; o nome fica ao lado, no texto.
 */
@Composable
fun BankLogo(bank: BankRef?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val ispb = (bank as? BankRef.Catalog)?.ispb
    val bitmap = remember(ispb) {
        ispb?.let { id ->
            runCatching { context.assets.open(BankCatalog.logoAsset(id)).use { BitmapFactory.decodeStream(it) } }.getOrNull()?.asImageBitmap()
        }
    }
    Surface(color = AccordLogoPlate.surface, shape = MaterialTheme.shapes.small, modifier = modifier.size(size)) {
        Box(Modifier.size(size).padding(3.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                DecorativeIcon(Lucide.drawable.lucide_ic_landmark, tint = AccordLogoPlate.glyph)
            }
        }
    }
}

/**
 * Seleção de banco em painel: busca por nome, lista alfabética com logo e nome textual, e "Outro banco"
 * com nome livre. Cancelar mantém o banco anterior; não há banco pré-selecionado.
 */
@Composable
fun BankPickerDialog(initial: BankRef?, onPick: (BankRef) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { BankCatalog.load(context) }
    var query by remember { mutableStateOf("") }
    var other by remember { mutableStateOf((initial as? BankRef.Other)?.name.orEmpty()) }
    var otherMode by remember { mutableStateOf(initial is BankRef.Other) }
    val results = remember(query) { catalog.search(query) }

    val listState = rememberLazyListState()
    LaunchedEffect(otherMode) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().imePadding().systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            })
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f)
                    .height(maxHeight.coerceAtMost(560.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(AccordSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.bank_picker_title),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.edit_cancel))
                            }
                        }
                    }
                    if (otherMode) {
                        item {
                            OutlinedTextField(
                                value = other, onValueChange = { other = it }, singleLine = true,
                                label = { Text(stringResource(R.string.bank_other_name)) }, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            TextButton(onClick = { otherMode = false }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.bank_back_to_list))
                            }
                        }
                        item {
                            TextButton(
                                onClick = { onPick(BankRef.Other(other.trim())) }, enabled = other.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
                            ) { Text(stringResource(R.string.bank_use_other)) }
                        }
                    } else {
                        item {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                label = { Text(stringResource(R.string.bank_search)) }, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            TextButton(onClick = { otherMode = true }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                                Text(stringResource(R.string.bank_other))
                            }
                        }
                        if (results.isEmpty()) {
                            item { Text(stringResource(R.string.bank_no_results), style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(results, key = { it.ispb }) { bank ->
                            val selected = (initial as? BankRef.Catalog)?.ispb == bank.ispb
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                                    .selectable(selected = selected, role = Role.RadioButton) { onPick(bank.toRef()) }
                                    .padding(vertical = AccordSpacing.s1),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                            ) {
                                BankLogo(bank.toRef().takeIf { bank.hasLogo })
                                Text(bank.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                // A seleção atual não depende de cor: ganha marca própria (o estado também vai ao TalkBack).
                                if (selected) DecorativeIcon(Lucide.drawable.lucide_ic_check, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
