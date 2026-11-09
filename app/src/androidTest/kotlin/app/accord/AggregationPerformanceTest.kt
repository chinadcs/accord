package app.accord

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.chat.ChatQueryRunner
import app.accord.core.chat.ConsultOutcome
import app.accord.core.chat.ConsultRequest
import app.accord.core.chat.ConsultType
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryNode
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.DateCriterion
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.EntryFilter
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Desempenho das agregações do S6 com histórico longo (`accord-vn2.7`, bloco 3): gera 36 meses de lançamentos fictícios no banco real do
 * aparelho (Pix, compras no cartão e compras parceladas), cronometra as consultas das telas 05, 07, 10, 29, 30, 33 e a consulta do Chat e
 * grava o relatório em `Log` (tag ACCORD_PERF) e em `files/accord-perf.txt` do app. Não define limiar: só mede.
 *
 * **Apaga o banco do app de debug** (`accord-…db`) antes de gerar os dados. Por isso só roda com
 * `-Pandroid.testInstrumentationRunnerArguments.accord.perf=true`, e nunca num aparelho cujos dados do app de debug importem. O volume é uma
 * proposta (ver `docs/validacao/s6-validacao-aparelhos.md`), não um número da spec. Emulador serve para conferir o teste, não para o resultado.
 */
@RunWith(AndroidJUnit4::class)
class AggregationPerformanceTest {
    private val months = 36
    private val pixPerMonth = 100
    private val cardPerMonth = 30
    private val installmentPurchasesPerMonth = 6
    private val runs = 10

    private val lines = mutableListOf<String>()
    private fun note(text: String) { lines += text; Log.i("ACCORD_PERF", text) }

    private fun <T> QueryResult<T>.data(what: String): T {
        assertTrue("$what falhou: $this", this is QueryResult.Data)
        return (this as QueryResult.Data).value
    }

    /** Primeira execução (fria) e mediana/pior das seguintes, em milissegundos. */
    private suspend fun measure(name: String, block: suspend () -> Unit) {
        val cold = time(block)
        val warm = List(runs) { time(block) }.sorted()
        note("%-52s fria %7.1f ms | mediana %7.1f ms | pior %7.1f ms".format(name, cold, warm[warm.size / 2], warm.last()))
    }

    private suspend fun time(block: suspend () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }

    @Test
    fun mede_as_agregacoes_com_36_meses_de_historico() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Passe -Pandroid.testInstrumentationRunnerArguments.accord.perf=true (apaga o banco do app de debug)", args.getString("accord.perf") == "true")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val existingFixture = args.getString("accord.perf.existing") == "true"
        if (existingFixture) check(context.packageName == "app.accord.uivalidation")
        if (!existingFixture) context.deleteDatabase(AppContainer.DATABASE_NAME)
        val container = DevIdentity.container(context)
        val space = container.space()
        val clock = BudgetClock.system()
        val today = clock.today()
        val current = YearMonth.from(today)

