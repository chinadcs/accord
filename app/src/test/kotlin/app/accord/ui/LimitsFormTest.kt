package app.accord.ui

import app.accord.core.LimitScope
import app.accord.core.LimitSetting
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.ui.limits.LimitFieldError
import app.accord.ui.limits.LimitKey
import app.accord.ui.limits.LimitRow
import app.accord.ui.limits.LimitSub
import app.accord.ui.limits.LimitsDraft
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitsFormTest {
    private val oct = YearMonth.of(2026, 10)

    private fun brl(reais: Int) = Money(reais * 100L)

    private val food = LimitRow(
        "food", "Alimentação", listOf(LimitSub("delivery", "Delivery"), LimitSub("market", "Mercado")),
        LimitSetting(brl(1200), mapOf("delivery" to brl(400))), brl(980), emptyList(),
    )
    private val pet = LimitRow("pet", "Pet", emptyList(), LimitSetting.UNDEFINED, brl(150), emptyList())
    private val rows = listOf(food, pet)

    private fun draft(scope: LimitScope? = LimitScope.FromThisMonth, change: (LimitsDraft) -> LimitsDraft = { it }) =
        change(LimitsDraft.of(rows).copy(scope = scope))

    @Test
    fun semMudancaNaoHaNadaParaSalvar() {
        val built = assertIs<LimitsDraft.Built.Invalid>(draft().build(rows, oct))
        assertTrue(built.nothingChanged)
        assertTrue(built.errors.isEmpty())
    }

    @Test
    fun soAsCategoriasEditadasViramAlteracaoEOComandoTemOMesDeReferenciaEOAlcance() {
        val d = draft { it.copy(totals = it.totals + ("food" to "1.500,00")) }
        val ok = assertIs<LimitsDraft.Built.Ok>(d.build(rows, oct))
        assertEquals(listOf("food"), ok.changed)
        assertEquals(oct, ok.command.month)
        assertEquals(LimitScope.FromThisMonth, ok.command.scope)
        val edit = ok.command.edits.single().edit
        assertEquals(TotalChange.Set(brl(1500)), edit.total)
        assertNull(edit.split)
    }

    @Test
    fun semAlcanceEscolhidoNaoSalvaENaoPresumeOpcao() {
        val d = draft(scope = null) { it.copy(totals = it.totals + ("food" to "1.500,00")) }
        val invalid = assertIs<LimitsDraft.Built.Invalid>(d.build(rows, oct))
        assertTrue(invalid.scopeMissing)
        assertNull(LimitsDraft.of(rows).scope)
    }

    @Test
    fun apagarOLimiteVoltaParaNaoDefinidoEDifereDeZero() {
        val cleared = assertIs<LimitsDraft.Built.Ok>(
            draft { it.copy(totals = it.totals + ("food" to ""), splits = it.splits + ("food" to emptyMap())) }.build(rows, oct),
        )
        assertEquals(TotalChange.Clear, cleared.command.edits.single().edit.total)
        assertEquals(emptyMap(), cleared.command.edits.single().edit.split)
        val zero = assertIs<LimitsDraft.Built.Ok>(draft { it.copy(totals = it.totals + ("pet" to "0")) }.build(rows, oct))
        assertEquals(TotalChange.Set(Money.ZERO), zero.command.edits.single().edit.total)
    }

    @Test
    fun categoriaSemLimiteEmBrancoNaoViraAlteracao() {
        // pet começou "não definido" e continua em branco: não vira zero escondido
        val built = draft { it.copy(totals = it.totals + ("food" to "1.500,00")) }.build(rows, oct)
        assertTrue(assertIs<LimitsDraft.Built.Ok>(built).command.edits.none { it.categoryId == "pet" })
    }

    @Test
    fun divisaoAcimaDoLimiteERecusadaComOExcessoEmCentavos() {
        val d = draft {
            it.copy(splits = it.splits + ("food" to mapOf("delivery" to "700,00", "market" to "600,00")))
        }
        val invalid = assertIs<LimitsDraft.Built.Invalid>(d.build(rows, oct))
        assertEquals(LimitFieldError.SplitExceeds(brl(100)), invalid.errors[LimitKey("food")])
    }

    @Test
    fun divisaoParcialEValidaESemValorNaoViraZero() {
        val d = draft { it.copy(splits = it.splits + ("food" to mapOf("delivery" to "400,00", "market" to "500,00"))) }
        val ok = assertIs<LimitsDraft.Built.Ok>(d.build(rows, oct))
        assertEquals(mapOf("delivery" to brl(400), "market" to brl(500)), ok.command.edits.single().edit.split)
        // só delivery: market em branco não tem limite zero
        val partial = assertIs<LimitsDraft.Built.Ok>(draft { it.copy(splits = it.splits + ("food" to mapOf("delivery" to "300,00", "market" to ""))) }.build(rows, oct))
        assertEquals(mapOf("delivery" to brl(300)), partial.command.edits.single().edit.split)
    }

    @Test
    fun reduzirOLimiteAbaixoDoDistribuidoExigeRevisarADivisao() {
        val d = draft { it.copy(totals = it.totals + ("food" to "300,00")) }
        val invalid = assertIs<LimitsDraft.Built.Invalid>(d.build(rows, oct))
        assertEquals(LimitFieldError.SplitExceeds(brl(100)), invalid.errors[LimitKey("food")])
    }

    @Test
    fun divisaoSemLimitePrincipalNaoEAceita() {
        val d = draft { it.copy(totals = it.totals + ("food" to ""), splits = it.splits + ("food" to mapOf("delivery" to "400,00"))) }
        val invalid = assertIs<LimitsDraft.Built.Invalid>(d.build(rows, oct))
        assertEquals(LimitFieldError.SplitWithoutTotal, invalid.errors[LimitKey("food")])
    }

    @Test
    fun valorNegativoAmbiguoEInvalidoTemErroNoCampoCerto() {
        val negative = assertIs<LimitsDraft.Built.Invalid>(draft { it.copy(totals = it.totals + ("pet" to "-5")) }.build(rows, oct))
        assertEquals(LimitFieldError.Negative, negative.errors[LimitKey("pet")])
        val ambiguous = assertIs<LimitsDraft.Built.Invalid>(draft { it.copy(totals = it.totals + ("pet" to "1.20")) }.build(rows, oct))
        assertEquals(LimitFieldError.Ambiguous, ambiguous.errors[LimitKey("pet")])
        val invalid = assertIs<LimitsDraft.Built.Invalid>(
            draft { it.copy(splits = it.splits + ("food" to mapOf("market" to "abc"))) }.build(rows, oct),
        )
        assertEquals(LimitFieldError.Invalid, invalid.errors[LimitKey("food", "market")])
    }

    @Test
    fun parteDeSubcategoriaQueATelaNaoListaNaoSomeEmSilencio() {
        val moved = food.copy(subs = listOf(LimitSub("market", "Mercado")), original = LimitSetting(brl(1200), mapOf("delivery" to brl(400))))
        val d = LimitsDraft.of(listOf(moved)).copy(scope = LimitScope.OnlyThisMonth)
            .let { it.copy(splits = it.splits + ("food" to mapOf("market" to "100,00"))) }
        val ok = assertIs<LimitsDraft.Built.Ok>(d.build(listOf(moved), oct))
        assertEquals(mapOf("market" to brl(100), "delivery" to brl(400)), ok.command.edits.single().edit.split)
    }
}
