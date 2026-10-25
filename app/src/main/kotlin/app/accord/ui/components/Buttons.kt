package app.accord.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.accord.ui.theme.AccordSpacing

/** Ação principal com a forma do Accord e área de toque mínima. */
@Composable
fun AccordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AccordSpacing.touchMin),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        content = content,
    )
}

/** Ação secundária com o mesmo alvo e escala de cantos. */
@Composable
fun AccordOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AccordSpacing.touchMin),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        content = content,
    )
}
