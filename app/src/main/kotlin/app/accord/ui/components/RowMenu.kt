package app.accord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** ⋮ de 48 × 48 dp; o nome do item vai no rótulo de acessibilidade ("Mais ações de …"). Extraído de `CategoriesScreen.kt` (spec 20). */
@Composable
fun RowMenu(name: String, items: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_ellipsis_vertical), contentDescription = stringResource(R.string.categories_menu_more, name))
        }
        DropdownMenu(containerColor = accordMenuColor(), expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

/** Item do [RowMenu]; [destructive] pinta o texto na cor de erro. */
@Composable
fun RowMenuItem(label: Int, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
    )
}
