package app.accord.persistence

import app.accord.core.Money
import app.accord.core.analysis.ComparisonGroupKey
import app.accord.core.analysis.Measure
import app.accord.core.analysis.PercentChange
import app.accord.core.analysis.PeriodChange
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.DateCriterion
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.QueryResult
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Comparações históricas (S6.5, tela 33), em 24/10/2026. Dados fictícios. */
class PeriodComparisonTest : SpaceTestBase() {
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

    private suspend fun Handle.card(description: String, reais: Int, installments: Int, category: String?, date: LocalDate, invoice: YearMonth) =
        space.execute(
            env(),
            RegisterCardPurchase(
                description, if (category == null) Classification.ToClassify else Classification.Category(seed(category)), date, brl(reais),
                if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
            ),
        ).applied()

    private fun month(y: Int, m: Int) = ComparisonPeriod(LocalDate.of(y, m, 1), LocalDate.of(y, m, 1).plusMonths(1))

    private suspend fun Handle.compare(
        a: ComparisonPeriod,
        b: ComparisonPeriod,
        criterion: DateCriterion = DateCriterion.BudgetMonth,
        grouping: ComparisonGrouping = ComparisonGrouping.Category,
    ) = space.comparePeriods(criterion, grouping, a, b).value()

    private fun key(category: String, sub: String? = null) = ComparisonGroupKey(seed(category), sub?.let { seed("$category.$it") })

