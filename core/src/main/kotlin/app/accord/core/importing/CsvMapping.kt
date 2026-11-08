package app.accord.core.importing

import app.accord.core.space.CategoryNode
import java.text.Normalizer

/**
 * Casa os nomes das colunas `categoria` e `origem` do CSV do Accord (ADR 0042) com o que existe no espaço. O casamento é por nome, sem
 * acento, caixa nem espaços repetidos, e **nunca por semelhança**: sem correspondência exata e única, não há resultado e a pessoa decide.
 */
object CsvMapping {
    /** Texto da coluna `categoria` do movimento, como veio no arquivo. */
    fun fileCategory(m: ExtractedMovement): String? = m.originalFields.firstOrNull { it.first == "CATEGORIA" }?.second?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * `Mercado`, `Alimentação` ou `Alimentação > Mercado` (também com `/`). Um nome sozinho vale a categoria principal que o tem; se só houver
     * subcategorias com esse nome em mais de um pai (por exemplo "Alimentação" em Pet), é ambíguo e exige o caminho.
     */
    fun resolveCategory(text: String, categories: List<CategoryNode>): String? {
        val parts = text.split('>', '/').map(::fold).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 2) return null
        val byId = categories.associateBy { it.id }
        if (parts.size == 2) {
            return categories.filter { fold(it.name) == parts[1] && it.parentId?.let(byId::get)?.let { p -> fold(p.name) } == parts[0] }.singleOrNull()?.id
        }
        val named = categories.filter { fold(it.name) == parts[0] }
        return (named.singleOrNull() ?: named.filter { it.parentId == null }.singleOrNull())?.id
    }

    data class Origin(val kind: DocumentKind, val originId: String)

    /** [accounts] são (id, apelido); [card] é (id, apelido) do cartão. Apelido repetido, ou igual ao do cartão, é ambíguo. */
    fun resolveOrigin(name: String, accounts: List<Pair<String, String>>, card: Pair<String, String>?): Origin? {
        return resolveOrigins(name, accounts, listOfNotNull(card))
    }

    fun resolveOrigins(name: String, accounts: List<Pair<String, String>>, cards: List<Pair<String, String>>): Origin? {
        val wanted = fold(name)
        if (wanted.isEmpty()) return null
        val found = accounts.filter { fold(it.second) == wanted }.map { Origin(DocumentKind.Account, it.first) } +
            cards.filter { fold(it.second) == wanted }.map { Origin(DocumentKind.Card, it.first) }
        return found.singleOrNull()
    }

    /** Minúsculas, sem acentos e com espaços repetidos reduzidos a um. */
    internal fun fold(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")
}
