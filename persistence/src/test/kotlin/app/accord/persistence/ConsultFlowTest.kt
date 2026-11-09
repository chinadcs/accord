package app.accord.persistence

import app.accord.core.Money
import app.accord.core.analysis.ProductPhase
import app.accord.core.chat.ChatQueryResult
import app.accord.core.chat.ConsultAskNotice
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.chat.InterpretRequest
import app.accord.core.chat.Interpreter
import app.accord.core.chat.MessageState
import app.accord.core.chat.RuleInterpreter
import app.accord.core.chat.StandardReply
import app.accord.core.space.AmountMeaning
import app.accord.core.space.Classification
import app.accord.core.space.DateCriterion
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntryFilter
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S6.6 (cenário 15): consulta pelo Chat com a fachada real em Room, o interpretador por regras e o relógio de 22/10/2026. O número vem da
 * mesma fachada das telas; a consulta não escreve nada. Dados fictícios.
 */
class ConsultFlowTest : ChatTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val modelCalls = AtomicInteger()

    private fun consultEnv(): Env = env(phase = ProductPhase.P1).apply {
        val rules = RuleInterpreter()
        interpretation = Interpreter { request: InterpretRequest -> modelCalls.incrementAndGet(); rules.interpret(request) }
    }

    private suspend fun Env.pix(description: String, reais: Int, category: String, date: LocalDate) = handle.space.execute(
        handle.env(),
        RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(reais)),
    ).applied()

    private suspend fun Env.card(description: String, reais: Int, installments: Int, category: String, date: LocalDate, invoice: YearMonth) =
        handle.space.execute(
            handle.env(),
            RegisterCardPurchase(
                description, Classification.Category(seed(category)), date, brl(reais),
                if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
            ),
        ).applied()

    private suspend fun Env.snapshotOf(messageId: String) = assertNotNull(ConsultSnapshot.fromFields(message(messageId).fields))

    private suspend fun Env.seedFood() {
        pix("Padaria", 300, "alimentacao", d(2026, 10, 5))
        card("Mercado do mês", 680, 1, "alimentacao", d(2026, 10, 10), oct)
    }

    @Test
    fun `margem no Chat usa mesmo orcamento real inclui parcela uma vez e nao grava`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood() // 980 reais: 300 pagos + 680 no cartão; outubro 22 tem 10 dias restantes.
        e.handle.space.execute(e.handle.env(), app.accord.core.space.DefineLimits(
            oct, app.accord.core.LimitScope.OnlyThisMonth,
            listOf(app.accord.core.space.CategoryLimitEdit(seed("alimentacao"),
                app.accord.core.LimitEdit(app.accord.core.TotalChange.Set(brl(1980))))),
        )).applied()
        // Categoria sem limite não reduz os 1000 reais restantes da categoria limitada.
        e.pix("Outra categoria", 900, "transporte", d(2026, 10, 21))
        val operations = e.space.operations().value().size
        val changes = e.financeChanges
        val budget = e.space.budget(oct).value()
        val expected = app.accord.core.spendingAllowance(budget, d(2026, 10, 22))
        assertEquals(100_000L, expected.remainingCents)
        assertEquals(10_000L, expected.todayCents)
        assertEquals(40_000L, expected.weekCents)
        for ((text, kind) in listOf("quanto posso gastar hoje?" to ChatQueryResult.Kind.TodayAllowance,
            "quanto posso gastar esta semana?" to ChatQueryResult.Kind.WeekAllowance)) {
            val message = e.say(text)
            val snapshot = e.snapshotOf(message.id)
            assertEquals(kind, snapshot.result?.kind)
            assertEquals(expected, snapshot.result?.allowance)
            assertEquals(snapshot, ConsultSnapshot.fromFields(ConsultSnapshot.toFields(snapshot)))
            // Atualizar histórico no mês seguinte usa o relógio atual, sem repetir o mês antigo.
            val nextClock = app.accord.core.BudgetClock.of(java.time.Clock.fixed(
                Instant.parse("2026-11-01T15:00:00Z"), java.time.ZoneOffset.UTC))
            val refreshed = assertIs<app.accord.core.chat.ConsultOutcome.Result>(
                app.accord.core.chat.ChatQueryRunner(e.space, nextClock).run(snapshot.request())).result
            assertEquals("2026-11", refreshed.month)
            assertEquals("2026-11-01", refreshed.allowance?.date)
            assertNull(refreshed.allowance?.todayCents) // O limite era só em outubro.
        }
        assertEquals(operations, e.space.operations().value().size)
        assertEquals(changes, e.financeChanges)
        assertTrue(e.space.drafts().value().isEmpty())
    }

    @Test
    fun `gasto de alimentacao em outubro devolve o mesmo numero do orcamento e nao escreve nada`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        val operations = e.space.operations().value().size
        val financeChanges = e.financeChanges

        val message = e.say("quanto gastei com alimentação em outubro?")

        assertEquals(MessageState.Replied, message.state)
        assertEquals(StandardReply.Consult, message.reply)
        val snapshot = e.snapshotOf(message.id)
        val result = assertNotNull(snapshot.result)
        assertEquals(ChatQueryResult.Kind.Spend, result.kind)
        assertEquals(98_000L, result.totalCents)
        assertEquals(30_000L, result.paidCents)
        assertEquals("Alimentação", result.label)
        assertEquals("2026-10", result.month)
        assertTrue(result.criterionAssumed && !result.periodAssumed)

        // Mesmo número da tela 05 e da lista da tela 07 com os mesmos filtros (link "Ver lançamentos").
        val budget = e.space.budget(oct).value().categories.single { it.categoryId == seed("alimentacao") }
        assertEquals(budget.net.cents, result.totalCents)
        assertEquals(budget.paid.cents, result.paidCents)
        val listed = e.space.entries(EntryFilter(from = oct, to = oct, categoryId = seed("alimentacao"))).value().summary
        assertEquals(Money(result.totalCents!!), listed.committedExpenses)

        // Leitura pura: nenhuma operação na fila, nenhuma mudança financeira, nenhum rascunho, nenhuma pendência nova.
        assertEquals(operations, e.space.operations().value().size)
        assertEquals(financeChanges, e.financeChanges)
        assertTrue(e.space.drafts().value().isEmpty())
        assertTrue(e.space.pendings().value().isEmpty())
    }

    @Test
    fun `atualizar recalcula depois de um novo registro sem chamar o interpretador`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        val message = e.say("quanto gastei com alimentação em outubro?")
        val before = assertNotNull(e.snapshotOf(message.id).result).totalCents
        val calls = modelCalls.get()

        e.pix("Feira", 50, "alimentacao", d(2026, 10, 12))
        e.coordinator.refreshQuery(message.id)

        assertEquals(before!! + 5_000L, assertNotNull(e.snapshotOf(message.id).result).totalCents)
        assertEquals(calls, modelCalls.get())
        assertEquals(StandardReply.Consult, e.message(message.id).reply)
    }

    @Test
    fun `sem periodo assume o mes corrente e a resposta diz o criterio assumido`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        val result = assertNotNull(e.snapshotOf(e.say("quanto gastei com alimentação?").id).result)
        assertTrue(result.periodAssumed && result.criterionAssumed)
        assertEquals("2026-10", result.month)
        assertEquals(DateCriterion.BudgetMonth.name, result.criterion)
    }

    @Test
    fun `por data da compra usa a atividade e a compra parcelada conta uma vez`() = runBlocking<Unit> {
        val e = consultEnv()
        e.card("Equipamento", 1200, 6, "eletronicos", d(2026, 9, 25), oct)
        val result = assertNotNull(
            e.snapshotOf(e.say("quanto comprei de eletrônicos em setembro pela data da compra?").id).result,
        )
        assertEquals(DateCriterion.PurchaseDate.name, result.criterion)
        assertEquals(120_000L, result.totalCents)
        assertEquals(1, result.count)
        assertTrue(!result.criterionAssumed)
    }

    @Test
    fun `cronograma de uma compra parcelada calculado pelo app`() = runBlocking<Unit> {
        val e = consultEnv()
        e.card("Equipamento", 1200, 6, "eletronicos", d(2026, 9, 25), oct)
        val result = assertNotNull(e.snapshotOf(e.say("quanto falta do equipamento?").id).result)
        assertEquals(ChatQueryResult.Kind.Schedule, result.kind)
        assertEquals("Equipamento", result.label)
        assertEquals(120_000L, result.totalCents)
        assertEquals(6, result.lines.size)
        assertTrue(result.lines.all { it.cents == 20_000L })
        // Meses depois de outubro/2026: 5 × R$ 200,00, sem afirmar situação de pagamento.
        assertEquals(100_000L, result.laterCents)
        assertEquals(5, result.laterCount)
        assertEquals("2026-10", result.lines.first().month)
        assertEquals("2027-03", result.lines.last().month)
    }

    @Test
    fun `duas compras com o mesmo nome perguntam qual e a opcao enviada resolve`() = runBlocking<Unit> {
        val e = consultEnv()
        e.card("Notebook", 3000, 3, "eletronicos", d(2026, 8, 10), YearMonth.of(2026, 9))
        e.card("Notebook", 600, 2, "eletronicos", d(2026, 10, 2), YearMonth.of(2026, 11))

        val asked = e.snapshotOf(e.say("quais são as parcelas do notebook?").id)
        assertEquals(ConsultSnapshot.Status.Ask, asked.status)
        assertNull(asked.result)
        assertEquals(ConsultAskNotice.Reason.AmbiguousPurchase.name, asked.askReason)
        assertEquals(2, asked.askOptions.size)

        val chosen = asked.askOptions.first { "10/08/2026" in it.text }
        val result = assertNotNull(e.snapshotOf(e.say(chosen.text).id).result)
        assertEquals(300_000L, result.totalCents)
        assertEquals(3, result.lines.size)
    }

    @Test
    fun `compromissos do mes seguinte vem da mesma consulta da tela 10`() = runBlocking<Unit> {
        val e = consultEnv()
        e.card("Equipamento", 1200, 6, "eletronicos", d(2026, 9, 25), oct)
        val result = assertNotNull(e.snapshotOf(e.say("como estão as parcelas do mês que vem?").id).result)
        val screen = e.space.commitments(YearMonth.of(2026, 11)).value()
        assertEquals(ChatQueryResult.Kind.Commitments, result.kind)
        assertEquals(screen.knownTotal.cents, result.totalCents)
        assertEquals(screen.items.size, result.count)
        assertEquals("2026-11", result.month)
    }

    @Test
    fun `categoria desconhecida ou ausente nunca vira numero`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        val message = e.say("quanto gastei este mês?")
        assertEquals(StandardReply.ConsultAsk, message.reply)
        val snapshot = e.snapshotOf(message.id)
        assertNull(snapshot.result)
        assertEquals(ConsultAskNotice.Reason.MissingCategory.name, snapshot.askReason)
        assertTrue(snapshot.askOptions.isNotEmpty())
        // Tocar numa opção reenvia a pergunta completa e responde com o número.
        val answered = e.say(snapshot.askOptions.first { it.label == "Alimentação" }.text)
        assertEquals(98_000L, assertNotNull(e.snapshotOf(answered.id).result).totalCents)
    }

    @Test
    fun `periodo que nao e mes e comparacao orientam sem numero`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        for (text in listOf("quanto gastei com alimentação na semana passada?", "quanto gastei com alimentação comparado com o mês passado?")) {
            val message = e.say(text)
            assertEquals(MessageState.Replied, message.state, text)
            assertTrue(message.reply == StandardReply.ConsultOutOfCatalog || message.reply == StandardReply.Query, "$text -> ${message.reply}")
            assertNull(ConsultSnapshot.fromFields(message.fields), text)
        }
    }

    @Test
    fun `mensagem com registro e pergunta juntos pede uma coisa por vez e nao grava nada`() = runBlocking<Unit> {
        val e = consultEnv()
        val before = e.space.operations().value().size
        for (text in listOf("gastei 50 no mercado, quanto sobrou?", "recebi 1000, quanto sobra?", "gastei 50 no mercado. Quanto já gastei esse mês?")) {
            val message = e.say(text)
            assertEquals(MessageState.Replied, message.state, text)
            assertEquals(StandardReply.MixedRequest, message.reply, text)
            assertNull(ConsultSnapshot.fromFields(message.fields), text)
        }
        assertEquals(before, e.space.operations().value().size)
        assertTrue(e.space.entries(EntryFilter(from = oct, to = oct)).value().rows.isEmpty())
        assertTrue(e.space.drafts().value().isEmpty())
        assertTrue(e.space.pendings().value().isEmpty())
    }

    @Test
    fun `no P0 a pergunta continua com a resposta padrao e atalhos`() = runBlocking<Unit> {
        val e = env(Instant.parse("2026-10-22T15:00:00Z")).apply { interpretation = RuleInterpreter() }
        val message = e.say("quanto gastei com alimentação em outubro?")
        assertEquals(StandardReply.Query, message.reply)
        assertNull(ConsultSnapshot.fromFields(message.fields))
    }

    @Test
    fun `a consulta fica so no historico local e nao e sincronizada`() = runBlocking<Unit> {
        val e = consultEnv()
        e.seedFood()
        val operations = e.space.operations().value()
        e.say("quanto gastei com alimentação em outubro?")
        assertEquals(operations.size, e.space.operations().value().size)
        assertIs<ConsultSnapshot>(e.store.snapshot().messages.last { it.reply == StandardReply.Consult }.let { ConsultSnapshot.fromFields(it.fields) })
    }
}
