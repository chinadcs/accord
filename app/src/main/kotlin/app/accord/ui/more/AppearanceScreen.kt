package app.accord.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.CornerStyle
import app.accord.ui.theme.ThemePreference
import com.composables.icons.lucide.R as Lucide

@Composable
fun AppearanceScreen(
    padding: PaddingValues,
    theme: ThemePreference,
    corners: CornerStyle,
    onTheme: (ThemePreference) -> Unit,
    onCorners: (CornerStyle) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.more_theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            listOf(
                ThemePreference.System to R.string.more_theme_system,
                ThemePreference.Light to R.string.more_theme_light,
                ThemePreference.Dark to R.string.more_theme_dark,
            ).forEach { (value, label) -> AppearanceOption(stringResource(label), theme == value) { onTheme(value) } }
        }
        Text(stringResource(R.string.appearance_corners), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            listOf(CornerStyle.Rounded to R.string.appearance_rounded, CornerStyle.Straight to R.string.appearance_straight)
                .forEach { (value, label) -> AppearanceOption(stringResource(label), corners == value) { onCorners(value) } }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s4), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_palette)
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.appearance_preview_note), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(stringResource(R.string.more_theme_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
