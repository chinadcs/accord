package app.accord.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.accord.ui.theme.Accord

/**
 * Cartão do aplicativo (ADR 0085, spec de cores §5): `surfaceContainerLow` com borda de 1 dp em `outlineVariant`, sem sombra.
 * Cartão dentro de cartão usa [AccordInnerCard] (Container, sem borda dupla). A versão sem `onClick` aceita
 * `Modifier.clickable`/`selectable` como as linhas de lista de hoje.
 */
@Composable
fun AccordCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Cartão que abre algo: mesmo desenho, com alvo de toque e semântica de botão do Material (`Surface` com `onClick`). */
@Composable
fun AccordCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Painel interno de um [AccordCard]: `surfaceContainer` e nenhuma borda, para não duplicar a do cartão. */
@Composable
fun AccordInnerCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, content = content)
}

/** Dialog, bottom sheet e menu no claro sobre `surfaceContainerLowest` (branco); no escuro ficam no padrão do Material 3. */
@Composable
fun accordDialogColor(): Color =
    if (Accord.dark) AlertDialogDefaults.containerColor else MaterialTheme.colorScheme.surfaceContainerLowest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun accordSheetColor(): Color =
    if (Accord.dark) BottomSheetDefaults.ContainerColor else MaterialTheme.colorScheme.surfaceContainerLowest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun accordMenuColor(): Color =
    if (Accord.dark) MenuDefaults.containerColor else MaterialTheme.colorScheme.surfaceContainerLowest
