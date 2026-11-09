package app.accord.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedContentTest {
    @Test
    fun `quebras e espacos viram um espaco so`() {
        assertEquals("Boleto da luz R\$ 120,50 vence dia 10", SharedContent.normalizeText("  Boleto da luz\nR\$ 120,50\r\n\tvence dia 10  "))
    }

    @Test
    fun `texto so de espacos nao vira nada`() {
        assertNull(SharedContent.normalizeText("  \n\t "))
        assertNull(SharedContent.normalizeText(null))
    }

    @Test
    fun `texto longo e cortado no limite da mensagem sem espaco sobrando`() {
        val longo = "palavra ".repeat(200)
        val out = SharedContent.normalizeText(longo)!!
        assertEquals(true, out.length <= 600)
        assertEquals(out.trimEnd(), out)
    }
}
