package app.accord.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CardCycleTest {
    private val card = CardCycle(closingDay = 8, dueDay = 15)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    @Test
    fun `fechamento 8 vencimento 15 atribui a fatura e marca só a janela do fechamento`() {
        // (compra, mês de vencimento, a conferir) — tabela de S1 §7.2
        val cases = listOf(
            Triple(d(2026, 10, 5), ym(2026, 10), false),
            Triple(d(2026, 10, 7), ym(2026, 10), true),
            Triple(d(2026, 10, 8), ym(2026, 10), true), // dia do fechamento: entra na que fecha (ADR 0079)
            Triple(d(2026, 10, 9), ym(2026, 11), true),
            Triple(d(2026, 10, 22), ym(2026, 11), false),
            Triple(d(2026, 9, 25), ym(2026, 10), false),
        )
        for ((purchase, due, toConfirm) in cases) {
            val a = card.assign(purchase)
            assertEquals(due, a.dueMonth, "fatura de $purchase")
            assertEquals(toConfirm, a.toConfirm, "a conferir em $purchase")
        }
    }

    @Test
    fun `data de vencimento e de fechamento da fatura atribuída`() {
        val a = card.assign(d(2026, 10, 5))
        assertEquals(d(2026, 10, 8), a.closingDate)
        assertEquals(d(2026, 10, 15), a.dueDate)
    }

    @Test
    fun `vencimento antes do fechamento cai no mês seguinte ao fechamento`() {
        val c = CardCycle(closingDay = 20, dueDay = 5)
        val a = c.assign(d(2026, 10, 10))
        assertEquals(d(2026, 10, 20), a.closingDate)
        assertEquals(d(2026, 11, 5), a.dueDate)
        assertEquals(ym(2026, 11), a.dueMonth)
    }

    @Test
    fun `dia inexistente no mês usa o último dia — fechamento 31 vencimento 10`() {
        val c = CardCycle(closingDay = 31, dueDay = 10)
        assertEquals(d(2026, 11, 30), c.closingDate(ym(2026, 11)))
        assertEquals(d(2027, 2, 28), c.closingDate(ym(2027, 2)))
        // compra em 29/11: essa fatura, a conferir
        val a29 = c.assign(d(2026, 11, 29))
        assertEquals(d(2026, 11, 30), a29.closingDate)
        assertEquals(true, a29.toConfirm)
        // compra em 30/11 (dia do fechamento ajustado): a fatura que fecha nele, a conferir
        val a30 = c.assign(d(2026, 11, 30))
        assertEquals(d(2026, 11, 30), a30.closingDate)
        assertEquals(true, a30.toConfirm)
        // compra em 25/11: essa fatura, sem marca
        val a25 = c.assign(d(2026, 11, 25))
        assertEquals(d(2026, 11, 30), a25.closingDate)
        assertEquals(false, a25.toConfirm)
    }

    @Test
    fun `vencimento 30 cai em 28 de fevereiro`() {
        val c = CardCycle(closingDay = 20, dueDay = 30)
        assertEquals(d(2027, 2, 28), c.assign(d(2027, 2, 10)).dueDate)
    }

    @Test
    fun `janela a conferir atravessa a virada do mês`() {
        val c = CardCycle(closingDay = 1, dueDay = 10)
        // 31/10 é a véspera do fechamento de 01/11
        val a = c.assign(d(2026, 10, 31))
        assertEquals(true, a.toConfirm)
        assertEquals(d(2026, 11, 1), a.closingDate)
        // 02/11 é o dia seguinte ao fechamento de 01/11
        assertEquals(true, c.assign(d(2026, 11, 2)).toConfirm)
        assertEquals(false, c.assign(d(2026, 11, 3)).toConfirm)
    }

    @Test
    fun `fechamento e vencimento fora de 1 a 31 são inválidos`() {
        assertFailsWith<IllegalArgumentException> { CardCycle(0, 15) }
        assertFailsWith<IllegalArgumentException> { CardCycle(8, 32) }
    }

    @Test
    fun `fatura fica em aberto até o dia de fechamento e depois é fechada estimada`() {
        assertEquals(InvoicePhase.Open, card.phase(ym(2026, 10), today = d(2026, 10, 7)))
        assertEquals(InvoicePhase.Open, card.phase(ym(2026, 10), today = d(2026, 10, 8)), "no dia do fechamento ainda entra compra")
        assertEquals(InvoicePhase.ClosedEstimated, card.phase(ym(2026, 10), today = d(2026, 10, 9)))
        assertEquals(InvoicePhase.Open, card.phase(ym(2026, 11), today = d(2026, 10, 9)))
    }

    @Test
    fun `fatura com fechamento no mês anterior ao vencimento`() {
        val c = CardCycle(closingDay = 20, dueDay = 5)
        assertEquals(d(2026, 10, 20), c.closingDateOfInvoice(ym(2026, 11)))
        assertEquals(InvoicePhase.ClosedEstimated, c.phase(ym(2026, 11), today = d(2026, 10, 25)))
    }

    // ---- parcelas ----

    @Test
    fun `100 em 3x vira 33,33 33,33 e 33,34`() {
        assertEquals(
            listOf(Money(3_333), Money(3_333), Money(3_334)),
            Installments.splitTotal(Money(10_000), 3),
        )
    }

    @Test
    fun `1200 em 6x são seis de 200`() {
        assertEquals(List(6) { Money(20_000) }, Installments.splitTotal(Money(120_000), 6))
    }

    @Test
    fun `a soma das parcelas sempre fecha o total`() {
        for (count in 1..24) {
            for (total in count.toLong()..3_000L) {
                val parts = Installments.splitTotal(Money(total), count)
                assertEquals(count, parts.size)
                assertEquals(Money(total), parts.sum(), "total=$total count=$count")
                // a diferença fica só na última parcela
                assertEquals(1, parts.dropLast(1).distinct().size.coerceAtLeast(1))
            }
        }
    }

    @Test
    fun `valor da parcela informado gera total parcela vezes quantidade`() {
        val parts = Installments.fromEach(Money(20_000), 6)
        assertEquals(List(6) { Money(20_000) }, parts)
        assertEquals(Money(120_000), parts.sum())
    }

    @Test
    fun `parcela menor que um centavo não existe`() {
        assertFailsWith<IllegalArgumentException> { Installments.splitTotal(Money(2), 3) }
        assertFailsWith<IllegalArgumentException> { Installments.splitTotal(Money(0), 1) }
        assertFailsWith<IllegalArgumentException> { Installments.splitTotal(Money(100), 0) }
    }

    @Test
    fun `seis parcelas com primeira fatura em outubro ocupam de outubro a março e não abril`() {
        val s = Installments.schedule(first = ym(2026, 10), count = 6)
        assertEquals(
            listOf(ym(2026, 10), ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3)),
            s,
        )
        assertEquals(false, ym(2027, 4) in s)
    }

    @Test
    fun `deslocar a primeira parcela move todas uma por mês`() {
        val r = Installments.shiftFirst(
            oldFirst = ym(2026, 10), count = 6, newFirst = ym(2026, 11),
            paidMonths = emptySet(), cancelledNumbers = emptySet(),
        )
        assertIs<ShiftResult.Shifted>(r)
        assertEquals(
            listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3), ym(2027, 4)),
            r.after,
        )
        assertEquals(Installments.schedule(ym(2026, 10), 6), r.before)
        // outubro perde uma parcela, abril ganha uma
        assertEquals(false, ym(2026, 10) in r.after)
        assertEquals(true, ym(2027, 4) in r.after)
        // nenhuma fatura com duas parcelas da compra
        assertEquals(r.after.size, r.after.toSet().size)
    }

    @Test
    fun `deslocar para trás também funciona`() {
        val r = Installments.shiftFirst(ym(2026, 11), 3, ym(2026, 10), emptySet(), emptySet())
        assertIs<ShiftResult.Shifted>(r)
        assertEquals(listOf(ym(2026, 10), ym(2026, 11), ym(2026, 12)), r.after)
    }

    @Test
    fun `deslocamento com faturas de outubro e novembro quitadas é bloqueado listando ambas`() {
        val r = Installments.shiftFirst(
            ym(2026, 10), 6, ym(2026, 11),
            paidMonths = setOf(ym(2026, 10), ym(2026, 11)), cancelledNumbers = emptySet(),
        )
        assertIs<ShiftResult.Blocked>(r)
        assertEquals(listOf(ym(2026, 10), ym(2026, 11)), r.paidMonths)
        assertEquals(emptyList(), r.cancelledNumbers)
    }

    @Test
    fun `fatura quitada fora do intervalo afetado não bloqueia`() {
        val r = Installments.shiftFirst(
            ym(2026, 10), 3, ym(2026, 11),
            paidMonths = setOf(ym(2026, 9)), cancelledNumbers = emptySet(),
        )
        assertIs<ShiftResult.Shifted>(r)
    }

    @Test
    fun `parcela cancelada bloqueia o deslocamento mesmo sem fatura quitada`() {
        val r = Installments.shiftFirst(
            ym(2026, 10), 6, ym(2026, 11),
            paidMonths = emptySet(), cancelledNumbers = setOf(5, 6),
        )
        assertIs<ShiftResult.Blocked>(r)
        assertEquals(listOf(5, 6), r.cancelledNumbers)
        assertEquals(emptyList(), r.paidMonths)
    }

    @Test
    fun `deslocar para o mesmo mês não muda nada`() {
        val r = Installments.shiftFirst(ym(2026, 10), 3, ym(2026, 10), setOf(ym(2026, 10)), setOf(1))
        assertIs<ShiftResult.Shifted>(r)
        assertEquals(r.before, r.after)
    }
}
