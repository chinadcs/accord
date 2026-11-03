package app.accord.persistence

import app.accord.core.Frequency
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
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CreateCategory
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.Decision
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditCategory
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.MoveScope
import app.accord.core.space.PreviewResult
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RemoveCategory
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0039: editar ícone e cor, mover lançamentos entre categorias e remover categoria. */
class CategoryEditMoveRemoveTest : SpaceTestBase() {
    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)
    private val dec = ym(2026, 12)

    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private suspend fun Handle.spend(categoryId: String, reais: Int, date: LocalDate = d(2026, 10, 20)): String =
        space.execute(
            env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Gasto", Classification.Category(categoryId), date, brl(reais)),
        ).applied().changes.single().id

    private suspend fun Handle.gross(category: String, month: YearMonth = oct) =
        space.budget(month).value().categories.single { it.categoryId == seed(category) }.gross

    private suspend fun Handle.classificationOf(entryId: String) =
        assertIs<Classification.Category>(space.entry(entryId).value()!!.classification).id

    private suspend fun Handle.operationCount() = space.operations().value().size

    // ---- editar ----

    @Test
    fun `editar muda ícone e cor da categoria principal e mantém identidade e lançamentos`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.spend(seed("lazer"), 40)
        val r = h.space.execute(h.env(), EditCategory(seed("lazer"), iconKey = "popcorn", colorIndex = 4)).applied()
        assertEquals(listOf(EntityType.Category), r.changes.map { it.type })
        val node = h.space.categories().value().single { it.id == seed("lazer") }
        assertEquals("popcorn", node.iconKey)
        assertEquals(4, node.colorIndex)
        assertEquals("Lazer", node.name)
        assertEquals(brl(40), h.gross("lazer"))
    }

    @Test
    fun `subcategoria não escolhe cor e a cor precisa estar na paleta`() = runBlocking<Unit> {
        val h = open()
        val sub = h.space.execute(h.env(), EditCategory(seed("lazer.passeios"), colorIndex = 2))
        assertEquals("colorIndex", assertIs<CommandResult.Rejected>(sub).errors.single().field)
        val outside = h.space.execute(h.env(), EditCategory(seed("lazer"), colorIndex = 12))
        assertEquals(ErrorCode.OutOfRange, assertIs<CommandResult.Rejected>(outside).errors.single().code)
        // O ícone da subcategoria pode mudar.
        h.space.execute(h.env(), EditCategory(seed("lazer.passeios"), iconKey = "mountain")).applied()
        assertNull(h.space.categories().value().single { it.id == seed("lazer.passeios") }.colorIndex)
    }

    @Test
    fun `editar exige algo para mudar, nome livre no nível e versão atual`() = runBlocking<Unit> {
        val h = open()
        val nothing = h.space.execute(h.env(), EditCategory(seed("lazer")))
        assertEquals("fields", assertIs<CommandResult.Rejected>(nothing).errors.single().field)
        val duplicate = h.space.execute(h.env(), EditCategory(seed("lazer"), name = " casa"))
        assertEquals(ErrorCode.DuplicateName, assertIs<CommandResult.Rejected>(duplicate).errors.single().code)
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), EditCategory(seed("lazer"), colorIndex = 1, expectedRevision = 7)))
        val renamed = h.space.execute(h.env(), EditCategory(seed("lazer"), name = "Diversão", colorIndex = 1)).applied()
        assertEquals(1L, renamed.changes.single().revision)
    }

    // ---- mover lançamentos ----

    @Test
    fun `mover todos leva lançamentos da categoria e das subcategorias numa só operação e some da origem`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val a = h.spend(seed("alimentacao"), 30, d(2026, 9, 10))
        val b = h.spend(seed("alimentacao.delivery"), 50)
        val c = h.spend(seed("alimentacao.mercado"), 70)
        val other = h.spend(seed("lazer"), 10)
        val before = h.operationCount()

        val receipt = h.space.execute(h.env(), MoveCategoryEntries(seed("alimentacao"), seed("casa"), MoveScope.All)).applied()
        assertEquals(before + 1, h.operationCount())
        assertEquals(setOf(EntityType.Movement), receipt.changes.map { it.type }.toSet())
        assertEquals(3, receipt.changes.size)
        assertFalse(receipt.undoable)

        listOf(a, b, c).forEach { assertEquals(seed("casa"), h.classificationOf(it)) }
        assertEquals(seed("lazer"), h.classificationOf(other))
        assertEquals(brl(30), h.gross("casa", sep))
        assertEquals(brl(120), h.gross("casa", oct))
        assertEquals(Money.ZERO, h.gross("alimentacao", oct))
        // Sem remover, a origem continua existindo.
        assertTrue(h.space.categories().value().any { it.id == seed("alimentacao") })
    }

    @Test
    fun `mover do mês atual em diante preserva os meses anteriores`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val old = h.spend(seed("lazer"), 30, d(2026, 9, 10))
        val current = h.spend(seed("lazer"), 50, d(2026, 10, 5))
        val future = h.spend(seed("lazer"), 20, d(2026, 11, 5))
        h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("casa"), MoveScope.FromMonth(oct))).applied()
        assertEquals(seed("lazer"), h.classificationOf(old))
        assertEquals(seed("casa"), h.classificationOf(current))
        assertEquals(seed("casa"), h.classificationOf(future))
        assertEquals(brl(30), h.gross("lazer", sep))
    }

    @Test
    fun `compra parcelada entra inteira quando alguma parcela está no alcance`() = runBlocking<Unit> {
        val h = open()
        val purchase = h.space.execute(
            h.env(),
            RegisterCardPurchase("Sofá", Classification.Category(seed("casa")), d(2026, 10, 1), brl(300), AmountMeaning.Total, 3, invoice = oct),
        ).applied().changes.single().id
        h.space.execute(h.env(), MoveCategoryEntries(seed("casa"), seed("compras"), MoveScope.FromMonth(nov))).applied()
        assertEquals(seed("compras"), h.classificationOf(purchase))
        assertEquals(brl(100), h.gross("compras", oct)) // a parcela de outubro acompanha a compra inteira
        assertEquals(brl(100), h.gross("compras", dec))
        assertEquals(Money.ZERO, h.gross("casa", oct))
    }

    @Test
    fun `boleto muda pelo mês do orçamento`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val early = h.space.execute(h.env(), RegisterBoleto("Luz", Classification.Category(seed("casa")), d(2026, 9, 28), brl(90))).applied().changes.single().id
        val late = h.space.execute(h.env(), RegisterBoleto("Água", Classification.Category(seed("casa")), d(2026, 11, 8), brl(60))).applied().changes.single().id
        h.space.execute(h.env(), MoveCategoryEntries(seed("casa"), seed("servicos"), MoveScope.FromMonth(oct))).applied()
        assertEquals(seed("casa"), h.classificationOf(early))
        assertEquals(seed("servicos"), h.classificationOf(late))
    }

    @Test
    fun `recorrência muda a partir da ocorrência do alcance e preserva as anteriores`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.space.execute(
            h.env(),
            CreateRecurrence(
                RecurrenceKind.Expense, "Streaming", seed("assinaturas"), brl(40), RecurrenceForm.OffCard, account,
                Frequency.Monthly, d(2026, 10, 5),
            ),
        ).applied().changes.single().id

        h.space.execute(h.env(), MoveCategoryEntries(seed("assinaturas"), seed("lazer"), MoveScope.FromMonth(dec))).applied()
        fun category(month: YearMonth) = runBlocking { h.space.occurrences(month).value().single { it.recurrenceId == id }.categoryId }
        assertEquals(seed("assinaturas"), category(oct))
        assertEquals(seed("assinaturas"), category(nov))
        assertEquals(seed("lazer"), category(dec))
        assertEquals(seed("lazer"), category(ym(2027, 3)))
        assertEquals(brl(40), h.space.occurrences(dec).value().single { it.recurrenceId == id }.expectedAmount)
    }

    @Test
    fun `destino igual à origem ou dentro dela, categoria inexistente e nada a mover são recusados`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.spend(seed("lazer"), 10)
        fun code(r: CommandResult) = assertIs<CommandResult.Rejected>(r).errors.single().code
        assertEquals(ErrorCode.NotAllowed, code(h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("lazer"), MoveScope.All))))
        assertEquals(ErrorCode.NotAllowed, code(h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("lazer.passeios"), MoveScope.All))))
        assertEquals(ErrorCode.UnknownReference, code(h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), "nao-existe", MoveScope.All))))
        assertEquals(ErrorCode.NotAllowed, code(h.space.execute(h.env(), MoveCategoryEntries(seed("pet"), seed("casa"), MoveScope.All))))
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("casa"), MoveScope.All, expectedRevision = 5)))
    }

    @Test
    fun `a prévia conta lançamentos e meses sem gravar`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.spend(seed("lazer"), 30, d(2026, 9, 10))
        h.spend(seed("lazer"), 50, d(2026, 10, 5))
        val before = h.operationCount()
        val preview = assertIs<PreviewResult.Ready>(
            h.space.preview(MoveCategoryEntries(seed("lazer"), seed("casa"), MoveScope.FromMonth(oct), removeSource = false)),
        )
        assertEquals(1, preview.categoryMove!!.entries)
        assertEquals(listOf(oct), preview.categoryMove!!.months)
        assertEquals(before, h.operationCount())
    }

    @Test
    fun `lançamento excluído deixa de apontar para a categoria removida`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val entry = h.spend(seed("lazer"), 30)
        h.space.execute(h.env(), app.accord.core.space.DeleteEntry(entry)).applied()
        // Só há registro excluído: nada a mostrar, mas a remoção ainda limpa a referência.
        h.space.execute(h.env(), RemoveCategory(seed("lazer"))).applied()
        assertNull(h.database.db.dao().movement(entry)!!.classificationId)
    }

    @Test
    fun `desfazer uma correção cuja categoria anterior foi removida deixa o lançamento a classificar`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val entry = h.spend(seed("pet"), 12)
        val correction = h.space.execute(h.env(), CorrectEntry(entry, category = Classification.Category(seed("casa")))).applied()
        h.space.execute(h.env(), RemoveCategory(seed("pet"))).applied() // o lançamento já está em Casa
        h.space.execute(h.env(), app.accord.core.space.Undo(correction.operationId)).applied()
        assertEquals(Classification.ToClassify, h.space.entry(entry).value()!!.classification)
    }

    // ---- remover ----

    @Test
    fun `mover e remover marca a origem e as subcategorias como removidas, mantendo as linhas`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val entry = h.spend(seed("alimentacao.delivery"), 50)
        val receipt = h.space.execute(h.env(), MoveCategoryEntries(seed("alimentacao"), seed("casa"), MoveScope.All, removeSource = true)).applied()
        val removed = receipt.changes.filter { it.type == EntityType.Category }.map { it.id }.toSet()
        assertEquals(
            setOf(seed("alimentacao"), seed("alimentacao.delivery"), seed("alimentacao.mercado"), seed("alimentacao.restaurantes")),
            removed,
        )
        assertEquals(seed("casa"), h.classificationOf(entry))
        val ids = h.space.categories().value().map { it.id }
        assertTrue(seed("alimentacao") !in ids && seed("alimentacao.delivery") !in ids)
        assertTrue(h.space.budget(oct).value().categories.none { it.categoryId == seed("alimentacao") })
    }

    @Test
    fun `categoria removida não aceita novos lançamentos nem destino`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.space.execute(h.env(), RemoveCategory(seed("pet"))).applied()
        val register = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Ração", Classification.Category(seed("pet")), d(2026, 10, 20), brl(30)),
        )
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(register).errors.single().code)
        h.spend(seed("lazer"), 5)
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("pet"), MoveScope.All)))
        val entry = h.spend(seed("lazer"), 7)
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), CorrectEntry(entry, category = Classification.Category(seed("pet")))))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), CreateCategory("Filhote", seed("pet"))))
    }

    @Test
    fun `remover com lançamentos exige mover antes e remover para além do mês atual exige todos`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.spend(seed("lazer"), 30)
        val blocked = h.space.execute(h.env(), RemoveCategory(seed("lazer")))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(blocked).errors.single().code)
        val partial = h.space.execute(h.env(), MoveCategoryEntries(seed("lazer"), seed("casa"), MoveScope.FromMonth(oct), removeSource = true))
        assertEquals("scope", assertIs<CommandResult.Rejected>(partial).errors.single().field)
        assertTrue(h.space.categories().value().any { it.id == seed("lazer") })
    }

    @Test
    fun `remover categoria com limite definido pede confirmação e não transfere o limite`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.space.execute(
            h.env(),
            DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(seed("lazer"), LimitEdit(total = TotalChange.Set(brl(400)))))),
        ).applied()
        h.space.execute(
            h.env(),
            DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(seed("casa"), LimitEdit(total = TotalChange.Set(brl(900)))))),
        ).applied()
        val decision = assertIs<CommandResult.DecisionNeeded>(h.space.execute(h.env(), RemoveCategory(seed("lazer")))).decision
        assertEquals(Decision.RemovedCategoryLimits(seed("lazer"), listOf(oct)), decision)
        assertTrue(h.space.categories().value().any { it.id == seed("lazer") })

        h.space.execute(h.env(), RemoveCategory(seed("lazer"), confirmDiscardLimits = true)).applied()
        assertEquals(brl(900), h.space.budget(oct).value().totalLimits) // o limite de Lazer não foi para Casa nem fica contado
    }

    @Test
    fun `remover subcategoria libera a parte dela no limite da principal depois de confirmar`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.space.execute(
            h.env(),
            DefineLimits(
                oct, LimitScope.FromThisMonth,
                listOf(CategoryLimitEdit(seed("lazer"), LimitEdit(TotalChange.Set(brl(500)), mapOf(seed("lazer.eventos") to brl(200))))),
            ),
        ).applied()
        assertIs<CommandResult.DecisionNeeded>(h.space.execute(h.env(), RemoveCategory(seed("lazer.eventos"))))
        val r = h.space.execute(h.env(), RemoveCategory(seed("lazer.eventos"), confirmDiscardLimits = true)).applied()
        assertEquals(setOf(EntityType.Category, EntityType.LimitPlan), r.changes.map { it.type }.toSet())
        val lazer = h.space.budget(oct).value().categories.single { it.categoryId == seed("lazer") }
        assertEquals(brl(500), lazer.limit.total)
        assertEquals(brl(500), lazer.limit.undistributed)
    }
}
