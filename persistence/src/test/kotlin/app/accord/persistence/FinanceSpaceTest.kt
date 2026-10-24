package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.space.AccountInfo
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CategorySeed
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.Session
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceSpaceTest : SpaceTestBase() {
    private fun pix(
        description: String = "Mercado",
        category: String = "alimentacao.mercado",
        amount: Money = brl(50),
        date: LocalDate = d(2026, 10, 20),
        kind: DirectPaymentKind = DirectPaymentKind.Pix,
        accountId: String? = null,
    ) = RegisterDirectPayment(
        kind, description, Classification.Category(seed(category)), date, amount, accountId,
    )

    private suspend fun Handle.withDefaultAccount(): String {
        val acc = space.execute(env(), AddAccount(BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta corrente")).applied()
        val id = acc.changes.first().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    // ---- sementes ----

    @Test
    fun `categorias iniciais existem com identidade determinística e sem operação`() = runBlocking<Unit> {
        val h = open()
        val cats = h.space.categories().value()
        val delivery = cats.single { it.id == seed("alimentacao.delivery") }
        assertEquals("Delivery", delivery.name)
        assertEquals(seed("alimentacao"), delivery.parentId)
        assertEquals("bike", delivery.iconKey)
        assertEquals(10, cats.count { it.parentId == null })
        assertEquals(CategorySeed.items.size, cats.size)
        // outro aparelho do mesmo espaço chega aos mesmos IDs
        val other = open("bia")
        assertEquals(cats.map { it.id }.toSet(), other.space.categories().value().map { it.id }.toSet())
        assertEquals(emptyList(), h.space.operations().value())
    }

    @Test
    fun `abrir duas vezes não duplica a semente`() = runBlocking<Unit> {
        val db = newDatabase()
        val first = open("ana", db)
        val second = open("ana", db)
        assertEquals(CategorySeed.items.size, second.space.categories().value().size)
        assertEquals(first.space.categories().value().size, second.space.categories().value().size)
    }

    // ---- Pix, débito e dinheiro (cenário 4) e subcategoria (6) ----

    @Test
    fun `Pix usa a conta padrão do autor, ocupa o mês da movimentação e já nasce pago`() = runBlocking<Unit> {
        val h = open()
        val account = h.withDefaultAccount()
        h.space.execute(h.env(), pix()).applied()

        val rows = h.space.movements(ym(2026, 10)).value()
        assertEquals(account, rows.single().accountId)
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(50), b.committed)
        assertEquals(brl(50), b.paid)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `débito com conta e dinheiro sem conta entram em outubro já pagos`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), pix(kind = DirectPaymentKind.Debit, amount = brl(30))).applied()
        h.space.execute(h.env(), pix(kind = DirectPaymentKind.Cash, amount = brl(20))).applied()

        val rows = h.space.movements(ym(2026, 10)).value()
        assertNotEquals(null, rows.single { it.kind == DirectPaymentKind.Debit }.accountId)
        assertNull(rows.single { it.kind == DirectPaymentKind.Cash }.accountId)
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(50), b.committed)
        assertEquals(brl(50), b.paid)
    }

    @Test
    fun `dinheiro com conta informada é rejeitado`() = runBlocking<Unit> {
        val h = open()
        val account = h.withDefaultAccount()
        val r = h.space.execute(h.env(), pix(kind = DirectPaymentKind.Cash, accountId = account))
        val rejected = assertIs<CommandResult.Rejected>(r)
        assertEquals(ErrorCode.NotAllowed, rejected.errors.single { it.field == "accountId" }.code)
    }

    @Test
    fun `Pix sem conta informada e sem conta padrão é rejeitado`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), pix())
        val rejected = assertIs<CommandResult.Rejected>(r)
        assertEquals(ErrorCode.Required, rejected.errors.single().code)
        assertEquals("accountId", rejected.errors.single().field)
    }

    @Test
    fun `R$ 50 em Delivery conta em Delivery, Alimentação e no total geral uma única vez`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), pix(category = "alimentacao.delivery")).applied()
        val b = h.space.budget(ym(2026, 10)).value()
        val alimentacao = b.categories.single { it.categoryId == seed("alimentacao") }
        assertEquals(brl(50), alimentacao.gross)
        assertEquals(brl(50), alimentacao.net)
        assertEquals(brl(50), alimentacao.bySubcategory[seed("alimentacao.delivery")])
        assertEquals(brl(50), b.committed)
        assertEquals(1, b.categories.count { it.gross > Money.ZERO })
    }

    @Test
    fun `gasto direto na categoria principal fica em sem subcategoria`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), pix(category = "alimentacao")).applied()
        val alimentacao = h.space.budget(ym(2026, 10)).value().categories.single { it.categoryId == seed("alimentacao") }
        assertEquals(brl(50), alimentacao.bySubcategory[null])
    }

    @Test
    fun `duas compras iguais com IDs de comando diferentes são dois lançamentos`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), pix(description = "mercado 80", amount = brl(80))).applied()
        h.space.execute(h.env(), pix(description = "mercado 80", amount = brl(80))).applied()
        assertEquals(2, h.space.movements(ym(2026, 10)).value().size)
        assertEquals(brl(160), h.space.budget(ym(2026, 10)).value().committed)
    }

    // ---- validação ----

    @Test
    fun `valor zero, descrição vazia e referências inexistentes são rejeitados por campo sem gravar`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val before = h.space.operations().value().size
        val r = h.space.execute(
            h.env(),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, "  ", Classification.Category("nao-existe"), d(2026, 10, 20), Money.ZERO,
                accountId = "conta-inexistente",
            ),
        )
        val errors = assertIs<CommandResult.Rejected>(r).errors.associate { it.field to it.code }
        assertEquals(ErrorCode.Required, errors["description"])
        assertEquals(ErrorCode.MustBePositive, errors["amount"])
        assertEquals(ErrorCode.UnknownReference, errors["classification"])
        assertEquals(ErrorCode.UnknownReference, errors["accountId"])
        assertEquals(before, h.space.operations().value().size)
        assertEquals(emptyList(), h.space.movements(ym(2026, 10)).value())
    }

    @Test
    fun `comando rejeitado pode ser reenviado com o mesmo ID depois de corrigido`() = runBlocking<Unit> {
        val h = open()
        val id = CommandId("tentativa-1")
        assertIs<CommandResult.Rejected>(h.space.execute(Envelope(id), pix(amount = Money.ZERO)))
        h.withDefaultAccount()
        h.space.execute(Envelope(id), pix()).applied()
    }

    // ---- idempotência, atomicidade, operações (22–24) ----

    @Test
    fun `mesmo ID de comando duas vezes gera um lançamento, uma operação e Já aplicado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = CommandId("duplo-toque")
        val first = h.space.execute(Envelope(id), pix()).applied()
        val second = h.space.execute(Envelope(id), pix())
        val again = assertIs<CommandResult.AlreadyApplied>(second).receipt
        assertEquals(first, again)
        assertEquals(1, h.space.movements(ym(2026, 10)).value().size)
        assertEquals(1, h.space.operations().value().count { it.operationId == id.value })
    }

    @Test
    fun `falha na gravação não deixa nem a entidade nem a operação e o comando pode ser repetido`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = CommandId("vai-falhar")
        h.fault = { error("falha injetada") }
        assertFailsWith<IllegalStateException> { h.space.execute(Envelope(id), pix()) }
        h.fault = null

        assertEquals(emptyList(), h.space.movements(ym(2026, 10)).value())
        assertEquals(0, h.space.operations().value().count { it.operationId == id.value })
        h.space.execute(Envelope(id), pix()).applied()
        assertEquals(1, h.space.movements(ym(2026, 10)).value().size)
    }

    @Test
    fun `cada comando aplicado gera exatamente uma operação com sequência crescente e entidades afetadas`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount() // 2 operações: adicionar conta e conta padrão
        val r = h.space.execute(h.env(), pix()).applied()
        val ops = h.space.operations().value()
        assertEquals(3, ops.size)
        assertEquals(ops.map { it.sequence }.sorted(), ops.map { it.sequence })
        assertEquals(3, ops.map { it.sequence }.toSet().size)
        val op = ops.last()
        assertEquals(r.operationId, op.operationId)
        assertEquals(spaceId, op.spaceId)
        assertEquals("ana", op.authorId)
        assertEquals("RegisterDirectPayment", op.commandType)
        val change = op.changes.single()
        assertEquals(app.accord.core.space.EntityType.Movement, change.type)
        assertNull(change.baseVersion)
        assertEquals(1L, change.revision)
        assertEquals(false, op.acknowledged)
        assertEquals(setOf(ym(2026, 10)), r.affectedMonths)
    }

    @Test
    fun `a operação registra o autor e a origem do comando`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(Envelope(CommandId("do-chat"), app.accord.core.space.Origin.Chat), pix()).applied()
        val op = h.space.operations().value().last()
        assertEquals(app.accord.core.space.Origin.Chat, op.origin)
    }

    @Test
    fun `operations lê a partir de uma sequência`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), pix()).applied()
        val all = h.space.operations().value()
        val tail = h.space.operations(afterSequence = all[1].sequence).value()
        assertEquals(listOf(all[2].operationId), tail.map { it.operationId })
    }

    // ---- desfazer (26) ----

    @Test
    fun `desfazer registro recente anula, recalcula e gera nova operação`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val registered = h.space.execute(h.env(), pix()).applied()
        assertTrue(registered.undoable)

        val undone = h.space.execute(h.env(), Undo(registered.operationId)).applied()
        assertNotEquals(registered.operationId, undone.operationId)
        assertEquals(setOf(ym(2026, 10)), undone.affectedMonths)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(emptyList(), h.space.movements(ym(2026, 10)).value())
        // a operação original continua na fila
        assertTrue(h.space.operations().value().any { it.operationId == registered.operationId })
        assertEquals(2L, undone.changes.single().revision)
    }

    @Test
    fun `desfazer duas vezes é rejeitado porque a entidade já mudou depois da operação`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val registered = h.space.execute(h.env(), pix()).applied()
        h.space.execute(h.env(), Undo(registered.operationId)).applied()
        val again = h.space.execute(h.env(), Undo(registered.operationId))
        assertEquals(ErrorCode.ChangedAfter, assertIs<CommandResult.Rejected>(again).errors.single().code)
    }

    @Test
    fun `desfazer operação inexistente é rejeitado`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), Undo("nao-existe"))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    // ---- a classificar (30, 32) ----

    @Test
    fun `Pix de R$ 150 a classificar entra no total geral e no pago, numa linha própria, sem categoria`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val r = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Amazon", Classification.ToClassify, d(2026, 10, 22), brl(150)),
        ).applied()
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(150), b.committed)
        assertEquals(brl(150), b.paid)
        assertEquals(brl(150), b.toClassify.amount)
        assertEquals(1, b.toClassify.count)
        assertTrue(b.categories.all { it.gross == Money.ZERO })

        // desfazer anula e retira o valor do total geral
        h.space.execute(h.env(), Undo(r.operationId)).applied()
        val after = h.space.budget(ym(2026, 10)).value()
        assertEquals(Money.ZERO, after.committed)
        assertEquals(Money.ZERO, after.toClassify.amount)
        assertEquals(0, after.toClassify.count)
    }

    // ---- leitura com falha (29) ----

    @Test
    fun `falha de leitura vem identificada e não como zero`() = runBlocking<Unit> {
        val h = open()
        h.database.close()
        val r = h.space.budget(ym(2026, 10))
        assertIs<QueryResult.Failure>(r)
    }

    // ---- categorias ----

    @Test
    fun `nome igual no mesmo nível é rejeitado ignorando maiúsculas, acentos e espaços`() = runBlocking<Unit> {
        val h = open()
        val dup = h.space.execute(h.env(), CreateCategory("  MERCADO ", seed("alimentacao")))
        assertEquals(ErrorCode.DuplicateName, assertIs<CommandResult.Rejected>(dup).errors.single().code)
        val dupMain = h.space.execute(h.env(), CreateCategory("alimentacao"))
        assertEquals(ErrorCode.DuplicateName, assertIs<CommandResult.Rejected>(dupMain).errors.single().code)
    }

    @Test
    fun `níveis diferentes podem repetir o nome e a nova categoria usa o ícone tag`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), CreateCategory("Alimentação", seed("compras"))).applied()
        val created = h.space.categories().value().single { it.id == r.changes.single().id }
        assertEquals("tag", created.iconKey)
        assertNull(created.seedKey)
        assertEquals(seed("compras"), created.parentId)
    }

    @Test
    fun `subcategoria de subcategoria não existe`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), CreateCategory("Extra", seed("alimentacao.delivery")))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    @Test
    fun `categoria nova vai para o fim da lista do nível`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(h.env(), CreateCategory("Educação")).applied()
        val mains = h.space.categories().value().filter { it.parentId == null }.sortedBy { it.order }
        assertEquals("Educação", mains.last().name)
    }

    // ---- contas (18) ----

    @Test
    fun `conta única é padrão para os dois e preenche Pix sem criar preferências`() = runBlocking<Unit> {
        val db = newDatabase()
        val ana = open("ana", db)
        val bia = open("bia", db)
        val id = ana.space.execute(ana.env(), AddAccount(BankRef.Other("Banco Casa"), "Banco Casa")).applied().changes.single().id
        assertEquals(id, ana.space.accounts().value().myDefaultAccountId)
        assertEquals(id, bia.space.accounts().value().myDefaultAccountId)
        ana.space.execute(ana.env(), pix()).applied()
        val entry = ana.space.movements(ym(2026, 10)).value().single()
        assertEquals(id, ana.space.entry(entry.id).value()?.accountId)
        ana.space.execute(ana.env(), AddAccount(BankRef.Other("Outro"), "Segunda")).applied()
        assertNull(ana.space.accounts().value().myDefaultAccountId)
        ana.space.execute(ana.env(), SetMyDefaultAccount(id)).applied()
        assertEquals(id, ana.space.accounts().value().myDefaultAccountId)
        assertNull(bia.space.accounts().value().myDefaultAccountId)
    }

    @Test
    fun `duas contas do mesmo banco com apelidos diferentes e conta padrão só da pessoa`() = runBlocking<Unit> {
        val db = newDatabase()
        val ana = open("ana", db)
        val bia = open("bia", db)
        val itau = BankRef.Catalog("60701190", "Itaú Unibanco")
        val a1 = ana.space.execute(ana.env(), AddAccount(itau, "Conta salário")).applied().changes.single().id
        val a2 = ana.space.execute(ana.env(), AddAccount(itau, "Conta conjunta")).applied().changes.single().id
        ana.space.execute(ana.env(), SetMyDefaultAccount(a1)).applied()
        bia.space.execute(bia.env(), SetMyDefaultAccount(a2)).applied()

        val forAna = ana.space.accounts().value()
        val forBia = bia.space.accounts().value()
        assertEquals(setOf("Conta salário", "Conta conjunta"), forAna.accounts.map(AccountInfo::nickname).toSet())
        assertEquals(forAna.accounts.toSet(), forBia.accounts.toSet())
        assertEquals(a1, forAna.myDefaultAccountId)
        assertEquals(a2, forBia.myDefaultAccountId)
    }

    @Test
    fun `conta exige apelido e a padrão precisa existir`() = runBlocking<Unit> {
        val h = open()
        val noNick = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco Pequeno"), " "))
        assertEquals("nickname", assertIs<CommandResult.Rejected>(noNick).errors.single().field)
        val unknown = h.space.execute(h.env(), SetMyDefaultAccount("nada"))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
    }
}
