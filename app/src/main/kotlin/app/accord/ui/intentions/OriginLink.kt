package app.accord.ui.intentions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.analysis.ProductPhase
import app.accord.core.space.IntentionView
import app.accord.core.space.QueryResult
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * A intenção que originou o compromisso [rootId] (a compra, o lançamento, o boleto ou a série), para as telas 08 e 11. Só existe na fase P2 e
 * nunca falha a tela de quem a pede: sem a fase, sem vínculo ou com erro de leitura, devolve nulo.
 */
suspend fun SpaceProvider.originIntention(rootId: String): IntentionView? {
    if (!phase.includes(ProductPhase.P2)) return null
    val list = (space().intentions() as? QueryResult.Data)?.value ?: return null
    return list.firstOrNull { it.conversion?.id == rootId }
}

/** "Originado da intenção X" com o caminho de volta (telas 08 e 11, P2). */
@Composable
fun OriginIntentionLink(origin: IntentionView?, onOpen: (String) -> Unit) {
    if (origin == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DecorativeIcon(Lucide.drawable.lucide_ic_info)
            Text(stringResource(R.string.detail_origin_intention, origin.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onOpen(origin.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.detail_origin_open))
            }
        }
    }
}
