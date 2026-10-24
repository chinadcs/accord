package app.accord.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MoneyTest {
    private val nbsp = " "

    private fun cents(input: String): Long {
        val result = Money.parse(input)
        assertIs<MoneyParse.Ok>(result, "esperava valor válido para '$input' mas veio $result")
        return result.money.cents
    }

    private fun error(input: String): MoneyParse.Error {
        val result = Money.parse(input)
        assertIs<MoneyParse.Error>(result, "esperava erro para '$input' mas veio $result")
        return result
    }

    @Test
    fun `leitura aceita inteiro, decimal e milhar no padrão brasileiro`() {
        assertEquals(3_500L, cents("35"))
        assertEquals(3_590L, cents("35,90"))
        assertEquals(120_000L, cents("1.200"))
        assertEquals(120_000L, cents("1.200,00"))
        assertEquals(120_000L, cents("1200"))
        assertEquals(3_590L, cents("35,9"))
        assertEquals(0L, cents("0"))
        assertEquals(123_456_789L, cents("1.234.567,89"))
    }

    @Test
    fun `leitura ignora espaços nas pontas`() {
        assertEquals(3_590L, cents("  35,90 "))
    }

    @Test
    fun `leitura rejeita negativo`() {
        assertEquals(MoneyParse.Reason.Negative, error("-35").reason)
        assertEquals(MoneyParse.Reason.Negative, error("-1.200,00").reason)
    }

    @Test
    fun `leitura rejeita formato ambíguo`() {
        // "1.20" poderia ser R$ 1,20 (en-US) ou formato inválido: não adivinhar.
        assertEquals(MoneyParse.Reason.Ambiguous, error("1.20").reason)
        assertEquals(MoneyParse.Reason.Ambiguous, error("35.90").reason)
        assertEquals(MoneyParse.Reason.Ambiguous, error("1,200").reason)
        assertEquals(MoneyParse.Reason.Ambiguous, error("35,999").reason)
        assertEquals(MoneyParse.Reason.Ambiguous, error("1.2000").reason)
        assertEquals(MoneyParse.Reason.Ambiguous, error("12.34.56").reason)
    }

    @Test
    fun `leitura rejeita vazio e texto`() {
        assertEquals(MoneyParse.Reason.Empty, error("").reason)
        assertEquals(MoneyParse.Reason.Empty, error("   ").reason)
        assertEquals(MoneyParse.Reason.Invalid, error("abc").reason)
        assertEquals(MoneyParse.Reason.Invalid, error("35,").reason)
        assertEquals(MoneyParse.Reason.Invalid, error("R$ 35").reason)
    }

    @Test
    fun `leitura rejeita valor grande demais em vez de estourar`() {
        assertEquals(MoneyParse.Reason.TooLarge, error("99.999.999.999.999").reason)
    }

    @Test
    fun `formatação usa padrão brasileiro com espaço que não quebra`() {
        assertEquals("R$${nbsp}1.200,00", Money(120_000).format())
        assertEquals("R$${nbsp}0,00", Money(0).format())
        assertEquals("R$${nbsp}0,05", Money(5).format())
        assertEquals("R$${nbsp}33,34", Money(3_334).format())
        assertEquals("R$${nbsp}1.234.567,89", Money(123_456_789).format())
    }

    @Test
    fun `formatação de negativo mostra o sinal antes do símbolo`() {
        assertEquals("-R$${nbsp}40,00", Money(-4_000).format())
    }

    @Test
    fun `formatação reabre na leitura sem perder centavos`() {
        for (c in listOf(0L, 1L, 99L, 100L, 3_333L, 120_000L, 123_456_789L)) {
            val plain = Money(c).formatPlain()
            assertEquals(c, cents(plain), "ida e volta de $c via '$plain'")
        }
    }

    @Test
    fun `aritmética em centavos é exata e detecta estouro`() {
        assertEquals(Money(4_200), Money(3_200) + Money(1_000))
        assertEquals(Money(900), Money(1_000) - Money(100))
        assertEquals(Money(-4_000), Money(1_000) - Money(5_000))
        assertEquals(Money(30_000), Money(10_000) * 3)
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE) + Money(1) }
    }

    @Test
    fun `soma de coleção`() {
        assertEquals(Money(10_000), listOf(Money(3_333), Money(3_333), Money(3_334)).sum())
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }
}
