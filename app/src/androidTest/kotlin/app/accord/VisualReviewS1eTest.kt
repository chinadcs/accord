package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.RecurrenceScope
import app.accord.core.space.AccountInfo
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.CancelState
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.Classification
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.CommitmentNature
import app.accord.core.space.EntrySituation
import app.accord.core.space.IncomeSummary
import app.accord.core.space.IncomeView
import app.accord.core.space.InstallmentView
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.InvoiceView
import app.accord.core.space.MonthCommitments
import app.accord.core.space.OccurrenceView
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PaymentView
import app.accord.core.space.PurchaseDetail
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceView
import app.accord.core.space.RefundStatus
import app.accord.core.space.ScheduleRow
import app.accord.ui.card.CardContent
import app.accord.ui.card.CardData
import app.accord.ui.card.CardEditState
import app.accord.ui.card.CardForm
import app.accord.ui.card.CardScreen
import app.accord.ui.card.InvoiceContent
import app.accord.ui.card.InvoiceDialog
import app.accord.ui.card.InvoiceDialogState
import app.accord.ui.card.InvoiceScreen
import app.accord.ui.commitments.CommitmentDetailContent
import app.accord.ui.commitments.CommitmentDetailScreen
import app.accord.ui.commitments.CommitmentsContent
import app.accord.ui.commitments.CommitmentsData
import app.accord.ui.commitments.CommitmentsScreen
import app.accord.ui.commitments.CommitmentsUiState
import app.accord.ui.detail.LinkedActionDialog
import app.accord.ui.detail.LinkedAction
import app.accord.ui.detail.LinkedForm
import app.accord.ui.detail.LinkedState
import app.accord.ui.edit.EditContent
import app.accord.ui.edit.EditEntryScreen
import app.accord.ui.edit.EditForm
import app.accord.ui.edit.EditOriginal
import app.accord.ui.edit.EditTarget
import app.accord.ui.edit.EditUiState
import app.accord.ui.incomes.IncomesContent
import app.accord.ui.incomes.IncomesScreen
import app.accord.ui.incomes.IncomesUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * Imagens das telas da etapa S1.8d/e com dados sintéticos, para revisão visual manual (claro, escuro,
 * texto ampliado). Não afirma nada e não substitui a revisão nos Galaxy S20 FE e S23 FE.
 */
class VisualReviewS1eTest {
    @get:Rule
    val compose = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private fun brl(reais: Int) = Money(reais * 100L)

    private val categories = listOf(
        CategoryNode("eletro", null, "Eletrônicos", 0, "laptop", null),
        CategoryNode("equip", "eletro", "Equipamentos", 0, "laptop", null),
        CategoryNode("saude", null, "Saúde", 1, "heart-pulse", null),
        CategoryNode("cons", "saude", "Consultas", 0, "stethoscope", null),
    ).associateBy { it.id }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Spec(val theme: ThemePreference, val fontScale: Float, val content: @Composable () -> Unit)

    private var spec by mutableStateOf<Spec?>(null)
    private var started = false

