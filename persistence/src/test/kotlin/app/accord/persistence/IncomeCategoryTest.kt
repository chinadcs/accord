package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.IncomeCategorySeed
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RenameIncomeCategory
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-llf7: income categories are their own list of receipts, never mixed with expense categories. */
class IncomeCategoryTest : SpaceTestBase() {
    private fun inc(key: String) = DeterministicIds.seedIncomeCategory(spaceId, key)

    private suspend fun Handle.withAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private fun CommandResult.errorCodes() = assertIs<CommandResult.Rejected>(this).errors.map { it.code }

    @Test fun `every space starts with Investimento, Salário and Renda Extra with stable ids`() = runBlocking<Unit> {
        val a = open("ana")
        val b = open("bia")
        val list = a.space.incomeCategories().value()
        assertEquals(listOf("Investimento", "Salário", "Renda Extra"), list.map { it.name })
        assertEquals(listOf("investimento", "salario", "renda-extra"), list.map { it.seedKey })
        assertEquals(listOf(0, 1, 2), list.map { it.order })
        assertEquals(list.map { it.id }, b.space.incomeCategories().value().map { it.id }, "the same ids on every device of the space")
        assertEquals(inc("investimento"), list.first().id)
        assertEquals(IncomeCategorySeed.items.map { it.key }, list.map { it.seedKey })
    }

    @Test fun `income categories do not appear among expense categories and cannot classify an expense`() = runBlocking<Unit> {
        val h = open()
        h.withAccount()
        assertTrue(h.space.categories().value().none { it.name == "Investimento" || it.name == "Renda Extra" })
        val r = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "X", Classification.Category(inc("salario")), d(2026, 10, 20), brl(10)))
        assertTrue(r is CommandResult.Rejected)
    }

    @Test fun `a new category is added at the end, with a generic icon, and is a synced entity`() = runBlocking<Unit> {
        val h = open()
        val receipt = h.space.execute(h.env(), CreateIncomeCategory("  Aluguel ")).applied()
        val change = receipt.changes.single()
        assertEquals(EntityType.IncomeCategory, change.type)
        val list = h.space.incomeCategories().value()
        assertEquals(listOf("Investimento", "Salário", "Renda Extra", "Aluguel"), list.map { it.name })
        assertEquals(change.id, list.last().id)
        assertNull(list.last().seedKey)
        assertEquals("tag", list.last().iconKey)
        assertEquals(3, list.last().order)
    }

    @Test fun `names are unique ignoring case, accents and extra spaces, and cannot be empty`() = runBlocking<Unit> {
        val h = open()
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("SALARIO")).errorCodes())
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("renda   extra")).errorCodes())
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), CreateIncomeCategory("   ")).errorCodes())
        assertEquals(3, h.space.incomeCategories().value().size)
    }

    @Test fun `renaming keeps the identity and respects uniqueness and the revision`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), CreateIncomeCategory("Aluguel")).applied().changes.single().id
        h.space.execute(h.env(), RenameIncomeCategory(id, "Aluguéis")).applied()
        assertEquals("Aluguéis", h.space.incomeCategories().value().single { it.id == id }.name)
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), RenameIncomeCategory(id, "salário")).errorCodes())
        val stale = h.space.execute(h.env(), RenameIncomeCategory(id, "Outro", expectedRevision = 1))
        assertIs<CommandResult.StaleVersion>(stale)
    }

    @Test fun `an income carries its category, which survives receiving it`() = runBlocking<Unit> {
        val h = open()
        h.withAccount()
        val id = h.space.execute(h.env(), RegisterIncome("Resgate", brl(100), d(2026, 10, 5), incomeCategoryId = inc("investimento"))).applied().changes.single().id
        assertEquals(inc("investimento"), h.space.incomes(ym(2026, 10)).value().items.single().incomeCategoryId)
        h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 10, 6)))).applied()
        assertEquals(inc("investimento"), h.space.incomes(ym(2026, 10)).value().items.single().incomeCategoryId)
        val plain = h.space.execute(h.env(), RegisterIncome("Sem categoria", brl(10), d(2026, 10, 7))).applied().changes.single().id
        assertTrue(plain.isNotBlank())
        assertEquals(1, h.space.incomes(ym(2026, 10)).value().items.count { it.incomeCategoryId == null })
    }

    @Test fun `an unknown or expense category is rejected for an income and nothing is saved`() = runBlocking<Unit> {
        val h = open()
        h.withAccount()
        for (bad in listOf("nao-existe", seed("alimentacao"))) {
            val r = h.space.execute(h.env(), RegisterIncome("X", brl(10), d(2026, 10, 5), incomeCategoryId = bad))
            assertEquals(listOf(ErrorCode.UnknownReference), r.errorCodes())
        }
        assertTrue(h.space.incomes(ym(2026, 10)).value().items.isEmpty())
    }

    @Test fun `correcting the category of an income changes it, undo restores it, and expenses refuse it`() = runBlocking<Unit> {
        val h = open()
        h.withAccount()
        val id = h.space.execute(h.env(), RegisterIncome("Pagamento", brl(500), d(2026, 10, 5), incomeCategoryId = inc("salario"))).applied().changes.single().id
        val fix = h.space.execute(h.env(), CorrectEntry(id, incomeCategoryId = inc("renda-extra"))).applied()
        assertEquals(inc("renda-extra"), h.space.incomes(ym(2026, 10)).value().items.single().incomeCategoryId)
        h.space.execute(h.env(), Undo(fix.operationId)).applied()
        assertEquals(inc("salario"), h.space.incomes(ym(2026, 10)).value().items.single().incomeCategoryId)
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), CorrectEntry(id, incomeCategoryId = "nao-existe")).errorCodes())
        val expense = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "Delivery", Classification.Category(seed("alimentacao.delivery")), d(2026, 10, 20), brl(30))).applied().changes.single().id
        assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), CorrectEntry(expense, incomeCategoryId = inc("salario"))).errorCodes())
    }
}
