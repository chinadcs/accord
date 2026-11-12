package app.accord.ui.more

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.loadPalette
import app.accord.ui.theme.CornerStyle
import app.accord.ui.theme.ThemePreference
import app.accord.ui.theme.ThemePalette
import com.composables.icons.lucide.R as Lucide

@Composable
fun AppearanceScreen(
    padding: PaddingValues,
    theme: ThemePreference,
    corners: CornerStyle,
    onTheme: (ThemePreference) -> Unit,
    onCorners: (CornerStyle) -> Unit,
    onBack: () -> Unit,
    palette: ThemePalette = ThemePalette.Accord,
    onPalette: (ThemePalette) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.appearance_title), onBack)
        Text(stringResource(R.string.appearance_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.more_theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        AccordCard {
            Column(Modifier.selectableGroup().padding(horizontal = AccordSpacing.s4)) {
                listOf(
                ThemePreference.System to R.string.more_theme_system,
                ThemePreference.Light to R.string.more_theme_light,
                ThemePreference.Dark to R.string.more_theme_dark,
                ).forEachIndexed { index, (value, label) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AppearanceOption(stringResource(label), theme == value) { onTheme(value) }
                }
            }
        }
        Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        AccordCard {
            Column(Modifier.selectableGroup().padding(horizontal = AccordSpacing.s4)) {
                ThemePalette.values().forEachIndexed { index, value ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AppearanceOption(stringResource(value.labelRes), palette == value, swatches = paletteSwatches(value)) { onPalette(value) }
                }
            }
        }
        Text(stringResource(R.string.appearance_corners), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        AccordCard {
            Column(Modifier.selectableGroup().padding(horizontal = AccordSpacing.s4)) {
                listOf(CornerStyle.Rounded to R.string.appearance_rounded, CornerStyle.Straight to R.string.appearance_straight)
                    .forEachIndexed { index, (value, label) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AppearanceOption(stringResource(label), corners == value) { onCorners(value) }
                    }
            }
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s4), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_palette, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.appearance_preview_note), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(stringResource(R.string.more_theme_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, swatches: List<Color> = emptyList(), onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Amostra decorativa (fundo, cartão, container e marca da paleta no modo em uso): o nome continua o rótulo falado.
        if (swatches.isNotEmpty()) Row(
            Modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            swatches.forEach {
                Box(Modifier.size(20.dp).background(it, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
    }
}

/** Cores da paleta no modo em uso, lidas sem trocar a preferência salva: fundo, cartão, container e marca. */
@Composable
private fun paletteSwatches(palette: ThemePalette): List<Color> {
    val context = LocalContext.current
    val dark = Accord.dark
    return remember(palette, dark) {
        val scheme = loadPalette(context, palette, dark)
        listOf(scheme.background, scheme.surfaceContainerLow, scheme.surfaceContainerHighest, scheme.primary)
    }
}
