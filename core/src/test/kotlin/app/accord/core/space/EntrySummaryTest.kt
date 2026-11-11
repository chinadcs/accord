package app.accord.core.space

import app.accord.core.Money
import kotlin.test.assertEquals
import kotlin.test.Test

class EntrySummaryTest {
    // Renda: R$ 1.000 recebida e R$ 500 prevista; despesa R$ 300, estorno R$ 50.
    private val summary = EntrySummary(
        expenses = Money(30_000), incomes = Money(150_000), credits = Money(5_000), invoicePayments = Money.ZERO,
        receivedIncomes = Money(100_000),
    )

    @Test fun saldoSoContaARendaRecebida() = assertEquals(Money(75_000), summary.balance)

    @Test fun saldoContandoPrevistasSomaARendaPrevista() = assertEquals(Money(125_000), summary.balanceWithExpected)
}
