package app.accord.core.notification

import app.accord.core.notification.NotificationProposal.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Textos sintéticos, exceto os três do teste de amostras reais do Itaú (fornecidos pelo usuário em 09/11/2026). */
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
    @Test
    fun `amostras reais do Itau`() {
        val compra = parse("Olá! Compra aprovada de R\$ 25,51 em DL *UberRides no dia 09/11 as 10:58 no seu cartão Uniclass, Boas compras")!!
        assertEquals(2_551, compra.cents)
        assertEquals(Direction.Expense, compra.direction)
        assertEquals("DL *UberRides", compra.description)
        assertFalse(compra.viaPix)

        val enviado = parse("Você enviou R\$ 1,00 para Estabelecimento")!!
        assertEquals(100, enviado.cents)
        assertEquals(Direction.Expense, enviado.direction)
        assertEquals("Estabelecimento", enviado.description)
        assertTrue(enviado.viaPix) // o Itaú não escreve "Pix" aqui, mas foi um Pix (confirmado pelo usuário), não compra no cartão

        val recebido = parse("Você recebeu R\$ 1,00 de Estabelecimento")!!
        assertEquals(100, recebido.cents)
        assertEquals(Direction.Income, recebido.direction)
        assertEquals("Estabelecimento", recebido.description)
        assertTrue(recebido.viaPix) // também foi um Pix recebido
    }
}
