package app.accord.ui.review

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.Money
import app.accord.ui.theme.AccordSpacing

/**
 * Categorias a esclarecer na raiz Revisar (tela 18). A sugestão do modelo é só uma opção: "Aceitar" classifica o lançamento,
 * "Escolher no Chat" abre as demais categorias. Enquanto ninguém decide, o lançamento segue "a classificar" e fora dos limites.
 */
@Composable
fun ReviewCategories(
    items: List<CategoryReviewItem>,
    onAccept: (CategoryReviewItem) -> Unit,
    onChoose: () -> Unit,
) {
    if (items.isEmpty()) return
    AccordCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.review_categories_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.review_categories_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { item -> CategoryRow(item, onAccept, onChoose) }
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryReviewItem, onAccept: (CategoryReviewItem) -> Unit, onChoose: () -> Unit) {
    val question = item.question
    val title = stringResource(R.string.review_category_item_unknown).let { fallback ->
        val name = question.description ?: fallback
        question.amountCents?.let { stringResource(R.string.review_category_item, name, Money(it).format()) } ?: name
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        val suggestion = item.suggestion
        Text(
            if (suggestion != null) stringResource(R.string.review_category_suggested, suggestion.label) else stringResource(R.string.review_category_no_suggestion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (suggestion != null) {
                val description = stringResource(R.string.review_category_accept_desc, suggestion.label, title)
                Button(
                    onClick = { onAccept(item) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = description },
                ) { Text(stringResource(R.string.review_category_accept)) }
            }
            TextButton(onClick = onChoose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.review_category_choose))
            }
        }
    }
}
