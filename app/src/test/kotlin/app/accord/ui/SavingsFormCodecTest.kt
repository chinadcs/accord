package app.accord.ui

import app.accord.ui.savings.SavingsForm
import app.accord.ui.savings.SavingsFormCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class SavingsFormCodecTest {
    @Test fun `recriacao preserva campos acentuados identificador idempotente e versoes vistas`() {
        val form = SavingsForm("create", "command-stable", "account", "pocket", "Viagem & férias = verão + casal",
            "123,45", "2026-11-09", "456,78", "2027-01-01", true, 8, 17)
        assertEquals(form, SavingsFormCodec.decode(SavingsFormCodec.encode(form)))
    }
    @Test fun `nenhuma escolha de origem inicial continua sem escolha ao retomar`() {
        val form = SavingsForm("create", "command-stable", amount = "50")
        assertEquals(form, SavingsFormCodec.decode(SavingsFormCodec.encode(form)))
    }
}
