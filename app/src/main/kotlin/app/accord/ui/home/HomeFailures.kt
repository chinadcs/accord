package app.accord.ui.home

import app.accord.ui.pending.PendingQueueUiState

/** Os blocos da Início que dependem de uma leitura, na ordem de leitura da tela (ADR 0078). */
enum class HomeBlock { Balance, Income, Review, Dates, Exceed, Allowance, Forecast }

/**
 * Quais leituras da Início falharam, na ordem de leitura. Alimenta o banner único "Tentar de novo": carga não é falha,
 * e uma lista vazia significa que o banner não aparece. Não lê nada; só olha os estados já conhecidos.
 */
fun homeFailures(
    pending: PendingQueueUiState,
    balance: HomeBalanceState,
    upcoming: HomeUpcomingState,
    projection: HomeProjectionState,
    allowance: HomeAllowanceState,
    cashFlow: HomeCashFlowState,
): List<HomeBlock> {
    val attention = attentionPlan(pending, upcoming, projection).failed
    return buildList {
        if (balance is HomeBalanceState.Failed) add(HomeBlock.Balance)
        if (balance is HomeBalanceState.Ready && balance.incomes is HomeIncomeState.Failed) add(HomeBlock.Income)
        if (AttentionSource.Review in attention) add(HomeBlock.Review)
        if (AttentionSource.Dates in attention) add(HomeBlock.Dates)
        if (AttentionSource.Exceed in attention) add(HomeBlock.Exceed)
        if (allowance is HomeAllowanceState.Failed) add(HomeBlock.Allowance)
        if (cashFlow is HomeCashFlowState.Failed) add(HomeBlock.Forecast)
    }
}

/**
 * Os blocos cuja leitura ainda corre. Serve ao banner: depois de "Tentar de novo" ele fica em "Atualizando…" enquanto algum dos
 * blocos que falharam estiver aqui, em vez de sumir no primeiro instante em que a falha vira carga.
 */
fun homeLoadingBlocks(
    pending: PendingQueueUiState,
    balance: HomeBalanceState,
    upcoming: HomeUpcomingState,
    projection: HomeProjectionState,
    allowance: HomeAllowanceState,
    cashFlow: HomeCashFlowState,
): Set<HomeBlock> = buildSet {
    if (balance is HomeBalanceState.Loading) add(HomeBlock.Balance)
    if (balance is HomeBalanceState.Ready && balance.incomes is HomeIncomeState.Loading) add(HomeBlock.Income)
    // A fila de pendências falha antes de carregar, como em attentionPlan.
    if (pending.loading && !pending.failed) add(HomeBlock.Review)
    if (upcoming is HomeUpcomingState.Loading) add(HomeBlock.Dates)
    if (projection is HomeProjectionState.Loading) add(HomeBlock.Exceed)
    if (allowance is HomeAllowanceState.Loading) add(HomeBlock.Allowance)
    if (cashFlow is HomeCashFlowState.Loading) add(HomeBlock.Forecast)
}

/** "a", "a e b" ou "a, b e c": lista legível para o apoio do banner. [and] é a conjunção da língua da tela. */
internal fun joinWithAnd(items: List<String>, and: String): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
}
