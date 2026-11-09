package app.accord.ui.entries

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Abre os filtros da lista de Lançamentos; mostra quantos estão ativos. Ficou aqui quando o saldo total foi para o Início (ADR 0071). */
@Composable
fun EntriesFiltersButton(filter: EntriesFilterState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (filter.activeCount > 0) stringResource(R.string.entries_filters_count, filter.activeCount) else stringResource(R.string.entries_filters)
    AccordOutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = label }) {
        DecorativeIcon(Lucide.drawable.lucide_ic_sliders_horizontal)
        Text(label, modifier = Modifier.padding(start = AccordSpacing.s1))
    }
}
