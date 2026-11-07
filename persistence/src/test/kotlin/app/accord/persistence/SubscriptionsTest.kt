package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.RecurrenceScope
import app.accord.core.analysis.ChargeComparison
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.PayOccurrence
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.Settlement
import app.accord.core.space.SubscriptionStatus
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Assinaturas (S6.2, telas 31 e 32): consultas sobre as séries recorrentes da categoria Assinaturas, em 24/10/2026. */
class SubscriptionsTest : SpaceTestBase() {
    private suspend fun Handle.withDefaultAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private suspend fun Handle.series(
        description: String,
        key: String,
        cents: Long,
        first: LocalDate,
        frequency: Frequency = Frequency.Monthly,
        form: RecurrenceForm = RecurrenceForm.OffCard,
    ): String = space.execute(
        env(),
        CreateRecurrence(RecurrenceKind.Expense, description, seed(key), Money(cents), form, frequency = frequency, firstDate = first),
    ).applied().changes.single().id

    private suspend fun Handle.pay(id: String, number: Int, date: LocalDate, cents: Long) {
        space.execute(env(), PayOccurrence(id, number, Settlement(date, Money(cents)))).applied()
    }

    @Test
    fun `34,90 e 39,90 pagos dão diferença observada de 5 reais e a próxima prevista`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.series("Streaming", "assinaturas.streaming", 4000, d(2026, 9, 5))
        h.pay(id, 1, d(2026, 9, 5), 3490)
        h.pay(id, 2, d(2026, 10, 5), 3990)

        val detail = assertNotNull(h.space.subscription(id).value())
        assertEquals(listOf(1, 2), detail.charges.map { it.number })
        val comparison = assertIs<ChargeComparison.Observed>(detail.comparison)
        assertEquals(Money(500), comparison.difference)
        assertEquals(0, comparison.missingCycles)
        assertEquals(SubscriptionStatus.Active, detail.status)
        assertEquals(3, detail.next!!.number)
        assertEquals(d(2026, 11, 5), detail.next!!.scheduledDate)
        // O esperado (R$ 40,00) difere da última cobrança (R$ 39,90): mostrado, sem reescrever a expectativa.
        assertEquals(Money(10), detail.nextVersusLastCharge)
        assertEquals("$id:2", detail.charges.last().entryId)
    }

    @Test
    fun `uma única cobrança não inventa variação`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.series("Streaming", "assinaturas.streaming", 3990, d(2026, 9, 5))
        h.pay(id, 1, d(2026, 9, 5), 3990)
        assertEquals(ChargeComparison.InsufficientHistory, assertNotNull(h.space.subscription(id).value()).comparison)
    }

    @Test
    fun `assinatura no cartão sem pagamento da ocorrência não tem histórico comparável`() = runBlocking<Unit> {
        val h = open()
        val id = h.series("Streaming", "assinaturas.streaming", 3990, d(2026, 8, 5), form = RecurrenceForm.Card)
        val detail = assertNotNull(h.space.subscription(id).value())
        assertEquals(emptyList(), detail.charges)
        assertEquals(ChargeComparison.InsufficientHistory, detail.comparison)
        assertNull(detail.nextVersusLastCharge)
    }

    @Test
    fun `série fora da categoria Assinaturas não aparece e o detalhe é nulo`() = runBlocking<Unit> {
        val h = open()
        val other = h.series("Curso", "lazer", 4000, d(2026, 9, 5))
        assertEquals(emptyList(), h.space.subscriptions(d(2026, 10, 24), d(2026, 11, 24)).value().items)
        assertNull(h.space.subscription(other).value())
        assertNull(h.space.subscription("nao-existe").value())
    }

    @Test
    fun `lista separa frequências sem converter e soma só o que vence no período`() = runBlocking<Unit> {
        val h = open()
        val streaming = h.series("Streaming", "assinaturas.streaming", 4000, d(2026, 9, 5))
        val software = h.series("Software", "assinaturas.software", 12000, d(2026, 11, 20), Frequency.Annual)

        val list = h.space.subscriptions(d(2026, 10, 24), d(2026, 12, 1)).value()
        assertEquals(listOf(streaming, software), list.items.map { it.recurrenceId })
        assertEquals(listOf(Frequency.Monthly, Frequency.Annual), list.subtotals.map { it.frequency })
        assertEquals(Money(4000), list.subtotals[0].expected)
        assertEquals(Money(12000), list.subtotals[1].expected)
        // Vencem no período: streaming #3 (5/11) e software #1 (20/11). Mensal não vira anual nem o contrário.
        assertEquals(2, list.dueCount)
        assertEquals(Money(16000), list.dueInPeriod)
        assertEquals(d(2026, 11, 5), list.items[0].next!!.scheduledDate)
    }

    @Test
    fun `ocorrência já paga no período soma o valor efetivo`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.series("Streaming", "assinaturas.streaming", 4000, d(2026, 9, 5))
        h.pay(id, 2, d(2026, 10, 5), 3990)
        val list = h.space.subscriptions(d(2026, 10, 1), d(2026, 11, 1)).value()
        assertEquals(1, list.dueCount)
        assertEquals(Money(3990), list.dueInPeriod)
    }

    @Test
    fun `série encerrada fica por último, sem próxima nem subtotal`() = runBlocking<Unit> {
        val h = open()
        val ended = h.series("Antiga", "assinaturas.streaming", 2000, d(2026, 9, 5))
        h.space.execute(h.env(), DeleteRecurrence(ended, 3, RecurrenceScope.ThisAndNext)).applied()
        val live = h.series("Streaming", "assinaturas.streaming", 4000, d(2026, 9, 5))

        val list = h.space.subscriptions(d(2026, 10, 24), d(2026, 11, 24)).value()
        assertEquals(listOf(live, ended), list.items.map { it.recurrenceId })
        assertEquals(SubscriptionStatus.Ended, list.items.last().status)
        assertNull(list.items.last().next)
        assertEquals(1, list.subtotals.single().count)
        assertEquals(Money(4000), list.subtotals.single().expected)
    }

    @Test
    fun `consultar não altera a série`() = runBlocking<Unit> {
        val h = open()
        val id = h.series("Streaming", "assinaturas.streaming", 4000, d(2026, 9, 5))
        val before = h.space.recurrence(id).value()
        h.space.subscriptions(d(2026, 10, 24), d(2026, 11, 24)).value()
        h.space.subscription(id).value()
        assertEquals(before, h.space.recurrence(id).value())
    }
}
