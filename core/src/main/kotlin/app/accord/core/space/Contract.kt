package app.accord.core.space

import app.accord.core.Frequency
import app.accord.core.analysis.ChargeComparison
import app.accord.core.analysis.ComparisonRow
import app.accord.core.analysis.Measure
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.analysis.ReviewPoint
import app.accord.core.analysis.PeriodChange
import app.accord.core.analysis.SubscriptionCharge
import app.accord.core.GENERAL_BUDGET_ID
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.alerts.AlertDisplay
import app.accord.core.alerts.AlertEvent
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.AlertSnapshot
import app.accord.core.alerts.DisplayState
import app.accord.core.alerts.NotificationAccess
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Excess
import app.accord.core.forecast.InvoiceClosure
import app.accord.core.forecast.MonthForecast
import java.time.LocalDate
import java.time.YearMonth

/**
 * Contrato da fachada do espaço financeiro (S1 §1, §3, §4, §8). Única porta de entrada para ler
 * e alterar dados financeiros e cadastros; Chat, importação e formulários usam os mesmos comandos.
 */
interface FinanceSpace {
    suspend fun clueEvaluations(): QueryResult<List<app.accord.core.clues.ClueEvaluation>>
    /** S7: original-date purchases and linked credits, versions, import limitations; no writes. */
    suspend fun clueSource(from: LocalDate, untilExclusive: LocalDate): QueryResult<app.accord.core.clues.ClueSource>

    /** Extracted fields only, from confirmed batches linked to this entry; never reads the external file. */
    suspend fun clueImportOrigins(entryId: String): QueryResult<List<app.accord.core.clues.ClueImportOrigin>>

    suspend fun execute(envelope: Envelope, command: Command): CommandResult

    /** Mesmas regras do [execute], sem persistir nada. A gravação reaplica regras e versões. */
    suspend fun preview(command: Command): PreviewResult

    suspend fun categories(): QueryResult<List<CategoryNode>>

    /** Categorias de renda (receitas): Investimento, Salário e Renda Extra de início, mais as criadas pelo casal. */
    suspend fun incomeCategories(): QueryResult<List<IncomeCategoryNode>>

    /** Ícones escolhidos por lançamento ou por nome de lançamento (ADR 0047), sem os removidos. */
    suspend fun iconAssociations(): QueryResult<List<IconAssociationView>>

    /** Intenções de compra do casal (S7, P2), da alteração mais recente para a mais antiga. Não são despesa e não ocupam o orçamento. */
    suspend fun intentions(): QueryResult<List<IntentionView>>

    suspend fun intention(id: String): QueryResult<IntentionView?>
    suspend fun accounts(): QueryResult<AccountsView>

    /** Saldo derivado de cada conta e da Carteira (ADR 0051); contas sem saldo inicial vêm com [AccountBalanceView.balance] nulo. */
    suspend fun accountBalances(): QueryResult<List<AccountBalanceView>>
    suspend fun savingsPockets(): QueryResult<List<SavingsPocket>>
    suspend fun savingsMoves(pocketId: String): QueryResult<List<SavingsMove>>

    /** Transferências e ajustes de saldo, sem os desfeitos, do mais recente para o mais antigo. */
    suspend fun accountMoves(): QueryResult<List<AccountMoveView>>
    suspend fun budget(month: YearMonth): QueryResult<MonthBudget>

    /** Limite vigente de cada categoria principal em [month], com a origem da vigência. */
    suspend fun limits(month: YearMonth): QueryResult<List<CategoryLimit>>
    suspend fun card(): QueryResult<CardInfo>
    suspend fun cards(): QueryResult<List<CardInfo>>
    suspend fun boleto(id: String): QueryResult<BoletoView?>
    suspend fun income(id: String): QueryResult<IncomeView?>
    suspend fun incomes(month: YearMonth): QueryResult<IncomeSummary>
    suspend fun recurrence(id: String): QueryResult<RecurrenceView?>

    /** Estornos vinculados à origem, em ordem de registro. */
    suspend fun refunds(originId: String): QueryResult<List<RefundView>>

    /** Ocorrências que ocupam o [month] do orçamento (calculadas sob demanda), em ordem de data. */
    suspend fun occurrences(month: YearMonth): QueryResult<List<OccurrenceView>>
    suspend fun purchase(id: String): QueryResult<PurchaseDetail?>

    /** Fatura pelo mês de vencimento, mesmo sem itens: composição, conferência, quitação e pendências. */
    suspend fun invoice(dueMonth: YearMonth, cardId: String? = null): QueryResult<InvoiceView>

    /** Faturas com algum dado (itens, total registrado ou pagamento), da mais antiga para a mais nova. */
    suspend fun invoices(cardId: String? = null): QueryResult<List<InvoiceView>>

    /** Lançamentos que ocupam o [month] do orçamento, do mais recente para o mais antigo. */
    suspend fun movements(month: YearMonth): QueryResult<List<MovementRow>>

    /**
     * Rascunhos locais (S1 §3.1): não são financeiros. Não geram operação, não entram em nenhum
     * total, não sincronizam e só existem neste aparelho. [saveDraft] com um ID existente atualiza.
     */
    suspend fun saveDraft(draft: LocalDraft): QueryResult<String>
    suspend fun discardDraft(id: String): QueryResult<Unit>
    suspend fun drafts(): QueryResult<List<LocalDraft>>

    /**
     * S2 registra que o serviço reconheceu a operação, com a versão de cada entidade no serviço.
     * S1 só guarda: a versão vira a "versão base" das próximas operações sobre a mesma entidade.
     */
    suspend fun recordAcknowledgement(operationId: String, serviceVersions: Map<EntityRef, Long>): QueryResult<Unit>

    /** Lista de Lançamentos (tela 07) com filtros, critério de data e paginação estável. */
    suspend fun entries(filter: EntryFilter, cursor: Int = 0, pageSize: Int = 50): QueryResult<EntryPage>

    /**
     * Saldo total (acumulado até o fim de [month], o mês exibido na lista) e o resumo de [month], sem outros filtros.
     * Mesma regra de [EntrySummary.balance]: renda recebida menos despesa comprometida; renda prevista fica de fora.
     */
    suspend fun totalBalance(criterion: DateCriterion, month: YearMonth): QueryResult<TotalBalance>

    /** Detalhe do lançamento (tela 08) por ID estável; nulo se não existe. */
    suspend fun entry(id: String): QueryResult<EntryDetail?>

    /** Compromissos conhecidos do mês (tela 10): parcelas, recorrências e contas previstas. */
    suspend fun commitments(month: YearMonth, kind: CommitmentKind = CommitmentKind.All): QueryResult<MonthCommitments>

    /** Detalhe do compromisso (tela 11): cronograma de parcelas ou frequência de recorrência. */
    suspend fun commitment(id: String): QueryResult<CommitmentDetail?>

    /** Pendências compartilhadas de S1 (tela 18), derivadas do estado dos lançamentos e faturas. */
    suspend fun pendings(): QueryResult<List<PendingItem>>

    /**
     * Assinaturas (S6, tela 31): séries de despesa recorrente cuja categoria atual está em Assinaturas, com a próxima cobrança a partir de
     * [from] e o que vence em [[from], [untilExclusive]). Leitura apenas: nada é criado, alterado nem classificado.
     */
    suspend fun subscriptions(from: LocalDate, untilExclusive: LocalDate): QueryResult<SubscriptionList>

    /**
     * Atividade de compras (S6, tela 30): as compras pela data original, em [[from], [untilExclusive]). A compra parcelada entra uma vez, pelo
     * total, na data da compra; renda e quitação de fatura ficam fora. [categoryId] restringe a uma categoria principal ou subcategoria.
     * Leitura apenas: nada é criado nem alterado, e o orçamento do mês continua sendo calculado por outra consulta.
     */
    suspend fun purchaseActivity(from: LocalDate, untilExclusive: LocalDate, categoryId: String? = null): QueryResult<PurchaseActivity>

    /**
     * Comparação histórica (S6, tela 33): os mesmos grupos em dois períodos, com um critério de data comum. No critério [DateCriterion.BudgetMonth]
     * os períodos são meses inteiros (início no dia 1 e fim exclusivo no dia 1) e os valores são os mesmos comprometidos do orçamento; em
     * [DateCriterion.PurchaseDate] valem as datas originais da atividade de compras. Um período que falhar vira falha nas suas células, nunca zero.
     */
    suspend fun comparePeriods(
        criterion: DateCriterion,
        grouping: ComparisonGrouping,
        a: ComparisonPeriod,
        b: ComparisonPeriod,
    ): QueryResult<PeriodComparison>

    /**
     * Revisão semanal (S6, tela 29): quatro blocos independentes, cada um com a sua referência temporal e a sua falha isolada. [period] é a
     * semana revisada (segunda a segunda) e [reference] é a data da consulta. Leitura apenas: consultar outro período não altera limites,
     * lançamentos nem a janela ou a cota dos alertas, e nada é marcado como conferido.
     */
    suspend fun weeklyReview(period: ReviewPeriod, reference: LocalDate): QueryResult<WeeklyReviewSummary>

