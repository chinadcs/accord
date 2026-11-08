package app.accord.core.space

import app.accord.core.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBalanceTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun e(day: Int, cents: Long, source: BalanceSource = BalanceSource.Expense) = BalanceEvent(d(day), Money(cents), source)

    @Test
    fun `saldo e o inicial mais o que entrou menos o que saiu a partir do corte`() {
        // Inicial R$ 1.000,00 em 01/11: renda +3.000, pix -250, boleto -480,90, quitação -1.200.
        val events = listOf(
            e(5, 300_000, BalanceSource.Income), e(6, -25_000), e(9, -48_090, BalanceSource.Boleto), e(10, -120_000, BalanceSource.InvoicePayment),
        )
        assertEquals(Money(100_000 + 300_000 - 25_000 - 48_090 - 120_000), AccountBalances.balance(Money(100_000), d(1), events))
    }

    @Test
    fun `eventos anteriores ao corte ja estao no saldo inicial e nao contam de novo`() {
        val events = listOf(e(2, -10_000), e(3, -5_000), e(4, -2_000))
        // Corte em 03/11: o evento de 02/11 é anterior; o de 03/11 (o próprio dia) conta.
        assertEquals(Money(50_000 - 5_000 - 2_000), AccountBalances.balance(Money(50_000), d(3), events))
    }

    @Test
    fun `saldo pode ficar negativo e a data de referencia limita o que conta`() {
        val events = listOf(e(5, -80_000), e(20, 10_000, BalanceSource.Income))
        assertEquals(Money(-30_000 + 10_000), AccountBalances.balance(Money(50_000), d(1), events))
        assertEquals(Money(-30_000), AccountBalances.balance(Money(50_000), d(1), events, asOf = d(10)))
    }

    @Test
    fun `ajuste leva o saldo calculado ao saldo real sem mexer no que foi registrado`() {
        val events = listOf(e(5, -80_000))
        // Calculado R$ -300,00 em 10/11; a pessoa vê R$ 120,00 no banco: o ajuste soma R$ 420,00.
        val adjustment = AccountBalances.adjustmentFor(Money(50_000), d(1), events, actual = Money(12_000), date = d(10))
        assertEquals(Money(42_000), adjustment)
        val withAdjustment = events + e(10, adjustment.cents, BalanceSource.Adjustment)
        assertEquals(Money(12_000), AccountBalances.balance(Money(50_000), d(1), withAdjustment, asOf = d(10)))
    }

    @Test
    fun `transferencia debita uma conta e credita a outra pelo mesmo valor`() {
        val from = listOf(e(8, -70_000, BalanceSource.TransferOut))
        val to = listOf(e(8, 70_000, BalanceSource.TransferIn))
        val a = AccountBalances.balance(Money(100_000), d(1), from)
        val b = AccountBalances.balance(Money(20_000), d(1), to)
        assertEquals(Money(30_000), a)
        assertEquals(Money(90_000), b)
        assertEquals(Money(120_000), a + b, "a soma das contas não muda com a transferência")
    }
}
