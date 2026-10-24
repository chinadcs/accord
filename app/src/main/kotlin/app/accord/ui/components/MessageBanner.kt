package app.accord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Mensagem de situação da tela. O tipo aparece no ícone e no texto, nunca só na cor, e é anunciado
 * pelo TalkBack quando muda, sem deslocar os botões (a área existe mesmo vazia nas telas que a usam).
 */
@Composable
fun MessageBanner(message: ScreenMessage, modifier: Modifier = Modifier) {
    val (container, content, icon) = when (message.kind) {
        MessageKind.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Lucide.drawable.lucide_ic_circle_alert)
        MessageKind.Attention -> Triple(Accord.colors.attentionContainer, Accord.colors.onAttentionContainer, Lucide.drawable.lucide_ic_triangle_alert)
        MessageKind.Success -> Triple(Accord.colors.successContainer, Accord.colors.onSuccessContainer, Lucide.drawable.lucide_ic_circle_check)
        MessageKind.Neutral -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, Lucide.drawable.lucide_ic_info)
    }
    Surface(
        color = container, contentColor = content, shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            DecorativeIcon(icon, tint = content)
            Text(stringResource(message.textRes, *message.args.toTypedArray()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
