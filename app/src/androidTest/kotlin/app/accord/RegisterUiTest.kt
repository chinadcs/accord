package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.accord.core.Money
import app.accord.core.space.AccountInfo
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryNode
import app.accord.core.space.InstallmentView
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormError
import app.accord.ui.register.FormField
import app.accord.ui.register.FormKind
import app.accord.ui.register.RegisterForm
import app.accord.ui.register.RegisterPhase
import app.accord.ui.register.RegisterScreen
import app.accord.ui.register.RegisterUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tela 09 com estado sintético: campos, erros junto do campo, parcelas, fatura e saída sem salvar. */
class RegisterUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private fun brl(reais: Int) = Money(reais * 100L)

    private val categories = listOf(
        CategoryNode("food", null, "Alimentação", 0, "utensils", null),
        CategoryNode("delivery", "food", "Delivery", 0, "bike", null),
        CategoryNode("pet", null, "Pet", 1, "paw-print", null),
    ).associateBy { it.id }

    private val accounts = listOf(AccountInfo("acc", BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta salário"))

    private fun base(
        form: RegisterForm = RegisterForm(date = today),
        errors: Map<FormField, FormError> = emptyMap(),
        preview: List<InstallmentView> = emptyList(),
        phase: RegisterPhase = RegisterPhase.Editing,
        recovered: Boolean = false,
        askBack: Boolean = false,
        general: String? = null,
        focus: Pair<FormField?, Int> = null to 0,
    ) = RegisterUiState(
        form = form, errors = errors, generalError = general, categories = categories, accounts = accounts, defaultAccountId = "acc",
        preview = preview, phase = phase, loading = false, recoveredDraft = recovered, askKeepOrDiscard = askBack,
        focusField = focus.first, focusTick = focus.second,
    )

    /** O formulário rola: leva o texto à tela antes de conferir. */
    private fun see(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private class Calls {
        var saves = 0
        var backs = 0
        var keep = 0
        var draft = 0
        var discard = 0
        var dismissRecovered = 0
    }

    /** Pequeno "ViewModel" de teste: guarda o formulário e aplica as mudanças como o real faria. */
    private fun show(state: RegisterUiState, calls: Calls = Calls()): Calls {
        compose.setContent {
            AccordTheme {
                var form by remember { mutableStateOf(state.form) }
                RegisterScreen(
                    state.copy(form = form), PaddingValues(),
                    onChange = { form = it(form) }, onSave = { calls.saves++ }, onBack = { calls.backs++ },
                    onKeepEditing = { calls.keep++ }, onSaveDraft = { calls.draft++ }, onDiscard = { calls.discard++ },
                    onDismissRecovered = { calls.dismissRecovered++ },
                )
            }
        }
        return calls
    }

    @Test
    fun formularioMinimoMostraOsCamposBasicosDeUmaDespesa() {
        show(base())
        compose.onNodeWithText("Nova despesa no cartão").assertIsDisplayed()
        compose.onNodeWithText("Descrição").assertIsDisplayed()
        compose.onNodeWithText("Valor (R$)").assertIsDisplayed()
        compose.onNodeWithText("Salvar lançamento").assertIsDisplayed()
        // compra no cartão (o padrão) não oferece as formas de pagamento imediatas
        assertEquals(0, compose.onAllNodes(hasText("Pagamento")).fetchSemanticsNodes().size)
    }

    @Test
    fun despesaImediataOfereceAsFormasDePagamento() {
        show(base(form = RegisterForm(date = today, form = ExpenseForm.Pix)))
        compose.onNodeWithText("Pagamento").assertIsDisplayed()
        listOf("Pix", "Débito", "Dinheiro", "Boleto").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun escolherRendaTiraPagamentoECategoria() {
        // o tipo vem do botão "+" (Renda, Despesa ou Despesa no cartão), não de chips no formulário
        show(base(form = RegisterForm(date = today, kind = FormKind.Income)))
        compose.onNodeWithText("Data prevista do recebimento").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Categoria", substring = true) and hasText("Escolher categoria", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("Pix")).fetchSemanticsNodes().size)
    }

    @Test
    fun boletoTrocaORotuloDaDataParaVencimento() {
        show(base(form = RegisterForm(date = today, form = ExpenseForm.Pix)))
        compose.onNodeWithText("Boleto").performClick()
        compose.onNodeWithText("Vencimento").assertIsDisplayed()
    }

    @Test
    fun errosFicamJuntoDoCampoComTextoClaro() {
        show(
            base(
                errors = linkedMapOf(
                    FormField.Description to FormError.Required,
                    FormField.Amount to FormError.AmountAmbiguous,
                    FormField.Category to FormError.CategoryRequired,
                ),
            ),
        )
        see("Preencha este campo.")
        see("Valor ambíguo. Use vírgula nos centavos, como 1.200,00.")
        see("Escolha a categoria.")
    }

    @Test
    fun errosDaFachadaApareceMantendoOQueFoiDigitado() {
        show(base(form = RegisterForm(date = today, description = "Mercado", amountText = "50,00"), errors = mapOf(FormField.Account to FormError.AccountRequired)))
        compose.onNodeWithText("Mercado").assertIsDisplayed()
        compose.onNodeWithText("50,00").assertIsDisplayed()
    }

    @Test
    fun digitarValorEDescricaoAtualizaOFormulario() {
        show(base())
        compose.onNodeWithText("Descrição").performTextInput("Delivery")
        compose.onNodeWithText("Valor (R$)").performTextInput("68,00")
        compose.onNodeWithText("Delivery").assertIsDisplayed()
        compose.onNodeWithText("68,00").assertIsDisplayed()
    }

    @Test
    fun seletorDeCategoriaMostraCaminhoCompletoDepoisDeEscolher() {
        show(base())
        compose.onNodeWithText("Escolher categoria").performClick()
        // duas etapas: primeiro o grupo, depois a categoria
        compose.onNodeWithText("Alimentação").performClick()
        compose.onNodeWithText("Delivery").performClick()
        compose.onNodeWithText("Alimentação > Delivery").assertIsDisplayed()
    }

    @Test
    fun contaPadraoJaVemPreenchidaECorrigivel() {
        show(base(form = RegisterForm(date = today, form = ExpenseForm.Pix, accountId = "acc")))
        see("Itaú Unibanco · Conta salário")
    }

    @Test
    fun dinheiroNaoPedeConta() {
        show(base(form = RegisterForm(date = today, form = ExpenseForm.Cash)))
        assertEquals(0, compose.onAllNodes(hasText("Conta")).fetchSemanticsNodes().size)
    }

    @Test
    fun parceladoPerguntaSeOValorETotalOuParcela() {
        show(base(form = RegisterForm(date = today, installmentsText = "6", moreOptions = true), errors = mapOf(FormField.AmountMeaning to FormError.MeaningRequired)))
        see("O valor é total ou de cada parcela?")
        see("Total da compra")
        see("Valor de cada parcela")
        see("Escolha se o valor é o total ou o de cada parcela.")
    }

    @Test
    fun maisOpcoesRevelamParcelasERecorrencia() {
        show(base())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mais opções", substring = true))
        compose.onNodeWithText("Mais opções").performClick()
        // "Parcelas" também aparece no título da seção e na dica do botão: o campo é o que aceita texto
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Parcelas") and hasSetTextAction())
        compose.onNode(hasText("Parcelas") and hasSetTextAction()).assertIsDisplayed()
        see("Todo mês")
        see("Todo ano")
    }

    @Test
    fun recorrenciaEscondeParcelasEExplicaQueNaoTemTermino() {
        show(base(form = RegisterForm(date = today, moreOptions = true)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Todo mês"))
        compose.onNodeWithText("Todo mês").performClick()
        see("Sem data de término.")
        assertEquals(0, compose.onAllNodes(hasText("Parcelas")).fetchSemanticsNodes().size)
    }

    @Test
    fun faturaEstimadaMostraOMesEOSeloAConferirQuandoNaJanelaDoFechamento() {
        val preview = listOf(InstallmentView(1, brl(68), YearMonth.of(2026, 11), AssignmentState.ToConfirm))
        show(base(preview = preview))
        see("Fatura estimada: Novembro/2026 · a conferir")
        see("Corrigir")
    }

    @Test
    fun faturaEstimadaSemMarcaQuandoForaDaJanela() {
        val preview = listOf(InstallmentView(1, brl(68), YearMonth.of(2026, 11), AssignmentState.Estimated))
        show(base(preview = preview))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Fatura estimada", substring = true))
        compose.onNodeWithText("Fatura estimada: Novembro/2026").assertIsDisplayed()
    }

    @Test
    fun cronogramaDeParcelasMostraValorEFaturaDeCada() {
        val preview = (1..6).map { InstallmentView(it, brl(200), YearMonth.of(2026, 10).plusMonths(it - 1L), AssignmentState.Estimated) }
        show(base(form = RegisterForm(date = today, installmentsText = "6"), preview = preview))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("1/6", substring = true))
        compose.onNodeWithText("1/6 · ${brl(200).format()} · Outubro/2026").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("6/6", substring = true))
        compose.onNodeWithText("6/6 · ${brl(200).format()} · Março/2027").assertIsDisplayed()
    }

    @Test
    fun faturaInformadaPelaPessoaAparecePeloMesEOferecaVoltarParaAEstimada() {
        val preview = listOf(InstallmentView(1, brl(68), YearMonth.of(2026, 12), AssignmentState.Confirmed))
        show(base(form = RegisterForm(date = today, invoice = YearMonth.of(2026, 12)), preview = preview))
        see("Fatura informada: Dezembro/2026")
        see("Usar a estimada")
    }

    @Test
    fun boletoPagoEmOutroMesMostraAMudancaDeCompetencia() {
        show(
            base(
                form = RegisterForm(
                    date = LocalDate.of(2026, 10, 30), form = ExpenseForm.Boleto, settledDate = LocalDate.of(2026, 11, 3), moreOptions = true,
                ),
            ),
        )
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Previsão de", substring = true))
        compose.onNodeWithText("Previsão de Outubro/2026 → pago em Novembro/2026").assertIsDisplayed()
    }

    @Test
    fun rendaRecebidaNoMesmoMesNaoMostraMudanca() {
        show(base(form = RegisterForm(kind = FormKind.Income, date = today, settledDate = today, moreOptions = true)))
        assertEquals(0, compose.onAllNodes(hasText("Previsão de", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun salvandoBloqueiaOSegundoToqueEMostraSalvando() {
        show(base(phase = RegisterPhase.Saving))
        compose.onNodeWithText("Salvando…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun salvarChamaOCallbackUmaVez() {
        val calls = show(base())
        compose.onNodeWithText("Salvar lançamento").assertIsEnabled().performClick()
        assertEquals(1, calls.saves)
    }

    @Test
    fun saidaSemSalvarOfereceContinuarGuardarRascunhoOuDescartar() {
        val calls = show(base(askBack = true))
        compose.onNodeWithText("Sair sem salvar?").assertIsDisplayed()
        compose.onNodeWithText("Continuar editando").performClick()
        compose.onNodeWithText("Guardar rascunho").performClick()
        compose.onNodeWithText("Descartar").performClick()
        assertEquals(1, calls.keep)
        assertEquals(1, calls.draft)
        assertEquals(1, calls.discard)
    }

    @Test
    fun rascunhoRecuperadoAvisaEPermiteDescartar() {
        val calls = show(base(recovered = true, form = RegisterForm(date = today, description = "Mercado")))
        compose.onNodeWithText("Rascunho recuperado. Confira os campos antes de salvar.").assertIsDisplayed()
        compose.onNodeWithText("Descartar rascunho").performClick()
        assertEquals(1, calls.dismissRecovered)
    }

    @Test
    fun voltarPeloCabecalhoChamaOCallback() {
        val calls = show(base())
        compose.onNode(hasContentDescription("Voltar")).performClick()
        assertEquals(1, calls.backs)
    }

    @Test
    fun erroGeralNaoApagaOQueFoiDigitado() {
        show(base(form = RegisterForm(date = today, description = "Mercado", amountText = "50,00"), general = "Falha inesperada."))
        compose.onNodeWithText("Falha inesperada.").assertIsDisplayed()
        compose.onNodeWithText("Mercado").assertIsDisplayed()
        assertTrue(true)
    }
}
