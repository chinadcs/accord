package app.accord.persistence

import app.accord.core.space.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AccountBalanceTest : SpaceTestBase() {
    @Test
    fun `recebimento efetivo e quitacao debitam somente conta escolhida`() = runBlocking<Unit> {
        val h = open()
        val a = h.account()
        val b = h.account()
        h.space.execute(h.env(), SetAccountBalance(a, brl(100), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), SetAccountBalance(b, brl(200), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), RegisterIncome("Prevista", brl(900), d(2026, 10, 5), accountId = a)).applied()
        h.space.execute(h.env(), RegisterIncome("Recebida", brl(500), d(2026, 9, 30), accountId = a, received = Settlement(d(2026, 10, 2), brl(450)))).applied()
        h.space.execute(h.env(), RegisterCardPurchase("Compra", Classification.Category(seed("compras")), d(2026, 9, 25), brl(80), invoice = ym(2026, 10))).applied()
        assertEquals(brl(550), h.balance(a))
        assertEquals(brl(200), h.balance(b))
        h.space.execute(h.env(), RegisterInvoiceTotal(ym(2026, 10), brl(80))).applied()
        h.space.execute(h.env(), PayInvoice(ym(2026, 10), brl(80), d(2026, 10, 15), b)).applied()
        assertEquals(brl(550), h.balance(a))
        assertEquals(brl(120), h.balance(b))
    }

    private suspend fun Handle.account() = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
    private suspend fun Handle.balance(id: String) = space.accountBalances().value().single { it.accountId == id }.balance

    @Test
    fun `saldo desconhecido difere de zero e corte inclui somente a partir da data`() = runBlocking<Unit> {
        val h = open()
        val id = h.account()
        assertNull(h.balance(id))
        h.space.execute(h.env(), SetAccountBalance(id, brl(100), d(2026, 10, 20))).applied()
        for (day in listOf(19, 20, 21)) h.space.execute(h.env(), RegisterDirectPayment(
            DirectPaymentKind.Pix, "Compra", Classification.Category(seed("alimentacao.mercado")), d(2026, 10, day), brl(60), id,
        )).applied()
        assertEquals(brl(-20), h.balance(id))
    }

    @Test
    fun `transferencia e ajuste nao mudam orcamento e desfazer restaura os saldos`() = runBlocking<Unit> {
        val h = open()
        val a = h.account()
        val b = h.space.accounts().value().wallet!!.id
        h.space.execute(h.env(), SetAccountBalance(a, brl(100), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), SetAccountBalance(b, brl(0), d(2026, 10, 1))).applied()
        val budget = h.space.budget(ym(2026, 10)).value()
        val env = h.env()
        val transfer = TransferBetweenAccounts(a, b, brl(150), d(2026, 10, 24))
        val receipt = h.space.execute(env, transfer).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(env, transfer))
        assertEquals(brl(-50), h.balance(a))
        assertEquals(brl(150), h.balance(b))
        val adjust = h.space.execute(h.env(), AdjustAccountBalance(a, brl(-10), d(2026, 10, 24))).applied()
        assertEquals(brl(-10), h.balance(a))
        assertEquals(budget, h.space.budget(ym(2026, 10)).value())
        h.space.execute(h.env(), Undo(adjust.operationId)).applied()
        assertEquals(brl(-50), h.balance(a))
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertEquals(brl(100), h.balance(a))
        assertEquals(brl(0), h.balance(b))
    }

    @Test
    fun `dinheiro sai somente da carteira e carteira nao vira padrao`() = runBlocking<Unit> {
        val h = open()
        val wallet = h.space.accounts().value().wallet!!.id
        h.space.execute(h.env(), SetAccountBalance(wallet, brl(80), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "Compra",
            Classification.Category(seed("alimentacao.mercado")), d(2026, 10, 24), brl(30))).applied()
        assertEquals(brl(50), h.balance(wallet))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), SetMyDefaultAccount(wallet)))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), TransferBetweenAccounts(wallet, wallet, brl(1), d(2026, 10, 24))))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), AdjustAccountBalance(wallet, brl(1), d(2026, 9, 30))))
    }
}