    /** Detalhe de uma assinatura (S6, tela 32); nulo se a série não existe, foi anulada ou não está em Assinaturas. */
    suspend fun subscription(recurrenceId: String): QueryResult<SubscriptionDetail?>

    /** Operações locais em ordem de sequência, a partir de [afterSequence] (exclusive). */
    suspend fun operations(afterSequence: Long = 0, limit: Int = 100): QueryResult<List<LocalOperation>>

    /**
     * Previsão do mês de orçamento por categoria principal (S5, telas 20): conhecidos + estimados. Calculada neste aparelho a partir dos dados locais; não é
     * persistida nem sincronizada.
     */
    suspend fun forecasts(month: YearMonth): QueryResult<MonthForecast>

    /** Detalhe da previsão de uma categoria principal (tela 21); nulo se [categoryId] não é uma categoria principal. */
    suspend fun forecastDetail(categoryId: String, month: YearMonth): QueryResult<ForecastDetail?>

    /**
     * Avisos a exibir neste aparelho (adaptador de notificações, S5). Para cada evento pendente aplica [AlertDisplay.attempt] com a preferência e o acesso
     * lidos agora: o que não pode ser exibido (preferência, permissão, canal, janela encerrada) é gravado como tal e não volta; o que pode continua
     * pendente até [markAlertShown], de modo que um processo encerrado antes da exibição tenta de novo, sem exibir duas vezes.
     */
    suspend fun alertNotices(preference: AlertPreference, access: NotificationAccess): QueryResult<List<AlertNotice>>

    /** O adaptador exibiu o aviso. Idempotente: um evento que já tem situação final não muda. */
    suspend fun markAlertShown(eventId: String): QueryResult<Unit>

    /** Eventos da janela atual e a situação de cada um neste aparelho (tela 20: "Aviso desta semana"). */
    suspend fun alertStatuses(): QueryResult<List<AlertStatus>>

    /** Um evento de aviso como este aparelho o conhece, pela chave da notificação (tela 21 aberta por aviso); nulo se não é deste espaço ou conta. */
    suspend fun alertEvent(id: String): QueryResult<AlertEventView?>

    /**
     * Preferência de avisos deste aparelho (tela 27), ligada ao espaço e à conta. Só local: não sincroniza nem gera operação. [AlertPreference.Undefined]
     * só existe antes do passo de notificações dos Primeiros passos.
     */
    suspend fun alertPreference(): QueryResult<AlertPreference>

    /** Salvar a preferência não avalia nem exibe nada, nem retroativo, e não muda limite ou cálculo algum. */
    suspend fun setAlertPreference(preference: AlertPreference): QueryResult<Unit>
}

/** S7: recomputed under the command transaction; no change to money or alert quota. */
data class DismissClue(val key: String, val fingerprint: String, val reference: LocalDate) : Command

/** Um aviso pronto para exibir. [id] é a chave da notificação (idempotente); o texto público não leva valores. */
data class AlertNotice(val id: String, val categoryId: String, val kind: AlertKind, val windowMonday: LocalDate, val snapshot: AlertSnapshot)

/**
 * [snapshot] é o do evento canônico; [shownSnapshot] é o que ESTE aparelho calculou e mostrou, quando o gerou aqui (a tela 21 mostra o do aviso e o cálculo atual).
 */
data class AlertEventView(
    val id: String,
    val categoryId: String,
    val month: YearMonth,
    val windowMonday: LocalDate,
    val kind: AlertKind,
    val snapshot: AlertSnapshot,
    val shownSnapshot: AlertSnapshot?,
    val generatedAtMillis: Long,
    val display: DisplayState,
)

data class AlertStatus(
    val id: String,
    val categoryId: String,
    val windowMonday: LocalDate,
    val kind: AlertKind,
    val generatedAtMillis: Long,
    val display: DisplayState,
)

/** Natureza do que compõe os conhecidos de uma categoria no mês (S5 "Detalhe da previsão"). */
enum class KnownNature { Realized, Installments, Recurrences, Boletos, Credits }

/** Uma linha da composição. [cents] é o valor com sinal (créditos de estorno são negativos); [count] é nulo quando a natureza não conta itens. */
data class KnownLine(val nature: KnownNature, val cents: Long, val count: Int?)

/**
 * Composição aditiva dos conhecidos: a soma de [known] é exatamente `forecast.knownCents`. [toConfirm] é o subconjunto dos conhecidos que está na fatura
 * estimada ("a conferir"), nunca somado de novo. [bySubcategory] segue o detalhe da categoria no mês (chave nula é "sem subcategoria").
 */
data class ForecastDetail(
    val month: YearMonth,
    val forecast: CategoryForecast,
    val ruleVersion: String,
    val invoice: InvoiceClosure,
    val known: List<KnownLine>,
    val toConfirm: KnownLine?,
    val bySubcategory: Map<String?, Money>,
    val calculatedAtMillis: Long,
)

// ---- rascunhos locais ----

/**
 * [Notification]: proposta lida de uma notificação de banco deste aparelho (ADR 0069). Os campos seguem o formulário de
 * registro mais `sourcePackage`; o texto da notificação nunca é guardado. Nada vale para o orçamento até a pessoa confirmar.
 */
enum class DraftSource { Chat, Form, Limits, Notification }

/**
 * Registro sem valor, intenção ou outro campo essencial, ou formulário em edição. Guarda só campos
 * estruturados; o texto do Chat fica no histórico do aparelho, nunca aqui.
 */
data class LocalDraft(
    /** Vazio pede um ID novo. */
    val id: String = "",
    val source: DraftSource,
    /** Campos conhecidos, por nome (ex.: `description`, `date`). */
    val fields: Map<String, String>,
    /** Campos que faltam para efetivar. */
    val missing: List<String> = emptyList(),
    val reason: String = "",
    val updatedAtMillis: Long = 0,
)

// ---- identidade e envelope ----

/** Espaço e pessoa da sessão; fornecidos por S2 (nos testes e na Etapa 1, identidade de desenvolvimento). */
data class Session(val spaceId: String, val personId: String)

fun interface SessionProvider {
    fun current(): Session
}

/** Gerado por quem chama, uma vez por intenção de salvar; repetir devolve o resultado original. */
@JvmInline
value class CommandId(val value: String) {
    init {
        require(value.isNotBlank()) { "ID do comando não pode ser vazio" }
    }
}

enum class Origin { Manual, Chat, Import, System }

/**
 * [draftId]: ao efetivar um rascunho local, o comando de registro cita o rascunho, que é descartado
 * na mesma transação em que o lançamento é gravado (e só se o comando for aplicado).
 */
data class Envelope(
    val commandId: CommandId,
    val origin: Origin = Origin.Manual,
    val draftId: String? = null,
)

// ---- comandos ----

/** Categoria principal, subcategoria ou, nos registros de despesa, "a classificar" (§3.1). */
sealed interface Classification {
    data class Category(val id: String) : Classification
    data object ToClassify : Classification
}

sealed interface BankRef {
    /** Banco do catálogo local, identificado por ISPB, nunca por semelhança de nome. */
    data class Catalog(val ispb: String, val name: String) : BankRef
    data class Other(val name: String) : BankRef
}

enum class DirectPaymentKind { Pix, Debit, Cash }

sealed interface Command

data class CreateCategory(val name: String, val parentId: String? = null) : Command

/**
 * Registra o evento de aviso gerado pela avaliação interna da fachada (S5, ADR 0044). Nunca vem da interface nem do Chat: só a fachada o emite, com origem
 * [Origin.System]. Cria o evento imutável, sua situação de exibição "pendente" e a operação que o leva ao serviço, na mesma transação.
 */
data class RecordAlertEvent(val event: AlertEvent) : Command

/** Categoria de renda (receitas), separada das categorias de despesa; lista plana, nome único sem diferenciar caixa e acentos. */
data class CreateIncomeCategory(val name: String) : Command

data class RenameIncomeCategory(val categoryId: String, val name: String, val expectedRevision: Long? = null) : Command

/**
 * Edita a categoria de renda (ADR 0038, emenda de 09/11/2026): nome, ícone e cor (índice da paleta, [CategoryPalette]). Só o que vem
 * preenchido muda. A categoria de renda não tem subcategorias, então a cor é sempre a própria.
 */
data class EditIncomeCategory(
    val categoryId: String,
    val name: String? = null,
    val iconKey: String? = null,
    val colorIndex: Int? = null,
    val expectedRevision: Long? = null,
) : Command

