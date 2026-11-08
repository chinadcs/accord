package app.accord.core.simulation

import app.accord.core.Money
import app.accord.core.sum
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import java.time.YearMonth

/**
 * Cenário de compra do simulador (S7, tela 36). Não é registro financeiro nem sincroniza: vive só no estado da tela.
 * Só compra parcelada no cartão (uma fatura por parcela); parcelamento fora do cartão é decisão aberta do S7.
 */
data class Scenario(
    /** Valor total da compra, em centavos. */
    val total: Money,
    val installments: Int,
    /** Mês de vencimento da fatura da 1ª parcela, escolhido explicitamente; a simulação não promete a atribuição do emissor. */
    val firstMonth: YearMonth,
    /** Categoria principal a comparar com o limite; sem ela só se compara com os conhecidos do mês. */
    val categoryId: String? = null,
    /** Intenção de origem, se a simulação partiu de uma; não altera a intenção. */
    val originIntentionId: String? = null,
)

/** Números reais de um mês, lidos pelas consultas de S1/S5. Entrada da conta pura; nada aqui é do cenário. */
data class MonthBaseline(
    val month: YearMonth,
    /** Compromissos conhecidos do mês (tela 10), sem intenções nem estimativas. */
    val knownCents: Long,
    /** Estimativa de novos gastos variáveis (S5), somada nas categorias que têm estimativa; nulo se não há. Linha à parte. */
    val variableEstimateCents: Long?,
    val category: CategoryBaseline?,
)

/** Conhecidos da categoria no mês e o limite vigente nele; limite nulo é "sem limite definido", nunca zero. */
data class CategoryBaseline(val categoryId: String, val limitCents: Long?, val knownCents: Long)

/** Comparação do cenário com o limite da categoria num mês. */
data class CategoryComparison(
    val categoryId: String,
    /** Nulo: sem limite definido naquele mês (não é limite zero). */
    val limit: Money?,
    val known: Money,
    val scenario: Money,
    val totalWithScenario: Money,
    /** Excesso que o cenário **acrescenta** ao que já excedia; nulo sem limite. Só resultado do cenário: não é alerta. */
    val scenarioExcess: Money?,
)

data class SimulatedMonth(
    val month: YearMonth,
    val installmentNumber: Int,
    val known: Money,
    val scenario: Money,
    val totalWithScenario: Money,
    /** Estimativa de variáveis (S5), separada dos conhecidos e do cenário; não entra em [totalWithScenario]. */
    val variableEstimate: Money?,
    val category: CategoryComparison?,
)

sealed interface SimulationResult {
    data class Ready(
        val months: List<SimulatedMonth>,
        /** Soma das parcelas do cenário; sempre igual ao total informado. */
        val scenarioTotal: Money,
        /** Situação da 1ª fatura: sempre escolha/estimativa do casal, nunca confirmada pelo emissor. */
        val firstInvoiceState: AssignmentState = AssignmentState.Estimated,
    ) : SimulationResult

    /** Entrada inválida: erro no campo, nenhum resultado fictício. */
    data class Invalid(val errors: List<FieldError>) : SimulationResult

    /** Não foi possível ler a base real; nenhum número é inventado. */
    data object Unavailable : SimulationResult
}

/** Regras puras da simulação. A leitura da base real fica em [ScenarioSimulator]. */
object Simulation {
    /** Mesmo teto de parcelas que o Chat aceita; S1 não define máximo. */
    const val MAX_INSTALLMENTS = 120

    fun validate(scenario: Scenario): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (scenario.total.cents <= 0) errors += FieldError("total", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        if (scenario.installments < 1) {
            errors += FieldError("installments", ErrorCode.OutOfRange, "A quantidade de parcelas começa em 1.")
        } else if (scenario.installments > MAX_INSTALLMENTS) {
            errors += FieldError("installments", ErrorCode.OutOfRange, "Use até $MAX_INSTALLMENTS parcelas.")
        }
        if (errors.isEmpty() && scenario.total.cents < scenario.installments) {
            errors += FieldError("total", ErrorCode.MustBePositive, "Cada parcela precisa ter ao menos um centavo.")
        }
        return errors
    }

    /** Meses que o cenário ocupa, na ordem das parcelas. Vazio se o cenário é inválido. */
    fun months(scenario: Scenario): List<YearMonth> =
        if (validate(scenario).isNotEmpty()) emptyList()
        else PurchaseSchedule.build(scenario.total, AmountMeaning.Total, scenario.installments, scenario.firstMonth, AssignmentState.Estimated).map { it.invoiceMonth }

    /** A parcela do cenário que cai em [month], ou nulo se o cenário é inválido ou não ocupa esse mês. Usada pela tela 10 para a camada "cenário". */
    fun partIn(scenario: Scenario, month: YearMonth): app.accord.core.space.InstallmentView? =
        if (validate(scenario).isNotEmpty()) null
        else PurchaseSchedule.build(scenario.total, AmountMeaning.Total, scenario.installments, scenario.firstMonth, AssignmentState.Estimated)
            .firstOrNull { it.invoiceMonth == month }

    fun run(scenario: Scenario, baselineOf: (YearMonth) -> MonthBaseline): SimulationResult {
        val errors = validate(scenario)
        if (errors.isNotEmpty()) return SimulationResult.Invalid(errors)
        val schedule = PurchaseSchedule.build(scenario.total, AmountMeaning.Total, scenario.installments, scenario.firstMonth, AssignmentState.Estimated)
        val months = schedule.map { part ->
            val base = baselineOf(part.invoiceMonth)
            val known = Money(base.knownCents)
            val comparison = base.category?.let { c ->
                val categoryKnown = Money(c.knownCents)
                val withScenario = categoryKnown + part.amount
                CategoryComparison(
                    categoryId = c.categoryId,
                    limit = c.limitCents?.let(::Money),
                    known = categoryKnown,
                    scenario = part.amount,
                    totalWithScenario = withScenario,
                    scenarioExcess = c.limitCents?.let { limit -> Money(excessOver(withScenario.cents, limit) - excessOver(categoryKnown.cents, limit)) },
                )
            }
            SimulatedMonth(
                month = part.invoiceMonth,
                installmentNumber = part.number,
                known = known,
                scenario = part.amount,
                totalWithScenario = known + part.amount,
                variableEstimate = base.variableEstimateCents?.let(::Money),
                category = comparison,
            )
        }
        return SimulationResult.Ready(months, schedule.map { it.amount }.sum())
    }

    private fun excessOver(spent: Long, limit: Long): Long = (spent - limit).coerceAtLeast(0)
}
