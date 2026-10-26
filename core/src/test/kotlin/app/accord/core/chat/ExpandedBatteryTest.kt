package app.accord.core.chat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ExpandedBatteryTest {
    @Test
    fun `gabaritos financeiros independentes conferem total parcela conta e datas`() {
        val ctx = ChatBattery.realisticContext()
        fun ref(id: String) = ctx.categories.single { it.id == id }.ref
        val proposals = mapOf(
            "x021" to Proposal.Register(description = "notebook", amount = "360", role = AmountRole.Total,
                installments = "3x", category = ref("eletronicos.equipamentos"), payment = ChatPayment.Card),
            "x026" to Proposal.Register(description = "notebook", amount = "120", role = AmountRole.PerInstallment,
                installments = "3 parcelas", category = ref("eletronicos.equipamentos"), payment = ChatPayment.Card),
            "x007" to Proposal.Register(description = "mercado", amount = "1.045,67",
                category = ref("alimentacao.mercado"), payment = ChatPayment.Pix, account = "a2"),
            "x035" to Proposal.Register(description = "mercado", amount = "82", payment = ChatPayment.Debit,
                category = ref("alimentacao.mercado"), date = DateExpression(DateKind.DayOfMonth, "dia 28")),
            "x061" to Proposal.Register(nature = Nature.Income, description = "salário", amount = "5.300",
                account = "a2", date = DateExpression(DateKind.NextMonth, "mês que vem")),
        )
        proposals.forEach { (id, proposal) ->
            val case = ExpandedBattery.cases.single { it.id == id }
            val decision = ChatValidator.evaluate(case.text, ProposalCodec.encode(proposal), ctx)
            assertEquals(ChatBattery.Outcome.Match, ChatGrading.grade(case, decision).outcome, "$id: $decision")
        }
    }
}
