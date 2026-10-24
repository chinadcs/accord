package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.space.AddAccount
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.Decision
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.EditRecurrence
import app.accord.core.space.ErrorCode
import app.accord.core.space.PayOccurrence
import app.accord.core.space.PreviewResult
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceTest : SpaceTestBase() {
    private suspend fun Handle.withDefaultAccount(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private fun gym(
        amount: Int = 100,
        first: LocalDate = d(2026, 9, 5),
        form: RecurrenceForm = RecurrenceForm.OffCard,
        frequency: Frequency = Frequency.Monthly,
    ) = CreateRecurrence(
        kind = RecurrenceKind.Expense,
        description = "Academia",
        categoryId = seed("saude"),
        amount = brl(amount),
        form = form,
        frequency = frequency,
        firstDate = first,
    )

    private suspend fun Handle.create(command: CreateRecurrence = gym()) =
        space.execute(env(), command).applied().changes.single().id

    /** Valor da ocorrência da série que ocupa o mês; nulo se não há ocorrência. */
    private suspend fun Handle.amountIn(id: String, month: YearMonth): Money? =
        space.occurrences(month).value().singleOrNull { it.recurrenceId == id }
            ?.let { it.settledAmount ?: it.expectedAmount }

    // ---- série sem término ----

    @Test
    fun `recorrência mensal sem término aparece nos meses seguintes como comprometido ainda não pago`() = runBlocking {
        val h = open()
        val id = h.create()
        for (m in listOf(ym(2026, 9), ym(2026, 10), ym(2027, 6), ym(2030, 1))) {
            assertEquals(brl(100), h.amountIn(id, m), "mês $m")
        }
        assertNull(h.amountIn(id, ym(2026, 8)))
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), oct.committed)
        assertEquals(Money.ZERO, oct.paid)
        assertEquals(brl(100), oct.categories.single { it.categoryId == seed("saude") }.gross)
        assertEquals(RecurrenceKind.Expense, h.space.recurrence(id).value()!!.kind)
        assertNull(h.space.recurrence(id).value()!!.endsBefore)
    }

    @Test
    fun `mensal no dia 31 usa 30 de novembro e 28 de fevereiro e anual de 29 de fevereiro cai em 28`() = runBlocking {
        val h = open()
        val monthly = h.create(gym(first = d(2026, 10, 31)))
        val nov = h.space.occurrences(ym(2026, 11)).value().single { it.recurrenceId == monthly }
        assertEquals(d(2026, 11, 30), nov.scheduledDate)
        assertEquals(d(2027, 2, 28), h.space.occurrences(ym(2027, 2)).value().single { it.recurrenceId == monthly }.scheduledDate)

        val annual = h.create(gym(first = d(2028, 2, 29), frequency = Frequency.Annual))
        assertEquals(d(2029, 2, 28), h.space.occurrences(ym(2029, 2)).value().single { it.recurrenceId == annual }.scheduledDate)
        assertNull(h.amountIn(annual, ym(2029, 3)))
    }

    // ---- editar (cenário 16) ----

    @Test
    fun `só esta ocorrência em outubro para 120 mantém setembro e novembro em 100`() = runBlocking {
        val h = open()
        val id = h.create()
        h.space.execute(h.env(), EditRecurrence(id, 2, RecurrenceScope.OnlyThis, RecurrenceFields(amount = brl(120)))).applied()
        assertEquals(brl(100), h.amountIn(id, ym(2026, 9)))
        assertEquals(brl(120), h.amountIn(id, ym(2026, 10)))
        assertEquals(brl(100), h.amountIn(id, ym(2026, 11)))
    }

    @Test
    fun `esta e as próximas dá 100 120 120 e dezembro 120 numa só série`() = runBlocking {
        val h = open()
        val id = h.create()
        h.space.execute(h.env(), EditRecurrence(id, 2, RecurrenceScope.ThisAndNext, RecurrenceFields(amount = brl(120)))).applied()
        assertEquals(brl(100), h.amountIn(id, ym(2026, 9)))
        assertEquals(brl(120), h.amountIn(id, ym(2026, 10)))
        assertEquals(brl(120), h.amountIn(id, ym(2026, 11)))
        assertEquals(brl(120), h.amountIn(id, ym(2026, 12)))
        assertEquals(1, h.space.operations().value().count { it.commandType == "CreateRecurrence" })
    }

    @Test
    fun `sem escolher o alcance pede decisão e não altera nada`() = runBlocking {
        val h = open()
        val id = h.create()
        val before = h.space.operations().value().size
        val r = h.space.execute(h.env(), EditRecurrence(id, 2, null, RecurrenceFields(amount = brl(120))))
        assertEquals(Decision.ScopeRequired, assertIs<CommandResult.DecisionNeeded>(r).decision)
        assertEquals(before, h.space.operations().value().size)
        assertEquals(brl(100), h.amountIn(id, ym(2026, 10)))
        val del = h.space.execute(h.env(), DeleteRecurrence(id, 2, null))
        assertEquals(Decision.ScopeRequired, assertIs<CommandResult.DecisionNeeded>(del).decision)
    }

    @Test
    fun `aluguel com dezembro em 1600 e esta e as próximas de novembro para 1550 — a prévia lista dezembro`() = runBlocking {
        val h = open()
        val id = h.create(gym(1500))
        h.space.execute(h.env(), EditRecurrence(id, 4, RecurrenceScope.OnlyThis, RecurrenceFields(amount = brl(1600)))).applied()
        val command = EditRecurrence(id, 3, RecurrenceScope.ThisAndNext, RecurrenceFields(amount = brl(1550)))

        val preview = assertIs<PreviewResult.Ready>(h.space.preview(command))
        assertEquals(listOf(4), preview.replacedOccurrences)
        assertEquals(brl(1600), h.amountIn(id, ym(2026, 12))) // prévia não grava

        h.space.execute(h.env(), command).applied()
        assertEquals(brl(1500), h.amountIn(id, ym(2026, 10)))
        for (m in listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1))) assertEquals(brl(1550), h.amountIn(id, m))
    }

    @Test
    fun `corrigir categoria da série altera o orçamento das duas categorias a partir da ocorrência`() = runBlocking {
        val h = open()
        val id = h.create()
        h.space.execute(
            h.env(),
            EditRecurrence(id, 2, RecurrenceScope.ThisAndNext, RecurrenceFields(classificationId = seed("lazer"))),
        ).applied()
        val sep = h.space.budget(ym(2026, 9)).value()
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), sep.categories.single { it.categoryId == seed("saude") }.gross)
        assertEquals(Money.ZERO, oct.categories.single { it.categoryId == seed("saude") }.gross)
        assertEquals(brl(100), oct.categories.single { it.categoryId == seed("lazer") }.gross)
    }

    @Test
    fun `categoria inexistente na edição é rejeitada`() = runBlocking {
        val h = open()
        val id = h.create()
        val r = h.space.execute(h.env(), EditRecurrence(id, 2, RecurrenceScope.OnlyThis, RecurrenceFields(classificationId = "nada")))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    // ---- excluir ----

    @Test
    fun `excluir esta e as próximas a partir de novembro mantém setembro e outubro e encerra a mesma série`() = runBlocking {
        val h = open()
        val id = h.create()
        h.space.execute(h.env(), DeleteRecurrence(id, 3, RecurrenceScope.ThisAndNext)).applied()
        assertEquals(brl(100), h.amountIn(id, ym(2026, 9)))
        assertEquals(brl(100), h.amountIn(id, ym(2026, 10)))
        assertNull(h.amountIn(id, ym(2026, 11)))
        assertNull(h.amountIn(id, ym(2030, 1)))
        assertEquals(3, h.space.recurrence(id).value()!!.endsBefore)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 12)).value().committed)
    }

    @Test
    fun `excluir só esta em janeiro mantém dezembro e fevereiro`() = runBlocking {
        val h = open()
        val id = h.create()
        h.space.execute(h.env(), DeleteRecurrence(id, 5, RecurrenceScope.OnlyThis)).applied()
        assertEquals(brl(100), h.amountIn(id, ym(2026, 12)))
        assertNull(h.amountIn(id, ym(2027, 1)))
        assertEquals(brl(100), h.amountIn(id, ym(2027, 2)))
        assertEquals(6, h.space.occurrences(ym(2027, 2)).value().single().number)
    }

    // ---- ocorrências realizadas ----

    @Test
    fun `ocorrência paga no intervalo pede confirmação e depois da confirmação mantém o valor pago`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.create()
        h.space.execute(h.env(), PayOccurrence(id, 2, Settlement(d(2026, 10, 5), brl(100)))).applied()

        val edit = EditRecurrence(id, 2, RecurrenceScope.ThisAndNext, RecurrenceFields(amount = brl(120)))
        val r = h.space.execute(h.env(), edit)
        assertEquals(Decision.RealizedOccurrences(listOf(2)), assertIs<CommandResult.DecisionNeeded>(r).decision)
        assertEquals(brl(100), h.amountIn(id, ym(2026, 11)))

        h.space.execute(h.env(), edit.copy(confirmRealized = true)).applied()
        // a ocorrência paga mantém o valor efetivo; as seguintes seguem o novo valor
        assertEquals(brl(100), h.amountIn(id, ym(2026, 10)))
        assertEquals(brl(120), h.amountIn(id, ym(2026, 11)))
    }

    @Test
    fun `aluguel por Pix com vencimento em 5 de novembro pago em 30 de outubro por 1520`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.create(gym(1500, first = d(2026, 11, 5)))
        h.space.execute(h.env(), PayOccurrence(id, 1, Settlement(d(2026, 10, 30), brl(1520)))).applied()

        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(1520), oct.committed)
        assertEquals(brl(1520), oct.paid)
        val nov = h.space.occurrences(ym(2026, 11)).value()
        assertTrue(nov.none { it.recurrenceId == id && it.number == 1 })
        assertEquals(brl(1500), h.amountIn(id, ym(2026, 12)))
    }

    @Test
    fun `desfazer o pagamento da ocorrência devolve a previsão ao mês original`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.create(gym(1500, first = d(2026, 11, 5)))
        val paid = h.space.execute(h.env(), PayOccurrence(id, 1, Settlement(d(2026, 10, 30)))).applied()
        h.space.execute(h.env(), Undo(paid.operationId)).applied()
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(brl(1500), h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `pagar a mesma ocorrência duas vezes e pagar ocorrência inexistente são rejeitados`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.create()
        h.space.execute(h.env(), PayOccurrence(id, 2, Settlement(d(2026, 10, 5)))).applied()
        val twice = h.space.execute(h.env(), PayOccurrence(id, 2, Settlement(d(2026, 10, 6))))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(twice).errors.single().code)
        h.space.execute(h.env(), DeleteRecurrence(id, 4, RecurrenceScope.OnlyThis)).applied()
        val gone = h.space.execute(h.env(), PayOccurrence(id, 4, Settlement(d(2026, 12, 5))))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(gone).errors.single().code)
    }

    // ---- cartão ----

    @Test
    fun `recorrência no cartão recebe fatura como compra à vista e não é paga por pagamento direto`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.create(gym(first = d(2026, 10, 22), form = RecurrenceForm.Card))
        val first = h.space.occurrences(ym(2026, 11)).value().single { it.recurrenceId == id }
        assertEquals(1, first.number)
        assertEquals(AssignmentState.Estimated, first.assignment)
        assertEquals(brl(100), h.space.budget(ym(2026, 11)).value().committed)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().paid)

        val r = h.space.execute(h.env(), PayOccurrence(id, 1, Settlement(d(2026, 11, 10))))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    @Test
    fun `ocorrência no cartão na janela do fechamento fica a conferir`() = runBlocking {
        val h = open()
        h.create(gym(first = d(2026, 10, 8), form = RecurrenceForm.Card))
        assertEquals(1, h.space.budget(ym(2026, 11)).value().invoiceLinksToConfirm)
    }

    // ---- renda recorrente ----

    @Test
    fun `salário mensal recorrente aparece a receber e vira recebido ao confirmar o recebimento`() = runBlocking {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(
            h.env(),
            CreateRecurrence(
                kind = RecurrenceKind.Income, description = "Salário", categoryId = null, amount = brl(6000),
                frequency = Frequency.Monthly, firstDate = d(2026, 10, 5),
            ),
        ).applied().changes.single().id

        var oct = h.space.incomes(ym(2026, 10)).value()
        assertEquals(brl(6000), oct.toReceive)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)

        h.space.execute(h.env(), PayOccurrence(id, 1, Settlement(d(2026, 10, 6), brl(6100)))).applied()
        oct = h.space.incomes(ym(2026, 10)).value()
        assertEquals(brl(6100), oct.received)
        assertEquals(Money.ZERO, oct.toReceive)
        assertEquals(brl(6000), h.space.incomes(ym(2026, 11)).value().toReceive)
    }

    // ---- validação, versão e desfazer ----

    @Test
    fun `dados inválidos da recorrência são rejeitados por campo`() = runBlocking {
        val h = open()
        val r = h.space.execute(h.env(), gym(0).copy(description = " ", categoryId = null))
        val fields = assertIs<CommandResult.Rejected>(r).errors.map { it.field }.toSet()
        assertEquals(setOf("description", "amount", "categoryId"), fields)
        val unknown = h.space.execute(h.env(), gym().copy(categoryId = "nada"))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
    }

    @Test
    fun `versão antiga na edição devolve a atual e não altera`() = runBlocking {
        val h = open()
        val id = h.create()
        val r = h.space.execute(h.env(), EditRecurrence(id, 2, RecurrenceScope.OnlyThis, RecurrenceFields(amount = brl(120)), expectedRevision = 9))
        assertEquals(1L, assertIs<CommandResult.StaleVersion>(r).currentRevision)
        assertEquals(brl(100), h.amountIn(id, ym(2026, 10)))
    }

    @Test
    fun `edição gera nova revisão e uma operação com a série`() = runBlocking {
        val h = open()
        val id = h.create()
        val receipt = h.space.execute(h.env(), EditRecurrence(id, 2, RecurrenceScope.OnlyThis, RecurrenceFields(amount = brl(120)))).applied()
        assertEquals(id, receipt.changes.single().id)
        assertEquals(2L, receipt.changes.single().revision)
        assertEquals(2L, h.space.recurrence(id).value()!!.revision)
    }

    @Test
    fun `desfazer a criação da recorrência tira todas as ocorrências dos totais`() = runBlocking {
        val h = open()
        val created = h.space.execute(h.env(), gym()).applied()
        h.space.execute(h.env(), Undo(created.operationId)).applied()
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        assertTrue(h.space.recurrence(created.changes.single().id).value()!!.voided)
        assertEquals(emptyList(), h.space.occurrences(ym(2026, 10)).value())
    }

    @Test
    fun `mesmo ID de comando não duplica a série`() = runBlocking {
        val h = open()
        val env = h.env()
        h.space.execute(env, gym()).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(env, gym()))
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
    }
}
