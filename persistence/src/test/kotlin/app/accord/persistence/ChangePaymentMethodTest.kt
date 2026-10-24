package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.Origin
import app.accord.core.space.PaymentMethod
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangePaymentMethodTest : SpaceTestBase() {
    private val day = LocalDate.of(2026, 10, 22)

    private suspend fun Handle.accounts(): Pair<String, String> {
        val a = space.execute(env(), AddAccount(BankRef.Other("Banco A"), "A")).applied().changes.single().id
        val b = space.execute(env(), AddAccount(BankRef.Other("Banco B"), "B")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(a)).applied()
        return a to b
    }

    private suspend fun Handle.pix(accountId: String? = null) = space.execute(
        Envelope(CommandId("pix-${nextId().value}"), Origin.Chat),
        RegisterDirectPayment(DirectPaymentKind.Pix, "Mercado", Classification.Category(seed("alimentacao.mercado")), day, brl(35), accountId),
    ).applied()

    private suspend fun Handle.card(installments: Int = 1) = space.execute(
        Envelope(CommandId("card-${nextId().value}"), Origin.Chat),
        RegisterCardPurchase(
            "Delivery", Classification.Category(seed("alimentacao.delivery")), day, brl(68),
            if (installments > 1) AmountMeaning.Total else null, installments,
        ),
    ).applied()

    @Test
    fun `de pix para debito mantem a identidade e o desfazer restaura`() = runBlocking<Unit> {
        val h = open()
        val (a, _) = h.accounts()
        val created = h.pix()
        val id = created.changes.single().id
        val receipt = h.space.execute(h.env(), ChangePaymentMethod(id, DirectPaymentKind.Debit)).applied()
        assertEquals(id, receipt.changes.single().id)
        val detail = h.space.entry(id).value()!!
        assertEquals(PaymentMethod.Debit, detail.method)
        assertEquals(a, detail.accountId)
        assertTrue(receipt.undoable)

        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertEquals(PaymentMethod.Pix, h.space.entry(id).value()!!.method)
    }

    @Test
    fun `dinheiro nao tem conta e volta a ter ao trocar para pix`() = runBlocking<Unit> {
        val h = open()
        val (a, b) = h.accounts()
        val id = h.pix(b).changes.single().id
        h.space.execute(h.env(), ChangePaymentMethod(id, DirectPaymentKind.Cash)).applied()
        val cash = h.space.entry(id).value()!!
        assertEquals(PaymentMethod.Cash, cash.method)
        assertNull(cash.accountId)
        // Sem conta informada nem atual, usa o padrão de quem registra.
        h.space.execute(h.env(), ChangePaymentMethod(id, DirectPaymentKind.Pix)).applied()
        assertEquals(a, h.space.entry(id).value()!!.accountId)
    }

    @Test
    fun `repetir a mesma forma de pagamento nao muda nada`() = runBlocking<Unit> {
        val h = open()
        h.accounts()
        val id = h.pix().changes.single().id
        val result = h.space.execute(h.env(), ChangePaymentMethod(id, DirectPaymentKind.Pix))
        assertIs<CommandResult.Rejected>(result)
    }

    @Test
    fun `compra a vista no cartao vira pix com nova identidade numa so operacao`() = runBlocking<Unit> {
        val h = open()
        val (a, _) = h.accounts()
        val purchaseId = h.card().changes.first { it.type == EntityType.CardPurchase }.id
        assertEquals(brl(68), h.space.budget(ym(2026, 11)).value().committed)
        val receipt = h.space.execute(h.env(), ChangePaymentMethod(purchaseId, DirectPaymentKind.Pix)).applied()

        val types = receipt.changes.map { it.type }.toSet()
        assertEquals(setOf(EntityType.CardPurchase, EntityType.Movement), types)
        val movementId = receipt.changes.first { it.type == EntityType.Movement }.id
        val movement = h.space.entry(movementId).value()!!
        assertEquals(PaymentMethod.Pix, movement.method)
        assertEquals(a, movement.accountId)
        assertEquals(brl(68), movement.amount)
        assertTrue(h.space.entry(purchaseId).value()!!.voided)
        // O gasto saiu da fatura e entrou no mês da movimentação, uma única vez.
        assertEquals(brl(0), h.space.budget(ym(2026, 11)).value().committed)
        assertEquals(brl(68), h.space.budget(ym(2026, 10)).value().committed)

        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertTrue(!h.space.entry(purchaseId).value()!!.voided)
        assertTrue(h.space.entry(movementId).value()!!.voided)
        assertEquals(brl(0), h.space.budget(ym(2026, 10)).value().committed)
    }

    @Test
    fun `parcelada e compra com estorno nao trocam de forma de pagamento`() = runBlocking<Unit> {
        val h = open()
        h.accounts()
        val parcelada = h.card(3).changes.first { it.type == EntityType.CardPurchase }.id
        val rejected = assertIs<CommandResult.Rejected>(h.space.execute(h.env(), ChangePaymentMethod(parcelada, DirectPaymentKind.Pix)))
        assertEquals(ErrorCode.NotAllowed, rejected.errors.single().code)

        val avista = h.card().changes.first { it.type == EntityType.CardPurchase }.id
        h.space.execute(h.env(), RegisterRefund(avista, brl(10), RefundStatus.Requested)).applied()
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), ChangePaymentMethod(avista, DirectPaymentKind.Debit)))
    }

    @Test
    fun `sem conta padrao nem informada a troca para pix exige a conta`() = runBlocking<Unit> {
        val h = open()
        val a = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco A"), "A")).applied().changes.single().id
        h.space.execute(h.env(), AddAccount(BankRef.Other("Banco B"), "B")).applied()
        val cash = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Cash, "Feira", Classification.ToClassify, day, brl(20)),
        ).applied().changes.single().id
        val rejected = assertIs<CommandResult.Rejected>(h.space.execute(h.env(), ChangePaymentMethod(cash, DirectPaymentKind.Pix)))
        assertEquals(ErrorCode.Required, rejected.errors.single().code)
        h.space.execute(h.env(), ChangePaymentMethod(cash, DirectPaymentKind.Pix, accountId = a)).applied()
        assertEquals(a, h.space.entry(cash).value()!!.accountId)
        assertNotNull(h.space.entry(cash).value())
    }

    @Test
    fun `reenvio com o mesmo id de comando nao troca duas vezes`() = runBlocking<Unit> {
        val h = open()
        h.accounts()
        val purchaseId = h.card().changes.first { it.type == EntityType.CardPurchase }.id
        val envelope = Envelope(CommandId("troca-1"), Origin.Chat)
        h.space.execute(envelope, ChangePaymentMethod(purchaseId, DirectPaymentKind.Pix)).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(envelope, ChangePaymentMethod(purchaseId, DirectPaymentKind.Pix)))
        assertEquals(1, h.space.movements(ym(2026, 10)).value().size)
    }
}
