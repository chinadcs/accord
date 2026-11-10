package app.accord.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.accord.R
import com.composables.icons.lucide.R as Lucide

/**
 * As cinco raízes da barra inferior, na ordem Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071; README §3). Cada uma é um grafo próprio: a pilha de cada aba
 * é salva e restaurada ao alternar, e tocar de novo na aba atual não apaga filtros nem muda o mês.
 */
enum class Root(
    val graph: String,
    val start: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home("home_graph", "home", R.string.tab_home, Lucide.drawable.lucide_ic_house),
    Entries("entries_graph", "entries", R.string.tab_entries, Lucide.drawable.lucide_ic_list),
    Chat("chat_graph", "chat", R.string.tab_chat, R.drawable.lucide_ic_astroid),
    Budget("budget_graph", "budget", R.string.tab_budget, Lucide.drawable.lucide_ic_wallet),
    More("more_graph", "more", R.string.tab_more, Lucide.drawable.lucide_ic_ellipsis),
}

/** Acesso (renovação da sessão), espaço do casal e primeiros passos, abertos por cima do aplicativo. */
const val ROUTE_ACCESS = "access"
const val ROUTE_SPACE = "space"
const val ROUTE_FIRST_STEPS = "first_steps"
const val ROUTE_ALERT_PREFS = "alert_prefs"

/** Escolha dos apps cujas notificações o Accord lê (ADR 0069). */
const val ROUTE_BANK_NOTIFICATIONS = "bank_notifications"

/** Previsões e alertas (tela 20), `forecasts?month=2026-10`, e o detalhe da previsão (tela 21), `forecast/{categoryId}/{month}?event=` (event: a chave do aviso que a abriu). */
const val ROUTE_FORECASTS = "forecasts"
const val ROUTE_FORECAST = "forecast"

/** Sincronização (tela 26) e o modo conflito da tela 19: `conflict/{operationId}`. */
const val ROUTE_SYNC = "sync"
const val ROUTE_CONFLICT = "conflict"

/**
 * Registro manual: sempre disponível, sem depender do modelo nem da rede (tela 09). Aberto pelo Chat, leva o
 * formulário já preenchido com o que foi entendido (`form`, ainda sem salvar) e a mensagem de origem.
 */
const val ROUTE_REGISTER = "register"
const val ARG_REGISTER_KIND = "kind"
const val ARG_REGISTER_FORM = "form"
const val ARG_REGISTER_CHAT = "chatMessage"

/** Modelo no aparelho (tela 25), aberto pelo indicador do Chat e pelos Primeiros passos. */
const val ROUTE_MODEL = "model"

/** Compromissos futuros, com mês opcional: `commitments?month=2026-11`. */
const val ROUTE_COMMITMENTS = "commitments"
const val ARG_MONTH = "month"

/** Simulador de compra (tela 36, P2): consulta pura, não grava nada. Pré-preenchido por `total` (centavos), `month` (AAAA-MM) e `intention`. */
const val ROUTE_SIMULATOR = "simulator"

/** Intenções de compra (tela 34) e uma intenção (tela 35): `intention/{id}` ou `intention/new?value=centavos&month=AAAA-MM`. */
const val ROUTE_INTENTIONS = "intentions"
const val ROUTE_CLUES = "clues"
const val ROUTE_CLUE = "clue"
const val ROUTE_INTENTION = "intention"

/** Detalhe de um compromisso: `commitment/{id}`; o ID pode ter `#` ou `:`, por isso vai codificado. */
const val ROUTE_COMMITMENT = "commitment"

/** Assinaturas (tela 31) e detalhe (tela 32): `subscription/{recurrenceId}`. */
const val ROUTE_SUBSCRIPTIONS = "subscriptions"
const val ROUTE_SUBSCRIPTION = "subscription"

/** Cartão e faturas (tela 12) e detalhe da fatura: `invoice/{yyyy-MM}`. */
const val ROUTE_CARD = "card"
const val ROUTE_INVOICE = "invoice"

/** Categorias, contas e limites, abertos pela aba Mais; voltar retorna a ela. */
const val ROUTE_CATEGORIES = "categories"
const val ROUTE_CATEGORY_MOVE = "category-move"
const val ROUTE_ACCOUNTS = "accounts"
const val ROUTE_LIMITS = "limits"

/**
 * Importação (S4): o hub `import_hub`, aberto por Mais (continuar, histórico, cobertura), escolher o arquivo `import`, revisar o lote
 * `import_review/{batchId}` e ver o resultado `import_result/{batchId}`.
 */
const val ROUTE_IMPORT_HUB = "import_hub"
const val ROUTE_IMPORT = "import"
const val ROUTE_IMPORT_REVIEW = "import_review"
const val ROUTE_IMPORT_RESULT = "import_result"

/** Fila de pendências (tela 18), `pending?batch=` filtrada por lote, e a resolução de uma dúvida de importação `pending_item/{batchId}/{ordinal}` (tela 19). */
const val ROUTE_PENDING = "pending"
const val ROUTE_PENDING_ITEM = "pending_item"

/** Rendas do mês: `incomes?month=2026-11`. */
const val ROUTE_INCOMES = "incomes"

/** "Quem me deve" (ADR 0083): lista e detalhe de devedores; `receivable_new` é o formulário (valor, conta e data podem vir do Registrar). */
const val ROUTE_RECEIVABLES = "receivables"
const val ROUTE_RECEIVABLE_NEW = "receivable_new"

/** Edição de um lançamento existente: `edit/{entryId}`. */
const val ROUTE_EDIT = "edit"

/** Categoria no mês: `category/{categoryId}/{month}`, com o mês como `2026-10`. */
const val ROUTE_CATEGORY = "category"

/** Detalhe do lançamento: `entry/{entryId}`; o ID pode ter `#` ou `:`, por isso vai codificado. */
const val ROUTE_ENTRY = "entry"

/** Lista de Lançamentos aberta de outra tela, com filtros já aplicados; voltar retorna à origem. */
const val ROUTE_ENTRIES_FILTERED = "entries_filtered"
const val ARG_FILTER_MONTH = "filterMonth"
const val ARG_FILTER_CATEGORY = "filterCategory"
const val ARG_FILTER_SITUATION = "filterSituation"
const val ARG_FILTER_CRITERION = "filterCriterion"

/** Atividade de compras (tela 30): compras pela data original. */
const val ROUTE_ACTIVITY = "activity"

/** Comparações históricas (tela 33). */
const val ROUTE_COMPARISON = "comparison"

/** Revisão semanal (tela 29). */
const val ROUTE_WEEKLY_REVIEW = "weekly_review"

/** Revisar, aberto pelo Início ou Mais; voltar preserva a origem. */
const val ROUTE_REVIEW = "review"
