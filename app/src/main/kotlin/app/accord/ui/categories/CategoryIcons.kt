package app.accord.ui.categories

/** Ícone Lucide oferecido na edição de categoria (ADR 0039), com o nome em português que o leitor de tela anuncia. */
data class IconChoice(val key: String, val label: String)

/**
 * Lista curada: ícones que existem na biblioteca embarcada (a ausência cai no ícone genérico "tag") e cobrem os gastos
 * comuns do casal. As categorias iniciais usam estes mesmos nomes; um ícone fora da lista continua válido e aparece
 * selecionado como "atual" na edição.
 */
val CategoryIconChoices: List<IconChoice> = listOf(
    IconChoice("utensils", "Talheres"), IconChoice("chef-hat", "Chapéu de chef"), IconChoice("coffee", "Café"),
    IconChoice("pizza", "Pizza"), IconChoice("wine", "Vinho"), IconChoice("beer", "Cerveja"), IconChoice("apple", "Maçã"),
    IconChoice("shopping-cart", "Carrinho de compras"), IconChoice("shopping-bag", "Sacola"), IconChoice("store", "Loja"),
    IconChoice("shirt", "Camiseta"), IconChoice("gift", "Presente"), IconChoice("house", "Casa"), IconChoice("bed", "Cama"),
    IconChoice("sofa", "Sofá"), IconChoice("bath", "Banheira"), IconChoice("key-round", "Chave"), IconChoice("hammer", "Martelo"),
    IconChoice("wrench", "Chave inglesa"), IconChoice("lightbulb", "Lâmpada"), IconChoice("plug-zap", "Energia"),
    IconChoice("droplets", "Água"), IconChoice("flame", "Gás"), IconChoice("wifi", "Internet"), IconChoice("smartphone", "Celular"),
    IconChoice("laptop", "Notebook"), IconChoice("tv", "Televisão"), IconChoice("repeat", "Recorrente"), IconChoice("receipt", "Recibo"),
    IconChoice("heart-pulse", "Saúde"), IconChoice("pill", "Remédio"), IconChoice("stethoscope", "Estetoscópio"),
    IconChoice("dumbbell", "Academia"), IconChoice("car", "Carro"), IconChoice("fuel", "Combustível"),
    IconChoice("bus-front", "Ônibus"), IconChoice("bike", "Bicicleta"), IconChoice("plane", "Avião"), IconChoice("truck", "Caminhão"),
    IconChoice("ship", "Navio"), IconChoice("map", "Mapa"), IconChoice("tree-palm", "Palmeira"), IconChoice("mountain", "Montanha"),
    IconChoice("umbrella", "Guarda-chuva"), IconChoice("sun", "Sol"), IconChoice("ticket", "Ingresso"), IconChoice("popcorn", "Pipoca"),
    IconChoice("film", "Cinema"), IconChoice("music", "Música"), IconChoice("gamepad-2", "Jogos"), IconChoice("camera", "Câmera"),
    IconChoice("party-popper", "Festa"), IconChoice("paw-print", "Pata"), IconChoice("dog", "Cachorro"), IconChoice("cat", "Gato"),
    IconChoice("bone", "Osso"), IconChoice("baby", "Bebê"), IconChoice("graduation-cap", "Formatura"), IconChoice("book-open", "Livro"),
    IconChoice("school", "Escola"), IconChoice("briefcase", "Trabalho"), IconChoice("scissors", "Tesoura"), IconChoice("palette", "Arte"),
    IconChoice("flower-2", "Flor"), IconChoice("trees", "Árvores"), IconChoice("wallet", "Carteira"), IconChoice("piggy-bank", "Cofrinho"),
    IconChoice("banknote", "Dinheiro"), IconChoice("credit-card", "Cartão"), IconChoice("landmark", "Banco"), IconChoice("percent", "Taxas"),
    IconChoice("shield", "Seguro"), IconChoice("star", "Estrela"), IconChoice("heart", "Coração"), IconChoice("tag", "Etiqueta"),
)
