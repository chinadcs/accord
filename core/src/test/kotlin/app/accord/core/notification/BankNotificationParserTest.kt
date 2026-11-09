package app.accord.core.notification

import app.accord.core.notification.NotificationProposal.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Textos sintéticos: o produto ainda não tem amostras reais de notificação de nenhum banco (ADR 0069). */
class BankNotificationParserTest {
    private fun parse(body: String, title: String? = null) = BankNotificationParser.parse(title, body)

    @Test
    fun `compra aprovada vira despesa com valor em centavos e estabelecimento`() {
        val p = parse("Compra aprovada no cartão final 1234 de R$ 1.234,56 em PADARIA CENTRAL.")!!
        assertEquals(123_456, p.cents)
        assertEquals(Direction.Expense, p.direction)
        assertEquals("PADARIA CENTRAL", p.description)
        assertFalse(p.viaPix)
    }

    @Test
    fun `pix enviado vira despesa via pix com o nome do favorecido`() {
        val p = parse("Pix enviado de R$ 30,00 para Maria Souza às 14:05.")!!
        assertEquals(3_000, p.cents)
        assertEquals(Direction.Expense, p.direction)
        assertEquals("Maria Souza", p.description)
        assertTrue(p.viaPix)
    }

    @Test
    fun `pix recebido vira renda`() {
        val p = parse("Você recebeu um Pix de R$ 80,5 de João Lima.")!!
        assertEquals(8_050, p.cents)
        assertEquals(Direction.Income, p.direction)
        assertEquals("João Lima", p.description)
    }

    @Test
    fun `titulo e corpo se juntam`() {
        val p = parse(title = "Compra aprovada", body = "R$ 12,90 em MERCADO BOM PRECO")!!
        assertEquals(1_290, p.cents)
        assertEquals("MERCADO BOM PRECO", p.description)
    }

    @Test
    fun `saldo e limite nao sao o valor da movimentacao`() {
        val p = parse("Saldo disponível R$ 5.000,00. Compra aprovada de R$ 45,00 em LOJA X.")!!
        assertEquals(4_500, p.cents)
    }

    @Test
    fun `estabelecimento incerto deixa a descricao vazia em vez de inventar`() {
        val p = parse("Compra aprovada no Itaú Visa final 1234 de R$ 20,00.")!!
        assertEquals(2_000, p.cents)
        assertEquals("", p.description)
    }

    @Test
    fun `estorno e cancelamento nao viram proposta`() {
        assertNull(parse("Estorno de compra de R$ 50,00 em LOJA Y."))
        assertNull(parse("Compra cancelada de R$ 50,00 em LOJA Y."))
    }

    @Test
    fun `sem valor em reais ou sem sentido claro nao propoe`() {
        assertNull(parse("Compra aprovada em LOJA Y."))
        assertNull(parse("Ganhe R$ 50,00 de cashback nas suas compras!"))
        assertNull(parse("Pix enviado e recebido de R$ 10,00 de Ana"))
        assertNull(parse("Seu extrato chegou"))
        assertNull(BankNotificationParser.parse(null, null))
    }

    @Test
    fun `valor zero nao propoe`() {
        assertNull(parse("Compra aprovada de R$ 0,00 em LOJA Z."))
    }

    @Test
    fun `filtro de palavras ignora maiusculas e acentos`() {
        assertTrue(BankNotificationParser.matchesKeywords("DEBITO em conta"))
        assertTrue(BankNotificationParser.matchesKeywords("transferencia concluída"))
        assertFalse(BankNotificationParser.matchesKeywords("Atualize o aplicativo"))
    }

    @Test
    fun `descricao longa e cortada`() {
        val p = parse("Compra aprovada de R$ 9,00 em " + "ABCDEFGHIJ ".repeat(8))!!
        assertTrue(p.description.length <= 40)
    }
}
