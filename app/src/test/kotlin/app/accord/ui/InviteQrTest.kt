package app.accord.ui

import app.accord.ui.space.InviteQr
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteQrTest {
    /** Desenha os módulos como o app (escuro sobre claro, com margem) e lê de volta com o decodificador do ZXing. */
    private fun decode(code: String): String {
        val modules = InviteQr.modules(code)
        val scale = 8
        val margin = 4 * scale
        val size = modules.size * scale + 2 * margin
        val pixels = IntArray(size * size) { 0xFFFFFFFF.toInt() }
        for (y in modules.indices) for (x in modules.indices) {
            if (!modules[y][x]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) pixels[(margin + y * scale + dy) * size + margin + x * scale + dx] = 0xFF000000.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        return MultiFormatReader().decode(bitmap).text
    }

    @Test
    fun `o QR leva exatamente o código de oito caracteres, o mesmo que se digita`() {
        for (code in listOf("ABCD2345", "ZZZZ9999", "K2M7QWXY")) assertEquals(code, decode(code))
    }

    @Test
    fun `so um codigo de convite bem formado sai da leitura`() {
        assertEquals("ABCD2345", InviteQr.codeFrom(" abcd 2345 "))
        assertNull(InviteQr.codeFrom("https://exemplo.com/convite"))
        assertNull(InviteQr.codeFrom("ABCD23"))
        assertNull(InviteQr.codeFrom("ABCD-2345"))
        assertNull(InviteQr.codeFrom(null))
    }
}
