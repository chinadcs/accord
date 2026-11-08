package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.simulation.Scenario
import app.accord.core.simulation.ScenarioSimulator
import app.accord.core.simulation.SimulationResult
import app.accord.core.space.AmountMeaning
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.DefineLimits
import app.accord.core.space.ErrorCode
import app.accord.core.space.RegisterCardPurchase
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S7 — simulador de compra como consulta pura (cenários 3 a 9 do spec). Valores fictícios; hoje é 24/10/2026. */
class SimulationTest : SpaceTestBase() {
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)
    private val dec = ym(2026, 12)
    private val apr = ym(2027, 4)
    private val watched = generateSequence(oct) { it.plusMonths(1) }.take(8).toList() // out/2026 a mai/2027

    private fun Handle.simulator() = ScenarioSimulator(space, clock)

    private fun scenario(total: Money = brl(1200), parcels: Int = 6, first: YearMonth = nov, category: String? = null) =
        Scenario(total, parcels, first, category?.let { seed(it) })

    private fun ready(result: SimulationResult) = assertIs<SimulationResult.Ready>(result)

    private suspend fun Handle.realPurchase(amount: Money, parcels: Int, first: YearMonth, category: String = "eletronicos.equipamentos"): String =
        space.execute(
            env(),
            RegisterCardPurchase(
                description = "Equipamento",
                classification = Classification.Category(seed(category)),
                date = d(2026, 9, 25),
                amount = amount,
                amountMeaning = AmountMeaning.Total,
                installments = parcels,
                invoice = first,
            ),
        ).applied().changes.single().id

    private suspend fun Handle.limit(category: String, reais: Int, month: YearMonth, scope: LimitScope = LimitScope.FromThisMonth) =
        space.execute(env(), DefineLimits(month, scope, listOf(CategoryLimitEdit(seed(category), LimitEdit(total = TotalChange.Set(brl(reais))))))).applied()

    /** Tudo o que o orçamento real e a fila mostram, para provar que simular não mexe em nada. */
    private suspend fun Handle.snapshot(): List<Any?> = buildList {
        for (m in watched) {
            add(space.budget(m).value())
            add(space.commitments(m).value())
            add(space.limits(m).value())
        }
        add(space.operations().value().map { it.operationId })
        add(space.alertStatuses().value())
        add(database.db.dao().alertEvents())
    }

    // ---- cenário 3: referência ----

    @Test
    fun `R$ 1200 em 6 parcelas com primeira fatura em novembro ocupa R$ 200 de novembro a abril e nada muda no real`() = runBlocking<Unit> {
        val h = open()
        h.realPurchase(brl(600), 3, oct) // algo já existente no orçamento real
        val before = h.snapshot()

        val result = ready(h.simulator().simulate(scenario()))

        assertEquals(listOf(nov, dec, ym(2027, 1), ym(2027, 2), ym(2027, 3), apr), result.months.map { it.month })
        assertTrue(result.months.all { it.scenario == brl(200) })
        assertEquals(brl(1200), result.scenarioTotal)
        assertEquals((1..6).toList(), result.months.map { it.installmentNumber })
        // O real de out/2026 a abr/2027 é idêntico antes e depois.
        assertEquals(before, h.snapshot())
    }

    // ---- cenário 4: limite e compra real ----

    @Test
    fun `Eletrônicos com limite de R$ 300 e o equipamento real mostram excesso de R$ 100 do cenário sem gerar alerta`() = runBlocking<Unit> {
        val h = open()
        h.limit("eletronicos", 300, oct)
        h.realPurchase(brl(1200), 6, oct)
        val before = h.snapshot()

        val result = ready(h.simulator().simulate(scenario(category = "eletronicos")))

        val byMonth = result.months.associateBy { it.month }
        for (m in listOf(nov, dec, ym(2027, 1), ym(2027, 2), ym(2027, 3))) {
            val month = byMonth.getValue(m)
            assertEquals(brl(200), month.known, "conhecidos em $m")
            assertEquals(brl(400), month.totalWithScenario, "total em $m")
            val c = month.category!!
            assertEquals(brl(300), c.limit)
            assertEquals(brl(200), c.known)
            assertEquals(brl(400), c.totalWithScenario)
            assertEquals(brl(100), c.scenarioExcess, "excesso do cenário em $m")
        }
        val april = byMonth.getValue(apr)
        assertEquals(Money.ZERO, april.known)
        assertEquals(brl(200), april.totalWithScenario)
        assertEquals(brl(300), april.category!!.limit)
        assertEquals(Money.ZERO, april.category!!.scenarioExcess)
        // Nenhum evento de alerta, cota semanal e fila intactos; o mês anterior (sem cenário) segue com R$ 200.
        assertEquals(before, h.snapshot())
        assertEquals(brl(200), h.space.commitments(oct).value().knownTotal)
    }

    // ---- cenário 5: vigência por mês ----

    @Test
    fun `edição só de dezembro faz a simulação usar R$ 500 em dezembro e R$ 300 nos outros meses`() = runBlocking<Unit> {
        val h = open()
        h.limit("eletronicos", 300, oct)
        h.limit("eletronicos", 500, dec, LimitScope.OnlyThisMonth)

        val result = ready(h.simulator().simulate(scenario(category = "eletronicos")))

        for (m in result.months) {
            assertEquals(if (m.month == dec) brl(500) else brl(300), m.category!!.limit, "limite em ${m.month}")
        }
    }

    // ---- cenário 6: limite ausente x zero ----

    @Test
    fun `categoria sem limite aparece como sem limite definido e limite zero definido mostra excesso`() = runBlocking<Unit> {
        val h = open()

        val without = ready(h.simulator().simulate(scenario(category = "alimentacao")))
        for (m in without.months) {
            assertNull(m.category!!.limit, "sem limite em ${m.month}")
            assertNull(m.category!!.scenarioExcess)
        }

        h.limit("alimentacao", 0, oct)
        val zero = ready(h.simulator().simulate(scenario(category = "alimentacao")))
        for (m in zero.months) {
            assertEquals(Money.ZERO, m.category!!.limit)
            assertEquals(brl(200), m.category!!.scenarioExcess)
        }
    }

    // ---- cenário 7: parcela não exata ----

    @Test
    fun `R$ 1000 em 3 parcelas soma exatamente R$ 1000 e coincide com o cronograma do registro real`() = runBlocking<Unit> {
        val simulated = ready(open().simulator().simulate(scenario(brl(1000), 3, nov)))
        assertEquals(brl(1000), simulated.months.map { it.scenario }.fold(Money.ZERO) { a, b -> a + b })

        val disposable = open()
        val id = disposable.realPurchase(brl(1000), 3, nov)
        val real = disposable.space.purchase(id).value()!!.installments
        assertEquals(real.map { it.amount }, simulated.months.map { it.scenario })
        assertEquals(real.map { it.invoiceMonth }, simulated.months.map { it.month })
    }

    // ---- cenário 8: entrada inválida ----

    @Test
    fun `valor zero, negativo e parcelas zero dão erro no campo sem resultado e sem tocar o real`() = runBlocking<Unit> {
        val h = open()
        h.realPurchase(brl(600), 3, oct)
        val before = h.snapshot()

        fun fields(r: SimulationResult) = assertIs<SimulationResult.Invalid>(r).errors.map { it.field to it.code }
        assertEquals(listOf("total" to ErrorCode.MustBePositive), fields(h.simulator().simulate(scenario(total = Money.ZERO))))
        assertEquals(listOf("total" to ErrorCode.MustBePositive), fields(h.simulator().simulate(scenario(total = Money(-100)))))
        assertEquals(listOf("installments" to ErrorCode.OutOfRange), fields(h.simulator().simulate(scenario(parcels = 0))))
        assertEquals(listOf("installments" to ErrorCode.OutOfRange), fields(h.simulator().simulate(scenario(parcels = 121))))
        assertEquals(listOf("total" to ErrorCode.MustBePositive), fields(h.simulator().simulate(scenario(total = Money(2), parcels = 3))))
        assertEquals(before, h.snapshot())
    }

    // ---- cenário 9: recalcular e abandonar ----

    @Test
    fun `recalcular dez vezes e abandonar não cria registro nem operação na fila`() = runBlocking<Unit> {
        val h = open()
        h.realPurchase(brl(600), 3, oct)
        val before = h.snapshot()
        repeat(10) { ready(h.simulator().simulate(scenario(parcels = 2 + it, category = "eletronicos"))) }
        assertEquals(before, h.snapshot())
    }

    @Test
    fun `a primeira fatura sugerida é a que o motor estimaria para uma compra hoje`() = runBlocking<Unit> {
        // Hoje 24/10/2026, cartão 8/15 (configuração inicial): a fatura estimada vence em novembro.
        assertEquals(nov, open().simulator().suggestedFirstMonth())
    }
}
