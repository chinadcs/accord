package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.TotalChange
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.analysis.ReviewPoint
import app.accord.core.forecast.Excess
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.PendingItem
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.ReviewBlock
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.reviewId
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Revisão semanal (S6.3, tela 29): sábado 24/10/2026; a última semana completa é 12 a 18/10. Dados fictícios. */
class WeeklyReviewTest : SpaceTestBase() {
    private val today = LocalDate.of(2026, 10, 24)
    private val period = ReviewPeriod.lastCompleteWeek(Instant.parse("2026-10-24T15:00:00Z"))

    private fun openWithAccount(): Handle = open().also { h ->
        runBlocking {
            val id = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
            h.space.execute(h.env(), SetMyDefaultAccount(id)).applied()
        }
    }

    private suspend fun Handle.pix(description: String, reais: Int, category: String, date: LocalDate) =
        space.execute(
            env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(reais)),
        ).applied()

    private suspend fun Handle.review() = space.weeklyReview(period, today).value()

    private fun <T> app.accord.core.space.ReviewBlock<T>.ready(): T = assertIs<ReviewBlock.Ready<T>>(this).value

    @Test
    fun `a semana padrão em 24-10 é 12 a 18 de outubro`() {
        assertEquals(LocalDate.of(2026, 10, 12), period.start)
        assertEquals(LocalDate.of(2026, 10, 18), period.lastDay)
    }

    @Test
    fun `movimentos do período usam a data original e ficam só na semana`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Dentro", 100, "alimentacao.mercado", LocalDate.of(2026, 10, 14))
        h.pix("Domingo", 40, "alimentacao.mercado", LocalDate.of(2026, 10, 18))
        h.pix("Depois", 50, "alimentacao.mercado", LocalDate.of(2026, 10, 19))
        h.pix("Antes", 70, "alimentacao.mercado", LocalDate.of(2026, 10, 11))

        val movements = h.review().movements.ready()
        assertEquals(brl(140), movements.total.total)
        assertEquals(2, movements.total.count)
    }

    @Test
    fun `categoria acima do limite aparece pela situação atual do mês da consulta`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.space.execute(
            h.env(),
            DefineLimits(
                YearMonth.of(2026, 10), LimitScope.OnlyThisMonth,
                listOf(CategoryLimitEdit(seed("alimentacao"), LimitEdit(total = TotalChange.Set(brl(100))))),
            ),
        ).applied()
        h.pix("Mercado", 130, "alimentacao.mercado", LocalDate.of(2026, 10, 20)) // fora da semana, no mês da consulta

        val attention = h.review().attention.ready()
        assertEquals(YearMonth.of(2026, 10), attention.month)
        val food = attention.items.single { it.categoryId == seed("alimentacao") }
        assertIs<Excess.Occurred>(food.excess)
    }

    @Test
    fun `sem limite definido não é excesso e a lista fica vazia`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 500, "alimentacao.mercado", LocalDate.of(2026, 10, 14))
        assertEquals(emptyList(), h.review().attention.ready().items)
    }

    @Test
    fun `pendências refletem a situação atual com identidade estável`() = runBlocking<Unit> {
        val h = openWithAccount()
        val receipt = h.space.execute(
            h.env(),
            RegisterCardPurchase("Presente", Classification.ToClassify, LocalDate.of(2026, 10, 10), brl(80), invoice = YearMonth.of(2026, 11)),
        ).applied()
        val pendings = h.review().pendings.ready()
        val item = pendings.filterIsInstance<PendingItem.CategoryToClarify>().single()
        assertEquals(brl(80), item.amount)
        assertEquals("classify:${item.entryId}", item.reviewId)
        assertTrue(receipt.changes.isNotEmpty())
    }

    @Test
    fun `próximos compromissos são os não pagos que vencem em 7 dias a partir da consulta`() = runBlocking<Unit> {
        val h = openWithAccount()
        fun boleto(name: String, due: LocalDate) = RegisterBoleto(name, Classification.Category(seed("casa.contas-domesticas")), due, brl(90))
        h.space.execute(h.env(), boleto("Hoje", LocalDate.of(2026, 10, 24))).applied()
        h.space.execute(h.env(), boleto("Em 6 dias", LocalDate.of(2026, 10, 30))).applied()
        h.space.execute(h.env(), boleto("Em 7 dias", LocalDate.of(2026, 10, 31))).applied()
        h.space.execute(h.env(), boleto("Ontem", LocalDate.of(2026, 10, 23))).applied()

        val upcoming = h.review().upcoming.ready()
        assertEquals(LocalDate.of(2026, 10, 24), upcoming.from)
        assertEquals(LocalDate.of(2026, 10, 31), upcoming.untilExclusive)
        assertEquals(listOf("Hoje", "Em 6 dias"), upcoming.items.map { it.description })
    }

    @Test
    fun `horizonte que cruza o mês traz os vencimentos dos dois meses`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(
            h.env(),
            RegisterBoleto("Fim do mês", Classification.Category(seed("casa.contas-domesticas")), LocalDate.of(2026, 10, 31), brl(90)),
        ).applied()
        h.space.execute(
            h.env(),
            RegisterBoleto("Início do mês", Classification.Category(seed("casa.contas-domesticas")), LocalDate.of(2026, 11, 2), brl(90)),
        ).applied()
        val nearMonthEnd = h.space.weeklyReview(period, LocalDate.of(2026, 10, 28)).value().upcoming.ready()
        assertEquals(listOf("Fim do mês", "Início do mês"), nearMonthEnd.items.map { it.description })
    }

    @Test
    fun `falha em um bloco não derruba os outros`() = runBlocking<Unit> {
        val queries = WeeklyReviewQueries(
            activity = { _, _ -> error("falha de leitura") },
            forecast = { error("falha de previsão") },
            pendings = { emptyList() },
            commitments = { error("falha de compromissos") },
        )
        val summary = queries.review(period, today)
        assertEquals(ReviewBlock.Failed, summary.movements)
        assertEquals(ReviewBlock.Failed, summary.attention)
        assertEquals(ReviewBlock.Failed, summary.upcoming)
        assertEquals(emptyList(), summary.pendings.ready())
        assertEquals(emptyList(), summary.points)
    }

    @Test
    fun `pontos seguem a ordem da tela e usam identidade estável`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.space.execute(
            h.env(),
            DefineLimits(
                YearMonth.of(2026, 10), LimitScope.OnlyThisMonth,
                listOf(CategoryLimitEdit(seed("alimentacao"), LimitEdit(total = TotalChange.Set(brl(100))))),
            ),
        ).applied()
        h.pix("Mercado", 130, "alimentacao.mercado", LocalDate.of(2026, 10, 20))
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Presente", Classification.ToClassify, LocalDate.of(2026, 10, 10), brl(80), invoice = YearMonth.of(2026, 11)),
        ).applied()
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), LocalDate.of(2026, 10, 27), brl(90)),
        ).applied()

        val kinds = h.review().points.map { it.kind }
        assertEquals(listOf(ReviewPoint.Kind.Category, ReviewPoint.Kind.Pending, ReviewPoint.Kind.Commitment), kinds)
    }

    @Test
    fun `consultar a revisão não altera o orçamento nem as pendências`() = runBlocking<Unit> {
        val h = openWithAccount()
        h.pix("Mercado", 100, "alimentacao.mercado", LocalDate.of(2026, 10, 14))
        val budget = h.space.budget(YearMonth.of(2026, 10)).value()
        val pendings = h.space.pendings().value()
        h.review()
        h.space.weeklyReview(ReviewPeriod(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14)), today).value()
        assertEquals(budget, h.space.budget(YearMonth.of(2026, 10)).value())
        assertEquals(pendings, h.space.pendings().value())
    }
}
