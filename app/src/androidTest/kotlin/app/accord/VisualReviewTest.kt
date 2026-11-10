package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeTotals
import app.accord.core.space.MonthBudget
import app.accord.core.space.ToClassifyLine
import app.accord.ui.budget.BudgetContent
import app.accord.ui.budget.BudgetData
import app.accord.ui.budget.BudgetScreen
import app.accord.ui.budget.BudgetUiState
import app.accord.ui.category.CategoryContent
import app.accord.ui.category.CategoryData
import app.accord.ui.category.CategoryScreen
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/**
 * Gera imagens das telas com dados sintéticos para revisão visual manual (tema claro e escuro,
 * texto ampliado). Não afirma nada: as imagens ficam em `files/screens` do app e são lidas por
 * uma pessoa. Não substitui a revisão nos Galaxy S20 FE e S23 FE.
 */
class VisualReviewTest {
    @get:Rule
    val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private fun brl(reais: Int) = Money(reais * 100L)

    private fun node(id: String, name: String, order: Int, icon: String, parent: String? = null) =
        CategoryNode(id, parent, name, order, icon, null)

    private val nodes = listOf(
        node("food", "Alimentação", 0, "utensils"),
        node("delivery", "Delivery", 0, "bike", "food"),
        node("market", "Mercado", 1, "shopping-cart", "food"),
        node("rest", "Restaurantes", 2, "chef-hat", "food"),
        node("shop", "Compras", 1, "shopping-bag"),
        node("pet", "Pet", 2, "paw-print"),
        node("fun", "Lazer", 3, "tree-palm"),
    ).associateBy { it.id }

    private fun view(total: Int?, net: Int, split: Map<String, Int> = emptyMap(), subNet: Map<String, Int> = emptyMap()) = LimitView.of(
        LimitSetting(total?.let(::brl), split.mapValues { brl(it.value) }), brl(net), subNet.mapValues { brl(it.value) },
    )

    private fun category(id: String, gross: Int, credits: Int, limit: LimitView, subs: Map<String?, Int> = emptyMap()) = CategoryBudget(
        categoryId = id, gross = brl(gross), credits = brl(credits), net = brl(gross - credits), paid = brl(gross / 3),
        bySubcategory = subs.mapValues { brl(it.value) }, limit = limit,
    )

