package app.accord.core.chat

import app.accord.core.space.CategorySeed

/** Vocabulário ilustrativo das categorias iniciais, sem frases, valores ou gabaritos de avaliação. */
object SeedCategoryVocabulary {
    private val terms = mapOf(
        "alimentacao" to "comida;bebida;almoço;jantar;café;lanche;salgado;sanduíche;suco;refrigerante;água mineral;pão;chocolate;fruta",
        "alimentacao.mercado" to "supermercado;mercado;feira;açougue;hortifruti;padaria;cesta básica;compras de alimentos",
        "alimentacao.delivery" to "delivery;entrega de comida;comida por aplicativo;pedi pizza;pedido de lanche;comida entregue",
        "alimentacao.restaurantes" to "restaurante;lanchonete;churrascaria;bar;rodízio;buffet;refeição no local",
        "compras.roupas" to "roupa;camiseta;calça;blusa;casaco;sapato;tênis;vestido;bermuda;jaqueta",
        "compras.presentes" to "presente;lembrancinha;presente de aniversário",
        "casa" to "geladeira;fogão;micro-ondas;liquidificador;lavadora;máquina de lavar;sofá;cama;armário;móvel;panela;ventilador",
        "casa.moradia" to "aluguel;condomínio;financiamento da casa;IPTU",
        "casa.contas-domesticas" to "conta de luz;conta de água;conta de gás;internet residencial;telefone fixo",
        "casa.manutencao" to "conserto da casa;reforma;pedreiro;encanador;eletricista;cimento;tijolo;material de construção;pintura da casa",
        "eletronicos.equipamentos" to "tv;televisor;celular;smartphone;notebook;computador;tablet;console;monitor;impressora",
        "eletronicos.acessorios" to "fone;carregador;cabo;mouse;teclado;capinha;periférico",
        "pet" to "pet;animal de estimação",
        "pet.alimentacao" to "ração;petisco para cachorro;comida de cachorro;comida de gato;ração para gato",
        "pet.veterinario" to "veterinário;banho e tosa;vacina do cachorro;exame do animal",
        "pet.acessorios" to "coleira;guia de cachorro;caminha;brinquedo de cachorro",
        "saude.consultas" to "consulta médica;consulta no dentista;dentista;exame médico;psicólogo;terapia;fisioterapia;médico",
        "saude.medicamentos" to "remédio;medicamento;vitamina;antibiótico;analgésico",
        "transporte.combustivel" to "gasolina;etanol;diesel;combustível;abastecimento;abasteci;recarga do carro elétrico",
        "transporte.aplicativos" to "corrida de aplicativo;táxi;transporte por aplicativo",
        "transporte.transporte-publico" to "ônibus;metrô;trem;passagem de ônibus;bilhete de transporte",
        "lazer.passeios" to "viagem;passeio;parque;museu;excursão;hotel;hospedagem",
        "lazer.eventos" to "ingresso;show;teatro;cinema;evento;entrada de festa",
        "servicos.tarifas-e-juros" to "tarifa bancária;juros;multa bancária;anuidade do cartão",
        "servicos.servicos-pessoais" to "barbeiro;manicure;cabeleireiro;salão;depilação;massagem",
        "servicos.outros-servicos" to "costureira;ajuste de roupa;lavanderia;advogado;contador;despachante",
        "assinaturas.streaming" to "assinatura de vídeo;assinatura de música;streaming",
        "assinaturas.software" to "assinatura de programa;software;nuvem por assinatura",
    ).mapValues { (_, list) -> list.split(';') }
    private val extra = mapOf(
        "alimentacao" to "pizza;hambúrguer;açaí;pastel;sorvete;marmita;coxinha;tapioca;bolo;doce",
        "casa" to "cadeira;mesa;estante;guarda-roupa;colchão;cortina",
        "eletronicos.equipamentos" to "televisão;videogame",
        "compras.roupas" to "roupas;calçados;camisas;camisetas;casacos",
        "saude.medicamentos" to "remédios;medicamentos",
        "transporte.transporte-publico" to "passagem",
        "servicos.servicos-pessoais" to "corte de cabelo;barbearia",
    ).mapValues { (_, list) -> list.split(';') }

