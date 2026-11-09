package app.accord.core

import app.accord.core.space.*
import java.time.LocalDate
import kotlin.test.*

class UpcomingWeekTest {
    private val today = LocalDate.parse("2026-11-09")

    private fun commitment(id: String, nature: CommitmentNature, due: String, vararg situations: EntrySituation) = Commitment(
        id, nature, id, null, Money(10_000), LocalDate.parse(due), null, null, situations.toSet(), null,
    )

    private fun card(id: String, closing: Int, due: Int, configured: Boolean = true) =
        CardInfo(id, null, id, closing, due, configured = configured)

    @Test fun `janela vai de hoje a seis dias depois e exclui o oitavo dia`() {
        val items = listOf(
            commitment("a", CommitmentNature.Bill, "2026-11-08"),
            commitment("b", CommitmentNature.Bill, "2026-11-09"),
            commitment("c", CommitmentNature.Bill, "2026-11-15"),
            commitment("d", CommitmentNature.Bill, "2026-11-16"),
        )
        assertEquals(listOf("b", "c"), upcomingWeek(items, emptyList(), today).map { (it as UpcomingEvent.BillDue).commitment.id })
    }

    @Test fun `boleto pago sai da previsao e parcela nao aparece uma a uma`() {
        val items = listOf(
            commitment("paga", CommitmentNature.Bill, "2026-11-10", EntrySituation.Paid),
            commitment("compra#2", CommitmentNature.Finite, "2026-11-10"),
            commitment("serie:3", CommitmentNature.Recurring, "2026-11-11"),
            commitment("aberto", CommitmentNature.Bill, "2026-11-10", EntrySituation.Overdue),
        )
        val events = upcomingWeek(items, emptyList(), today)
        assertEquals(listOf("aberto", "serie:3"), events.map { (it as? UpcomingEvent.BillDue)?.commitment?.id ?: (it as UpcomingEvent.RecurrenceDue).commitment.id })
    }

    @Test fun `cartao configurado mostra fechamento e vencimento dentro da janela`() {
        val events = upcomingWeek(emptyList(), listOf(card("c1", 12, 15)), today)
        assertEquals(listOf(LocalDate.parse("2026-11-12") to "closing", LocalDate.parse("2026-11-15") to "due"), events.map {
            it.date to if (it is UpcomingEvent.CardClosing) "closing" else "due"
        })
    }

    @Test fun `cartao com semente 8 e 15 nao configurado fica de fora`() {
        assertTrue(upcomingWeek(emptyList(), listOf(card("c1", 12, 15, configured = false)), today).isEmpty())
    }

    @Test fun `janela que atravessa o mes usa o dia do mes seguinte e dia inexistente cai no ultimo`() {
        val late = LocalDate.parse("2026-11-27")
        val events = upcomingWeek(emptyList(), listOf(card("c1", 2, 30)), late)
        // 30/11 existe; fechamento dia 2 cai em 02/12.
        assertEquals(listOf(LocalDate.parse("2026-11-30"), LocalDate.parse("2026-12-02")), events.map { it.date })
        val feb = upcomingWeek(emptyList(), listOf(card("c1", 31, 5)), LocalDate.parse("2027-02-24"))
        assertEquals(listOf(LocalDate.parse("2027-02-28")), feb.map { it.date })
    }

    @Test fun `mesmo dia ordena fechamento vencimento boleto e recorrencia`() {
        val items = listOf(
            commitment("r", CommitmentNature.Recurring, "2026-11-12"),
            commitment("b", CommitmentNature.Bill, "2026-11-12"),
        )
        val events = upcomingWeek(items, listOf(card("c1", 12, 12)), today)
        assertEquals(
            listOf("CardClosing", "CardDue", "BillDue", "RecurrenceDue"),
            events.map { it::class.simpleName },
        )
    }
}
