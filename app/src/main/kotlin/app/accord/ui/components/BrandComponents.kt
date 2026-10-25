package app.accord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import java.util.Locale

/** Duas perspectivas lado a lado. Símbolo decorativo: o nome ao lado é a descrição acessível. */
@Composable
fun AccordMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val stroke = this.size.minDimension * 0.25f
        val first = Color(ink.red, ink.green, ink.blue, alpha = 0.55f)
        drawLine(
            color = first,
            start = Offset(this.size.width * 0.22f, this.size.height * 0.65f),
            end = Offset(this.size.width * 0.57f, this.size.height * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink,
            start = Offset(this.size.width * 0.43f, this.size.height * 0.79f),
            end = Offset(this.size.width * 0.78f, this.size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
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
