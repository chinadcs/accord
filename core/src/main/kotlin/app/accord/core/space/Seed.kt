package app.accord.core.space

import java.text.Normalizer
import java.util.UUID

/** Categorias iniciais (plano §3; docs/icones-categorias.md). Subcategorias são sugestões editáveis. */
object CategorySeed {
    const val FEES_AND_INTEREST = "servicos.tarifas-e-juros"

    data class Item(val key: String, val name: String, val icon: String, val parentKey: String? = null)

    val items: List<Item> = buildList {
        fun main(key: String, name: String, icon: String, vararg subs: Triple<String, String, String>) {
            add(Item(key, name, icon))
            subs.forEach { (subKey, subName, subIcon) -> add(Item("$key.$subKey", subName, subIcon, key)) }
        }
        main(
            "alimentacao", "Alimentação", "utensils",
            Triple("delivery", "Delivery", "bike"),
            Triple("mercado", "Mercado", "shopping-cart"),
            Triple("restaurantes", "Restaurantes", "chef-hat"),
        )
        main(
            "compras", "Compras", "shopping-bag",
            Triple("roupas", "Roupas", "shirt"),
            Triple("presentes", "Presentes", "gift"),
        )
        main(
            "assinaturas", "Assinaturas", "repeat",
            Triple("streaming", "Streaming", "tv"),
            Triple("software", "Software", "app-window"),
            Triple("outros-servicos-recorrentes", "Outros serviços recorrentes", "calendar-sync"),
        )
        main(
            "lazer", "Lazer", "tree-palm",
            Triple("passeios", "Passeios", "map"),
            Triple("eventos", "Eventos", "ticket"),
        )
        main(
            "pet", "Pet", "paw-print",
            Triple("alimentacao", "Alimentação", "bone"),
            Triple("veterinario", "Veterinário", "syringe"),
            Triple("acessorios", "Acessórios", "dog"),
        )
        main(
            "servicos", "Serviços", "concierge-bell",
            Triple("tarifas-e-juros", "Tarifas e juros", "percent"),
            Triple("servicos-pessoais", "Serviços pessoais", "scissors"),
            Triple("outros-servicos", "Outros serviços", "hand-helping"),
        )
        main(
            "casa", "Casa", "house",
            Triple("moradia", "Moradia", "key-round"),
            Triple("contas-domesticas", "Contas domésticas", "receipt"),
            Triple("manutencao", "Manutenção", "hammer"),
        )
        main(
            "saude", "Saúde", "heart-pulse",
            Triple("consultas", "Consultas", "stethoscope"),
            Triple("medicamentos", "Medicamentos", "pill"),
        )
        main(
            "transporte", "Transporte", "car",
            Triple("combustivel", "Combustível", "fuel"),
            Triple("aplicativos", "Aplicativos", "car-taxi-front"),
            Triple("transporte-publico", "Transporte público", "bus-front"),
        )
        main(
            "eletronicos", "Eletrônicos", "monitor-smartphone",
            Triple("equipamentos", "Equipamentos", "laptop"),
            Triple("acessorios", "Acessórios", "headphones"),
        )
    }

    /** Ícone de categoria criada pelo casal (docs/icones-categorias.md §3). */
    const val CUSTOM_ICON = "tag"
}

/**
 * Identidades determinísticas (S1 §6): dois aparelhos, mesmo offline, chegam à mesma identidade
 * e o resultado vira comparação de versões em S2, nunca duplicação.
 */
object DeterministicIds {
    fun of(spaceId: String, kind: String, key: String): String =
        UUID.nameUUIDFromBytes("$spaceId|$kind|$key".toByteArray(Charsets.UTF_8)).toString()

    fun seedCategory(spaceId: String, seedKey: String) = of(spaceId, "category", seedKey)
    fun preferences(spaceId: String, personId: String) = of(spaceId, "preferences", personId)
}

object NameKey {
    /** Compara nomes sem diferenciar maiúsculas, acentos e espaços extras (S1 §4.1, proposta). */
    fun of(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
