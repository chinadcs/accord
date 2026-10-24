package app.accord.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.accord.R
import com.composables.icons.lucide.R as Lucide

/**
 * As cinco raízes da barra inferior (README §3). Cada uma é um grafo próprio: a pilha de cada aba
 * é salva e restaurada ao alternar, e tocar de novo na aba atual não apaga filtros nem muda o mês.
 */
enum class Root(
    val graph: String,
    val start: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Chat("chat_graph", "chat", R.string.tab_chat, Lucide.drawable.lucide_ic_message_circle),
    Budget("budget_graph", "budget", R.string.tab_budget, Lucide.drawable.lucide_ic_wallet),
    Entries("entries_graph", "entries", R.string.tab_entries, Lucide.drawable.lucide_ic_list),
    Review("review_graph", "review", R.string.tab_review, Lucide.drawable.lucide_ic_circle_check),
    More("more_graph", "more", R.string.tab_more, Lucide.drawable.lucide_ic_ellipsis),
}

/** Registro manual: sempre disponível, sem depender do modelo nem da rede (tela 09). */
const val ROUTE_REGISTER = "register"
const val ARG_REGISTER_KIND = "kind"

/** Compromissos futuros, com mês opcional: `commitments?month=2026-11`. */
const val ROUTE_COMMITMENTS = "commitments"
const val ARG_MONTH = "month"

/** Detalhe de um compromisso: `commitment/{id}`; o ID pode ter `#` ou `:`, por isso vai codificado. */
const val ROUTE_COMMITMENT = "commitment"

/** Cartão e faturas (tela 12) e detalhe da fatura: `invoice/{yyyy-MM}`. */
const val ROUTE_CARD = "card"
const val ROUTE_INVOICE = "invoice"

/** Categorias, contas e limites, abertos pela aba Mais; voltar retorna a ela. */
const val ROUTE_CATEGORIES = "categories"
const val ROUTE_ACCOUNTS = "accounts"
const val ROUTE_LIMITS = "limits"

/** Rendas do mês: `incomes?month=2026-11`. */
const val ROUTE_INCOMES = "incomes"

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