    /** v15: marcas de aplicativos de transporte e de entrega de comida. "99" só vale colado à marca (99pop, 99taxi, 99food): sozinho é um valor possível. */
    private val brands = mapOf(
        "transporte.aplicativos" to "uber;cabify;indrive;in driver;bolt;99pop;99taxi;lady driver",
        "alimentacao.delivery" to "ifood;i food;rappi;uber eats;99food;aiqfome;keeta;ze delivery",
    ).mapValues { (_, list) -> list.split(';') }

    /**
     * v17: marcas e termos de uso comum no Brasil, de fontes públicas (Abras, Abrafarma, Worldpanel, Opinion Box, ANP, operadoras),
     * não derivados de nenhuma bateria. Palavras soltas ambíguas ("extra", "dia", "big", "net", "bar" para pub) ficam de fora.
     */
    private val common = mapOf(
        "alimentacao" to "pão de queijo;coxinha;pastel;esfiha;empada;pamonha;tapioca;cuscuz;marmita;quentinha;picolé;brigadeiro;brownie;milkshake;" +
            "torta;acarajé;cafezinho;sanduíche natural;água de coco;café da manhã",
        "alimentacao.mercado" to "mercadinho;sacolão;peixaria;quitanda;padoca;atacadão;assaí;carrefour;pão de açúcar;compras do mês;feira livre;" +
            "makro;sams club;mateus;muffato",
        "alimentacao.delivery" to "pelo app;pelo aplicativo;pedido pelo app;entrega de hambúrguer;entrega de pizza;entrega de sushi;rappi;aiqfome;" +
            "ze delivery;uber eats;ifood;keeta",
        "alimentacao.restaurantes" to "mcdonald's;mcdonalds;burger king;habib's;habibs;giraffas;outback;madero;spoleto;bob's;bobs;boteco;buffet;" +
            "restaurante a quilo;pizzaria;hamburgueria;japonês;subway;pizza hut;popeyes;kfc;ragazzo;coco bambu;sushi",
        "compras.roupas" to "renner;c&a;riachuelo;marisa;zara;hering;h&m;moletom;biquíni;sandália;chinelo;bota;meia;meias;cueca;pijama;short;saia;" +
            "camisa;jeans;lingerie;roupa de academia;calça jeans",
        "compras.presentes" to "amigo secreto;presente de natal;presente de casamento;presente de aniversário;lembrancinha",
        "eletronicos.equipamentos" to "smartwatch;videogame;playstation;xbox;smart tv;televisão;smartphone;console;impressora",
        "eletronicos.acessorios" to "hd externo;pen drive;película;cabo usb;ssd;power bank;fone de ouvido;capinha de celular",
        "casa" to "air fryer;ar condicionado;aspirador;batedeira;cafeteira;tapete;travesseiro;lençol;jogo de panelas;guarda-roupa;cadeira",
        "casa.moradia" to "aluguel do apê;taxa do condomínio;aluguel do apartamento",
        "casa.contas-domesticas" to "internet de casa;internet;vivo fibra;claro net;brisanet;enel;sabesp;cemig;copel;sanepar;light;cpfl;energisa;" +
            "comgas;naturgy;conta de internet;energia elétrica;luz",
        "casa.manutencao" to "tinta;pintura;conserto do chuveiro;conserto do portão;conserto do telhado;reforma do banheiro;chuveiro;" +
            "pintura da sala;conserto",
        "pet.alimentacao" to "sachê;petisco;ração de filhote;ração do cachorro;ração do gato",
        "pet.veterinario" to "castração;exame do cachorro;tosa;vacina da gata;vacina do gato",
        "pet.acessorios" to "guia retrátil;comedouro;bebedouro;casinha;caminha do cachorro;brinquedo de gato",
        "saude.consultas" to "ultrassom;oftalmologista;dermatologista;nutricionista;ortopedista;cardiologista;pediatra;ginecologista;" +
            "exame de sangue;consulta",
        "saude.medicamentos" to "farmácia;drogaria;drogasil;droga raia;raia;pague menos;panvel;pacheco;drogaria são paulo;dipirona;antialérgico",
        "transporte.combustivel" to "gnv;posto;posto ipiranga;ipiranga;shell;abasteci o carro",
        "transporte.transporte-publico" to "bilhete único;riocard;recarga do bilhete;passagem do metrô;vlt;brt;barca;cptm;passagem de ônibus",
        "lazer.passeios" to "airbnb;booking;zoológico;pousada;decolar;cvc",
        "lazer.eventos" to "sympla;balada;festival;ticketmaster;eventim;ingresso do jogo;entrada da festa",
        "servicos.tarifas-e-juros" to "tarifa do banco;tarifa de saque;iof;anuidade;juros do cheque especial;cheque especial;tarifa",
        "servicos.servicos-pessoais" to "sobrancelha;barba;escova;unha;design de sobrancelha;corte de cabelo",
        "servicos.outros-servicos" to "chaveiro;conserto de sapato;sapateiro;engraxate",
    ).mapValues { (_, list) -> list.split(';') }

