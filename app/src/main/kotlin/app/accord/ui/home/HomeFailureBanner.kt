package app.accord.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Banner único de falha da Início (ADR 0078): aparece só quando alguma leitura falhou, diz quais blocos ficaram sem valor
 * (nunca como zero) e oferece um único "Tentar de novo", que quem chama liga só às leituras que falharam. É o único aviso
 * dinâmico da tela (`liveRegion`); os blocos falhos mostram só a frase curta, sem botão. Ícone e texto, nunca só a cor.
 *
 * Depois do toque, [updating] mantém o mesmo banner, com o botão desabilitado e o texto "Atualizando…", até as leituras
 * terminarem: o botão não some sob o dedo (o foco do TalkBack não se perde), a troca de texto é anunciada pela região viva e,
 * se uma leitura falhar de novo, o banner volta ao estado de falha no mesmo lugar. [blocks] são os blocos citados no apoio.
 */
@Composable
fun HomeFailureBanner(blocks: List<HomeBlock>, onRetry: () -> Unit, modifier: Modifier = Modifier, updating: Boolean = false) {
    if (blocks.isEmpty()) return
    val names = joinWithAnd(blocks.map { stringResource(it.labelRes()) }, stringResource(R.string.home_banner_and))
    val content = MaterialTheme.colorScheme.onErrorContainer
    Surface(
        color = MaterialTheme.colorScheme.errorContainer, contentColor = content,
        shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(AccordSpacing.s4),
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Column(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                ) {
                    Text(
                        stringResource(if (updating) R.string.home_banner_updating_title else R.string.home_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(if (updating) R.string.home_banner_updating_support else R.string.home_banner_support, names),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Botão cheio na cor do conteúdo do banner: o contraste vem do par errorContainer/onErrorContainer.
                Button(
                    onClick = onRetry,
                    enabled = !updating,
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer,
                        // Enquanto atualiza o botão vira só contorno com o texto na cor cheia: continua legível (4,5:1), sem parecer ativo.
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = if (updating) BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer) else null,
                ) { Text(stringResource(if (updating) R.string.home_banner_updating_button else R.string.action_retry)) }
            }
        }
    }
}

private fun HomeBlock.labelRes(): Int = when (this) {
    HomeBlock.Balance -> R.string.home_banner_block_balance
    HomeBlock.Income -> R.string.home_banner_block_income
    HomeBlock.Review -> R.string.home_banner_block_review
    HomeBlock.Dates -> R.string.home_banner_block_dates
    HomeBlock.Exceed -> R.string.home_banner_block_exceed
    HomeBlock.Allowance -> R.string.home_banner_block_allowance
    HomeBlock.Forecast -> R.string.home_banner_block_forecast
}
