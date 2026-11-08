package app.accord.core.analysis

import app.accord.core.Money

/**
 * Resultado de uma medida das visões de análise (S6). Um valor existente (inclusive zero apurado) é diferente de "sem dados" e de "falha ao
 * calcular"; nenhuma das duas últimas vira R$ 0,00 nem entra em soma ou diferença como zero.
 */
sealed interface Measure {
    data class Value(val money: Money) : Measure

    /** Não há registros que sustentem o número (período sem cobertura ou sem lançamentos relevantes). */
    data object NoData : Measure

    /** O cálculo falhou; a interface oferece repetir a leitura. Sem detalhe financeiro. */
    data object Failed : Measure

    companion object {
        fun of(cents: Long): Measure = Value(Money(cents))
    }
}

/**
 * Fase do produto que habilita as entradas de cada tela. P1 não é pré-requisito de P0 (S6 "Princípio geral", proposta de configuração de build);
 * P2 (S7: intenções, simulador e pistas) também não é pré-requisito de P0/P1 e só aparece quando a fase estiver ativa.
 */
enum class ProductPhase {
    P0,
    P1,
    P2,
    ;

    /** `true` se esta fase habilita recursos da fase [required]. */
    fun includes(required: ProductPhase): Boolean = ordinal >= required.ordinal
}