        note("Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        note("Data da medição: $today; $months meses até ${current}; $pixPerMonth Pix + $cardPerMonth cartão + $installmentPurchasesPerMonth parceladas por mês; $runs execuções quentes")

        // ---- dados ----
        if (existingFixture) {
            // Recupera só a medição após o executor expirar: nunca confunde uma base parcial com a completa.
            assertTrue("Fixture completa de 4.896 comandos + conta/padrão ausente",
                container.access.runtime.value!!.syncStore.summary().awaitingOperations == months * (pixPerMonth + cardPerMonth + installmentPurchasesPerMonth) + 2)
            assertTrue(space.movements(current).data("movimentos atuais").size == pixPerMonth)
            assertTrue(space.movements(current.minusMonths(35)).data("movimentos antigos").size == pixPerMonth)
            note("Base completa existente reutilizada; geração não repetida (mesmo volume de 36 meses).")
        } else {
        val account = (space.execute(Envelope(CommandId("perf-acc")), AddAccount(BankRef.Other("Banco"), "Conta")) as CommandResult.Applied).receipt.changes.single().id
        space.execute(Envelope(CommandId("perf-def")), SetMyDefaultAccount(account))
        val leaves = space.categories().data("categorias").filter { it.seedKey != null && it.parentId != null }
        check(leaves.size >= 5) { "esperava subcategorias iniciais" }
        val random = Random(42)
        fun pick(): CategoryNode = leaves[random.nextInt(leaves.size)]
        var counter = 0
        val seedStart = System.nanoTime()
        for (offset in months - 1 downTo 0) {
            val month = current.minusMonths(offset.toLong())
            fun day() = month.atDay(1 + random.nextInt(month.lengthOfMonth().coerceAtMost(if (month == current) today.dayOfMonth else 28)))
            repeat(pixPerMonth) {
                space.execute(
                    Envelope(CommandId("perf-${counter++}")),
                    RegisterDirectPayment(DirectPaymentKind.Pix, "Compra ${counter}", Classification.Category(pick().id), day(), Money(500L + random.nextInt(30_000))),
                )
            }
            repeat(cardPerMonth) {
                space.execute(
                    Envelope(CommandId("perf-${counter++}")),
                    RegisterCardPurchase("Cartão ${counter}", Classification.Category(pick().id), day(), Money(1_000L + random.nextInt(40_000))),
                )
            }
            repeat(installmentPurchasesPerMonth) {
                space.execute(
                    Envelope(CommandId("perf-${counter++}")),
                    RegisterCardPurchase(
                        "Parcelado ${counter}", Classification.Category(pick().id), day(), Money(30_000L + random.nextInt(300_000)),
                        AmountMeaning.Total, 3 + random.nextInt(10),
                    ),
                )
            }
        }
        note("Dados gerados: $counter comandos em %.1f s (não é medida do produto)".format((System.nanoTime() - seedStart) / 1e9))
        }

        // ---- consultas ----
        val food = space.categories().data("categorias").first { it.seedKey == "alimentacao" }
        measure("Orçamento do mês (tela 05)") { space.budget(current).data("budget") }
        measure("Orçamento de 12 meses atrás (tela 05)") { space.budget(current.minusMonths(12)).data("budget") }
        measure("Lançamentos do mês, 1ª página (tela 07)") { space.entries(EntryFilter(current, current)).data("entries") }
        measure("Lançamentos de 12 meses, 1ª página (tela 07)") { space.entries(EntryFilter(current.minusMonths(11), current)).data("entries") }
        measure("Compromissos do mês (tela 10)") { space.commitments(current).data("commitments") }
        val start3 = current.minusMonths(2).atDay(1)
        val start12 = current.minusMonths(11).atDay(1)
        val start36 = current.minusMonths(35).atDay(1)
        val end = current.plusMonths(1).atDay(1)
        measure("Atividade de compras, 3 meses (tela 30)") { space.purchaseActivity(start3, end).data("atividade") }
        measure("Atividade de compras, 12 meses (tela 30)") { space.purchaseActivity(start12, end).data("atividade") }
        measure("Atividade de compras, 36 meses (tela 30)") { space.purchaseActivity(start36, end).data("atividade") }
        measure("Atividade de compras, 12 meses, só Alimentação") { space.purchaseActivity(start12, end, food.id).data("atividade") }
        val a12 = ComparisonPeriod(start12, end)
        val b12 = ComparisonPeriod(start36, start12)
        for (criterion in DateCriterion.entries) for (grouping in ComparisonGrouping.entries) {
            measure("Comparação 12 x 12 meses, $criterion, $grouping (tela 33)") { space.comparePeriods(criterion, grouping, a12, b12).data("comparação") }
        }
        measure("Comparação mês x mês, orçamento, categoria (tela 33)") {
            space.comparePeriods(DateCriterion.BudgetMonth, ComparisonGrouping.Category, ComparisonPeriod(current.atDay(1), end), ComparisonPeriod(current.minusMonths(1).atDay(1), current.atDay(1))).data("comparação")
        }
        measure("Revisão semanal (tela 29)") { space.weeklyReview(ReviewPeriod.lastCompleteWeek(clock.now()), today).data("revisão") }
        val runner = ChatQueryRunner(space, clock)
        val request = ConsultRequest(ConsultType.CategorySpend, current, false, DateCriterion.BudgetMonth, true, categoryId = food.id)
        measure("Consulta do Chat: gasto de Alimentação no mês") { check(runner.run(request) is ConsultOutcome.Result) }
        measure("Consulta do Chat: compromissos do mês") {
            check(runner.run(ConsultRequest(ConsultType.MonthCommitments, current, false, DateCriterion.BudgetMonth, true)) is ConsultOutcome.Result)
        }

        File(context.getExternalFilesDir(null), "accord-perf.txt").writeText(lines.joinToString("\n") + "\n")
    }
}
