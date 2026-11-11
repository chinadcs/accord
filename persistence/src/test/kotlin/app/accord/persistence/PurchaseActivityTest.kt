package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DateCriterion
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntryFilter
import app.accord.core.space.QueryResult
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Atividade de compras (S6.4, tela 30): compras pela data original, em 24/10/2026. Dados fictícios. */
class PurchaseActivityTest : SpaceTestBase() {
    private fun openWithAccount(): Handle = open().also { h ->
        runBlocking {
            val id = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
            h.space.execute(h.env(), SetMyDefaultAccount(id)).applied()
        }
    }

    private suspend fun Handle.pix(description: String, reais: Int, category: String, date: LocalDate): String = space.execute(
        env(),
        RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(reais)),
    ).applied().changes.single().id

    private suspend fun Handle.card(
        description: String,
        reais: Int,
        installments: Int,
        category: String?,
        date: LocalDate,
        invoice: java.time.YearMonth,
    ): String = space.execute(
        env(),
        RegisterCardPurchase(
            description, if (category == null) Classification.ToClassify else Classification.Category(seed(category)), date, brl(reais),
            if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
        ),
    ).applied().changes.single().id

    private suspend fun Handle.activity(from: LocalDate, until: LocalDate, category: String? = null) =
        space.purchaseActivity(from, until, category?.let { seed(it) }).value()

    @Test
    fun `compra de 1200 em seis parcelas aparece uma vez em setembro e as parcelas ficam no orçamento`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.card("Equipamento", 1200, 6, "eletronicos", d(2026, 9, 25), ym(2026, 10))

        val september = h.activity(d(2026, 9, 1), d(2026, 10, 1))
        assertEquals(brl(1200), september.total.total)
        assertEquals(1, september.total.count)

        // A atividade de outubro não repete a compra; o orçamento de outubro mostra a 1ª parcela (R$ 200).
        val october = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(0, october.total.count)
        assertEquals(Money.ZERO, october.total.total)
        val budget = h.space.entries(EntryFilter(ym(2026, 10), ym(2026, 10), DateCriterion.BudgetMonth)).value()
        assertEquals(brl(200), budget.summary.expenses)
    }

    @Test
    fun `categorias e subcategorias fecham o total, com sem subcategoria como grupo`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.pix("Jantar", 50, "alimentacao.restaurantes", d(2026, 10, 21))
        h.pix("Lanche", 30, "alimentacao", d(2026, 10, 22))
        h.pix("Camiseta", 70, "compras.roupas", d(2026, 10, 18))

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(brl(250), a.total.total)
        assertEquals(4, a.total.count)
        assertEquals(listOf(seed("alimentacao"), seed("compras")), a.groups.map { it.categoryId })
        val food = a.groups.first()
        assertEquals(brl(180), food.line.total)
        assertEquals(
            mapOf<String?, Money>(seed("alimentacao.mercado") to brl(100), seed("alimentacao.restaurantes") to brl(50), null to brl(30)),
            food.subgroups.associate { it.subcategoryId to it.line.total },
        )
        // A soma dos grupos mais "a classificar" fecha o total.
        assertEquals(a.total.total, a.groups.map { it.line.total }.fold(Money.ZERO) { acc, m -> acc + m })
        assertEquals(food.line.total, food.subgroups.map { it.line.total }.fold(Money.ZERO) { acc, m -> acc + m })
    }

    @Test
    fun `compra a classificar vira grupo próprio e o total continua fechando`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.card("Presente", 80, 1, null, d(2026, 10, 21), ym(2026, 11))

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(brl(180), a.total.total)
        assertEquals(brl(80), a.toClassify!!.total)
        assertEquals(1, a.toClassify!!.count)
        assertEquals(brl(100), a.groups.single().line.total)
        assertEquals(a.total.total, a.groups.single().line.total + a.toClassify!!.total)
    }

    @Test
    fun `boleto sem data de compra é lacuna e não entra no total`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 25), brl(90)),
        ).applied()

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(brl(100), a.total.total)
        assertEquals(1, a.total.count)
        assertEquals(brl(90), a.gaps!!.total)
        assertEquals(1, a.gaps!!.count)
        assertEquals(listOf(seed("alimentacao")), a.groups.map { it.categoryId })
    }

    @Test
    fun `estorno confirmado é linha própria e não desconta o total de compras`() = runBlocking<Unit> {
        val h = openWithAccount()
        val id = h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.space.execute(h.env(), RegisterRefund(id, brl(30), RefundStatus.Confirmed, d(2026, 10, 25))).applied()

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(brl(100), a.total.total)
        assertEquals(brl(30), a.credits!!.total)
        assertEquals(1, a.credits!!.count)
    }

    @Test
    fun `ocorrência de despesa recorrente entra pela data da cobrança`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), brl(40), frequency = Frequency.Monthly, firstDate = d(2026, 9, 5)),
        ).applied()

        val october = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(brl(40), october.total.total)
        assertEquals(seed("assinaturas"), october.groups.single().categoryId)
        // Uma ocorrência por mês: setembro e outubro somam dois meses de cobrança, sem repetir nenhuma.
        val twoMonths = h.activity(d(2026, 9, 1), d(2026, 11, 1))
        assertEquals(2, twoMonths.total.count)
        assertEquals(brl(80), twoMonths.total.total)
    }

    @Test
    fun `filtro por categoria restringe tudo, inclusive o total`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.pix("Camiseta", 70, "compras.roupas", d(2026, 10, 18))

        val food = h.activity(d(2026, 10, 1), d(2026, 11, 1), "alimentacao")
        assertEquals(brl(100), food.total.total)
        assertEquals(seed("alimentacao"), food.categoryId)
        val sub = h.activity(d(2026, 10, 1), d(2026, 11, 1), "alimentacao.mercado")
        assertEquals(brl(100), sub.total.total)
        assertEquals(0, h.activity(d(2026, 10, 1), d(2026, 11, 1), "lazer").total.count)
    }

    @Test
    fun `semanas começam na segunda e só aparecem as que têm compras`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("A", 10, "alimentacao.mercado", d(2026, 10, 14)) // quarta, semana de 12/10
        h.pix("B", 20, "alimentacao.mercado", d(2026, 10, 18)) // domingo, mesma semana
        h.pix("C", 30, "alimentacao.mercado", d(2026, 10, 20)) // terça, semana de 19/10

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(listOf(d(2026, 10, 12), d(2026, 10, 19)), a.weeks.map { it.weekStart })
        assertEquals(listOf(brl(30), brl(30)), a.weeks.map { it.line.total })
        assertEquals(listOf(2, 1), a.weeks.map { it.line.count })
    }

    @Test
    fun `período usa início inclusivo e fim exclusivo`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Primeiro dia", 10, "alimentacao.mercado", d(2026, 10, 1))
        h.pix("Último dia", 20, "alimentacao.mercado", d(2026, 10, 31))
        h.pix("Mês seguinte", 40, "alimentacao.mercado", d(2026, 11, 1))
        assertEquals(brl(30), h.activity(d(2026, 10, 1), d(2026, 11, 1)).total.total)
        assertEquals(brl(20), h.activity(d(2026, 10, 2), d(2026, 11, 1)).total.total)
    }

    @Test
    fun `período sem compras tem total zero apurado e nenhuma linha extra`() = runBlocking<Unit> {
        val h = openWithAccount()
        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        assertEquals(0, a.total.count)
        assertEquals(emptyList(), a.groups)
        assertNull(a.toClassify)
        assertNull(a.credits)
        assertNull(a.gaps)
    }

    @Test
    fun `coerente com a lista de Lançamentos pela data da compra, exceto a compra parcelada (uma linha por parcela, ADR 0090)`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 20))
        h.card("Equipamento", 1200, 6, "eletronicos", d(2026, 10, 3), ym(2026, 11))
        h.card("Fone", 300, 1, "eletronicos", d(2026, 10, 4), ym(2026, 11))
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 25), brl(90)),
        ).applied()

        val a = h.activity(d(2026, 10, 1), d(2026, 11, 1))
        // A atividade segue a compra original: o Equipamento entra inteiro (1.200) na data da compra.
        assertEquals(brl(1600), a.total.total)
        // A lista traz o Equipamento como parcelas no mês da fatura (novembro) e o que é à vista pela data da compra (outubro).
        val october = h.space.entries(EntryFilter(ym(2026, 10), ym(2026, 10), DateCriterion.PurchaseDate), 0, Int.MAX_VALUE).value()
        assertEquals(a.total.total - brl(1200) + a.gaps!!.total, october.summary.expenses)
        val november = h.space.entries(EntryFilter(ym(2026, 11), ym(2026, 11), DateCriterion.PurchaseDate), 0, Int.MAX_VALUE).value()
        assertEquals(listOf(brl(200)), november.rows.filter { it.description == "Equipamento" }.map { it.amount })
    }

    @Test
    fun `período invertido é recusado e nada é alterado`() = runBlocking<Unit> {
        val h = openWithAccount()
        assertIs<QueryResult.Failure>(h.space.purchaseActivity(d(2026, 11, 1), d(2026, 10, 1)))
    }
}
