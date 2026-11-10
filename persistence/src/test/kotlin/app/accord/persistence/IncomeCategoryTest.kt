package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.sync.EntityKey
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.EditIncomeCategory
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

    /** As principais, na ordem de exibição (as subcategorias ficam em [subs]). */
    private suspend fun Handle.mains() = space.incomeCategories().value().filter { it.parentId == null }
    private suspend fun Handle.subs(parentId: String) = space.incomeCategories().value().filter { it.parentId == parentId }

    @Test fun `every space starts with the six main income categories with stable ids`() = runBlocking<Unit> {
        val a = open("ana")
        val b = open("bia")
        val list = a.mains()
        assertEquals(listOf("Investimento", "Salário", "Renda Extra", "Freela / Autônomo", "Aluguéis", "Outros"), list.map { it.name })
        assertEquals(listOf("investimento", "salario", "renda-extra", "freela-autonomo", "alugueis", "outros"), list.map { it.seedKey })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), list.map { it.order })
        assertEquals(list.map { it.id }, b.mains().map { it.id }, "the same ids on every device of the space")
        assertEquals(inc("investimento"), list.first().id)
        assertEquals(IncomeCategorySeed.items.map { it.key }.sorted(), a.space.incomeCategories().value().map { it.seedKey }.sortedBy { it })
    }

    @Test fun `the seeded subcategories are exactly the agreed ones, one level below their main`() = runBlocking<Unit> {
        val h = open()
        suspend fun names(main: String) = h.subs(inc(main)).sortedBy { it.order }.map { it.name }
        assertEquals(listOf("CLT", "13º", "PLR / Bônus", "Outros"), names("salario"))
        assertEquals(listOf("Serviços prestados", "Consultoria", "Projetos", "Outros"), names("freela-autonomo"))
        assertEquals(listOf("Dividendos", "Juros / Rendimentos", "Venda de Ativos", "Outros"), names("investimento"))
        assertEquals(listOf("Vendas", "Cashback", "Restituição de IR", "Outros"), names("renda-extra"))
        assertEquals(listOf("Imóvel Residencial", "Imóvel Comercial", "Outros"), names("alugueis"))
        assertEquals(listOf("Empréstimos Recebidos", "Devoluções", "Diversos", "Outros"), names("outros"))
        assertEquals(inc("outros.emprestimos-recebidos"), h.subs(inc("outros")).single { it.name == "Empréstimos Recebidos" }.id)
        assertEquals(23, h.space.incomeCategories().value().count { it.parentId != null }, "só as subcategorias têm pai")
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
        val list = h.mains()
        assertEquals(listOf("Investimento", "Salário", "Renda Extra", "Freela / Autônomo", "Aluguéis", "Outros", "Aluguel"), list.map { it.name })
        assertEquals(change.id, list.last().id)
        assertNull(list.last().seedKey)
        assertEquals("tag", list.last().iconKey)
        assertEquals(6, list.last().order)
    }

    @Test fun `names are unique ignoring case, accents and extra spaces, and cannot be empty`() = runBlocking<Unit> {
        val h = open()
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("SALARIO")).errorCodes())
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("renda   extra")).errorCodes())
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), CreateIncomeCategory("   ")).errorCodes())
        assertEquals(6, h.mains().size)
    }

    @Test fun `a subcategory is created under a main, names are unique only among siblings and there is one level`() = runBlocking<Unit> {
        val h = open()
        val salary = inc("salario")
        val sub = h.space.execute(h.env(), CreateIncomeCategory("Comissão", salary)).applied().changes.single()
        assertEquals(EntityType.IncomeCategory, sub.type)
        val created = h.subs(salary).single { it.id == sub.id }
        assertEquals(salary, created.parentId)
        assertEquals(4, created.order, "depois das quatro subcategorias iniciais")
        h.space.execute(h.env(), CreateIncomeCategory("Comissão", inc("renda-extra"))).applied()
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory(" comissao ", salary)).errorCodes())
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("clt", salary)).errorCodes())
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), CreateIncomeCategory("outros")).errorCodes())
        h.space.execute(h.env(), CreateIncomeCategory("Pix", inc("alugueis"))).applied()
        assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), CreateIncomeCategory("Neta", sub.id)).errorCodes())
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), CreateIncomeCategory("X", "nao-existe")).errorCodes())
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), CreateIncomeCategory("X", seed("alimentacao"))).errorCodes())
    }

    @Test fun `renaming a subcategory respects its siblings and an income can use a subcategory`() = runBlocking<Unit> {
        val h = open()
        h.withAccount()
        val clt = h.subs(inc("salario")).single { it.name == "CLT" }
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), RenameIncomeCategory(clt.id, "13º")).errorCodes())
        h.space.execute(h.env(), RenameIncomeCategory(h.subs(inc("alugueis")).first().id, "CLT")).applied()
        val id = h.space.execute(h.env(), RegisterIncome("Salário", brl(100), d(2026, 10, 5), incomeCategoryId = clt.id)).applied().changes.single().id
        assertEquals(clt.id, h.space.incomes(ym(2026, 10)).value().items.single { it.id == id }.incomeCategoryId)
    }

    @Test fun `seeding again on the same database changes nothing`() = runBlocking<Unit> {
        val a = open("ana")
        val before = a.space.incomeCategories().value()
        assertEquals(29, before.size, "seis principais e 23 subcategorias")
        assertEquals(before, open("ana", database = a.database).space.incomeCategories().value())
    }

    @Test fun `the parent of an income subcategory is synced as a reference to an income category, not to an expense category`() = runBlocking<Unit> {
        val h = open()
        val sub = h.space.execute(h.env(), CreateIncomeCategory("Comissão", inc("salario"))).applied().changes.single().id
        val state = checkNotNull(EntityStates.read(h.database.db.dao(), EntityType.IncomeCategory, sub))
        assertEquals(listOf(EntityKey("IncomeCategory", inc("salario"))), state.references)
        assertTrue(state.state.contains("\"parentId\""))
    }

    @Test fun `renaming keeps the identity and respects uniqueness and the revision`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), CreateIncomeCategory("Prêmios")).applied().changes.single().id
        h.space.execute(h.env(), RenameIncomeCategory(id, "Premiações")).applied()
        assertEquals("Premiações", h.space.incomeCategories().value().single { it.id == id }.name)
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), RenameIncomeCategory(id, "salário")).errorCodes())
        val stale = h.space.execute(h.env(), RenameIncomeCategory(id, "Outro", expectedRevision = 1))
        assertIs<CommandResult.StaleVersion>(stale)
    }

    @Test fun `editing changes only what came filled, keeps the identity and validates name, icon, color and revision`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), CreateIncomeCategory("Prêmios")).applied().changes.single().id
        assertNull(h.space.incomeCategories().value().single { it.id == id }.colorIndex)
        h.space.execute(h.env(), EditIncomeCategory(id, iconKey = "house", colorIndex = 4)).applied()
        val edited = h.space.incomeCategories().value().single { it.id == id }
        assertEquals("Prêmios", edited.name)
        assertEquals("house", edited.iconKey)
        assertEquals(4, edited.colorIndex)
        assertEquals(2, edited.revision)
        h.space.execute(h.env(), EditIncomeCategory(id, name = "Premiações")).applied()
        val renamed = h.space.incomeCategories().value().single { it.id == id }
        assertEquals("Premiações", renamed.name)
        assertEquals(4, renamed.colorIndex)
        assertEquals("house", renamed.iconKey)
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), EditIncomeCategory(id)).errorCodes())
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), EditIncomeCategory(id, name = "  ")).errorCodes())
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), EditIncomeCategory(id, name = "salário")).errorCodes())
        assertEquals(listOf(ErrorCode.OutOfRange), h.space.execute(h.env(), EditIncomeCategory(id, colorIndex = 12)).errorCodes())
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), EditIncomeCategory("nao-existe", colorIndex = 1)).errorCodes())
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), EditIncomeCategory(id, colorIndex = 1, expectedRevision = 1)))
        // A semente também pode ser editada, sem perder a chave que a identifica.
        h.space.execute(h.env(), EditIncomeCategory(inc("salario"), colorIndex = 0)).applied()
        val seed = h.space.incomeCategories().value().single { it.id == inc("salario") }
        assertEquals(0, seed.colorIndex)
        assertEquals("salario", seed.seedKey)
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