    private val budget = MonthBudget(
        month = oct, committed = brl(3200), totalLimits = brl(4500), paid = brl(1000), toClassify = ToClassifyLine(brl(150), 1),
        credits = brl(40), invoiceLinksToConfirm = 2, income = IncomeTotals(brl(6000), brl(500)),
        categories = listOf(
            category("food", 980, 0, view(1200, 980), mapOf("delivery" to 280, "market" to 500, "rest" to 200)),
            category("shop", 640, 40, view(600, 600)),
            category("pet", 150, 0, view(null, 150)),
            category("fun", 0, 0, view(300, 0)),
        ),
    )

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** [scrollTo]: texto de um item a trazer para o topo antes de capturar (as telas são listas rolantes). */
    private fun capture(
        name: String,
        theme: ThemePreference,
        fontScale: Float = 1f,
        scrollTo: String? = null,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (UiAuditSyntheticEvidence.enabled) density else Density(density.density, fontScale)) {
                AccordTheme(UiAuditSyntheticEvidence.theme ?: theme) {
                    // Como no aplicativo, o Scaffold pinta o fundo; aqui a Surface faz esse papel.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        compose.waitForIdle()
        if (scrollTo != null) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(scrollTo, substring = true))
            compose.waitForIdle()
        }
        if (UiAuditSyntheticEvidence.enabled) UiAuditSyntheticEvidence.capture(compose, name, UiAuditSyntheticEvidence.theme ?: theme)
        else save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun budgetScreen(state: BudgetUiState) = @Composable {
        BudgetScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {})
    }

    @Test
    fun orcamentoClaro() = capture("orcamento-claro", ThemePreference.Light,
        content = budgetScreen(BudgetUiState(oct, BudgetContent.Ready(BudgetData(budget, nodes)))))

    @Test
    fun orcamentoClaroCategorias() = capture("orcamento-claro-categorias", ThemePreference.Light, scrollTo = "Pet",
        content = budgetScreen(BudgetUiState(oct, BudgetContent.Ready(BudgetData(budget, nodes)))))

    @Test
    fun orcamentoEscuro() = capture("orcamento-escuro", ThemePreference.Dark,
        content = budgetScreen(BudgetUiState(oct, BudgetContent.Ready(BudgetData(budget, nodes)))))

    @Test
    fun orcamentoEscuroCategorias() = capture("orcamento-escuro-categorias", ThemePreference.Dark, scrollTo = "Pet",
        content = budgetScreen(BudgetUiState(oct, BudgetContent.Ready(BudgetData(budget, nodes)))))

    @Test
    fun orcamentoTextoAmpliado() = capture("orcamento-texto-ampliado", ThemePreference.Light, fontScale = 2f,
        content = budgetScreen(BudgetUiState(oct, BudgetContent.Ready(BudgetData(budget, nodes)))))

    @Test
    fun orcamentoFalha() = capture("orcamento-falha", ThemePreference.Light,
        content = budgetScreen(BudgetUiState(YearMonth.of(2026, 11), BudgetContent.Failed(BudgetData(budget, nodes)))))

    @Test
    fun categoriaClaroEEscuro() {
        val food = budget.categories.first()
        val data = CategoryData(BudgetData(budget, nodes), food, nodes.getValue("food"))
        val screen = @Composable { CategoryScreen(CategoryContent.Ready(data), oct, PaddingValues(), {}, {}, { _, _ -> }) }
        capture("categoria-claro", ThemePreference.Light, content = screen)
    }

    @Test
    fun categoriaComDivisaoEscuro() {
        val split = view(1200, 980, split = mapOf("delivery" to 400, "market" to 500), subNet = mapOf("delivery" to 280, "market" to 500, "rest" to 200))
        val food = category("food", 980, 0, split, mapOf("delivery" to 280, "market" to 500, "rest" to 200, null to 0))
        val data = CategoryData(BudgetData(budget, nodes), food, nodes.getValue("food"))
        val screen = @Composable { CategoryScreen(CategoryContent.Ready(data), oct, PaddingValues(), {}, {}, { _, _ -> }) }
        capture("categoria-divisao-escuro", ThemePreference.Dark, content = screen)
    }

    // ---- Lançamentos e Detalhe ----

    private val entryNodes = nodes + mapOf(
        "elec" to node("elec", "Eletrônicos", 4, "monitor-smartphone"),
        "equip" to node("equip", "Equipamentos", 0, "laptop", "elec"),
    )

    private fun entryRow(
        id: String, description: String, amount: Int, category: String?, method: app.accord.core.space.PaymentMethod?,
        kind: app.accord.core.space.EntryKind = app.accord.core.space.EntryKind.DirectPayment,
        situations: Set<app.accord.core.space.EntrySituation> = setOf(app.accord.core.space.EntrySituation.Paid),
        number: Int? = null, count: Int? = null, day: Int = 20,
    ) = app.accord.core.space.EntryRow(
        id, kind, description, category, brl(amount), java.time.LocalDate.of(2026, 10, day), null, method, number, count, situations, "dev-person",
    )

    private val entriesState = app.accord.ui.entries.EntriesUiState(
        filter = app.accord.ui.entries.EntriesFilterState(oct, oct),
        rows = listOf(
            entryRow("p#1", "Equipamento", 200, "equip", app.accord.core.space.PaymentMethod.Card, app.accord.core.space.EntryKind.CardInstallment, setOf(app.accord.core.space.EntrySituation.NotPaid), 1, 6, 25),
            entryRow("a#1", "Amazon", 150, null, app.accord.core.space.PaymentMethod.Card, app.accord.core.space.EntryKind.CardInstallment, setOf(app.accord.core.space.EntrySituation.ToClassify, app.accord.core.space.EntrySituation.NotPaid), day = 22),
            entryRow("b1", "Luz", 120, "food", app.accord.core.space.PaymentMethod.Boleto, app.accord.core.space.EntryKind.Boleto, setOf(app.accord.core.space.EntrySituation.NotPaid, app.accord.core.space.EntrySituation.Overdue), day = 10),
            entryRow("m1", "Mercado", 50, "market", app.accord.core.space.PaymentMethod.Pix),
        ),
        total = 4,
        summary = app.accord.core.space.EntrySummary(brl(520), Money.ZERO, brl(40), Money.ZERO),
        status = app.accord.ui.entries.EntriesStatus.Ready,
        categories = entryNodes,
    )

    private fun entriesScreen(state: app.accord.ui.entries.EntriesUiState) = @Composable {
        app.accord.ui.entries.EntriesScreen(state, PaddingValues(), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun lancamentosClaro() = capture("lancamentos-claro", ThemePreference.Light, content = entriesScreen(entriesState))

    @Test
    fun lancamentosEscuroLista() = capture("lancamentos-escuro-lista", ThemePreference.Dark, scrollTo = "Luz", content = entriesScreen(entriesState))

    // Card "Saldo total" do Início (accord-7cj, movido em accord-wecg): acumulado de 1000 − (300 − 50) = 750, receita e despesa só do mês.
    private val balancePositive = app.accord.core.space.TotalBalance(
        app.accord.core.space.EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000)),
        app.accord.core.space.EntrySummary(brl(120), brl(400), brl(20), Money.ZERO, receivedIncomes = brl(400)),
    )
    private val balanceNegative = app.accord.core.space.TotalBalance(
        app.accord.core.space.EntrySummary(brl(1400), Money.ZERO, Money.ZERO, Money.ZERO, receivedIncomes = brl(900)),
        app.accord.core.space.EntrySummary(brl(520), Money.ZERO, brl(40), Money.ZERO, receivedIncomes = brl(300)),
    )

    private fun homeScreen(balance: app.accord.ui.home.HomeBalanceState) = @Composable {
        app.accord.ui.home.HomeScreen(PaddingValues(), app.accord.ui.pending.PendingQueueUiState(loading = false), {}, {}, balance = balance)
    }

    @Test
    fun inicioSaldoClaro() = capture("inicio-saldo-claro", ThemePreference.Light, content = homeScreen(app.accord.ui.home.HomeBalanceState.Ready(balancePositive)))

    @Test
    fun inicioSaldoEscuro() = capture("inicio-saldo-escuro", ThemePreference.Dark, content = homeScreen(app.accord.ui.home.HomeBalanceState.Ready(balancePositive)))

    @Test
    fun inicioSaldoNegativo() = capture("inicio-saldo-negativo", ThemePreference.Light, content = homeScreen(app.accord.ui.home.HomeBalanceState.Ready(balanceNegative)))

    @Test
    fun inicioSaldoTexto150() = capture("inicio-saldo-150", ThemePreference.Light, fontScale = 1.5f, content = homeScreen(app.accord.ui.home.HomeBalanceState.Ready(balancePositive)))

    @Test
    fun inicioCarregandoEsqueleto() = capture("inicio-esqueleto-claro", ThemePreference.Light, content = homeScreen(app.accord.ui.home.HomeBalanceState.Loading))

    @Test
    fun painelDeFiltros() = capture("filtros-claro", ThemePreference.Light) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(900.dp)) {
            app.accord.ui.entries.FilterPanel(entriesState.filter, entryNodes, {}, {})
        }
    }

    private fun detailData(): app.accord.ui.detail.DetailData {
        val installments = (1..6).map {
            app.accord.core.space.InstallmentView(
                it, brl(200), oct.plusMonths(it - 1L),
                if (it == 1) app.accord.core.space.AssignmentState.ToConfirm else app.accord.core.space.AssignmentState.Estimated,
                if (it >= 5) app.accord.core.space.CancelState.CancelRequested else app.accord.core.space.CancelState.Active,
            )
        }
        val purchase = app.accord.core.space.PurchaseDetail(
            "p", "Equipamento", app.accord.core.space.Classification.Category("equip"), java.time.LocalDate.of(2026, 9, 25),
            brl(1200), false, 3, false, installments,
        )
        val detail = app.accord.core.space.EntryDetail(
            "p#1", app.accord.core.space.EntryKind.CardInstallment, "Equipamento", app.accord.core.space.Classification.Category("equip"),
            brl(200), java.time.LocalDate.of(2026, 9, 25), java.time.LocalDate.of(2026, 10, 15), null,
            app.accord.core.space.PaymentMethod.Card, "acc", "dev-person", null, app.accord.core.space.Origin.Manual, 3, false,
            app.accord.core.space.ItemSync.SavedOnDevice, purchase, null, null, null,
            listOf(app.accord.core.space.RefundView("r", "p", brl(40), app.accord.core.space.RefundStatus.Confirmed, java.time.LocalDate.of(2026, 11, 5), java.time.YearMonth.of(2026, 11), 1, false)),
        )
        val accounts = mapOf("acc" to app.accord.core.space.AccountInfo("acc", app.accord.core.space.BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta salário"))
        return app.accord.ui.detail.DetailData(detail, entryNodes, accounts, "dev-person")
    }

    @Test
    fun detalheClaro() = capture("detalhe-claro", ThemePreference.Light) {
        app.accord.ui.detail.EntryDetailScreen(
            app.accord.ui.detail.DetailContent.Ready(detailData()), app.accord.ui.detail.DeletePhase.Idle, PaddingValues(), {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun detalheEscuroConfirmandoExclusao() = capture("detalhe-escuro-exclusao", ThemePreference.Dark) {
        app.accord.ui.detail.EntryDetailScreen(
            app.accord.ui.detail.DetailContent.Ready(detailData()), app.accord.ui.detail.DeletePhase.Confirming, PaddingValues(), {}, {}, {}, {}, {}, {},
        )
    }
}