    /** [window]: captura a tela inteira pelo UiAutomator, porque diálogos vivem em outra janela. */
    private fun capture(name: String, theme: ThemePreference, fontScale: Float = 1f, window: Boolean = false, content: @Composable () -> Unit) {
        spec = Spec(theme, fontScale, content)
        if (!started) {
            started = true
            compose.setContent {
                spec?.let { s ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, s.fontScale)) {
                        AccordTheme(s.theme) { Surface(color = MaterialTheme.colorScheme.background) { s.content() } }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (window) {
            Thread.sleep(600)
            save(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        } else {
            save(name, compose.onRoot().captureToImage().asAndroidBitmap())
        }
    }

    private fun both(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        capture("${name}_claro", ThemePreference.Light, fontScale, content = content)
        capture("${name}_escuro", ThemePreference.Dark, fontScale, content = content)
    }

    // ---- compromissos ----

    private val commitments = CommitmentsData(
        MonthCommitments(
            nov,
            listOf(
                Commitment("p#2", CommitmentNature.Finite, "Equipamento", "equip", brl(200), LocalDate.of(2026, 11, 15), 2, 6, setOf(EntrySituation.NotPaid), PaymentMethod.Card),
                Commitment("s:3", CommitmentNature.Recurring, "Streaming", "cons", Money(3_990), LocalDate.of(2026, 11, 20), 3, null, setOf(EntrySituation.NotPaid), PaymentMethod.Card),
                Commitment("b1", CommitmentNature.Bill, "Conta de luz", "cons", brl(180), LocalDate.of(2026, 11, 10), null, null, setOf(EntrySituation.Overdue), PaymentMethod.Boleto),
            ),
            brl(200) + Money(3_990) + brl(180),
        ),
        categories,
    )

    @Test
    fun compromissos() {
        both("s1e_compromissos") {
            CommitmentsScreen(CommitmentsUiState(nov, content = CommitmentsContent.Ready(commitments)), PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {})
        }
        capture("s1e_compromissos_fonte150", ThemePreference.Light, 1.5f) {
            CommitmentsScreen(CommitmentsUiState(nov, content = CommitmentsContent.Ready(commitments)), PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    private val purchase = PurchaseDetail(
        "p", "Equipamento", Classification.Category("equip"), LocalDate.of(2026, 9, 25), brl(1200), false, 1, false,
        (1..6).map { InstallmentView(it, brl(200), YearMonth.of(2026, 9).plusMonths(it.toLong()), AssignmentState.Estimated, if (it == 4) CancelState.CancelRequested else CancelState.Active) },
    )

    @Test
    fun detalheDoCompromisso() {
        val schedule = purchase.installments.map {
            ScheduleRow(it.number, it.invoiceMonth, it.invoiceMonth.atDay(15), it.amount, setOfNotNull(
                if (it.number == 1) EntrySituation.Paid else EntrySituation.NotPaid,
                EntrySituation.CancelRequested.takeIf { _ -> it.cancel == CancelState.CancelRequested },
            ))
        }
        both("s1e_compromisso_parcelas") {
            CommitmentDetailScreen(
                CommitmentDetailContent.Ready(CommitmentDetail.Installments("p#1", purchase, schedule), categories), PaddingValues(), {}, {}, {}, {},
            )
        }
        val recurrence = RecurrenceView("s", RecurrenceKind.Expense, RecurrenceForm.Card, Frequency.Monthly, LocalDate.of(2026, 9, 20), null, 1, false)
        val occ = OccurrenceView("s", 3, RecurrenceKind.Expense, RecurrenceForm.Card, LocalDate.of(2026, 11, 20), "Streaming", "cons", Money(3_990), null, null, null, nov, false, AssignmentState.Estimated)
        both("s1e_compromisso_recorrente") {
            CommitmentDetailScreen(
                CommitmentDetailContent.Ready(CommitmentDetail.Recurring("s:3", recurrence, occ, occ.copy(number = 4, scheduledDate = LocalDate.of(2026, 12, 20))), categories),
                PaddingValues(), {}, {}, {}, {},
            )
        }
    }

    // ---- cartão e fatura ----

    private val card = CardInfo("c", BankRef.Catalog("60701190", "Itaú"), "Cartão principal", 8, 15)
    private fun invoice(month: YearMonth, known: Int, checked: Int?, status: InvoiceStatus, paid: Boolean = false) = InvoiceView(
        month, brl(known + 100), Money(0), brl(100), brl(known), checked?.let(::brl), checked?.let { InvoiceTotalSource.Person },
        checked == known, checked?.let { brl(it - known) }.takeIf { checked != known }, paid, LocalDate.of(2026, 11, 14).takeIf { paid },
        status, if (checked == null) 2 else 0, emptyList(), 1,
    )

    @Test
    fun cartao() {
        val data = CardData(card, listOf(invoice(YearMonth.of(2026, 10), 900, 900, InvoiceStatus.Closed, paid = true), invoice(nov, 1300, null, InvoiceStatus.ClosedEstimated)))
        both("s1e_cartao") { CardScreen(CardContent.Ready(data), null, PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {}) }
        capture("s1e_cartao_editor", ThemePreference.Light) {
            CardScreen(
                CardContent.Ready(data), CardEditState(CardForm("", null, "40", "15"), mapOf(app.accord.ui.card.CardField.Nickname to "Informe um apelido para o cartão.", app.accord.ui.card.CardField.Closing to "O dia de fechamento vai de 1 a 31.")),
                PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }

    @Test
    fun fatura() {
        val divergent = invoice(nov, 1300, 1280, InvoiceStatus.Closed).copy(divergentPayments = listOf(PaymentView("x", brl(500), LocalDate.of(2026, 11, 14), null, false)))
        both("s1e_fatura_divergente") { InvoiceScreen(nov, InvoiceContent.Ready(divergent, card), null, PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) }
        val conferred = invoice(nov, 1300, 1300, InvoiceStatus.Closed)
        capture("s1e_fatura_quitacao", ThemePreference.Light, window = true) {
            InvoiceScreen(
                nov, InvoiceContent.Ready(conferred, card), InvoiceDialogState(InvoiceDialog.Pay, "1.300,00", LocalDate.of(2026, 11, 14)),
                PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            )
        }
        capture("s1e_fatura_fonte150", ThemePreference.Light, 1.5f) {
            InvoiceScreen(nov, InvoiceContent.Ready(conferred, card), null, PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
    }

    // ---- rendas ----

    @Test
    fun rendas() {
        val items = listOf(
            IncomeView("i1", "Salário", brl(6000), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), brl(6000), null, nov, 1, false),
            IncomeView("i2", "Freelance", brl(500), LocalDate.of(2026, 11, 25), null, null, null, nov, 1, false),
            IncomeView("i3", "Bônus", brl(1000), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 30), brl(900), null, nov, 1, false),
        )
        val summary = IncomeSummary(brl(6900), brl(500), items)
        both("s1e_rendas") { IncomesScreen(IncomesUiState(nov, IncomesContent.Ready(summary)), PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
        capture("s1e_rendas_fonte150", ThemePreference.Light, 1.5f) {
            IncomesScreen(IncomesUiState(nov, IncomesContent.Ready(summary)), PaddingValues(), {}, {}, {}, {}, {}, {}, {})
        }
    }

    // ---- edição e ações vinculadas ----

    @Test
    fun edicao() {
        val original = EditOriginal("s", EditTarget.Occurrence, "Streaming", "cons", null, Money(3_990), null, 1, recurrenceId = "s", occurrence = 3)
        val state = EditUiState(
            content = EditContent.Ready(original), form = EditForm.of(original).copy(amountText = "44,90"), categories = categories,
            accounts = listOf(AccountInfo("a", BankRef.Other("Banco"), "Conta")), replacedOccurrences = listOf(5, 6),
        )
        both("s1e_edicao_recorrencia") { EditEntryScreen(state, PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {}) }
        val parcelada = EditOriginal("p", EditTarget.CardPurchase, "Equipamento", "equip", LocalDate.of(2026, 9, 25), brl(1200), null, 1, installments = 6)
        capture("s1e_edicao_parcelada", ThemePreference.Light) {
            EditEntryScreen(
                EditUiState(content = EditContent.Ready(parcelada), form = EditForm.of(parcelada).copy(date = LocalDate.of(2026, 10, 2)), categories = categories, suggestedInvoice = YearMonth.of(2026, 11)),
                PaddingValues(), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }

    @Test
    fun acoesVinculadas() {
        val refund = LinkedAction.NewRefund("p", brl(1200), 1)
        capture("s1e_estorno", ThemePreference.Light, window = true) {
            LinkedActionDialog(LinkedState(refund, LinkedForm(LocalDate.of(2026, 11, 14), "300,00", RefundStatus.Confirmed)), {}, {}, {})
        }
        capture("s1e_estorno_escuro_fonte150", ThemePreference.Dark, 1.5f, window = true) {
            LinkedActionDialog(LinkedState(refund, LinkedForm(LocalDate.of(2026, 11, 14), "300,00", RefundStatus.Confirmed), message = "O estorno não pode passar do valor da compra."), {}, {}, {})
        }
        val cancel = LinkedAction.Cancellation("p", listOf(2, 3, 4), nonCharge = false, revision = 1)
        capture("s1e_cancelamento", ThemePreference.Light, window = true) {
            LinkedActionDialog(LinkedState(cancel, LinkedForm(LocalDate.of(2026, 11, 14), selected = setOf(3))), {}, {}, {})
        }
    }

    @Test
    fun seletorDeBanco() {
        capture("s1e_banco", ThemePreference.Light, window = true) {
            app.accord.ui.banks.BankPickerDialog(initial = BankRef.Catalog("60701190", "Itaú"), onPick = {}, onDismiss = {})
        }
        capture("s1e_banco_escuro", ThemePreference.Dark, window = true) {
            app.accord.ui.banks.BankPickerDialog(initial = null, onPick = {}, onDismiss = {})
        }
    }

    @Suppress("unused")
    private val scope = RecurrenceScope.OnlyThis

    // ---- mais, contas, categorias e limites ----

    @Test
    fun mais() {
        val actions = app.accord.ui.more.MoreActions({}, {}, {}, {}, {}, {})
        both("s1f_mais") { app.accord.ui.more.MoreScreen(PaddingValues(), ThemePreference.System, {}, actions) }
        capture("s1f_mais_fonte150", ThemePreference.Light, 1.5f) { app.accord.ui.more.MoreScreen(PaddingValues(), ThemePreference.Dark, {}, actions) }
    }

    @Test
    fun contas() {
        val accounts = app.accord.core.space.AccountsView(
            listOf(AccountInfo("a", BankRef.Catalog("60701190", "Itaú"), "Conta salário"), AccountInfo("b", BankRef.Other("Banco Local"), "Reserva")), "a",
        )
        both("s1f_contas") {
            app.accord.ui.more.AccountsScreen(app.accord.ui.more.AccountsContent.Ready(accounts), null, PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
        capture("s1f_contas_editor", ThemePreference.Light) {
            app.accord.ui.more.AccountsScreen(
                app.accord.ui.more.AccountsContent.Ready(accounts),
                app.accord.ui.more.AccountForm(null, null, "", bankError = true, nicknameError = true), PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            )
        }
    }

    @Test
    fun categorias() {
        val content = app.accord.ui.categories.CategoriesContent.Ready(
            listOf(
                app.accord.ui.categories.MainCategory(categories.getValue("eletro"), listOf(categories.getValue("equip"))),
                app.accord.ui.categories.MainCategory(categories.getValue("saude"), listOf(categories.getValue("cons"))),
            ),
        )
        both("s1f_categorias") {
            app.accord.ui.categories.CategoriesScreen(content, null, setOf("eletro"), true, PaddingValues(), {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {})
        }
        val review = app.accord.ui.categories.CategoryDialog.ReviewParts(
            categories.getValue("cons"), "eletro",
            listOf(app.accord.ui.categories.PartAnswer(nov, brl(200), app.accord.ui.categories.PartChoice.Carry), app.accord.ui.categories.PartAnswer(nov.plusMonths(1), brl(250))),
        )
        capture("s1f_categorias_partes", ThemePreference.Light, window = true) {
            app.accord.ui.categories.CategoriesScreen(content, review, emptySet(), false, PaddingValues(), {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {})
        }
    }

    @Test
    fun limites() {
        val rows = listOf(
            app.accord.ui.limits.LimitRow(
                "eletro", "Eletrônicos", listOf(app.accord.ui.limits.LimitSub("equip", "Equipamentos")),
                app.accord.core.LimitSetting(brl(1200), mapOf("equip" to brl(400))), brl(980), listOf(YearMonth.of(2026, 12)),
            ),
            app.accord.ui.limits.LimitRow("saude", "Saúde", emptyList(), app.accord.core.LimitSetting.UNDEFINED, brl(150), emptyList()),
        )
        val draft = app.accord.ui.limits.LimitsDraft.of(rows).copy(
            totals = mapOf("eletro" to "1.000,00", "saude" to "300,00"),
            splits = mapOf("eletro" to mapOf("equip" to "400,00"), "saude" to emptyMap()),
        )
        val state = app.accord.ui.limits.LimitsUiState(
            nov, app.accord.ui.limits.LimitsContent.Ready(rows), draft,
            errors = mapOf(app.accord.ui.limits.LimitKey("eletro") to app.accord.ui.limits.LimitFieldError.SplitExceeds(brl(100))),
            scopeMissing = true, replacedMonths = listOf(YearMonth.of(2026, 12)),
        )
        both("s1f_limites") { app.accord.ui.limits.LimitsScreen(state, PaddingValues(), {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}) }
        capture("s1f_limites_resumo", ThemePreference.Light) {
            app.accord.ui.limits.LimitsScreen(
                state.copy(errors = emptyMap(), scopeMissing = false, draft = draft.copy(scope = app.accord.core.LimitScope.FromThisMonth)),
                PaddingValues(), {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {},
            )
        }
    }
}