    @Test
    fun `alimentação de 800 em setembro para 980 em outubro é mais 180 e mais 22,5 por cento`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 800, "alimentacao.mercado", d(2026, 9, 10))
        h.pix("Mercado", 980, "alimentacao.mercado", d(2026, 10, 10))

        val result = h.compare(month(2026, 9), month(2026, 10))
        val row = result.rows.single { it.key == key("alimentacao") }
        assertEquals(Measure.Value(brl(800)), row.a)
        assertEquals(Measure.Value(brl(980)), row.b)
        val change = assertIs<PeriodChange.Computed>(row.change)
        assertEquals(brl(180), change.difference)
        assertEquals(PercentChange.Value(225), change.percent)
        assertEquals(Measure.Value(brl(800)), result.totalA)
        assertEquals(Measure.Value(brl(980)), result.totalB)
    }

    @Test
    fun `período anterior sem registros é sem dados e não zero`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 980, "alimentacao.mercado", d(2026, 10, 10))

        val result = h.compare(month(2026, 8), month(2026, 10))
        val row = result.rows.single()
        assertEquals(Measure.NoData, row.a)
        assertIs<PeriodChange.Unavailable>(row.change)
        assertEquals(Measure.NoData, result.totalA)
        assertFalse(result.a.failed)
    }

    @Test
    fun `categoria nova no segundo período tem base zero sem percentual`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 800, "alimentacao.mercado", d(2026, 9, 10))
        h.pix("Mercado", 800, "alimentacao.mercado", d(2026, 10, 10))
        h.pix("Camiseta", 120, "compras.roupas", d(2026, 10, 12))

        val row = h.compare(month(2026, 9), month(2026, 10)).rows.single { it.key == key("compras") }
        assertEquals(Measure.Value(Money.ZERO), row.a)
        assertEquals(PercentChange.NoBase(PercentChange.Reason.ZeroBase), (row.change as PeriodChange.Computed).percent)
    }

    @Test
    fun `estorno confirmado abate a categoria no critério do orçamento`() = runBlocking<Unit> {
        val h = openWithAccount()
        val id = h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 10))
        h.space.execute(h.env(), RegisterRefund(id, brl(30), RefundStatus.Confirmed, d(2026, 10, 15))).applied()
        h.pix("Mercado", 50, "alimentacao.mercado", d(2026, 9, 10))

        val row = h.compare(month(2026, 9), month(2026, 10)).rows.single { it.key == key("alimentacao") }
        assertEquals(Measure.Value(brl(70)), row.b)
    }

    @Test
    fun `subcategorias mais sem subcategoria fecham a categoria`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 10))
        h.pix("Jantar", 50, "alimentacao.restaurantes", d(2026, 10, 11))
        h.pix("Lanche", 30, "alimentacao", d(2026, 10, 12))
        h.pix("Mercado", 10, "alimentacao.mercado", d(2026, 9, 10))

        val result = h.compare(month(2026, 9), month(2026, 10), grouping = ComparisonGrouping.Subcategory)
        fun b(k: ComparisonGroupKey) = (result.rows.single { it.key == k }.b as Measure.Value).money
        assertEquals(brl(100), b(key("alimentacao", "mercado")))
        assertEquals(brl(50), b(key("alimentacao", "restaurantes")))
        assertEquals(brl(30), b(key("alimentacao")))
        assertEquals(brl(180), listOf(key("alimentacao", "mercado"), key("alimentacao", "restaurantes"), key("alimentacao")).map(::b).fold(Money.ZERO) { x, y -> x + y })
    }

    @Test
    fun `os critérios não se misturam, compra de 1200 em seis vezes`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.card("Equipamento", 1200, 6, "eletronicos", d(2026, 9, 25), YearMonth.of(2026, 10))
        h.pix("Mercado", 10, "alimentacao.mercado", d(2026, 9, 5))
        h.pix("Mercado", 10, "alimentacao.mercado", d(2026, 10, 5))

        // Orçamento: setembro não tem parcela; outubro tem a 1ª (R$ 200).
        val budget = h.compare(month(2026, 9), month(2026, 10), DateCriterion.BudgetMonth)
        val eb = budget.rows.single { it.key == key("eletronicos") }
        assertEquals(Measure.Value(Money.ZERO), eb.a)
        assertEquals(Measure.Value(brl(200)), eb.b)

        // Data da compra: setembro tem o total (R$ 1.200); outubro não tem compra.
        val purchase = h.compare(month(2026, 9), month(2026, 10), DateCriterion.PurchaseDate)
        val ep = purchase.rows.single { it.key == key("eletronicos") }
        assertEquals(Measure.Value(brl(1200)), ep.a)
        assertEquals(Measure.Value(Money.ZERO), ep.b)
    }

    @Test
    fun `compra sem categoria vira o grupo a classificar fora de qualquer categoria`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.card("Presente", 80, 1, null, d(2026, 10, 10), YearMonth.of(2026, 11))
        h.pix("Mercado", 20, "alimentacao.mercado", d(2026, 9, 10))
        h.pix("Mercado", 20, "alimentacao.mercado", d(2026, 10, 10))

        val result = h.compare(month(2026, 9), month(2026, 10), DateCriterion.PurchaseDate)
        assertEquals(Measure.Value(brl(80)), result.rows.single { it.key == ComparisonGroupKey(null) }.b)
        assertEquals(brl(80), result.b.toClassify)
        assertEquals(Measure.Value(brl(100)), result.totalB)
    }

    @Test
    fun `período em andamento e duração diferente aparecem antes da interpretação`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 10, "alimentacao.mercado", d(2026, 9, 5))
        val result = h.compare(month(2026, 9), month(2026, 10))
        assertFalse(result.a.inProgressOrFuture)
        assertTrue(result.b.inProgressOrFuture) // outubro inclui hoje (24/10/2026)
        assertEquals(30, result.a.period.days)
        assertEquals(31, result.b.period.days)
        assertTrue(result.differentDuration)
        assertFalse(h.compare(month(2026, 10), month(2026, 10)).differentDuration)
    }

    @Test
    fun `período de vários meses soma mês a mês`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 8, 10))
        h.pix("Mercado", 200, "alimentacao.mercado", d(2026, 9, 10))
        h.pix("Mercado", 600, "alimentacao.mercado", d(2026, 10, 10))

        val twoMonths = ComparisonPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 1))
        val result = h.compare(twoMonths, month(2026, 10))
        assertEquals(Measure.Value(brl(300)), result.rows.single { it.key == key("alimentacao") }.a)
        assertEquals(61, result.a.period.days)
    }

    @Test
    fun `no critério do orçamento período que não é de meses inteiros é recusado`() = runBlocking<Unit> {
        val h = openWithAccount()
        val partial = ComparisonPeriod(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 1))
        assertIs<QueryResult.Failure>(h.space.comparePeriods(DateCriterion.BudgetMonth, ComparisonGrouping.Category, partial, month(2026, 10)))
        // Por data da compra, qualquer intervalo vale.
        assertIs<QueryResult.Data<*>>(h.space.comparePeriods(DateCriterion.PurchaseDate, ComparisonGrouping.Category, partial, month(2026, 10)))
    }

    @Test
    fun `comparar não altera nada`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", d(2026, 10, 10))
        val before = h.space.budget(YearMonth.of(2026, 10)).value()
        h.compare(month(2026, 9), month(2026, 10))
        assertEquals(before, h.space.budget(YearMonth.of(2026, 10)).value())
    }
}
