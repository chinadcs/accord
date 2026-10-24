package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.DeleteEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.EntityType
import app.accord.core.space.ExtraReason
import app.accord.core.space.Origin
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.PreviewResult
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeleteEntryTest : SpaceTestBase() {
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.withDefaultAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private suspend fun Handle.pix(
        description: String = "mercado 80",
        amount: Int = 80,
        origin: Origin = Origin.Manual,
        date: LocalDate = d(2026, 10, 20),
    ): String = space.execute(
        Envelope(CommandId("pix-${description}-${nextId().value}"), origin),
        RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed("alimentacao.mercado")), date, brl(amount)),
    ).applied().changes.single().id

    private suspend fun Handle.card(amount: Int = 1200, installments: Int = 6, invoice: YearMonth = oct): String =
        space.execute(
            env(),
            RegisterCardPurchase(
                "Equipamento", Classification.Category(seed("eletronicos")), d(2026, 9, 25), brl(amount),
                if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
            ),
        ).applied().changes.single().id

    // ---- cenário 26a ----

    @Test
    fun `dois Pix iguais registrados e um excluído no dia seguinte — outubro perde R$ 80, o outro fica`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val first = h.pix()
        val second = h.pix()
        assertEquals(brl(160), h.space.budget(oct).value().committed)

        val receipt = h.space.execute(h.env(), DeleteEntry(first)).applied()

        assertEquals(brl(80), h.space.budget(oct).value().committed)
        assertEquals(listOf(second), h.space.movements(oct).value().map { it.id })
        val change = receipt.changes.single()
        assertEquals(first, change.id) // registro de exclusão com a mesma identidade
        assertEquals(EntityType.Movement, change.type)
        assertEquals(1, h.space.operations().value().count { it.operationId == receipt.operationId })
    }

    @Test
    fun `compra de 6 x R$ 200 excluída zera a compra em todos os seis meses`() = runBlocking {
        val h = open()
        val id = h.card()
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        for (m in listOf(oct, nov, ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3))) {
            assertEquals(Money.ZERO, h.space.budget(m).value().committed, "mês $m")
        }
        val detail = h.space.purchase(id).value()!!
        assertTrue(detail.voided)
        assertEquals(6, detail.installments.size) // a identidade e o cronograma permanecem
    }

    @Test
    fun `item de fatura quitada sem confirmação extra pede decisão e não altera nada`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.card(100, 1)
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(100))).applied()
        h.space.execute(h.env(), PayInvoice(oct, brl(100), d(2026, 10, 15))).applied()
        val before = h.space.operations().value().size

        val r = h.space.execute(h.env(), DeleteEntry(id))
        assertEquals(
            Decision.ExtraConfirmationRequired(listOf(ExtraReason.PaidInvoice)),
            assertIs<CommandResult.DecisionNeeded>(r).decision,
        )
        assertEquals(before, h.space.operations().value().size)
        assertEquals(brl(100), h.space.budget(oct).value().committed)

        h.space.execute(h.env(), DeleteEntry(id, confirmExtra = true)).applied()
        assertEquals(Money.ZERO, h.space.budget(oct).value().committed)
    }

    @Test
    fun `lançamento importado exige a confirmação extra`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(origin = Origin.Import)
        val r = h.space.execute(h.env(), DeleteEntry(id))
        assertEquals(
            Decision.ExtraConfirmationRequired(listOf(ExtraReason.Imported)),
            assertIs<CommandResult.DecisionNeeded>(r).decision,
        )
        h.space.execute(h.env(), DeleteEntry(id, confirmExtra = true)).applied()
        assertTrue(h.space.movements(oct).value().isEmpty())
    }

    @Test
    fun `importado em fatura quitada lista os dois motivos`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(
            Envelope(CommandId("imp"), Origin.Import),
            RegisterCardPurchase("Compra", Classification.Category(seed("compras")), d(2026, 9, 25), brl(100), invoice = oct),
        ).applied().changes.single().id
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(100))).applied()
        h.space.execute(h.env(), PayInvoice(oct, brl(100), d(2026, 10, 15))).applied()
        val r = h.space.execute(h.env(), DeleteEntry(id))
        val reasons = (assertIs<CommandResult.DecisionNeeded>(r).decision as Decision.ExtraConfirmationRequired).reasons
        assertEquals(setOf(ExtraReason.PaidInvoice, ExtraReason.Imported), reasons.toSet())
    }

    @Test
    fun `versão antiga devolve a atual`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix()
        val r = h.space.execute(h.env(), DeleteEntry(id, expectedRevision = 9))
        assertEquals(1L, assertIs<CommandResult.StaleVersion>(r).currentRevision)
        assertEquals(1, h.space.movements(oct).value().size)
    }

    @Test
    fun `compra Pix de R$ 100 com estorno confirmado de R$ 40 excluída leva o crédito junto numa só operação`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix("Compra", 100)
        val refundId = h.space.execute(h.env(), RegisterRefund(id, brl(40), RefundStatus.Confirmed, d(2026, 11, 5))).applied().changes.single().id

        val preview = assertIs<PreviewResult.Ready>(h.space.preview(DeleteEntry(id)))
        assertEquals(listOf(refundId), preview.linkedRefundIds)

        val receipt = h.space.execute(h.env(), DeleteEntry(id)).applied()
        assertEquals(setOf(EntityType.Movement, EntityType.Refund), receipt.changes.map { it.type }.toSet())
        assertEquals(Money.ZERO, h.space.budget(oct).value().committed)
        assertEquals(Money.ZERO, h.space.budget(nov).value().credits)
        assertTrue(h.space.refunds(id).value().isEmpty())
        assertEquals(setOf(oct, nov), receipt.affectedMonths)
    }

    @Test
    fun `excluir compra parcelada com estorno remove o crédito do mês da fatura`() = runBlocking {
        val h = open()
        val id = h.card(300, 3)
        h.space.execute(h.env(), RegisterRefund(id, brl(50), RefundStatus.Confirmed, d(2026, 11, 5), invoice = nov)).applied()
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        assertEquals(Money.ZERO, h.space.budget(nov).value().credits)
        assertEquals(Money.ZERO, h.space.invoice(nov).value().credits)
    }

    @Test
    fun `fatura conferida cuja composição deixa de coincidir volta a ter diferença`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val keep = h.card(1000, 1)
        val drop = h.card(300, 1)
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(1300))).applied()
        assertTrue(h.space.invoice(oct).value().conferred)

        h.space.execute(h.env(), DeleteEntry(drop)).applied()

        val invoice = h.space.invoice(oct).value()
        assertFalse(invoice.conferred)
        assertEquals(brl(300), invoice.totalDifference)
        assertEquals(brl(1000), invoice.knownTotal)
        assertEquals(brl(1000), h.space.purchase(keep).value()!!.total)
    }

    @Test
    fun `boleto e renda também podem ser excluídos`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val boleto = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.space.execute(h.env(), PayBoleto(boleto, Settlement(d(2026, 11, 3)))).applied()
        val income = h.space.execute(h.env(), RegisterIncome("Salário", brl(500), d(2026, 10, 5))).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(income, Settlement(d(2026, 10, 6)))).applied()

        h.space.execute(h.env(), DeleteEntry(boleto)).applied()
        h.space.execute(h.env(), DeleteEntry(income)).applied()

        assertEquals(Money.ZERO, h.space.budget(nov).value().committed)
        assertEquals(Money.ZERO, h.space.incomes(oct).value().received)
        assertTrue(h.space.boleto(boleto).value()!!.voided)
        assertTrue(h.space.income(income).value()!!.voided)
    }

    @Test
    fun `excluir de novo, id desconhecido e ocorrência de recorrência são rejeitados`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix()
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        val again = h.space.execute(h.env(), DeleteEntry(id))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(again).errors.single().code)
        val unknown = h.space.execute(h.env(), DeleteEntry("nada"))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
    }

    @Test
    fun `excluir não é estorno nem cancelamento`() = runBlocking {
        val h = open()
        val id = h.card(300, 3)
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        assertEquals(emptyList(), h.space.refunds(id).value())
        assertTrue(h.space.purchase(id).value()!!.installments.all { it.cancel == app.accord.core.space.CancelState.Active })
        assertEquals(Money.ZERO, h.space.budget(oct).value().credits)
    }
}
