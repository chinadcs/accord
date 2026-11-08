package app.accord.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.core.sync.InviteCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR do convite (ADR 0053): o conteúdo é só o código de 8 caracteres, o mesmo que se digita. O QR nunca
 * carrega link, senha, espaço ou e-mail; quem o lê precisa estar autenticado para o serviço aceitar o código.
 */
object InviteQr {
    /** Módulos do QR (verdadeiro = escuro), sem a margem; a margem de leitura é desenhada na tela. */
    fun modules(code: String): Array<BooleanArray> {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, hints)
        return Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
    }

    /**
     * O que o leitor devolveu, se for um código de convite bem formado; qualquer outro conteúdo (um link,
     * texto de outro QR) é recusado sem ser enviado ao serviço.
     */
    fun codeFrom(scanned: String?): String? =
        scanned?.let(InviteCode::normalize)?.takeIf(InviteCode::isWellFormed)
}

/** Sempre escuro sobre claro, mesmo no tema escuro: o leitor precisa desse contraste, então a margem branca faz parte do QR. */
@Composable
fun InviteQrImage(code: String, modifier: Modifier = Modifier) {
    val modules = remember(code) { InviteQr.modules(code) }
    val description = androidx.compose.ui.res.stringResource(app.accord.R.string.space_qr_content_description)
    Canvas(
        modifier.size(220.dp).background(Color.White).padding(16.dp).semantics { contentDescription = description },
    ) {
        val n = modules.size
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (modules[y][x]) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
