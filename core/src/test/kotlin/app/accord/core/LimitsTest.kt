package app.accord.core

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitsTest {
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)
    private fun brl(reais: Int) = Money(reais * 100L)

    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)
    private val dec = ym(2026, 12)
    private val sep = ym(2026, 9)

    /** Alimentação com R$ 1.200 vigente desde setembro. */
    private fun alimentacao(): LimitTimeline {
        val r = LimitTimeline.EMPTY.apply(
            LimitEdit(total = TotalChange.Set(brl(1200))), LimitScope.FromThisMonth, sep,
        )
        return (r as LimitApplyResult.Applied).timeline
    }

    private fun applied(r: LimitApplyResult) = assertIs<LimitApplyResult.Applied>(r).timeline

    // ---- vigência ----

    @Test
    fun `somente outubro cria exceção e mantém setembro e novembro`() {
        val t = applied(alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.OnlyThisMonth, oct))
        assertEquals(brl(1200), t.settingFor(sep).total)
        assertEquals(brl(1400), t.settingFor(oct).total)
        assertEquals(brl(1200), t.settingFor(nov).total)
    }

    @Test
    fun `outubro e seguintes muda de outubro em diante e preserva setembro`() {
        val t = applied(alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.FromThisMonth, oct))
        assertEquals(brl(1200), t.settingFor(sep).total)
        assertEquals(brl(1400), t.settingFor(oct).total)
        assertEquals(brl(1400), t.settingFor(nov).total)
        assertEquals(brl(1400), t.settingFor(ym(2027, 6)).total)
    }

    @Test
    fun `mês anterior à primeira vigência não tem limite definido`() {
        assertNull(alimentacao().settingFor(ym(2026, 8)).total)
        assertNull(LimitTimeline.EMPTY.settingFor(oct).total)
    }

    @Test
    fun `exceção futura é listada na prévia e substituída pelo valor novo`() {
        val withDecException = applied(
            alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(1600))), LimitScope.OnlyThisMonth, dec),
        )
        assertEquals(brl(1600), withDecException.settingFor(dec).total)

        val r = withDecException.apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.FromThisMonth, oct)
        assertIs<LimitApplyResult.Applied>(r)
        assertEquals(listOf(dec), r.replacedFutureMonths)
        assertEquals(brl(1400), r.timeline.settingFor(dec).total)
        assertEquals(brl(1400), r.timeline.settingFor(nov).total)
        assertEquals(brl(1200), r.timeline.settingFor(sep).total)
    }

    @Test
    fun `sem exceções futuras a prévia não lista nada`() {
        val r = alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.FromThisMonth, oct)
        assertIs<LimitApplyResult.Applied>(r)
        assertEquals(emptyList(), r.replacedFutureMonths)
    }

    @Test
    fun `editar não muda a linha do tempo original`() {
        val t = alimentacao()
        t.apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.FromThisMonth, oct)
        assertEquals(brl(1200), t.settingFor(oct).total)
    }

    // ---- divisão ----

    private val delivery = "delivery"
    private val mercado = "mercado"
    private val restaurantes = "restaurantes"

    @Test
    fun `divisão parcial de 400 e 500 deixa 300 sem distribuir`() {
        val t = applied(
            alimentacao().apply(
                LimitEdit(split = mapOf(delivery to brl(400), mercado to brl(500))), LimitScope.FromThisMonth, oct,
            ),
        )
        val v = LimitView.of(t.settingFor(oct), categoryNet = brl(980), subNet = mapOf(delivery to brl(280), mercado to brl(500), restaurantes to brl(200)))
        assertEquals(brl(1200), v.total)
        assertEquals(brl(900), v.distributed)
        assertEquals(brl(300), v.undistributed)
        assertEquals(brl(220), v.remaining)
        assertEquals(brl(120), v.sub(delivery).remainingOfPart)
        assertEquals(Money.ZERO, v.sub(mercado).remainingOfPart)
        // sem divisão definida: nada de limite zero presumido
        assertNull(v.sub(restaurantes).part)
        assertNull(v.sub(restaurantes).remainingOfPart)
        assertNull(v.excess)
    }

    @Test
    fun `divisão 300 600 300 gera restantes 20 100 100`() {
        val t = applied(
            alimentacao().apply(
                LimitEdit(split = mapOf(delivery to brl(300), mercado to brl(600), restaurantes to brl(300))),
                LimitScope.FromThisMonth, oct,
            ),
        )
        val v = LimitView.of(t.settingFor(oct), brl(980), mapOf(delivery to brl(280), mercado to brl(500), restaurantes to brl(200)))
        assertEquals(brl(20), v.sub(delivery).remainingOfPart)
        assertEquals(brl(100), v.sub(mercado).remainingOfPart)
        assertEquals(brl(100), v.sub(restaurantes).remainingOfPart)
        assertEquals(Money.ZERO, v.undistributed)
    }

    @Test
    fun `divisão de 1300 num limite de 1200 é rejeitada com diferença de 100 e nada muda`() {
        val before = alimentacao()
        val r = before.apply(
            LimitEdit(split = mapOf(delivery to brl(800), mercado to brl(500))), LimitScope.FromThisMonth, oct,
        )
        val rejected = assertIs<LimitApplyResult.Rejected>(r)
        assertEquals(listOf(LimitProblem.SplitExceedsTotal(oct, brl(100))), rejected.problems)
        assertNull(before.settingFor(oct).split.takeIf { it.isNotEmpty() })
    }

    @Test
    fun `reduzir o limite abaixo do distribuído não corta partes — rejeita`() {
        val t = applied(
            alimentacao().apply(
                LimitEdit(split = mapOf(delivery to brl(400), mercado to brl(500))), LimitScope.FromThisMonth, oct,
            ),
        )
        val r = t.apply(LimitEdit(total = TotalChange.Set(brl(800))), LimitScope.OnlyThisMonth, oct)
        val rejected = assertIs<LimitApplyResult.Rejected>(r)
        assertEquals(listOf(LimitProblem.SplitExceedsTotal(oct, brl(100))), rejected.problems)
    }

    @Test
    fun `reduzir limite de outubro em diante lista todos os meses futuros que ficam inválidos e não grava nada`() {
        var t = applied(
            alimentacao().apply(
                LimitEdit(split = mapOf(delivery to brl(400), mercado to brl(500))), LimitScope.FromThisMonth, sep,
            ),
        )
        // dezembro tem exceção própria com divisão de 1.000
        t = applied(
            t.apply(
                LimitEdit(total = TotalChange.Set(brl(1500)), split = mapOf(delivery to brl(500), mercado to brl(500))),
                LimitScope.OnlyThisMonth, dec,
            ),
        )
        // reduzir para 950 de outubro em diante, sem editar a divisão: outubro (900) cabe; dezembro (1.000) não
        val r = t.apply(LimitEdit(total = TotalChange.Set(brl(950))), LimitScope.FromThisMonth, oct)
        val rejected = assertIs<LimitApplyResult.Rejected>(r)
        assertEquals(listOf(LimitProblem.SplitExceedsTotal(dec, brl(50))), rejected.problems)
        assertEquals(brl(1200), t.settingFor(oct).total)
        assertEquals(brl(1500), t.settingFor(dec).total)
    }

    @Test
    fun `divisão exige limite principal definido`() {
        val r = LimitTimeline.EMPTY.apply(LimitEdit(split = mapOf(delivery to brl(100))), LimitScope.FromThisMonth, oct)
        val rejected = assertIs<LimitApplyResult.Rejected>(r)
        assertEquals(listOf(LimitProblem.SplitWithoutTotal(oct)), rejected.problems)
    }

    @Test
    fun `limpar o limite com divisão existente é rejeitado`() {
        val t = applied(
            alimentacao().apply(LimitEdit(split = mapOf(delivery to brl(400))), LimitScope.FromThisMonth, oct),
        )
        val r = t.apply(LimitEdit(total = TotalChange.Clear), LimitScope.FromThisMonth, oct)
        assertIs<LimitApplyResult.Rejected>(r)
    }

    @Test
    fun `limpar a divisão junto com o limite é aceito`() {
        val t = applied(
            alimentacao().apply(LimitEdit(split = mapOf(delivery to brl(400))), LimitScope.FromThisMonth, oct),
        )
        val r = t.apply(LimitEdit(total = TotalChange.Clear, split = emptyMap()), LimitScope.FromThisMonth, oct)
        assertNull(applied(r).settingFor(nov).total)
    }

    @Test
    fun `parte negativa é rejeitada`() {
        val r = alimentacao().apply(LimitEdit(split = mapOf(delivery to Money(-1))), LimitScope.FromThisMonth, oct)
        val rejected = assertIs<LimitApplyResult.Rejected>(r)
        assertTrue(rejected.problems.single() is LimitProblem.NegativeAmount)
    }

    @Test
    fun `limite negativo é rejeitado`() {
        val r = LimitTimeline.EMPTY.apply(LimitEdit(total = TotalChange.Set(Money(-100))), LimitScope.FromThisMonth, oct)
        assertIs<LimitApplyResult.Rejected>(r)
    }

    // ---- ausente versus zero ----

    @Test
    fun `sem limite definido não há excesso nem restante nem percentual`() {
        val v = LimitView.of(LimitSetting.UNDEFINED, categoryNet = brl(50), subNet = emptyMap())
        assertNull(v.total)
        assertNull(v.excess)
        assertNull(v.remaining)
        assertNull(v.usedFraction)
    }

    @Test
    fun `limite zero com gasto de 50 mostra excesso de 50`() {
        val t = applied(LimitTimeline.EMPTY.apply(LimitEdit(total = TotalChange.Set(Money.ZERO)), LimitScope.FromThisMonth, oct))
        val v = LimitView.of(t.settingFor(oct), brl(50), emptyMap())
        assertEquals(Money.ZERO, v.total)
        assertEquals(brl(50), v.excess)
        assertNull(v.remaining)
        assertNull(v.usedFraction)
    }

    @Test
    fun `limite reduzido para 900 com gasto de 980 mostra excesso de 80 sem mexer em outros limites`() {
        val t = applied(alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(900))), LimitScope.FromThisMonth, oct))
        val v = LimitView.of(t.settingFor(oct), brl(980), emptyMap())
        assertEquals(brl(80), v.excess)
        assertNull(v.remaining)
        assertEquals(brl(900), v.total)
    }

    @Test
    fun `crédito líquido negativo não gera excesso`() {
        val v = LimitView.of(alimentacao().settingFor(oct), categoryNet = Money(-4_000), subNet = emptyMap())
        assertNull(v.excess)
        assertEquals(brl(1200) + Money(4_000), v.remaining)
    }

    @Test
    fun `percentual usado é a fração do líquido sobre o limite`() {
        val v = LimitView.of(alimentacao().settingFor(oct), brl(600), emptyMap())
        assertEquals(0.5, v.usedFraction)
        assertNotEquals(null, v.usedFraction)
    }

    // ---- total geral ----

    @Test
    fun `total dos limites soma só as categorias principais definidas`() {
        val settings = listOf(
            LimitSetting(brl(1200), mapOf(delivery to brl(400))),
            LimitSetting(brl(2000), emptyMap()),
            LimitSetting(brl(1300), emptyMap()),
            LimitSetting.UNDEFINED,
        )
        assertEquals(brl(4500), LimitView.totalOfLimits(settings))
    }

    // ---- parte por subcategoria (mover subcategoria) ----

    @Test
    fun `retirar a parte de uma subcategoria de outubro em diante aumenta o sem distribuir e preserva setembro`() {
        val t = applied(
            alimentacao().apply(
                LimitEdit(split = mapOf(delivery to brl(400), mercado to brl(500))), LimitScope.FromThisMonth, sep,
            ),
        )
        val r = t.withPart(oct, delivery, null)
        val after = applied(r)
        assertEquals(mapOf(delivery to brl(400), mercado to brl(500)), after.settingFor(sep).split)
        assertEquals(mapOf(mercado to brl(500)), after.settingFor(oct).split)
        assertEquals(mapOf(mercado to brl(500)), after.settingFor(ym(2027, 3)).split)
        val view = LimitView.of(after.settingFor(oct), Money.ZERO, emptyMap())
        assertEquals(brl(700), view.undistributed)
    }

    @Test
    fun `definir a parte no destino respeita o limite do destino`() {
        val destination = applied(
            LimitTimeline.EMPTY.apply(LimitEdit(total = TotalChange.Set(brl(300))), LimitScope.FromThisMonth, sep),
        )
        assertEquals(brl(250), applied(destination.withPart(oct, delivery, brl(250))).settingFor(nov).split.getValue(delivery))
        val rejected = assertIs<LimitApplyResult.Rejected>(destination.withPart(oct, delivery, brl(350)))
        assertEquals(listOf(LimitProblem.SplitExceedsTotal(oct, brl(50))), rejected.problems)
    }

    @Test
    fun `withPart em diante também ajusta exceções e vigências posteriores`() {
        var t = applied(
            alimentacao().apply(LimitEdit(split = mapOf(delivery to brl(400))), LimitScope.FromThisMonth, sep),
        )
        t = applied(t.apply(LimitEdit(total = TotalChange.Set(brl(1500)), split = mapOf(delivery to brl(500))), LimitScope.OnlyThisMonth, dec))
        val after = applied(t.withPart(oct, delivery, null))
        assertEquals(emptyMap(), after.settingFor(dec).split)
        assertEquals(brl(1500), after.settingFor(dec).total)
        assertEquals(mapOf(delivery to brl(400)), after.settingFor(sep).split)
    }

    @Test
    fun `entradas gravadas reconstroem a mesma linha do tempo`() {
        val t = applied(alimentacao().apply(LimitEdit(total = TotalChange.Set(brl(1400))), LimitScope.OnlyThisMonth, oct))
        val rebuilt = LimitTimeline.of(t.entries)
        for (m in listOf(sep, oct, nov, dec)) assertEquals(t.settingFor(m), rebuilt.settingFor(m))
    }
}
