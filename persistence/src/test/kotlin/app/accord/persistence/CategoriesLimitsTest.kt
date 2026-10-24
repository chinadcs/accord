package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.Decision
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditAccount
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.InvolvedPart
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.PartDecision
import app.accord.core.space.PreviewResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RenameCategory
import app.accord.core.space.ReorderCategories
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoriesLimitsTest : SpaceTestBase() {
    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)
    private val dec = ym(2026, 12)

    private suspend fun Handle.withDefaultAccount(): String {
        val id = space.execute(env(), AddAccount(BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta salário")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private suspend fun Handle.spend(category: String, reais: Int, date: LocalDate = d(2026, 10, 20), categoryId: String = seed(category)) {
        space.execute(
            env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Gasto", Classification.Category(categoryId), date, brl(reais)),
        ).applied()
    }

    private fun total(reais: Int) = LimitEdit(total = TotalChange.Set(brl(reais)))

    private suspend fun Handle.limit(
        category: String,
        edit: LimitEdit,
        month: YearMonth = oct,
        scope: LimitScope = LimitScope.FromThisMonth,
    ) = space.execute(env(), DefineLimits(month, scope, listOf(CategoryLimitEdit(seed(category), edit))))

    private suspend fun Handle.budgetOf(category: String, month: YearMonth = oct) =
        space.budget(month).value().categories.single { it.categoryId == seed(category) }

    // ---- divisão (cenário 13) ----

    @Test
    fun `Alimentação R$ 1200 com Delivery 400 e Mercado 500 — sem distribuir 300, restante 220, restante da parte 120 e 0`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        h.limit(
            "alimentacao",
            LimitEdit(
                total = TotalChange.Set(brl(1200)),
                split = mapOf(seed("alimentacao.delivery") to brl(400), seed("alimentacao.mercado") to brl(500)),
            ),
        ).applied()
        h.spend("alimentacao.delivery", 280)
        h.spend("alimentacao.mercado", 500)
        h.spend("alimentacao.restaurantes", 200)

        val limit = h.budgetOf("alimentacao").limit
        assertEquals(brl(1200), limit.total)
        assertEquals(brl(900), limit.distributed)
        assertEquals(brl(300), limit.undistributed)
        assertEquals(brl(220), limit.remaining)
        assertEquals(brl(120), limit.sub(seed("alimentacao.delivery")).remainingOfPart)
        assertEquals(Money.ZERO, limit.sub(seed("alimentacao.mercado")).remainingOfPart)
        assertNull(limit.sub(seed("alimentacao.restaurantes")).part) // sem divisão definida, sem limite zero
        assertNull(limit.excess)
    }

    @Test
    fun `divisão 300 600 300 gera restantes 20 100 100`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        h.limit(
            "alimentacao",
            LimitEdit(
                total = TotalChange.Set(brl(1200)),
                split = mapOf(
                    seed("alimentacao.delivery") to brl(300), seed("alimentacao.mercado") to brl(600),
                    seed("alimentacao.restaurantes") to brl(300),
                ),
            ),
        ).applied()
        h.spend("alimentacao.delivery", 280)
        h.spend("alimentacao.mercado", 500)
        h.spend("alimentacao.restaurantes", 200)
        val limit = h.budgetOf("alimentacao").limit
        assertEquals(brl(20), limit.sub(seed("alimentacao.delivery")).remainingOfPart)
        assertEquals(brl(100), limit.sub(seed("alimentacao.mercado")).remainingOfPart)
        assertEquals(brl(100), limit.sub(seed("alimentacao.restaurantes")).remainingOfPart)
    }

    @Test
    fun `divisão de R$ 1300 num limite de R$ 1200 é rejeitada com diferença de R$ 100 e nada é gravado`() = runBlocking {
        val h = open()
        val before = h.space.operations().value().size
        val r = h.limit(
            "alimentacao",
            LimitEdit(
                total = TotalChange.Set(brl(1200)),
                split = mapOf(seed("alimentacao.delivery") to brl(800), seed("alimentacao.mercado") to brl(500)),
            ),
        )
        val error = assertIs<CommandResult.Rejected>(r).errors.single()
        assertEquals(ErrorCode.SplitExceedsLimit, error.code)
        assertEquals(brl(100), error.remaining)
        assertEquals(oct, error.month)
        assertEquals(before, h.space.operations().value().size)
        assertNull(h.space.limits(oct).value().single { it.categoryId == seed("alimentacao") }.setting.total)
    }

    @Test
    fun `total geral soma só as categorias principais e três categorias geram uma operação com três entidades`() = runBlocking {
        val h = open()
        val r = h.space.execute(
            h.env(),
            DefineLimits(
                oct, LimitScope.FromThisMonth,
                listOf(
                    CategoryLimitEdit(
                        seed("alimentacao"),
                        LimitEdit(TotalChange.Set(brl(1200)), mapOf(seed("alimentacao.delivery") to brl(400))),
                    ),
                    CategoryLimitEdit(seed("compras"), total(2000)),
                    CategoryLimitEdit(seed("casa"), total(1300)),
                ),
            ),
        ).applied()
        assertEquals(3, r.changes.size)
        assertTrue(r.changes.all { it.type == EntityType.LimitPlan })
        assertEquals(1, h.space.operations().value().count { it.operationId == r.operationId })
        assertEquals(brl(4500), h.space.budget(oct).value().totalLimits)
    }

    // ---- ausente versus zero (cenário 14) ----

    @Test
    fun `sem limite com R$ 50 gastos não há excesso e limite zero mostra excesso de R$ 50`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        h.spend("lazer", 50)
        val none = h.budgetOf("lazer").limit
        assertNull(none.total)
        assertNull(none.excess)
        assertNull(none.remaining)

        h.limit("lazer", total(0)).applied()
        val zero = h.budgetOf("lazer").limit
        assertEquals(Money.ZERO, zero.total)
        assertEquals(brl(50), zero.excess)
    }

    @Test
    fun `limite reduzido para R$ 900 com gasto de R$ 980 mostra excesso de R$ 80 sem mexer em outras categorias`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        h.limit("alimentacao", total(1200)).applied()
        h.limit("compras", total(500)).applied()
        h.spend("alimentacao.mercado", 980)
        h.limit("alimentacao", total(900)).applied()
        assertEquals(brl(80), h.budgetOf("alimentacao").limit.excess)
        assertEquals(brl(500), h.budgetOf("compras").limit.total)
        assertEquals(brl(1400), h.space.budget(oct).value().totalLimits)
    }

    // ---- vigência (cenário 15) ----

    @Test
    fun `somente outubro muda só outubro e outubro e meses seguintes mantém setembro`() = runBlocking {
        val h = open()
        h.limit("alimentacao", total(1200), month = sep).applied()
        h.limit("alimentacao", total(1400), month = oct, scope = LimitScope.OnlyThisMonth).applied()
        assertEquals(brl(1200), h.budgetOf("alimentacao", sep).limit.total)
        assertEquals(brl(1400), h.budgetOf("alimentacao", oct).limit.total)
        assertEquals(brl(1200), h.budgetOf("alimentacao", nov).limit.total)

        val h2 = open()
        h2.limit("alimentacao", total(1200), month = sep).applied()
        h2.limit("alimentacao", total(1400), month = oct).applied()
        assertEquals(brl(1200), h2.budgetOf("alimentacao", sep).limit.total)
        assertEquals(brl(1400), h2.budgetOf("alimentacao", oct).limit.total)
        assertEquals(brl(1400), h2.budgetOf("alimentacao", ym(2027, 6)).limit.total)
    }

    @Test
    fun `exceção futura de dezembro aparece na prévia e é substituída`() = runBlocking {
        val h = open()
        h.limit("alimentacao", total(1200), month = sep).applied()
        h.limit("alimentacao", total(1600), month = dec, scope = LimitScope.OnlyThisMonth).applied()

        val command = DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(seed("alimentacao"), total(1400))))
        val preview = assertIs<PreviewResult.Ready>(h.space.preview(command))
        assertEquals(listOf(dec), preview.replacedLimitMonths)
        assertEquals(brl(1600), h.budgetOf("alimentacao", dec).limit.total) // prévia não grava

        h.space.execute(h.env(), command).applied()
        assertEquals(brl(1400), h.budgetOf("alimentacao", dec).limit.total)
        assertEquals(brl(1200), h.budgetOf("alimentacao", sep).limit.total)
    }

    @Test
    fun `divisão que excederia o limite de dezembro rejeita listando dezembro e não grava mês nenhum`() = runBlocking {
        val h = open()
        h.limit(
            "alimentacao",
            LimitEdit(TotalChange.Set(brl(1200)), mapOf(seed("alimentacao.delivery") to brl(400))), month = sep,
        ).applied()
        h.space.execute(
            h.env(),
            DefineLimits(
                dec, LimitScope.OnlyThisMonth,
                listOf(
                    CategoryLimitEdit(
                        seed("alimentacao"),
                        LimitEdit(TotalChange.Set(brl(1500)), mapOf(seed("alimentacao.delivery") to brl(1000))),
                    ),
                ),
            ),
        ).applied()

        val r = h.limit("alimentacao", total(900)) // de outubro em diante, sem editar a divisão
        val errors = assertIs<CommandResult.Rejected>(r).errors
        assertEquals(listOf(dec), errors.map { it.month })
        assertEquals(brl(1200), h.budgetOf("alimentacao", oct).limit.total)
        assertEquals(brl(1500), h.budgetOf("alimentacao", dec).limit.total)
    }

    @Test
    fun `sem escolher o alcance pede decisão e nada é gravado`() = runBlocking {
        val h = open()
        val r = h.space.execute(h.env(), DefineLimits(oct, null, listOf(CategoryLimitEdit(seed("alimentacao"), total(1200)))))
        assertEquals(Decision.ScopeRequired, assertIs<CommandResult.DecisionNeeded>(r).decision)
        assertEquals(emptyList(), h.space.operations().value())
    }

    @Test
    fun `limites informam a origem da vigência e as alterações futuras`() = runBlocking {
        val h = open()
        h.limit("alimentacao", total(1200), month = sep).applied()
        h.limit("alimentacao", total(1600), month = dec, scope = LimitScope.OnlyThisMonth).applied()
        val octLimit = h.space.limits(oct).value().single { it.categoryId == seed("alimentacao") }
        assertEquals(sep, octLimit.sinceMonth)
        assertEquals(false, octLimit.isException)
        assertEquals(listOf(dec), octLimit.laterChanges)
        assertEquals(true, h.space.limits(dec).value().single { it.categoryId == seed("alimentacao") }.isException)
        assertNull(h.space.limits(oct).value().single { it.categoryId == seed("lazer") }.sinceMonth)
    }

    @Test
    fun `categoria inexistente, subcategoria de outra categoria e edição vazia são rejeitadas`() = runBlocking {
        val h = open()
        val unknown = h.space.execute(h.env(), DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit("nada", total(10)))))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
        val foreign = h.limit(
            "alimentacao",
            LimitEdit(TotalChange.Set(brl(100)), mapOf(seed("casa.moradia") to brl(10))),
        )
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(foreign).errors.single().code)
        val empty = h.limit("alimentacao", LimitEdit())
        assertEquals(ErrorCode.Required, assertIs<CommandResult.Rejected>(empty).errors.single().code)
        val sub = h.space.execute(h.env(), DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(seed("alimentacao.delivery"), total(10)))))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(sub).errors.single().code)
    }

    // ---- renomear e reordenar ----

    @Test
    fun `renomear mantém identidade, ícone e valores`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        h.spend("alimentacao.delivery", 50)
        h.space.execute(h.env(), RenameCategory(seed("alimentacao.delivery"), "Entregas")).applied()
        val node = h.space.categories().value().single { it.id == seed("alimentacao.delivery") }
        assertEquals("Entregas", node.name)
        assertEquals("bike", node.iconKey)
        assertEquals(brl(50), h.budgetOf("alimentacao").bySubcategory[seed("alimentacao.delivery")])
    }

    @Test
    fun `renomear para um nome que já existe no nível é rejeitado e o próprio nome com outra caixa é aceito`() = runBlocking {
        val h = open()
        val dup = h.space.execute(h.env(), RenameCategory(seed("alimentacao.delivery"), " MERCADO"))
        assertEquals(ErrorCode.DuplicateName, assertIs<CommandResult.Rejected>(dup).errors.single().code)
        h.space.execute(h.env(), RenameCategory(seed("alimentacao.delivery"), "DELIVERY")).applied()
        val blank = h.space.execute(h.env(), RenameCategory(seed("alimentacao.delivery"), " "))
        assertEquals(ErrorCode.Required, assertIs<CommandResult.Rejected>(blank).errors.single().code)
        val stale = h.space.execute(h.env(), RenameCategory(seed("alimentacao.delivery"), "Outro", expectedRevision = 9))
        assertIs<CommandResult.StaleVersion>(stale)
    }

    @Test
    fun `a tarifa continua em Tarifas e juros mesmo depois de a subcategoria ser renomeada`() = runBlocking {
        val h = open()
        h.space.execute(h.env(), RenameCategory(seed("servicos.tarifas-e-juros"), "Encargos")).applied()
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Anuidade", Classification.ToClassify, d(2026, 9, 25), brl(15), isCharge = true, invoice = oct),
        ).applied()
        val servicos = h.budgetOf("servicos")
        assertEquals(brl(15), servicos.bySubcategory[seed("servicos.tarifas-e-juros")])
    }

    @Test
    fun `reordenar muda só a apresentação`() = runBlocking {
        val h = open()
        val siblings = h.space.categories().value().filter { it.parentId == seed("alimentacao") }.sortedBy { it.order }
        assertEquals(listOf("Delivery", "Mercado", "Restaurantes"), siblings.map { it.name })
        val reversed = siblings.reversed().map { it.id }
        val r = h.space.execute(h.env(), ReorderCategories(seed("alimentacao"), reversed)).applied()
        assertEquals(setOf(EntityType.Category), r.changes.map { it.type }.toSet())
        val after = h.space.categories().value().filter { it.parentId == seed("alimentacao") }.sortedBy { it.order }
        assertEquals(listOf("Restaurantes", "Mercado", "Delivery"), after.map { it.name })
    }

    @Test
    fun `reordenar exige todos os irmãos e uma ordem diferente`() = runBlocking {
        val h = open()
        val ids = h.space.categories().value().filter { it.parentId == seed("alimentacao") }.sortedBy { it.order }.map { it.id }
        val missing = h.space.execute(h.env(), ReorderCategories(seed("alimentacao"), ids.dropLast(1)))
        assertEquals("orderedIds", assertIs<CommandResult.Rejected>(missing).errors.single().field)
        val same = h.space.execute(h.env(), ReorderCategories(seed("alimentacao"), ids))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(same).errors.single().code)
    }

    // ---- mover subcategoria (cenário 19) ----

    private suspend fun Handle.pharmacy(parent: String): String =
        space.execute(env(), CreateCategory("Farmácia", seed(parent))).applied().changes.single().id

    @Test
    fun `Farmácia movida de Saúde para Casa em outubro — setembro fica em Saúde, outubro em diante vai para Casa, mesma identidade`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.pharmacy("saude")
        h.spend("", 200, d(2026, 9, 10), categoryId = id)
        h.spend("", 150, d(2026, 10, 10), categoryId = id)

        val receipt = h.space.execute(h.env(), MoveSubcategory(id, seed("casa"))).applied()
        assertEquals(id, receipt.changes.first().id)

        assertEquals(brl(200), h.budgetOf("saude", sep).gross)
        assertEquals(Money.ZERO, h.budgetOf("casa", sep).gross)
        assertEquals(Money.ZERO, h.budgetOf("saude", oct).gross)
        assertEquals(brl(150), h.budgetOf("casa", oct).gross)
        assertEquals(brl(150), h.budgetOf("casa", oct).bySubcategory[id])
        assertEquals(seed("casa"), h.space.categories().value().single { it.id == id }.parentId)
    }

    @Test
    fun `parcelas futuras de uma subcategoria movida acompanham o destino`() = runBlocking {
        val h = open()
        val id = h.pharmacy("saude")
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Remédios", Classification.Category(id), d(2026, 10, 1), brl(300), AmountMeaning.Total, 3, invoice = oct),
        ).applied()
        h.space.execute(h.env(), MoveSubcategory(id, seed("casa"))).applied()
        assertEquals(brl(100), h.budgetOf("casa", nov).gross)
        assertEquals(Money.ZERO, h.budgetOf("saude", nov).gross)
    }

    @Test
    fun `nome repetido no destino é bloqueado e em níveis diferentes é aceito`() = runBlocking {
        val h = open()
        val inHealth = h.pharmacy("saude")
        h.pharmacy("casa") // mesmo nome em outra categoria: aceito
        val r = h.space.execute(h.env(), MoveSubcategory(inHealth, seed("casa")))
        assertEquals(ErrorCode.DuplicateName, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    @Test
    fun `promover, rebaixar e mover para a mesma categoria não existem`() = runBlocking {
        val h = open()
        val main = h.space.execute(h.env(), MoveSubcategory(seed("saude"), seed("casa")))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(main).errors.single().code)
        val sub = h.space.execute(h.env(), MoveSubcategory(seed("saude.consultas"), seed("casa.moradia")))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(sub).errors.single().code)
        val same = h.space.execute(h.env(), MoveSubcategory(seed("saude.consultas"), seed("saude")))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(same).errors.single().code)
    }

    @Test
    fun `mover subcategoria com parte do limite pede decisão com meses e valores e nada é transferido em silêncio`() = runBlocking {
        val h = open()
        val id = h.pharmacy("saude")
        h.limit("saude", LimitEdit(TotalChange.Set(brl(1000)), mapOf(id to brl(300)))).applied()
        val before = h.space.operations().value().size

        val r = h.space.execute(h.env(), MoveSubcategory(id, seed("casa")))
        val decision = assertIs<CommandResult.DecisionNeeded>(r).decision
        assertEquals(Decision.LimitDivisionReview(id, listOf(InvolvedPart(oct, brl(300)))), decision)
        assertEquals(before, h.space.operations().value().size)
        assertEquals(seed("saude"), h.space.categories().value().single { it.id == id }.parentId)
    }

    @Test
    fun `decisão retira a parte da origem e define a parte no destino respeitando o limite do destino`() = runBlocking {
        val h = open()
        val id = h.pharmacy("saude")
        h.limit("saude", LimitEdit(TotalChange.Set(brl(1000)), mapOf(id to brl(300)))).applied()
        h.limit("casa", total(500)).applied()

        val tooMuch = h.space.execute(h.env(), MoveSubcategory(id, seed("casa"), listOf(PartDecision(oct, brl(600)))))
        assertEquals(ErrorCode.SplitExceedsLimit, assertIs<CommandResult.Rejected>(tooMuch).errors.single().code)
        assertEquals(seed("saude"), h.space.categories().value().single { it.id == id }.parentId) // nada gravado

        h.space.execute(h.env(), MoveSubcategory(id, seed("casa"), listOf(PartDecision(oct, brl(200))))).applied()
        val saude = h.budgetOf("saude").limit
        assertEquals(brl(1000), saude.undistributed) // a parte voltou ao "sem distribuir"
        assertEquals(brl(200), h.budgetOf("casa").limit.sub(id).part)
        assertEquals(brl(300), h.budgetOf("casa").limit.undistributed)
    }

    @Test
    fun `decisão de não definir parte no destino deixa a subcategoria sem divisão`() = runBlocking {
        val h = open()
        val id = h.pharmacy("saude")
        h.limit("saude", LimitEdit(TotalChange.Set(brl(1000)), mapOf(id to brl(300)))).applied()
        h.limit("casa", total(500)).applied()
        h.space.execute(h.env(), MoveSubcategory(id, seed("casa"), listOf(PartDecision(oct, null)))).applied()
        assertNull(h.budgetOf("casa").limit.sub(id).part)
        assertEquals(brl(500), h.budgetOf("casa").limit.undistributed)
    }

    @Test
    fun `decisões incompletas são rejeitadas`() = runBlocking {
        val h = open()
        val id = h.pharmacy("saude")
        h.limit("saude", LimitEdit(TotalChange.Set(brl(1000)), mapOf(id to brl(300)))).applied()
        val r = h.space.execute(h.env(), MoveSubcategory(id, seed("casa"), emptyList()))
        assertEquals("decisions", assertIs<CommandResult.Rejected>(r).errors.single().field)
    }

    // ---- contas (cenário 18) ----

    @Test
    fun `trocar o banco da conta mantém o ID e os lançamentos antigos exibem o banco novo`() = runBlocking {
        val h = open()
        val account = h.withDefaultAccount()
        h.spend("compras", 10)
        h.space.execute(h.env(), EditAccount(account, bank = BankRef.Catalog("60746948", "Bradesco"))).applied()
        val view = h.space.accounts().value()
        val edited = view.accounts.single { it.id == account }
        assertEquals(BankRef.Catalog("60746948", "Bradesco"), edited.bank)
        assertEquals("Conta salário", edited.nickname)
        assertEquals(account, h.space.movements(oct).value().single().accountId)
    }

    @Test
    fun `editar apelido de conta exige texto e respeita a versão`() = runBlocking {
        val h = open()
        val account = h.withDefaultAccount()
        h.space.execute(h.env(), EditAccount(account, nickname = "Conta conjunta")).applied()
        assertEquals("Conta conjunta", h.space.accounts().value().accounts.single().nickname)
        val blank = h.space.execute(h.env(), EditAccount(account, nickname = " "))
        assertEquals("nickname", assertIs<CommandResult.Rejected>(blank).errors.single().field)
        val none = h.space.execute(h.env(), EditAccount(account))
        assertEquals(ErrorCode.Required, assertIs<CommandResult.Rejected>(none).errors.single().code)
        val stale = h.space.execute(h.env(), EditAccount(account, nickname = "X", expectedRevision = 9))
        assertIs<CommandResult.StaleVersion>(stale)
        assertNotEquals("X", h.space.accounts().value().accounts.single().nickname)
    }
}
