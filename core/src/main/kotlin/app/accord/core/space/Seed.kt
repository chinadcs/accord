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

    /**
     * Dica de uso de cada categoria inicial para o interpretador do Chat (S3 §4): pai com descrição geral,
     * filha com a específica. Não é persistida nem mostrada ao casal, e categoria criada por ele não tem dica.
     * Descreve o tipo de gasto, nunca estabelecimentos (S3: não presumir categoria pelo nome da loja).
     */
    val hints: Map<String, String> = mapOf(
        "alimentacao" to "comida e bebida em geral, em casa ou na rua",
        "alimentacao.delivery" to "comida pedida por aplicativo ou telefone e entregue em casa",
        "alimentacao.mercado" to "compras de supermercado, feira, açougue, padaria e hortifruti para cozinhar em casa",
        "alimentacao.restaurantes" to "refeição pronta consumida fora de casa: restaurante, lanchonete, bar",
        "compras" to "bens para uso pessoal que não cabem nas outras categorias",
        "compras.roupas" to "roupas, calçados e peças de vestuário",
        "compras.presentes" to "presentes para outras pessoas e datas comemorativas",
        "assinaturas" to "serviços cobrados de forma recorrente por assinatura",
        "assinaturas.streaming" to "vídeo, música e jogos por assinatura",
        "assinaturas.software" to "aplicativos, nuvem e programas por assinatura",
        "assinaturas.outros-servicos-recorrentes" to "outras mensalidades e assinaturas recorrentes",
        "lazer" to "diversão e tempo livre",
        "lazer.passeios" to "viagens, parques, museus e saídas para passear",
        "lazer.eventos" to "ingressos e entradas para shows, teatro, esportes e festas",
        "pet" to "gastos com animais de estimação",
        "pet.alimentacao" to "ração, petiscos e comida de animais",
        "pet.veterinario" to "veterinário, vacinas, exames, banho e tosa",
        "pet.acessorios" to "coleira, caminha, brinquedos e outros acessórios do animal",
        "servicos" to "serviços contratados e tarifas",
        "servicos.tarifas-e-juros" to "tarifas bancárias, juros e multas cobrados por bancos e cartões",
        "servicos.servicos-pessoais" to "cabeleireiro, manicure, barbearia, estética e massagem",
        "servicos.outros-servicos" to "outros serviços contratados, como contador, advogado e lavanderia",
        "casa" to "moradia, móveis, eletrodomésticos e gastos gerais da casa",
        "casa.moradia" to "aluguel, condomínio, financiamento e IPTU",
        "casa.contas-domesticas" to "luz, água, gás, internet e telefone fixo",
        "casa.manutencao" to "consertos, reformas, materiais de obra e profissionais da casa, não compra de eletrodoméstico novo",
        "saude" to "cuidados com a saúde",
        "saude.consultas" to "consultas, exames, terapia e dentista",
        "saude.medicamentos" to "remédios e produtos de farmácia",
        "transporte" to "deslocamento de pessoas",
        "transporte.combustivel" to "gasolina, etanol, diesel e recarga de carro elétrico",
        "transporte.aplicativos" to "corridas de aplicativo e táxi",
        "transporte.transporte-publico" to "ônibus, metrô, trem e passagens",
        "eletronicos" to "aparelhos digitais, informática e seus acessórios",
        "eletronicos.equipamentos" to "TVs, celulares, computadores, tablets, consoles e outros aparelhos digitais, não eletrodomésticos de cozinha",
        "eletronicos.acessorios" to "fones, carregadores, capas, cabos e periféricos",
    )

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
