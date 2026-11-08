package app.accord.core.simulation

import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.InstallmentView
import java.time.YearMonth

/**
 * Cronograma de uma compra parcelada no cartão: parcelas, distribuição de centavos e mês de fatura de cada uma.
 *
 * É o **único** lugar que monta esse cronograma: o registro real (`RegisterCardPurchase`) e a simulação (S7) o usam,
 * para que o cenário e a compra salva nunca divirjam em arredondamento (S7 "Riscos"). Função pura, sem relógio nem banco.
 */
object PurchaseSchedule {
    /**
     * @param amount valor informado; é o total, ou o valor de cada parcela se [meaning] for [AmountMeaning.PerInstallment].
     * @param firstMonth mês de vencimento da fatura da 1ª parcela; as seguintes seguem mês a mês.
     * @param firstState situação da atribuição da 1ª parcela; as demais são sempre [AssignmentState.Estimated].
     * @throws IllegalArgumentException se [count] < 1 ou se alguma parcela ficaria com menos de 1 centavo.
     */
    fun build(
        amount: Money,
        meaning: AmountMeaning?,
        count: Int,
        firstMonth: YearMonth,
        firstState: AssignmentState,
    ): List<InstallmentView> {
        val amounts = if (meaning == AmountMeaning.PerInstallment) Installments.fromEach(amount, count) else Installments.splitTotal(amount, count)
        val months = Installments.schedule(firstMonth, count)
        return amounts.mapIndexed { i, each ->
            InstallmentView(i + 1, each, months[i], if (i == 0) firstState else AssignmentState.Estimated)
        }
    }
}