    private val stopwords = Regex("\\b(?:de|do|da|dos|das|para|e)\\b")

    /** v17: as mesmas palavras que o assunto da frase perde ([RoutedInterpreter.purpose]), para "comida por aplicativo" casar. */
    private val stopwords17 = Regex("\\b(?:de|do|da|dos|das|para|e|no|na|nos|nas|em|por|pelo|pela|com|o|a|os|as|um|uma)\\b")

    private fun normalized(text: String, version: Int = 11): String {
        val folded = ChatText.fold(text).replace('-', ' ')
        // v17: "Mercado Livre" e "Mercado Pago" são lojas/bancos, não o mercado de comida (accord-vt5).
        val base = if (version >= 17) folded.replace(Regex("(?<=[a-z0-9])&(?=[a-z0-9])"), "").replace(Regex("\\bmercado (livre|pago)\\b"), "mercado$1") else folded
        return base.replace(if (version >= 17) stopwords17 else stopwords, " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Todos os termos do vocabulário do perfil [version], já normalizados: base para provar que uma frase é de fora dele. */
    fun allTerms(version: Int): Set<String> =
        listOf(terms, extra, brands, common).flatMap { it.values }.flatten()
            .map { normalized(it, version) }.filter { it.isNotBlank() }.toSet()

    /**
     * [context] liga o id de cada categoria à sua chave de semente (`ChatContext.seedKeys`). No app o id é um UUID e o vocabulário só conhece a chave; sem
     * o contexto, ou nos contextos de bateria, id e chave são a mesma coisa.
     */
    fun matches(category: CategoryItem, subject: String, version: Int = 11, context: ChatContext? = null): List<String> {
        fun keyOf(id: String) = context?.seedKeys?.get(id) ?: id
        val key = keyOf(category.id)
        val seed = CategorySeed.items.firstOrNull { it.key == key } ?: return emptyList()
        if (ChatText.fold(seed.name) != ChatText.fold(category.name) || seed.parentKey != category.parentId?.let(::keyOf)) return emptyList()
        val folded = normalized(subject, version)
        val vocabulary = terms[key].orEmpty() +
            (if (version >= 12) extra[key].orEmpty() else emptyList()) +
            (if (version >= 15) brands[key].orEmpty() else emptyList()) +
            (if (version >= 17) common[key].orEmpty() else emptyList())
        return vocabulary.filter { term ->
            Regex("(?<![a-z0-9])${Regex.escape(normalized(term, version))}(?![a-z0-9])").containsMatchIn(folded)
        }
    }

    /** v15: a única categoria cujo vocabulário bateu com o maior número de palavras, quando é filha (a pai não entra na escolha). */
    fun soleChild(context: ChatContext, subject: String, version: Int): CategoryItem? {
        val scored = context.categories.map { c -> c to matches(c, subject, version, context).maxOfOrNull { normalized(it, version).split(' ').size } }
        val best = scored.mapNotNull { it.second }.maxOrNull() ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first?.takeIf { it.parentId != null }
    }

    fun candidates(context: ChatContext, subject: String, version: Int = 11): List<CategoryItem>? {
        val scored = context.categories.map { c -> c to matches(c, subject, version, context).maxOfOrNull { normalized(it, version).split(' ').size } }
        val best = scored.mapNotNull { it.second }.maxOrNull() ?: return null
        val ids = scored.filter { it.second == best }.map { it.first.id }.toMutableSet()
        context.categories.filter { it.id in ids }.forEach { it.parentId?.let(ids::add) }
        return context.categories.filter { it.id in ids }
    }
}
