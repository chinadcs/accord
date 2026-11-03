package app.accord.core.chat

import kotlin.test.*

class FinancialScopeBoundaryTest {
    private val context = ProtectedQualificationBattery.context

    @Test fun `a financial query cannot validate a forged new expense`() {
        val text = "Quanto gastei com café, 50 no pix?"
        val proposal = Proposal.Register(Nature.Expense, "café", "50", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, proposal, context))
    }

    @Test fun `an internal transfer cannot validate a forged new expense`() {
        val text = "transferi 500 pra poupança no pix"
        val proposal = Proposal.Register(Nature.Expense, "poupança", "500", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, proposal, context))
    }

    @Test fun `a transfer fee is still an ordinary expense`() {
        val text = "taxa de transferência 12 no pix"
        val proposal = Proposal.Register(Nature.Expense, "taxa de transferência", "12", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, proposal, context))
    }

    @Test fun `validator eleven remains replayable for historical query proposals`() {
        val text = "Quanto gastei com café, 50 no pix?"
        val proposal = Proposal.Register(Nature.Expense, "café", "50", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, proposal, context, version = 11))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, proposal, context, version = 12))
    }

    @Test fun `forged clarification and correction payloads cannot turn a query into a write`() {
        val text = "Quanto gastei com café, 50 no pix?"
        val entry = Proposal.Register(Nature.Expense, "café", "50", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, Proposal.Clarify(QuestionField.Category, emptyList(), entry), context))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, Proposal.Correct("s8", CorrectionField.Amount, text = "50"), context))
    }

    @Test fun `a financial query continues to receive a nonfinancial reply`() {
        assertEquals(ChatDecision.Reply(StandardReply.Query), ChatValidator.evaluate("Quanto gastei com café?", Proposal.Query, context))
    }

    @Test fun `internal transfer refusal is new in validator thirteen`() {
        val text = "Acabei de transferir 500 pra poupança no pix"
        val proposal = Proposal.Register(Nature.Expense, "Acabei de transferir", "500", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, proposal, context, version = 12))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, proposal, context, version = 13))
    }

    private fun paymentQuestion() = context.copy(question = OpenQuestion("q1", "question-1", QuestionField.Payment, EntryCompleter.paymentOptions(context)))

    @Test fun `typed Pix uses the current default account in a payment question`() {
        val ctx = paymentQuestion()
        val result = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMlNoThink, profileVersion = 10)
            .interpret(InterpretRequest("no pix", ctx))
        assertEquals(ChatDecision.Answer("question-1", AnswerValue.Option(OptionValue.Account("account-1"))),
            ChatValidator.evaluate("no pix", result.raw, ctx, version = 13))
        assertTrue(result.steps.isEmpty())
    }

    @Test fun `a forged alternate account cannot replace the default in a typed Pix answer`() {
        val ctx = paymentQuestion()
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("no pix", Proposal.Answer("q1", choice = "b4"), ctx, version = 13))
        assertIs<ChatDecision.Answer>(ChatValidator.evaluate("no pix", Proposal.Answer("q1", choice = "b6"), ctx, version = 13))
    }

    @Test fun `historical profile nine keeps its previous typed Pix outcome`() {
        val ctx = paymentQuestion()
        val result = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMlNoThink, profileVersion = 9)
            .interpret(InterpretRequest("no pix", ctx))
        assertIs<ChatDecision.Ask>(ChatValidator.evaluate("no pix", result.raw, ctx, version = 12))
    }
}