/**
 * Escolhe o ícone de um lançamento ([IconScope.Entry], [target] é a identidade do lançamento) ou de todos os lançamentos com o mesmo nome
 * ([IconScope.Name], [target] é o nome, comparado sem caixa, acento e espaços extras). Substitui a escolha anterior do mesmo alvo. O ícone é só visual:
 * não muda categoria, valores, datas nem dados importados (ADR 0047). A fachada confere o formato da chave do catálogo da origem e recusa
 * lançamento inexistente; a pertença ao catálogo embarcado é conferida na exibição. Não é desfazível: escolher de novo ou [ClearIconAssociation] volta.
 */
data class SetIconAssociation(val scope: IconScope, val target: String, val icon: IconRef, val expectedRevision: Long? = null) : Command

/** Remove a escolha de ícone do alvo (volta ao reconhecimento automático). Recusa quando não há escolha. */
data class ClearIconAssociation(val scope: IconScope, val target: String, val expectedRevision: Long? = null) : Command

/**
 * Guarda uma intenção de compra do casal (S7, ADR 0048): um desejo ainda não assumido. Só o [name] é obrigatório; [estimatedValue] ausente é
 * "sem valor definido" (nunca zero) e [desiredMonth] é só lembrete (não é data de compra nem vencimento). Não é despesa, não ocupa orçamento nem
 * limite, não entra em Compromissos futuros e não é desfazível: editar de novo é o caminho de volta.
 */
data class CreateIntention(
    val name: String,
    val estimatedValue: Money? = null,
    val desiredMonth: YearMonth? = null,
    val note: String? = null,
) : Command

/** Substitui todos os campos da intenção (versão inteira, sem mesclar). Recusa se [expectedRevision] não for a atual. */
data class EditIntention(
    val intentionId: String,
    val name: String,
    val estimatedValue: Money? = null,
    val desiredMonth: YearMonth? = null,
    val note: String? = null,
    val expectedRevision: Long? = null,
) : Command

/** [initialBalance] e [balanceCutoff] vêm juntos: o saldo calculado conta só os lançamentos a partir da data de corte (ADR 0051). */
data class AddAccount(
    val bank: BankRef,
    val nickname: String,
    val initialBalance: Money? = null,
    val balanceCutoff: LocalDate? = null,
) : Command

/** Define ou corrige o saldo inicial e a data de corte da conta (ou da Carteira). Não mexe em nenhum lançamento. */
data class SetAccountBalance(
    val accountId: String,
    val initialBalance: Money,
    val cutoff: LocalDate,
    val expectedRevision: Long? = null,
) : Command

/** Passa dinheiro de uma conta para outra (a Carteira inclusive). Não é despesa nem renda e não mexe no orçamento. */
data class TransferBetweenAccounts(
    val fromAccountId: String,
    val toAccountId: String,
    val amount: Money,
    val date: LocalDate,
    val note: String? = null,
) : Command

/**
 * Ajusta o saldo calculado ao saldo real que a pessoa vê, somando ou tirando a diferença. Não é despesa nem renda e não mexe no orçamento;
 * os lançamentos continuam como estavam. A conta precisa ter saldo inicial e a data não pode ser anterior à de corte.
 */
data class AdjustAccountBalance(
    val accountId: String,
    val actualBalance: Money,
    val date: LocalDate,
    val note: String? = null,
) : Command

data class SetMyDefaultAccount(val accountId: String) : Command

/**
 * Pix, débito ou dinheiro: ocupa o mês da movimentação e já nasce pago.
 * [isCharge] marca tarifa ou juros confirmado, como na compra no cartão: sem classificação usa Serviços > Tarifas e juros, e a marca (não o nome da
 * categoria, que é editável) mantém o movimento fora do ritmo histórico da previsão (S5).
 */
data class RegisterDirectPayment(
    val kind: DirectPaymentKind,
    val description: String,
    val classification: Classification,
    val date: LocalDate,
    val amount: Money,
    /** Pix e débito: o padrão do autor quando omitida. Dinheiro não tem conta. */
    val accountId: String? = null,
    val spentBy: String? = null,
    val isCharge: Boolean = false,
    /** Intenção de origem (S7, ADR 0048): vínculo imutável definido na criação; no máximo um compromisso por intenção no espaço. */
    val originIntentionId: String? = null,
) : Command

enum class AmountMeaning { Total, PerInstallment }

