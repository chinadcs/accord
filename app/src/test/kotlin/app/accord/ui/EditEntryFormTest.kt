package app.accord.ui

import app.accord.core.Money
import app.accord.core.RecurrenceScope
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.Classification
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditRecurrence
import app.accord.core.space.PaymentMethod
import app.accord.ui.edit.EditField
import app.accord.ui.edit.EditForm
import app.accord.ui.edit.EditOriginal
import app.accord.ui.edit.EditTarget
import app.accord.ui.register.FormError
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditEntryFormTest {
    private val day = LocalDate.of(2026, 10, 2)

    private fun original(
        target: EditTarget = EditTarget.Direct, installments: Int = 1, method: PaymentMethod? = PaymentMethod.Pix,
        account: String? = "conta", category: String? = "cat", date: LocalDate? = day,
    ) = EditOriginal(
        entityId = "id", target = target, description = "Mercado", categoryId = category, date = date, amount = Money(5_000),
        accountId = account, revision = 3, installments = installments, method = method,
        recurrenceId = "serie".takeIf { target == EditTarget.Occurrence }, occurrence = 2.takeIf { target == EditTarget.Occurrence },
    )

    private fun ok(form: EditForm, original: EditOriginal) = assertIs<EditForm.Built.Ok>(form.build(original))

    @Test
    fun ocorrenciaDeRendaNaoPedeCategoriaDeDespesa() {
        val o = original(target = EditTarget.Occurrence, category = null, date = null).copy(incomeSeries = true)
        assertFalse(o.categoryEditable)
        val built = ok(EditForm.of(o).copy(description = "Empréstimo", scope = RecurrenceScope.OnlyThis), o)
        val command = assertIs<EditRecurrence>(built.command)
        assertNull(command.fields.classificationId)
        assertTrue(built.changed)
    }

    @Test
    fun ocorrenciaDeRendaTrocaACategoriaDeRendaPeloCampoDaSerie() {
        val o = original(target = EditTarget.Occurrence, category = null, date = null).copy(incomeSeries = true, incomeCategoryId = "salario")
        assertTrue(o.incomeCategoryEditable)
        val built = ok(EditForm.of(o).copy(incomeCategoryId = "extra", scope = RecurrenceScope.OnlyThis), o)
        assertEquals("extra", assertIs<EditRecurrence>(built.command).fields.classificationId)
        assertTrue(built.changed)
    }

    @Test
    fun ocorrenciaDeDespesaSemCategoriaContinuaExigindoCategoria() {
        val o = original(target = EditTarget.Occurrence, category = null, date = null)
        val built = assertIs<EditForm.Built.Invalid>(EditForm.of(o).copy(scope = RecurrenceScope.OnlyThis).build(o))
        assertTrue(EditField.Category in built.errors)
    }

    @Test
    fun semMudancaOComandoNaoTemNadaParaGravar() {
        val o = original()
        assertFalse(ok(EditForm.of(o), o).changed)
    }

    @Test
    fun soOQueMudouVaiNoComandoComAVersaoEsperada() {
        val o = original()
        val built = ok(EditForm.of(o).copy(description = "  Feira ", amountText = "72,50"), o)
        val command = assertIs<CorrectEntry>(built.command)
        assertTrue(built.changed)
        assertEquals("Feira", command.description)
        assertEquals(Money(7_250), command.amount)
        assertNull(command.category)
        assertNull(command.date)
        assertNull(command.accountId)
        assertEquals(3, command.expectedRevision)
    }

    @Test
    fun valorIgualAoOriginalNaoViraAlteracaoMesmoDigitadoDeOutroJeito() {
        val o = original()
        val built = ok(EditForm.of(o).copy(amountText = "50"), o)
        assertFalse(built.changed)
    }

    @Test
    fun categoriaEDataNovasSaemNoComando() {
        val o = original()
        val built = ok(EditForm.of(o).copy(categoryId = "outra", date = day.plusDays(1)), o)
        val command = assertIs<CorrectEntry>(built.command)
        assertEquals(Classification.Category("outra"), command.category)
        assertEquals(day.plusDays(1), command.date)
    }

    @Test
    fun compraParceladaNaoEditaValorENuncaEnviaValor() {
        val o = original(target = EditTarget.CardPurchase, installments = 6, method = null, account = null)
        assertFalse(o.amountEditable)
        val built = ok(EditForm.of(o).copy(amountText = "9999", description = "Notebook"), o)
        assertNull(assertIs<CorrectEntry>(built.command).amount)
    }

    @Test
    fun compraAVistaNoCartaoEditaValorMasNaoTemConta() {
        val o = original(target = EditTarget.CardPurchase, installments = 1, method = null, account = null)
        assertTrue(o.amountEditable)
        assertFalse(o.accountEditable)
    }

    @Test
    fun dinheiroNaoTemContaEPixTem() {
        assertFalse(original(method = PaymentMethod.Cash, account = null).accountEditable)
        assertTrue(original(method = PaymentMethod.Pix).accountEditable)
    }

    @Test
    fun rendaNaoTemCategoriaEBoletoPagoMantemOEfetivo() {
        assertFalse(original(target = EditTarget.Income, category = null).categoryEditable)
        val o = original(target = EditTarget.Boleto, method = null)
        assertTrue(o.categoryEditable)
    }

    @Test
    fun descricaoVaziaValorInvalidoECategoriaAusenteSaoErrosPorCampo() {
        val o = original()
        val invalid = assertIs<EditForm.Built.Invalid>(EditForm.of(o).copy(description = " ", amountText = "1.20", categoryId = null).build(o))
        assertEquals(FormError.Required, invalid.errors[EditField.Description])
        assertEquals(FormError.AmountAmbiguous, invalid.errors[EditField.Amount])
        assertEquals(FormError.CategoryRequired, invalid.errors[EditField.Category])
        assertEquals(listOf(EditField.Amount, EditField.Description, EditField.Category), invalid.errors.keys.toList())
    }

    @Test
    fun recorrenciaSemAlcanceNaoMonta() {
        val o = original(target = EditTarget.Occurrence, method = null, date = null)
        val invalid = assertIs<EditForm.Built.Invalid>(EditForm.of(o).copy(amountText = "95,00").build(o))
        assertEquals(FormError.ScopeRequired, invalid.errors[EditField.Scope])
    }

    @Test
    fun recorrenciaComAlcanceLevaSoOsCamposAlterados() {
        val o = original(target = EditTarget.Occurrence, method = null, date = null)
        val built = ok(EditForm.of(o).copy(amountText = "95,00", scope = RecurrenceScope.ThisAndNext), o)
        val command = assertIs<EditRecurrence>(built.command)
        assertEquals(RecurrenceScope.ThisAndNext, command.scope)
        assertEquals(Money(9_500), command.fields.amount)
        assertNull(command.fields.description)
        assertEquals("serie", command.recurrenceId)
        assertEquals(2, command.occurrence)
        assertTrue(built.changed)
    }

    @Test
    fun ocorrenciaNaoEditaDataEConfirmacoesViramFlagsDoComando() {
        val o = original(target = EditTarget.Occurrence, method = null, date = null)
        assertFalse(o.dateEditable)
        val form = EditForm.of(o).copy(description = "Nova", scope = RecurrenceScope.OnlyThis)
        val command = assertIs<EditRecurrence>(assertIs<EditForm.Built.Ok>(form.build(o, confirmRealized = true)).command)
        assertTrue(command.confirmRealized)
        val plain = original()
        val extra = assertIs<CorrectEntry>(assertIs<EditForm.Built.Ok>(EditForm.of(plain).copy(description = "x").build(plain, confirmExtra = true)).command)
        assertTrue(extra.confirmExtra)
    }

    // ---- accord-llf7.5: categoria de renda ----

    private fun income(incomeCategory: String? = "salario") = original(target = EditTarget.Income, category = null).copy(incomeCategoryId = incomeCategory)

    @Test
    fun rendaTrocaACategoriaDeRendaEsoElaVaiNoComando() {
        val o = income()
        val built = ok(EditForm.of(o).copy(incomeCategoryId = "extra"), o)
        val command = assertIs<CorrectEntry>(built.command)
        assertTrue(built.changed)
        assertEquals("extra", command.incomeCategoryId)
        assertNull(command.category)
        assertNull(command.description)
    }

    @Test
    fun rendaSemTrocaDeCategoriaNaoViraAlteracao() {
        val o = income()
        val built = ok(EditForm.of(o), o)
        assertFalse(built.changed)
        assertNull(assertIs<CorrectEntry>(built.command).incomeCategoryId)
    }

    @Test
    fun rendaSemCategoriaPodeGanharUmaMasNaoVoltaASemCategoriaPeloComando() {
        val none = income(incomeCategory = null)
        assertEquals("salario", assertIs<CorrectEntry>(ok(EditForm.of(none).copy(incomeCategoryId = "salario"), none).command).incomeCategoryId)
        val had = income()
        assertFalse(ok(EditForm.of(had).copy(incomeCategoryId = null), had).changed)
    }

    @Test
    fun categoriaDeRendaSoEEditavelEmRenda() {
        assertTrue(income().incomeCategoryEditable)
        assertFalse(original().incomeCategoryEditable)
        val direct = original()
        assertNull(assertIs<CorrectEntry>(ok(EditForm.of(direct).copy(incomeCategoryId = "extra", description = "x"), direct).command).incomeCategoryId)
        assertEquals(EditField.IncomeCategory, EditForm.fieldOf("incomeCategoryId"))
    }

    @Test
    fun trocarPixPorDebitoGeraSoOComandoDeTrocaComAContaEAVersao() {
        val o = original()
        val built = ok(EditForm.of(o).copy(method = PaymentMethod.Debit), o)
        val change = assertNotNull(built.methodChange)
        assertEquals(ChangePaymentMethod("id", DirectPaymentKind.Debit, accountId = "conta", expectedRevision = 3), change)
        assertTrue(built.changed)
        assertFalse(built.fieldsChanged)
    }

    @Test
    fun trocarParaDinheiroNaoLevaConta() {
        val o = original()
        val change = assertNotNull(ok(EditForm.of(o).copy(method = PaymentMethod.Cash), o).methodChange)
        assertEquals(DirectPaymentKind.Cash, change.kind)
        assertNull(change.accountId)
    }

    @Test
    fun dinheiroParaPixLevaAContaEscolhida() {
        val o = original(method = PaymentMethod.Cash, account = null)
        val change = assertNotNull(ok(EditForm.of(o).copy(method = PaymentMethod.Pix, accountId = "nova"), o).methodChange)
        assertEquals(DirectPaymentKind.Pix, change.kind)
        assertEquals("nova", change.accountId)
    }

    @Test
    fun trocarAFormaECorrigirOutroCampoSaoDoisComandosEOSegundoNaoExigeARevisaoAntiga() {
        val o = original()
        val built = ok(EditForm.of(o).copy(method = PaymentMethod.Debit, description = "Feira", accountId = "outra"), o)
        assertNotNull(built.methodChange)
        assertTrue(built.fieldsChanged)
        val command = assertIs<CorrectEntry>(built.command)
        assertEquals("Feira", command.description)
        assertNull(command.accountId) // a conta viaja na troca
        assertNull(command.expectedRevision) // a troca já subiu a revisão
        assertEquals("outra", built.methodChange.accountId)
    }

    @Test
    fun semTrocaACorrecaoSegueComAVersaoEsperadaECadaFormaIgualNaoVira() {
        val o = original()
        val built = ok(EditForm.of(o).copy(method = PaymentMethod.Pix, description = "Feira"), o)
        assertNull(built.methodChange)
        assertEquals(3, assertIs<CorrectEntry>(built.command).expectedRevision)
    }

    @Test
    fun cartaoBoletoRendaRecorrenciaEImportadoNaoOferecemATroca() {
        assertFalse(original(target = EditTarget.CardPurchase, method = PaymentMethod.Card).methodEditable)
        assertFalse(original(target = EditTarget.Boleto, method = PaymentMethod.Boleto).methodEditable)
        assertFalse(original(target = EditTarget.Income, method = null).methodEditable)
        assertFalse(original(target = EditTarget.Occurrence, method = null).methodEditable)
        assertFalse(original().copy(imported = true).methodEditable)
        assertTrue(original().methodEditable)
        assertNull(EditForm.of(original(target = EditTarget.Boleto, method = PaymentMethod.Boleto)).method)
    }

    @Test
    fun contaSoValeParaPixEDebito() {
        val o = original()
        assertTrue(o.accountEditableFor(PaymentMethod.Debit))
        assertFalse(o.accountEditableFor(PaymentMethod.Cash))
    }
}
