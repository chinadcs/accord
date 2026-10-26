package app.accord.ui

import app.accord.core.Money
import app.accord.core.chat.AmountRole
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.MessageState
import app.accord.core.chat.Nature
import app.accord.core.chat.PartialEntry
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionState
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.space.AmountMeaning
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.ChatPrefill
import app.accord.ui.chat.buildChatItems
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatItemsTest {
    private fun message(id: String, at: Long, state: MessageState) = ChatMessage(id, "texto $id", at, state)

    private fun summary(toClassify: Boolean = false) = ReceiptSummary(
        nature = Nature.Expense, description = "Amazon", totalCents = 15_000, toClassify = toClassify,
        payment = ChatPayment.Card, dateIso = "2026-10-22", invoiceMonth = "2026-11",
    )

    private fun receipt(id: String, messageId: String, at: Long, state: ReceiptState = ReceiptState.Active, entry: String = "e-$id") =
        ChatReceiptRecord(id, messageId, "op-$id", entry, summary(), ReceiptKind.Registered, state, false, null, at)

    private fun question(id: String, messageId: String, link: QuestionLink, state: QuestionState = QuestionState.Open) = ChatQuestionRecord(
        id, messageId, QuestionField.Category, ChatQuestion.Reason.Missing, emptyList(), link, "Amazon", 15_000, state, 1,
    )

    @Test
    fun `conversa na ordem de envio com recibo depois da mensagem`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("b", 20, MessageState.Applied), message("a", 10, MessageState.Applied)),
            questions = emptyList(),
            receipts = listOf(receipt("r1", "a", 11), receipt("r2", "b", 21)),
        )
        val items = buildChatItems(snapshot)
        assertEquals(listOf("u-a", "r-r1", "u-b", "r-r2"), items.map { it.key })
    }

    @Test
    fun `desfazer so no recibo mais recente ainda ativo e nao fechado`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("a", 10, MessageState.Applied), message("b", 20, MessageState.Applied)),
            questions = emptyList(),
            receipts = listOf(receipt("r1", "a", 11).copy(undoClosed = true), receipt("r2", "b", 21)),
        )
        val receipts = buildChatItems(snapshot).filterIsInstance<ChatItem.Receipt>()
        assertEquals(listOf(false, true), receipts.map { it.undoAvailable })

        val closed = snapshot.copy(receipts = snapshot.receipts.map { if (it.id == "r2") it.copy(undoClosed = true) else it })
        assertTrue(buildChatItems(closed).filterIsInstance<ChatItem.Receipt>().none { it.undoAvailable })
        val changed = snapshot.copy(receipts = snapshot.receipts.map { if (it.id == "r2") it.copy(state = ReceiptState.ChangedLater) else it })
        assertTrue(buildChatItems(changed).filterIsInstance<ChatItem.Receipt>().none { it.undoAvailable })
    }

    @Test
    fun `pergunta de categoria fica no recibo a classificar e some depois de respondida`() {
        val open = ChatSnapshot(
            messages = listOf(message("a", 10, MessageState.ToClassify)),
            questions = listOf(question("q1", "a", QuestionLink.Entry("e-r1"))),
            receipts = listOf(receipt("r1", "a", 11)),
        )
        assertEquals("q1", buildChatItems(open).filterIsInstance<ChatItem.Receipt>().single().question?.id)
        val answered = open.copy(questions = listOf(question("q1", "a", QuestionLink.Entry("e-r1"), QuestionState.Answered)))
        assertNull(buildChatItems(answered).filterIsInstance<ChatItem.Receipt>().single().question)
    }

    @Test
    fun `rascunho com pergunta aberta aparece como cartao de pergunta e nao como recibo`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("a", 10, MessageState.Asked)),
            questions = listOf(question("q1", "a", QuestionLink.Draft("d1"))),
            receipts = emptyList(),
        )
        val items = buildChatItems(snapshot)
        assertIs<ChatItem.User>(items[0])
        assertEquals("q1", assertIs<ChatItem.Question>(items[1]).question.id)
        assertEquals(2, items.size)
    }

    @Test
    fun `estados de espera falha e resposta do app viram aviso ou progresso`() {
        val states = listOf(
            MessageState.Sent to ChatItem.Working::class, MessageState.Interpreting to ChatItem.Working::class,
            MessageState.Applying to ChatItem.Working::class, MessageState.Failed to ChatItem.Notice::class,
            MessageState.Interrupted to ChatItem.Notice::class, MessageState.Canceled to ChatItem.Notice::class,
            MessageState.NoModel to ChatItem.Notice::class, MessageState.Refused to ChatItem.Notice::class,
            MessageState.Replied to ChatItem.Notice::class, MessageState.ResolvedManually to ChatItem.Notice::class,
        )
        states.forEach { (state, type) ->
            val items = buildChatItems(ChatSnapshot(listOf(message("a", 1, state)), emptyList(), emptyList()))
            assertEquals(type, items[1]::class, state.name)
        }
    }

    @Test
    fun `recibo desfeito ou alterado continua na conversa sem acoes de desfazer`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("a", 10, MessageState.Applied)),
            questions = emptyList(),
            receipts = listOf(receipt("r1", "a", 11, ReceiptState.Undone)),
        )
        val item = buildChatItems(snapshot).filterIsInstance<ChatItem.Receipt>().single()
        assertEquals(false, item.undoAvailable)
        assertEquals(ReceiptState.Undone, item.receipt.state)
    }

    // ---- registro manual a partir da mensagem ----

    @Test
    fun `prefill leva so o que foi entendido ao formulario`() {
        val today = LocalDate.of(2026, 10, 22)
        val form = ChatPrefill.toForm(
            PartialEntry(
                description = "tv", amount = Money(120_000), role = AmountRole.Total, installments = 6,
                payment = ChatPayment.Card, categoryId = "cat-1",
            ),
            today,
        )
        assertEquals(FormKind.Expense, form.kind)
        assertEquals(ExpenseForm.Card, form.form)
        assertEquals("1.200,00", form.amountText)
        assertEquals("6", form.installmentsText)
        assertEquals(AmountMeaning.Total, form.amountMeaning)
        assertEquals(today, form.date)
        assertEquals("cat-1", form.categoryId)
    }

    @Test
    fun `prefill sem valor deixa o campo em branco e renda nao leva categoria`() {
        val empty = ChatPrefill.toForm(PartialEntry(description = "ração"), LocalDate.of(2026, 10, 22))
        assertEquals("", empty.amountText)
        assertEquals("ração", empty.description)
        val income = ChatPrefill.toForm(
            PartialEntry(nature = Nature.Income, description = "salário", amount = Money(600_000), categoryId = "cat-1", accountId = "a1"),
            LocalDate.of(2026, 10, 22),
        )
        assertEquals(FormKind.Income, income.kind)
        assertNull(income.categoryId)
        assertEquals("a1", income.accountId)
        assertEquals(ExpenseForm.Pix, ChatPrefill.toForm(PartialEntry(payment = ChatPayment.Pix), LocalDate.of(2026, 10, 22)).form)
    }

    @Test
    fun `recibo a classificar leva a opcao sugerida pelo modelo`() {
        val options = listOf(
            app.accord.core.chat.QuestionOption("c1", "Mercado", app.accord.core.chat.OptionValue.Category("alimentacao.mercado")),
            app.accord.core.chat.QuestionOption("c2", "Pet", app.accord.core.chat.OptionValue.Category("pet")),
        )
        val withHint = ChatMessage("a", "texto a", 10, MessageState.ToClassify, categoryHints = listOf("pet"))
        val q = question("q", "a", QuestionLink.Entry("e-r1")).copy(options = options)
        val items = buildChatItems(ChatSnapshot(listOf(withHint), listOf(q), listOf(receipt("r1", "a", 11))))
        assertEquals("c2", (items.filterIsInstance<ChatItem.Receipt>().single()).suggestedRef)

        val noHint = withHint.copy(categoryHints = emptyList())
        val items2 = buildChatItems(ChatSnapshot(listOf(noHint), listOf(q), listOf(receipt("r1", "a", 11))))
        assertNull((items2.filterIsInstance<ChatItem.Receipt>().single()).suggestedRef)
    }
}
