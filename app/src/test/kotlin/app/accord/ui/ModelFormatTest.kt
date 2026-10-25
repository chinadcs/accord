package app.accord.ui

import app.accord.ui.model.formatBytes
import app.accord.ui.model.percentOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelFormatTest {
    @Test
    fun `tamanho em unidades decimais como o teto de 2 GB`() {
        assertEquals("2,0 GB", formatBytes(2_000_000_000))
        assertEquals("1,5 GB", formatBytes(1_500_000_000))
        assertEquals("480,2 MB", formatBytes(480_200_000))
        assertEquals("4 KB", formatBytes(4_096))
        assertEquals("12 B", formatBytes(12))
    }

    @Test
    fun `percentual so quando o total e conhecido`() {
        assertEquals(40, percentOf(200, 500))
        assertEquals(100, percentOf(600, 500))
        assertNull(percentOf(10, 0))
    }
}
