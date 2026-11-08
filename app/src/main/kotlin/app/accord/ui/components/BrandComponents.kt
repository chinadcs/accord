package app.accord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import java.util.Locale

// Logo 02 Encontro, caixa 200 × 200: dois colchetes iguais, o segundo girado 180°, em volta de um espaço central
// compartilhado. Mesma geometria do ícone do launcher (res/drawable/ic_launcher_foreground.xml).
private const val MARK_BOX = 200f
private const val MARK_FIRST = "M0,138 L0,56 A56,56 0 0 1 56,0 L138,0 L138,56 L70,56 A14,14 0 0 0 56,70 L56,138 Z"
private const val MARK_SECOND = "M200,62 L200,144 A56,56 0 0 1 144,200 L62,200 L62,144 L130,144 A14,14 0 0 0 144,130 L144,62 Z"

/** Dois colchetes iguais em volta de um espaço comum. Símbolo decorativo: o nome ao lado é a descrição acessível. */
@Composable
fun AccordMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val ink = MaterialTheme.colorScheme.primary
    val first = remember { PathParser().parsePathString(MARK_FIRST).toPath() }
    val second = remember { PathParser().parsePathString(MARK_SECOND).toPath() }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        scale(scale = this.size.minDimension / MARK_BOX, pivot = Offset.Zero) {
            drawPath(first, color = ink)
            drawPath(second, color = Color(ink.red, ink.green, ink.blue, alpha = 0.55f))
        }
    }
}

/** Assinatura compacta para cabeçalhos; sem família tipográfica ou ativo remoto. */
@Composable
fun AccordWordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) AccordSpacing.s2 else AccordSpacing.s3),
    ) {
        AccordMark(size = if (compact) 24.dp else 32.dp)
        Text(
            text = stringResource(R.string.access_brand).lowercase(Locale.ROOT),
            style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium).copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.8f).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
