package app.accord.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing

/**
 * Espaço reservado enquanto um valor carrega (Início). Não é dado: nunca mostra número, e o leitor de tela recebe só a
 * descrição do que está sendo carregado, em um nó único. Com movimento reduzido, fica parado.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp, widthFraction: Float = 1f) {
    val reduced = Accord.motion.reduced
    val alpha = if (reduced) 0.6f else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.35f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse), label = "skeleton-alpha",
        )
        value
    }
    Box(
        modifier.fillMaxWidth(widthFraction).height(height).alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
    )
}

/** Linhas de esqueleto de um painel que carrega; [description] diz o que está carregando e substitui os blocos para o TalkBack. */
@Composable
fun SkeletonLines(description: String, modifier: Modifier = Modifier, lines: Int = 3) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        SkeletonBlock(height = 20.dp, widthFraction = 0.55f)
        repeat((lines - 1).coerceAtLeast(0)) { index -> SkeletonBlock(widthFraction = if (index % 2 == 0) 0.9f else 0.7f) }
    }
}
