package app.accord.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.accord.ui.theme.AccordSpacing

/** Estado de uma seção de resumo; quem a usa decide o estado, a seção só o apresenta (ADR 0078). */
enum class SectionStatus { Loading, Failed, Ready }

/** Só o estado pronto com ação abre o destino; carga e falha nunca criam alvo de toque nem botão aninhado. */
internal fun sectionIsActionable(status: SectionStatus, hasAction: Boolean): Boolean = status == SectionStatus.Ready && hasAction

/** Altura mínima comum aos três estados, para a tela não saltar quando o bloco carrega, falha ou fica pronto. */
val SectionMinHeight: Dp = 120.dp

/**
 * Cartão de seção da Início: título (cabeçalho para o TalkBack), ícone decorativo opcional e um de três corpos.
 *
 * - [SectionStatus.Loading]: esqueleto, com [loadingDescription] como único nó lido.
 * - [SectionStatus.Failed]: a frase curta [failedMessage]. Na Início não há botão (o "Tentar de novo" é do banner agregado);
 *   fora dela, quem não tem banner próprio passa [failedAction] (por exemplo, o Orçamento).
 * - [SectionStatus.Ready]: [content]. Com [onClick], o cartão inteiro é um botão com [onClickLabel]; sem ele, é só leitura.
 *
 * [loadingMinHeight] reserva, só na carga, uma altura próxima à do estado pronto típico (em dp com fonte 100%; cresce com a escala
 * da fonte), para o cartão não crescer centenas de dp quando o valor chega e empurrar o que está abaixo. Vale só na carga: em
 * falha ou sem dados o cartão volta a ser menor, e isso é aceito para não deixar um vazio grande onde há só uma frase.
 *
 * [headerAction] (por exemplo, o botão de informação) fica como irmão do alvo do cartão, no canto superior, para não
 * ser um clicável dentro de outro; só aparece no estado pronto.
 */
@Composable
fun HomeSectionCard(
    title: String,
    status: SectionStatus,
    loadingDescription: String,
    failedMessage: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    minHeight: Dp = SectionMinHeight,
    skeletonLines: Int = 2,
    loadingMinHeight: Dp = 0.dp,
    failedAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val actionable = sectionIsActionable(status, onClick != null)
    val action = headerAction.takeIf { status == SectionStatus.Ready }
    Box(modifier.fillMaxWidth()) {
        AccordCard(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = if (status == SectionStatus.Loading) maxOf(minHeight, loadingMinHeight * LocalDensity.current.fontScale) else minHeight)
                .let { if (actionable) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick!!) else it },) {
            Column(
                Modifier.fillMaxWidth().padding(AccordSpacing.s4),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                Row(
                    // Reserva o alvo do botão de informação, que é irmão do cartão e não pode cobrir o título.
                    Modifier.padding(end = if (action != null) AccordSpacing.touchMin else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                when (status) {
                    SectionStatus.Loading -> SkeletonLines(loadingDescription, lines = skeletonLines)
                    SectionStatus.Failed -> {
                        Text(failedMessage, style = MaterialTheme.typography.bodyMedium)
                        failedAction?.invoke()
                    }
                    SectionStatus.Ready -> content()
                }
            }
        }
        action?.let { Box(Modifier.align(Alignment.TopEnd)) { it() } }
    }
}
