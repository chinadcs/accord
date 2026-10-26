package app.accord.core.chat


/**
 * Experimento de admissibilidade por evidência para duas filhas do seed que exigem contexto.
 * Uma refeição não comprova consumo em restaurante; um eletrodoméstico não comprova conserto.
 * Não atribui categoria, não altera IDs e não impõe regras às categorias criadas pelo casal.
 */
object EvidenceCategories {
    const val VERSION = 1
    private val restaurant = Regex("\\b(?:restaurante|restaurantes|lanchonete|lanchonetes|bar|bares|boteco|churrascaria|consumo no local)\\b")
    private val maintenance = Regex("\\b(?:conserto|consertar|reparo|reparar|reforma|reformar|manutencao|obra|obras|pintura|eletricista|encanador|material de construcao|materiais de construcao|cimento|tijolos|telhas)\\b")

    fun allowed(context: ChatContext, text: String): List<CategoryItem> {
        val folded = ChatText.fold(text)
        return context.categories.filter { category ->
            when (category.id) {
                "alimentacao.restaurantes" -> restaurant.containsMatchIn(folded)
                "casa.manutencao" -> maintenance.containsMatchIn(folded)
                else -> true
            }
        }
    }
}