/** Cadastro ou edição de um cartão (ADR 0052); os dias sugerem a fatura sem garantir a atribuição do emissor. */
data class ConfigureCard(
    val bank: BankRef?,
    val nickname: String,
    val closingDay: Int,
    val dueDay: Int,
    /** Limite contratado, usado apenas para alertar quando o total em faturas não pagas o exceder. */
    val creditLimit: Money? = null,
    val cardId: String? = null,
    val createNew: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/**
 * Compra no cartão, à vista (1 parcela) ou parcelada. Com mais de uma parcela, [amountMeaning]
 * é obrigatório: o aplicativo não adivinha se o valor é total ou da parcela.
 * [isCharge] marca tarifa ou juros confirmado; sem classificação usa Serviços > Tarifas e juros.
 * [invoice] é a fatura (mês de vencimento) informada pela pessoa: fica confirmada, sem selo.
 */
data class RegisterCardPurchase(
    val description: String,
    val classification: Classification,
    val date: LocalDate,
    val amount: Money,
    val amountMeaning: AmountMeaning? = null,
    val installments: Int = 1,
    val spentBy: String? = null,
    val isCharge: Boolean = false,
    val invoice: YearMonth? = null,
    /** Intenção de origem (S7, ADR 0048): vínculo imutável definido na criação; no máximo um compromisso por intenção no espaço. */
    val originIntentionId: String? = null,
    val cardId: String? = null,
    /**
     * Cronograma explícito, um valor por parcela, na ordem (ADR 0081): quando o documento mostra o valor exato de cada parcela, o aplicativo o guarda em vez de repartir o total
     * (a sobra de centavos fica onde o banco a pôs). Exige [amountMeaning] total, [amount] igual à soma e uma entrada por parcela, cada uma com ao menos um centavo.
     */
    val installmentAmounts: List<Money>? = null,
) : Command

/**
 * Corrige a fatura atribuída da 1ª parcela; as demais acompanham, uma por mês. Só a 1ª parcela
 * muda de fatura; deslocamento que toca fatura quitada ou parcela cancelada é rejeitado.
 */
data class CorrectInvoiceAssignment(
    val purchaseId: String,
    val installmentNumber: Int,
    val invoice: YearMonth,
    val expectedRevision: Long? = null,
) : Command

/** Dá categoria a um lançamento "a classificar" (todas as parcelas, se parcelado); total geral inalterado. */
data class ClassifyEntry(
    val entityId: String,
    val category: Classification.Category,
    val expectedRevision: Long? = null,
) : Command

/** Data efetiva de um pagamento ou recebimento; [amount] nulo confirma o valor previsto. */
data class Settlement(val date: LocalDate, val amount: Money? = null)

/**
 * Boleto: previsto no mês do vencimento enquanto não pago; pago ocupa só o mês do pagamento
 * efetivo. As duas datas e os dois valores ficam guardados.
 */
data class RegisterBoleto(
    val description: String,
    val classification: Classification,
    val dueDate: LocalDate,
    val amount: Money,
    val accountId: String? = null,
    val payment: Settlement? = null,
    /** Intenção de origem (S7, ADR 0048): vínculo imutável definido na criação; no máximo um compromisso por intenção no espaço. */
    val originIntentionId: String? = null,
) : Command

data class PayBoleto(
    val boletoId: String,
    val payment: Settlement,
    /** Padrão do autor quando omitida. */
    val accountId: String? = null,
    val expectedRevision: Long? = null,
) : Command

/** Desliga o "Pago" do boleto: volta a previsto pelo vencimento, com valor e datas previstos intactos (switch Pago do detalhe). */
data class UndoBoletoPayment(
    val boletoId: String,
    val expectedRevision: Long? = null,
) : Command

/** Renda prevista (ou já recebida, se [received] vier junto). Conta no mês do recebimento efetivo. */
data class RegisterIncome(
    val description: String,
    val expectedAmount: Money,
    val expectedDate: LocalDate,
    val accountId: String? = null,
    val received: Settlement? = null,
    /** Categoria de renda (semente ou criada); nula quando a pessoa não escolhe. */
    val incomeCategoryId: String? = null,
) : Command

data class ReceiveIncome(
    val incomeId: String,
    val receipt: Settlement,
    val accountId: String? = null,
    val expectedRevision: Long? = null,
) : Command

/** Desliga o "Recebido" da renda: volta a prevista, com valor e data previstos intactos (switch Recebido do detalhe). */
data class UndoIncomeReceipt(
    val incomeId: String,
    val expectedRevision: Long? = null,
) : Command

enum class RecurrenceKind { Expense, Income }

/** Fora do cartão segue a regra do boleto; no cartão, cada ocorrência recebe fatura como compra à vista. */
enum class RecurrenceForm { OffCard, Card }

/**
 * Série sem término no cadastro (mensal ou anual). Despesa exige categoria; renda não tem categoria.
 * [form] só vale para despesa.
 */
data class CreateRecurrence(
    val kind: RecurrenceKind,
    val description: String,
    val categoryId: String?,
    val amount: Money,
    val form: RecurrenceForm = RecurrenceForm.OffCard,
    val accountId: String? = null,
    val frequency: Frequency,
    val firstDate: LocalDate,
    /** Intenção de origem (S7, ADR 0048): vínculo imutável definido na criação; no máximo um compromisso por intenção no espaço. */
    val originIntentionId: String? = null,
    val cardId: String? = null,
) : Command

/**
 * Edita a ocorrência [occurrence] (número na sequência original). O alcance é obrigatório:
 * sem ele o resultado é Decisão necessária e nada muda. Ocorrências já realizadas no alcance
 * exigem [confirmRealized]; o que foi pago continua com o valor efetivo.
 */
data class EditRecurrence(
    val recurrenceId: String,
    val occurrence: Int,
    val scope: RecurrenceScope?,
    val fields: RecurrenceFields,
    val confirmRealized: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/**
 * Exclui [occurrence] só ela (a série continua) ou ela e as próximas (a série termina).
 * Anteriores ficam intactas.
 */
data class DeleteRecurrence(
    val recurrenceId: String,
    val occurrence: Int,
    val scope: RecurrenceScope?,
    val confirmRealized: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/**
 * Confere ou corrige a fatura (mês de vencimento) de uma ocorrência de recorrência no cartão. Vale só para esta ocorrência
 * (ADR 0049): a escolha tira o selo "a conferir", e as demais ocorrências seguem o ciclo. [invoice] fica a no máximo um mês
 * da fatura estimada; mudar de fatura é recusado se alguma das duas já foi quitada.
 */
data class CorrectOccurrenceInvoice(
    val recurrenceId: String,
    val occurrence: Int,
    val invoice: YearMonth,
    val expectedRevision: Long? = null,
) : Command

/**
 * Pagamento de uma ocorrência fora do cartão (ou recebimento de renda recorrente): segue a
 * regra do boleto. No cartão, a ocorrência é paga pela quitação da fatura.
 */
data class PayOccurrence(
    val recurrenceId: String,
    val occurrence: Int,
    val settlement: Settlement,
    val accountId: String? = null,
    val expectedRevision: Long? = null,
) : Command

enum class RefundStatus { Requested, Confirmed }

/**
 * Estorno vinculado a uma compra, boleto ou lançamento direto ([originId]). A soma dos estornos
 * vinculados (solicitados inclusive) não supera o valor da origem (a compra inteira, se parcelada).
 * Só o confirmado afeta totais: abate a categoria da origem no mês da devolução ([date]) ou, no
 * cartão, no mês de vencimento da fatura que recebe o crédito ([invoice], ou a estimada pela data).
 * Estorno não é renda.
 */
data class RegisterRefund(
    val originId: String,
    val amount: Money,
    val status: RefundStatus,
    val date: LocalDate? = null,
    val invoice: YearMonth? = null,
) : Command

/** Confirma um estorno solicitado pelo valor efetivamente devolvido, sem presumir devolução integral. */
data class ConfirmRefund(
    val refundId: String,
    val amount: Money,
    val date: LocalDate,
    val invoice: YearMonth? = null,
    val expectedRevision: Long? = null,
) : Command

/**
 * Pedido de cancelamento de parcelas: continuam no comprometido ("cancelamento solicitado").
 * Não há comando para retirar o pedido; só o desfazer o anula.
 */
data class RequestInstallmentCancellation(
    val purchaseId: String,
    val installmentNumbers: List<Int>,
    val expectedRevision: Long? = null,
) : Command

/** Somente as parcelas informadas saem do comprometido; as demais e o histórico permanecem, sem crédito. */
data class ConfirmNonCharge(
    val purchaseId: String,
    val installmentNumbers: List<Int>,
    val expectedRevision: Long? = null,
) : Command

enum class InvoiceTotalSource { Person, Import }

/**
 * Registra o total cobrado pelo banco. A fatura passa a "fechada" e fica "conferida" quando o total
 * coincide com a composição conhecida; a diferença vira pendência de conferência, sem ajuste
 * automático. Não exige arquivo: a pessoa digita o total e a conferência no banco é dela.
 */
data class RegisterInvoiceTotal(
    val invoice: YearMonth,
    val total: Money,
    val source: InvoiceTotalSource = InvoiceTotalSource.Person,
    val cardId: String? = null,
) : Command

/**
 * Quitação da fatura. Só é aceita quando a fatura está conferida e o valor é igual ao total
 * conferido: a fatura fica paga e seus itens passam a pagos, sem nova despesa. Em qualquer outro
 * caso o pagamento é preservado pelo valor real numa pendência de pagamento divergente, a fatura
 * não é paga e nenhuma despesa ou baixa parcial é criada.
 */
data class PayInvoice(
    val invoice: YearMonth,
    val amount: Money,
    val date: LocalDate,
    /** Padrão do autor quando omitida. */
    val accountId: String? = null,
    val cardId: String? = null,
) : Command

/**
 * Exclui um lançamento (despesa direta, compra no cartão com todas as parcelas, boleto ou renda):
 * registro de exclusão com a mesma identidade, sem apagar operações nem originais importados. Não é
 * estorno nem cancelamento. Compra com estorno vinculado é excluída junto com seus estornos, numa só
 * operação. Item de fatura quitada ou com dados importados exige [confirmExtra]; sem ela, a
 * resposta é Decisão necessária e nada muda. Ocorrência de recorrência usa [DeleteRecurrence].
 */
data class DeleteEntry(
    val entityId: String,
    val confirmExtra: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/** Mantém identidade, ícone e vínculos; os lançamentos passam a exibir o novo nome. */
data class RenameCategory(
    val categoryId: String,
    val name: String,
    val expectedRevision: Long? = null,
) : Command

/** Só apresentação: nenhum valor muda. [orderedIds] é a nova ordem de todos os irmãos do nível. */
data class ReorderCategories(val parentId: String?, val orderedIds: List<String>) : Command

/** Escolha explícita, por mês, sobre a parte de limite de uma subcategoria que muda de categoria. */
data class PartDecision(val month: YearMonth, val destinationPart: Money?)

/**
 * Move uma subcategoria para outra categoria principal, daqui para frente (do mês atual,
 * inclusive): meses anteriores continuam contando os gastos na categoria de origem. Se a
 * subcategoria tem parte do limite, a resposta é Decisão necessária com os meses e valores; a
 * pessoa decide por mês, e a parte nunca é transferida em silêncio.
 */
data class MoveSubcategory(
    val subcategoryId: String,
    val destinationId: String,
    val decisions: List<PartDecision>? = null,
    val expectedRevision: Long? = null,
) : Command

/** Cores que a pessoa pode escolher para uma categoria principal (ADR 0039): índices `0 until SIZE` da paleta do aplicativo. */
object CategoryPalette {
    const val SIZE = 12
}

/**
 * Edita a categoria (ADR 0039): nome, ícone e, só na categoria principal, a cor (índice da paleta). Só o que vem
 * preenchido muda. Subcategoria usa a cor da categoria principal, então não aceita [colorIndex].
 */
data class EditCategory(
    val categoryId: String,
    val name: String? = null,
    val iconKey: String? = null,
    val colorIndex: Int? = null,
    val expectedRevision: Long? = null,
) : Command

/** Até onde a movimentação de lançamentos vale: todos os lançamentos da categoria ou os do mês [FromMonth] em diante. */
sealed interface MoveScope {
    data object All : MoveScope
    data class FromMonth(val month: YearMonth) : MoveScope
}

/**
 * Move para [toId] os lançamentos de [fromId] (e, se [fromId] é principal, os de suas subcategorias) numa só
 * operação atômica (ADR 0039). Compra parcelada entra inteira quando alguma parcela está no alcance; recorrência muda
 * a partir da primeira ocorrência do alcance (as anteriores ficam). Com [removeSource], a origem (e suas subcategorias) é removida na mesma operação, o que
 * exige [MoveScope.All]. Limites nunca são transferidos: se a remoção descarta limites definidos daqui em
 * diante, a resposta é Decisão necessária até [confirmDiscardLimits].
 */
data class MoveCategoryEntries(
    val fromId: String,
    val toId: String,
    val scope: MoveScope,
    val removeSource: Boolean = false,
    val confirmDiscardLimits: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/** Remove uma categoria que já não tem lançamentos (a linha permanece, marcada como removida). */
data class RemoveCategory(
    val categoryId: String,
    val confirmDiscardLimits: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

data class EditAccount(
    val accountId: String,
    val nickname: String? = null,
    val bank: BankRef? = null,
    val expectedRevision: Long? = null,
) : Command

data class CategoryLimitEdit(val categoryId: String, val edit: LimitEdit)

/**
 * Define limites de [month] para as categorias editadas, numa única gravação (uma operação, uma
 * entidade por categoria). O alcance é obrigatório: só o mês ou esse mês e os seguintes. Soma da
 * divisão acima do limite em qualquer mês afetado é rejeitada, listando os meses, sem gravar nada.
 */
data class DefineLimits(
    val month: YearMonth,
    val scope: LimitScope?,
    val edits: List<CategoryLimitEdit>,
) : Command

/**
 * Corrige campos de um lançamento (despesa direta, compra no cartão, boleto ou renda): mesmo
 * lançamento, mesma identidade, meses afetados recalculados. Só o que vem preenchido muda.
 *
 * Compra parcelada: descrição e categoria valem para todas as parcelas, inclusive anteriores; a data
 * da compra muda sem mover faturas (a prévia sugere a fatura que a nova data indicaria); valor e
 * quantidade não são editáveis (excluir e registrar de novo). Compra à vista no cartão: mudar a data
 * reestima só a atribuição ainda estimada. Item de fatura quitada ou importado pede [confirmExtra].
 * Não há como voltar para "a classificar" por aqui; só o desfazer.
 */
data class CorrectEntry(
    val entityId: String,
    val description: String? = null,
    val category: Classification.Category? = null,
    /** Data da movimentação, da compra, do vencimento (boleto) ou prevista (renda). */
    val date: LocalDate? = null,
    val amount: Money? = null,
    val accountId: String? = null,
    val spentBy: String? = null,
    val confirmExtra: Boolean = false,
    val expectedRevision: Long? = null,
    /** Só para renda: troca a categoria de renda. Em outros lançamentos é recusado. */
    val incomeCategoryId: String? = null,
) : Command

/**
 * Troca a forma de pagamento para Pix, débito ou dinheiro ("foi Pix", "foi débito" no Chat). Entre
 * lançamentos diretos mantém a identidade. Compra à vista no cartão (sem estorno, sem fatura quitada e
 * não importada) vira lançamento direto com nova identidade e a compra fica como registro de exclusão,
 * tudo numa só operação desfazível. Parcelada, importada e outras origens não mudam por aqui.
 * Pix e débito usam a conta informada, a atual ou o padrão do autor; dinheiro não tem conta.
 */
data class ChangePaymentMethod(
    val entityId: String,
    val kind: DirectPaymentKind,
    val accountId: String? = null,
    val expectedRevision: Long? = null,
) : Command

/**
 * Confirma uma revisão de importação (S4): cria, vincula, corrige e quita pelos mesmos comandos de S1, tudo numa só transação local, e grava o lote
 * como confirmado (único estado do lote que sincroniza). A operação id é a identidade da confirmação: repetir devolve o resultado original.
 * [summaryToken] identifica o que a pessoa viu; se algo relevante mudou, o resultado é versão desatualizada e nada é aplicado.
 */
data class ConfirmImportBatch(val batchId: String, val summaryToken: String) : Command

/**
 * Resolve uma dúvida que ficou guardada num lote confirmado (S4, fila de pendências): reaplica a reconciliação do movimento com as decisões
 * da pessoa e, se ele agora é claro, executa os mesmos comandos de S1 da confirmação, tudo numa só transação. O ID da operação é a identidade
 * da tentativa. [summaryToken] identifica o que a pessoa viu; se algo mudou, nada é aplicado.
 */
data class ResolveImportItem(val batchId: String, val ordinal: Int, val summaryToken: String) : Command

/** Desfaz uma operação aplicada, se nada nas entidades afetadas mudou depois dela. */
data class Undo(val operationId: String) : Command

// ---- resultados ----

sealed interface CommandResult {
    data class Applied(val receipt: Receipt) : CommandResult

    /** Mesmo ID de comando já processado: nada novo é aplicado. */
    data class AlreadyApplied(val receipt: Receipt) : CommandResult

    /** Nada é gravado. */
    data class Rejected(val errors: List<FieldError>) : CommandResult

    /** Falta uma escolha que a fachada não pode presumir; nada é gravado. */
    data class DecisionNeeded(val decision: Decision) : CommandResult

    /** A entidade mudou desde a versão esperada; nada é gravado e a versão atual vem para comparação. */
    data class StaleVersion(val entityId: String, val currentRevision: Long) : CommandResult
}

enum class ExtraReason { PaidInvoice, Imported }

data class InvolvedPart(val month: YearMonth, val part: Money)

sealed interface Decision {
    /** O valor digitado é o total ou o valor de cada parcela? */
    data class AmountMeaningRequired(val installments: Int, val amount: Money) : Decision

    /** Editar ou excluir recorrência exige escolher "só esta ocorrência" ou "esta e as próximas". */
    data object ScopeRequired : Decision

    /** Ocorrências já realizadas (pagas ou importadas) no alcance: só mudam com confirmação explícita. */
    data class RealizedOccurrences(val numbers: List<Int>) : Decision

    /** Excluir item de fatura quitada ou com dados importados pede uma confirmação a mais. */
    data class ExtraConfirmationRequired(val reasons: List<ExtraReason>) : Decision

    /** Mover subcategoria com parte do limite: a pessoa decide por mês o que fazer com cada parte. */
    data class LimitDivisionReview(val subcategoryId: String, val parts: List<InvolvedPart>) : Decision

    /**
     * Remover categoria com limite (ou parte de limite) definido daqui em diante: o limite não vai para outra
     * categoria, e descartá-lo exige confirmação explícita. [months] são os meses com limite definido.
     */
    data class RemovedCategoryLimits(val categoryId: String, val months: List<YearMonth>) : Decision
}

/** O que uma movimentação de lançamentos alcança (prévia do ADR 0039). Estornos acompanham a origem. */
data class CategoryMoveImpact(
    /** Lançamentos diretos, compras no cartão, boletos e recorrências que mudam de categoria. */
    val entries: Int,
    /** Meses do orçamento afetados, em ordem. */
    val months: List<YearMonth>,
    /** A origem seria removida junto. */
    val removesSource: Boolean,
)

sealed interface PreviewResult {
    data class Ready(
        val before: List<InstallmentView>,
        val after: List<InstallmentView>,
        val affectedMonths: Set<YearMonth>,
        /** Recorrência: ocorrências adiante com exceção própria que a edição substitui. */
        val replacedOccurrences: List<Int> = emptyList(),
        /** Exclusão: estornos vinculados que saem junto com a compra. */
        val linkedRefundIds: List<String> = emptyList(),
        /** Limites: meses posteriores com configuração própria que a edição substitui. */
        val replacedLimitMonths: List<YearMonth> = emptyList(),
        /** Compra parcelada com nova data: a fatura que a data indicaria, oferecida sem ser aplicada. */
        val suggestedInvoice: YearMonth? = null,
        /** Mover lançamentos entre categorias: quantos e em quais meses. */
        val categoryMove: CategoryMoveImpact? = null,
    ) : PreviewResult

    /** O comando seria rejeitado ou pediria uma decisão; o resultado explica o motivo. */
    data class Blocked(val result: CommandResult) : PreviewResult
}

enum class ErrorCode {
    Required,
    MustBePositive,
    UnknownReference,
    DuplicateName,
    NotAllowed,
    ChangedAfter,
    NotSupported,
    OutOfRange,
    ExceedsRemaining,
    SplitExceedsLimit,
}

/** [remaining] acompanha o erro quando há um limite a informar (ex.: restante a estornar). */
data class FieldError(
    val field: String,
    val code: ErrorCode,
    val message: String,
    val remaining: Money? = null,
    /** Mês a que o erro se refere, quando o erro é por mês (ex.: divisão acima do limite em dezembro). */
    val month: YearMonth? = null,
)

enum class EntityType { Category, Account, Preferences, Movement, Card, CardPurchase, Boleto, Income, Recurrence, Refund, Invoice, InvoicePayment, LimitPlan, IncomeCategory, ImportBatch, AlertEvent, IconAssociation, Intention, AccountMove, ClueEvaluation, SavingsPocket, SavingsMove }

data class EntityRef(val type: EntityType, val id: String)

/** Uma entidade tocada por uma operação, com a revisão local que a operação produziu. */
data class EntityChange(val type: EntityType, val id: String, val baseVersion: Long?, val revision: Long)

data class Receipt(
    val operationId: String,
    val sequence: Long,
    val commandType: String,
    val changes: List<EntityChange>,
    val affectedMonths: Set<YearMonth>,
    val undoable: Boolean,
)

/** Operação local gravada junto com a alteração (S1 §5.1); S2 lê esta saída. */
data class LocalOperation(
    val sequence: Long,
    val operationId: String,
    val spaceId: String,
    val authorId: String,
    val origin: Origin,
    val commandType: String,
    val deviceTimeMillis: Long,
    val changes: List<EntityChange>,
    val acknowledged: Boolean,
)

// ---- consultas ----

/** Falha de leitura vem identificada, nunca como zero. */
sealed interface QueryResult<out T> {
    data class Data<T>(val value: T) : QueryResult<T>
    data class Failure(val cause: Throwable) : QueryResult<Nothing>
}

data class CategoryNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val order: Int,
    val iconKey: String,
    val seedKey: String?,
    /** Cor escolhida na categoria principal (índice da paleta); nulo vale a cor derivada. Subcategoria usa a do pai. */
    val colorIndex: Int? = null,
    val revision: Long = 0,
)

/** Escolha de ícone ativa: [target] é a chave normalizada do nome ([IconScope.Name]) ou a identidade do lançamento ([IconScope.Entry]). */
data class IconAssociationView(
    val id: String,
    val scope: IconScope,
    val target: String,
    val icon: IconRef,
    val revision: Long,
)

/** Intenção de compra (S7). [createdBy] e [updatedBy] são identidades de pessoa do espaço; os instantes vêm do relógio do aparelho que salvou. */
data class IntentionView(
    val id: String,
    val name: String,
    /** Nulo: sem valor definido. Zero não existe aqui. */
    val estimatedValue: Money?,
    val desiredMonth: YearMonth?,
    val note: String?,
    val createdBy: String,
    val updatedBy: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long,
    /** "Salvo no aparelho" difere de "reconhecido": se o outro celular já pode ver esta intenção. */
    val sync: ItemSync = ItemSync.SavedOnDevice,
    /** Compromisso que nasceu desta intenção; nulo enquanto planejada. "Convertida" é derivada deste vínculo, nunca um campo editável. */
    val conversion: IntentionConversion? = null,
) {
    val situation: IntentionSituation get() = if (conversion == null) IntentionSituation.Planned else IntentionSituation.Converted
}

enum class IntentionSituation { Planned, Converted }

/** O compromisso vinculado: [type] é CardPurchase, Movement, Boleto ou Recurrence, e [id] a identidade da raiz (a compra, o lançamento, o boleto ou a série). */
data class IntentionConversion(val type: EntityType, val id: String)

data class IncomeCategoryNode(
    val id: String,
    val name: String,
    val order: Int,
    val iconKey: String,
    val seedKey: String?,
    val revision: Long = 0,
    /** Cor escolhida (índice da paleta, ADR 0038); nulo vale a cor derivada da semente ou da identidade. */
    val colorIndex: Int? = null,
)

data class MovementRow(
    val id: String,
    val kind: DirectPaymentKind,
    val description: String,
    val classification: Classification,
    val date: LocalDate,
    val amount: Money,
    val accountId: String?,
    val spentBy: String?,
    val revision: Long,
)

data class CardInfo(
    val id: String,
    val bank: BankRef?,
    val nickname: String,
    val closingDay: Int,
    val dueDay: Int,
    val creditLimit: Money? = null,
    /** Verdadeiro depois que alguém configurou o cartão (aqui ou no outro aparelho); a semente 8/15 não conta. */
    val configured: Boolean = true,
    val accountId: String? = null,
    val isDefault: Boolean = false,
    val revision: Long = 0,
)

/** Estado da atribuição de fatura de uma parcela (S1 §6). */
enum class AssignmentState { Estimated, ToConfirm, Confirmed }

enum class CancelState { Active, CancelRequested, Cancelled }

data class InstallmentView(
    val number: Int,
    val amount: Money,
    val invoiceMonth: YearMonth,
    val assignment: AssignmentState,
    val cancel: CancelState = CancelState.Active,
)

data class RefundView(
    val id: String,
    val originId: String,
    val amount: Money,
    val status: RefundStatus,
    val date: LocalDate?,
    /** Mês do orçamento em que o crédito abate; nulo enquanto solicitado. */
    val creditMonth: YearMonth?,
    val revision: Long,
    val voided: Boolean,
)

data class PurchaseDetail(
    val id: String,
    val description: String,
    val classification: Classification,
    val purchaseDate: LocalDate,
    val total: Money,
    val isCharge: Boolean,
    val revision: Long,
    val voided: Boolean,
    val installments: List<InstallmentView>,
)

data class BoletoView(
    val id: String,
    val description: String,
    val classification: Classification,
    val dueDate: LocalDate,
    val expectedAmount: Money,
    val paidDate: LocalDate?,
    val paidAmount: Money?,
    val accountId: String?,
    /** Mês do orçamento: o do pagamento efetivo, ou o do vencimento enquanto não pago. */
    val budgetMonth: YearMonth,
    /** Vencido e não pago: continua no mês do vencimento, marcado como pendente. */
    val overdue: Boolean,
    val revision: Long,
    val voided: Boolean,
)

data class IncomeView(
    val id: String,
    val description: String,
    val expectedAmount: Money,
    val expectedDate: LocalDate,
    val receivedDate: LocalDate?,
    val receivedAmount: Money?,
    val accountId: String?,
    val budgetMonth: YearMonth,
    val revision: Long,
    val voided: Boolean,
    val incomeCategoryId: String? = null,
)

/** Renda do mês: recebido e a receber como partes do esperado (R$ 6.000 + R$ 500 = R$ 6.500). */
data class IncomeSummary(val received: Money, val toReceive: Money, val items: List<IncomeView>) {
    val expected: Money get() = received + toReceive
}

data class RecurrenceView(
    val id: String,
    val kind: RecurrenceKind,
    val form: RecurrenceForm,
    val frequency: Frequency,
    val firstDate: LocalDate,
    /** Ocorrências de número igual ou maior deixaram de existir; nulo enquanto a série não termina. */
    val endsBefore: Int?,
    val revision: Long,
    val voided: Boolean,
)

data class OccurrenceView(
    val recurrenceId: String,
    val number: Int,
    val kind: RecurrenceKind,
    val form: RecurrenceForm,
    /** Data prevista pela sequência original. */
    val scheduledDate: LocalDate,
    val description: String,
    val categoryId: String?,
    val expectedAmount: Money,
    val accountId: String?,
    val settledDate: LocalDate?,
    val settledAmount: Money?,
    val budgetMonth: YearMonth,
    val paid: Boolean,
    val assignment: AssignmentState?,
    /** Fatura estimada pelo ciclo do cartão; nula fora do cartão. Difere de [budgetMonth] quando a pessoa escolheu outra. */
    val estimatedInvoice: YearMonth? = null,
    val cardId: String? = null,
)

/** Fase exibida da fatura; "fechada" quando um total foi registrado (pessoa ou importação). */
enum class InvoiceStatus { Open, ClosedEstimated, Closed }

data class PaymentView(val id: String, val amount: Money, val date: LocalDate, val accountId: String?, val accepted: Boolean)

data class InvoiceView(
    val dueMonth: YearMonth,
    /** Compras e parcelas (inclusive ocorrências recorrentes no cartão), sem encargos. */
    val purchases: Money,
    val charges: Money,
    val credits: Money,
    /** Composição conhecida: compras + encargos − créditos. */
    val knownTotal: Money,
    val checkedTotal: Money?,
    val checkedSource: InvoiceTotalSource?,
    /** O total registrado coincide com a composição conhecida. */
    val conferred: Boolean,
    /** Total registrado menos a composição, quando diverge. */
    val totalDifference: Money?,
    val paid: Boolean,
    val paidDate: LocalDate?,
    val status: InvoiceStatus,
    val linksToConfirm: Int,
    /** Pagamentos preservados em revisão: a fatura não foi paga por eles. */
    val divergentPayments: List<PaymentView>,
    val revision: Long,
    val cardId: String? = null,
)

// ---- lançamentos, detalhe, compromissos e pendências ----

enum class EntryKind { DirectPayment, CardPurchase, CardInstallment, Boleto, Income, Refund, InvoicePayment, RecurrenceOccurrence }

enum class PaymentMethod { Pix, Debit, Cash, Card, Boleto }

/** Mês do orçamento (parcela no mês de vencimento da fatura) ou data da compra (a compra uma vez, pelo total). */
enum class DateCriterion { BudgetMonth, PurchaseDate }

enum class EntrySituation {
    ToClassify,
    /** Atribuição de fatura "a conferir". */
    InvoiceLinkToConfirm,
    /** Vencido e não pago: continua no mês original, marcado como pendente. */
    Overdue,
    Paid,
    NotPaid,
    CancelRequested,
    Cancelled,
    /** Pagamento de fatura preservado em revisão. */
    DivergentPayment,
}

data class EntryFilter(
    val from: YearMonth,
    val to: YearMonth,
    val criterion: DateCriterion = DateCriterion.BudgetMonth,
    /** Categoria principal ou subcategoria. */
    val categoryId: String? = null,
    val methods: Set<PaymentMethod> = emptySet(),
    val situations: Set<EntrySituation> = emptySet(),
    /** Quem registrou ou quem gastou; filtro auxiliar, sem orçamento individual. */
    val person: String? = null,
    val search: String? = null,
    val cardId: String? = null,
)

data class EntryRow(
    /** Estável para navegação: ID do lançamento; `compra#n` para parcela; `série:n` para ocorrência. */
    val id: String,
    val kind: EntryKind,
    val description: String,
    val categoryId: String?,
    /** Valor aplicável ao critério: a parcela no orçamento, o total na compra. */
    val amount: Money,
    /** Data pertinente (da compra, do pagamento, do vencimento ou da devolução). */
    val date: LocalDate,
    val dueDate: LocalDate?,
    val method: PaymentMethod?,
    val installmentNumber: Int?,
    val installmentCount: Int?,
    val situations: Set<EntrySituation>,
    val authorId: String?,
    val cardId: String? = null,
    /** Categoria de renda (lista própria, distinta da de despesa); só preenchida em [EntryKind.Income]. */
    val incomeCategoryId: String? = null,
    /**
     * Só na compra parcelada ([EntryKind.CardPurchase] com mais de uma parcela): valor da parcela [installmentNumber] (a do mês filtrado), para a lista
     * não exibir o total. [amount] segue sendo o total da compra, e é ele que as somas usam.
     */
    val installmentAmount: Money? = null,
)

/**
 * Somas separadas por tipo; quitações de fatura nunca entram em despesa nem em saldo.
 * [incomes] mistura renda recebida e prevista; o saldo usa só [receivedIncomes], e renda prevista não vira saldo.
 */
data class EntrySummary(
    val expenses: Money,
    val incomes: Money,
    val credits: Money,
    val invoicePayments: Money,
    val receivedIncomes: Money = Money.ZERO,
    /** Quantos lançamentos de renda e de despesa entram nas somas [incomes] e [expenses]. */
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
) {
    /** Resultado da lista: rendas (recebidas e previstas) menos despesas, como o resumo da lista mostra. */
    val listResult: Money get() = incomes - expenses

    /** Despesa comprometida (paga e a pagar) já abatida dos estornos confirmados; estorno não vira renda. */
    val committedExpenses: Money get() = expenses - credits

    /** Renda recebida menos despesa comprometida, sobre as mesmas linhas filtradas. */
    val balance: Money get() = receivedIncomes - committedExpenses
}

/**
 * Cabeçalho de Lançamentos: [total] acumula desde o primeiro lançamento até o fim do mês atual;
 * [month] é só o mês atual. Os dois ignoram busca, filtros e o mês escolhido na lista.
 */
data class TotalBalance(val total: EntrySummary, val month: EntrySummary)

data class EntryPage(val rows: List<EntryRow>, val nextCursor: Int?, val total: Int, val summary: EntrySummary)

/** Situação do item quanto à sincronização; fornecida por S2. Antes dele, tudo está "salvo no aparelho". */
enum class ItemSync {
    SavedOnDevice,

    /** Há operação ainda não reconhecida pelo serviço (pendente, em envio ou retida). */
    AwaitingSend,
    Acknowledged,
    Conflict,

    /** O serviço recusou a operação; ela continua guardada com a causa. */
    Failed,
}

fun interface SyncStatusProvider {
    suspend fun statusOf(type: EntityType, id: String): ItemSync
}

data class EntryDetail(
    val id: String,
    val kind: EntryKind,
    val description: String,
    val classification: Classification,
    val amount: Money,
    val purchaseDate: LocalDate?,
    val dueDate: LocalDate?,
    val settledDate: LocalDate?,
    val method: PaymentMethod?,
    val accountId: String?,
    val authorId: String?,
    val spentBy: String?,
    val origin: Origin?,
    val revision: Long,
    val voided: Boolean,
    val sync: ItemSync,
    val purchase: PurchaseDetail?,
    val boleto: BoletoView?,
    val income: IncomeView?,
    val occurrence: OccurrenceView?,
    val refunds: List<RefundView>,
    val cardId: String? = null,
)

enum class CommitmentKind { All, Installments, Recurrences, Bills }

enum class CommitmentNature { Finite, Recurring, Bill }

data class Commitment(
    /** `compra#n`, `série:n` ou o ID do boleto. */
    val id: String,
    val nature: CommitmentNature,
    val description: String,
    val categoryId: String?,
    /** Valor exigível no mês. */
    val amount: Money,
    val dueDate: LocalDate,
    val number: Int?,
    /** Total de parcelas; nulo em recorrência, que não tem total nem "1/∞". */
    val count: Int?,
    val situations: Set<EntrySituation>,
    val method: PaymentMethod?,
)

data class MonthCommitments(
    val month: YearMonth,
    val items: List<Commitment>,
    /** Soma dos conhecidos do mês; sem intenções nem estimativas variáveis. */
    val knownTotal: Money,
)

data class ScheduleRow(
    val number: Int,
    val invoiceMonth: YearMonth,
    val dueDate: LocalDate,
    val amount: Money,
    val situations: Set<EntrySituation>,
)

sealed interface CommitmentDetail {
    val id: String

    /** Compra parcelada: cronograma completo de 1/N a N/N, inclusive canceladas. */
    data class Installments(
        override val id: String,
        val purchase: PurchaseDetail,
        val schedule: List<ScheduleRow>,
    ) : CommitmentDetail

    /** Recorrência: frequência e próxima cobrança, sem total de parcelas. */
    data class Recurring(
        override val id: String,
        val recurrence: RecurrenceView,
        val current: OccurrenceView,
        val next: OccurrenceView?,
    ) : CommitmentDetail

    data class Bill(override val id: String, val boleto: BoletoView) : CommitmentDetail
}

sealed interface PendingItem {
    val month: YearMonth

    /** Categoria a esclarecer: lançamento "a classificar". */
    data class CategoryToClarify(
        val entryId: String,
        val description: String,
        val amount: Money,
        override val month: YearMonth,
    ) : PendingItem

    /** Atribuição de fatura "a conferir" (selo na compra no cartão). */
    data class InvoiceLink(
        val purchaseId: String,
        val description: String,
        val amount: Money,
        override val month: YearMonth,
        val cardId: String? = null,
    ) : PendingItem

    /** Pagamento de fatura preservado pelo valor real, sem baixa parcial. */
    data class DivergentInvoicePayment(
        val paymentId: String,
        val amount: Money,
        val date: LocalDate,
        override val month: YearMonth,
        val cardId: String? = null,
    ) : PendingItem

    /** Total registrado diverge da composição conhecida; nada é ajustado automaticamente. */
    data class InvoiceTotalDifference(val difference: Money, override val month: YearMonth, val cardId: String? = null) : PendingItem
}

data class AccountInfo(val id: String, val bank: BankRef, val nickname: String, val isWallet: Boolean = false)

/**
 * [accounts] são as contas escolhíveis nos registros (bancos); a [wallet] (ADR 0051) fica à parte: dinheiro sai dela, e ela nunca é conta
 * padrão nem opção de Pix, débito, boleto ou renda.
 */
data class AccountsView(val accounts: List<AccountInfo>, val myDefaultAccountId: String?, val wallet: AccountInfo? = null)

/** Linha "A classificar": soma e quantidade, sem limite (S1 §7.3). */
data class ToClassifyLine(val amount: Money, val count: Int)

/** Limite vigente: valor ou "não definido", divisão e de onde vem a configuração. */
data class CategoryLimit(
    val categoryId: String,
    val setting: LimitSetting,
    /** Início da vigência que vale no mês; nulo se não há limite definido. */
    val sinceMonth: YearMonth?,
    /** O mês tem uma exceção própria (alteração só daquele mês). */
    val isException: Boolean,
    /** Meses posteriores com configuração própria (alterações futuras existentes). */
    val laterChanges: List<YearMonth>,
)

data class CategoryBudget(
    val categoryId: String,
    /** Gastos da categoria principal, somando todas as subcategorias e o que não tem subcategoria. */
    val gross: Money,
    val credits: Money,
    val net: Money,
    val paid: Money,
    /** Por subcategoria; a chave nula é "Sem subcategoria". */
    val bySubcategory: Map<String?, Money>,
    /** Limite, restante ou excesso, divisão e "sem distribuir" (S1 §7.5). */
    val limit: LimitView,
)

data class IncomeTotals(val received: Money, val toReceive: Money)

/**
 * Orçamento geral do mês: [spent] é o gasto líquido de todas as categorias (inclui "a classificar", abatido dos estornos
 * confirmados); [view] traz restante ou excesso. Sem total definido, é "não definido", nunca zero.
 */
data class GeneralBudgetView(val limit: CategoryLimit, val spent: Money, val view: LimitView) {
    val isDefined: Boolean get() = view.total != null

    companion object {
        fun undefined(month: YearMonth) = GeneralBudgetView(
            CategoryLimit(GENERAL_BUDGET_ID, LimitSetting.UNDEFINED, null, false, emptyList()),
            Money.ZERO, LimitView.of(LimitSetting.UNDEFINED, Money.ZERO, emptyMap()),
        )
    }
}

data class MonthBudget(
    val month: YearMonth,
    val committed: Money,
    /** Soma dos limites definidos das categorias principais; a divisão não entra de novo. */
    val totalLimits: Money,
    /** Subconjunto do comprometido; nunca somado a ele. */
    val paid: Money,
    val toClassify: ToClassifyLine,
    /** Créditos de estorno confirmados no mês, separados do bruto. */
    val credits: Money,
    /** Vínculos de fatura "a conferir" que ocupam o mês; o resumo avisa que há valores provisórios. */
    val invoiceLinksToConfirm: Int,
    val income: IncomeTotals,
    val categories: List<CategoryBudget>,
    /** Total do mês que vale para todas as categorias; as categorias não precisam de limite próprio. */
    val general: GeneralBudgetView = GeneralBudgetView.undefined(month),
)

// ---- assinaturas (S6, telas 31 e 32) ----

/** Ativa tem próxima cobrança; encerrada é série que já terminou (esta e as próximas excluídas) e mantém o histórico. */
enum class SubscriptionStatus { Active, Ended }

data class SubscriptionRow(
    val recurrenceId: String,
    val description: String,
    val categoryId: String?,
    val frequency: Frequency,
    val form: RecurrenceForm,
    val status: SubscriptionStatus,
    /** Próxima cobrança prevista a partir do início do período consultado; nula se a série terminou. */
    val next: OccurrenceView?,
    val comparison: ChargeComparison,
)

/** Soma dos valores esperados das próximas cobranças de assinaturas ativas com a mesma frequência. Frequências nunca são convertidas nem somadas entre si. */
data class FrequencySubtotal(val frequency: Frequency, val count: Int, val expected: Money)

data class SubscriptionList(
    val from: LocalDate,
    val untilExclusive: LocalDate,
    /** Ativas pela próxima cobrança, depois as encerradas por descrição. */
    val items: List<SubscriptionRow>,
    val subtotals: List<FrequencySubtotal>,
    /** Soma das ocorrências que vencem no intervalo (valor efetivo se já paga): soma de cobranças do período, não conversão de frequência. */
    val dueInPeriod: Money,
    val dueCount: Int,
)

data class SubscriptionDetail(
    val recurrence: RecurrenceView,
    val description: String,
    val categoryId: String?,
    val status: SubscriptionStatus,
    val next: OccurrenceView?,
    /** Cobranças realizadas (efetivadas), da mais antiga para a mais nova. */
    val charges: List<SubscriptionCharge>,
    val comparison: ChargeComparison,
    /** Esperado da próxima cobrança menos a última realizada: divergência mostrada como tal, sem reescrever a expectativa. Nulo sem uma das duas. */
    val nextVersusLastCharge: Money?,
)

// ---- atividade de compras (S6, tela 30) ----

/** Soma e quantidade de um grupo da atividade. */
data class ActivityLine(val total: Money, val count: Int)

/** [subcategoryId] nulo é "Sem subcategoria": compras classificadas direto na categoria principal. */
data class ActivitySubgroup(val subcategoryId: String?, val line: ActivityLine)

data class ActivityGroup(val categoryId: String, val line: ActivityLine, val subgroups: List<ActivitySubgroup>)

/** Compras da semana que começa em [weekStart] (segunda-feira), dentro do período; semanas sem compras conhecidas não aparecem. */
data class ActivityWeek(val weekStart: LocalDate, val line: ActivityLine)

/**
 * Atividade de compras pela **data original**, não pelo mês do orçamento. Soma por categoria mais [toClassify] fecha o [total]. Créditos e
 * lacunas ficam em linhas próprias e não entram no total: estorno não é descontado em silêncio e boleto sem data de compra não ganha uma data
 * emprestada do vencimento (o vencimento só localiza o aviso no período).
 */
data class PurchaseActivity(
    val from: LocalDate,
    val untilExclusive: LocalDate,
    val categoryId: String?,
    val total: ActivityLine,
    val groups: List<ActivityGroup>,
    /** Compras sem categoria ("a classificar"): grupo próprio, fora de qualquer categoria. Nulo se não houver. */
    val toClassify: ActivityLine?,
    val weeks: List<ActivityWeek>,
    /** Estornos confirmados com data de devolução no período; nulo se não houver. */
    val credits: ActivityLine?,
    /** Boletos sem data de compra com vencimento no período: ficam fora do total. Nulo se não houver. */
    val gaps: ActivityLine?,
    /** Compras do período com cancelamento solicitado ou confirmado (o total já desconta as parcelas canceladas confirmadas). */
    val withCancellation: Int,
)

// ---- comparações históricas (S6, tela 33) ----

enum class ComparisonGrouping { Category, Subcategory }

data class ComparisonPeriod(val from: LocalDate, val untilExclusive: LocalDate) {
    init {
        require(from.isBefore(untilExclusive)) { "O período precisa ter ao menos um dia" }
    }

    val days: Int get() = java.time.temporal.ChronoUnit.DAYS.between(from, untilExclusive).toInt()
}

/**
 * O que o período tem de limitação, mostrado antes da interpretação (tela 33 §4). [inProgressOrFuture]: o período inclui hoje ou dias futuros,
 * então tem valores previstos ou ainda por acontecer. [provisionalLinks]: vínculos de fatura "a conferir" que entram provisoriamente (só no
 * critério do orçamento). [toClassify]: compras sem categoria, que não pertencem a nenhum grupo. [failed]: a leitura deste período falhou.
 */
data class ComparisonSide(
    val period: ComparisonPeriod,
    val failed: Boolean,
    val inProgressOrFuture: Boolean,
    val provisionalLinks: Int,
    val toClassify: Money?,
)

data class PeriodComparison(
    val criterion: DateCriterion,
    val grouping: ComparisonGrouping,
    val a: ComparisonSide,
    val b: ComparisonSide,
    /** Os períodos têm números de dias diferentes (por exemplo fevereiro e março): a variação compara tamanhos diferentes. */
    val differentDuration: Boolean,
    val rows: List<ComparisonRow>,
    /** Soma das linhas de cada período (incluindo "a classificar"), para o total fechar com a lista. */
    val totalA: Measure,
    val totalB: Measure,
    val totalChange: PeriodChange,
)

// ---- revisão semanal (S6, tela 29) ----

/** Um bloco da revisão: a falha de um não esconde os outros e pode ser repetida. */
sealed interface ReviewBlock<out T> {
    data class Ready<T>(val value: T) : ReviewBlock<T>
    data object Failed : ReviewBlock<Nothing>
}

/** Categoria com possível excesso ou excesso já ocorrido, pela previsão do S5 no mês da consulta (situação atual, não a do fim da semana). */
data class ReviewAttentionItem(val categoryId: String, val excess: Excess)

data class ReviewAttention(val month: YearMonth, val items: List<ReviewAttentionItem>)

/** Compromissos ainda não pagos que vencem de [from] (a consulta) até [untilExclusive], 7 dias depois. */
data class ReviewUpcoming(val from: LocalDate, val untilExclusive: LocalDate, val items: List<Commitment>)

data class WeeklyReviewSummary(
    val period: ReviewPeriod,
    val reference: LocalDate,
    /** Movimentos do período pela data original da compra. */
    val movements: ReviewBlock<PurchaseActivity>,
    val attention: ReviewBlock<ReviewAttention>,
    /** Situação atual das pendências na data da consulta, não a da semana revisada. */
    val pendings: ReviewBlock<List<PendingItem>>,
    val upcoming: ReviewBlock<ReviewUpcoming>,
) {
    /** Pontos de leitura na ordem da tela: categorias, pendências e compromissos. Blocos com falha não têm pontos. */
    val points: List<ReviewPoint>
        get() = buildList {
            (attention as? ReviewBlock.Ready)?.value?.items?.forEach { add(ReviewPoint(ReviewPoint.Kind.Category, it.categoryId)) }
            (pendings as? ReviewBlock.Ready)?.value?.forEach { add(ReviewPoint(ReviewPoint.Kind.Pending, it.reviewId)) }
            (upcoming as? ReviewBlock.Ready)?.value?.items?.forEach { add(ReviewPoint(ReviewPoint.Kind.Commitment, it.id)) }
        }
}

/** Identidade estável de uma pendência para o progresso de leitura da revisão. */
val PendingItem.reviewId: String
    get() = when (this) {
        is PendingItem.CategoryToClarify -> "classify:$entryId"
        is PendingItem.InvoiceLink -> "link:$purchaseId"
        is PendingItem.DivergentInvoicePayment -> "payment:$paymentId"
        is PendingItem.InvoiceTotalDifference -> "difference:${cardId.orEmpty()}:$month"
    }

/** Preferencia individual sincronizada. */
data class SetMyDefaultCard(val cardId: String) : Command
