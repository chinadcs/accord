package app.accord.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.R
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
    val colors = Accord.colors
    val scheme = MaterialTheme.colorScheme
    // Container + borda de estado + ícone de forma própria (octógono, triângulo, círculo) + texto de abertura: nunca só cor (ADR 0085).
    val style = when (message.kind) {
        MessageKind.Error -> BannerStyle(scheme.errorContainer, scheme.onErrorContainer, colors.errorBorder, Lucide.drawable.lucide_ic_octagon_alert, R.string.banner_label_error)
        MessageKind.Attention -> BannerStyle(colors.attentionContainer, colors.onAttentionContainer, colors.attentionBorder, Lucide.drawable.lucide_ic_triangle_alert, R.string.banner_label_attention)
        MessageKind.Success -> BannerStyle(colors.successContainer, colors.onSuccessContainer, colors.successBorder, Lucide.drawable.lucide_ic_circle_check, R.string.banner_label_success)
        MessageKind.Neutral -> BannerStyle(colors.infoContainer, colors.onInfoContainer, colors.infoBorder, Lucide.drawable.lucide_ic_info, R.string.banner_label_info)
    }
    Surface(
        color = style.container, contentColor = style.content, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, style.border),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            DecorativeIcon(style.icon, tint = style.content)
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(stringResource(style.label), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(message.textRes, *message.args.toTypedArray()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Cores, borda, ícone e texto de abertura de um tipo de mensagem. */
private class BannerStyle(
    val container: Color, val content: Color, val border: Color, @androidx.annotation.DrawableRes val icon: Int, @androidx.annotation.StringRes val label: Int,
)
