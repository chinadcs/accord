package app.accord.core.chat

import java.time.LocalDate

/**
 * Vinte e sete frases sintéticas de validação do perfil 15 (marcas de aplicativos, categoria filha única e renda sem
 * modelo), com gabaritos definidos antes de qualquer inferência. Os termos das frases NÃO vieram das baterias de
 * desenvolvimento nem das privadas (Uber e iFood aparecem lá, então a prova aqui é com as outras marcas e com
 * variações de forma). Contexto: realisticContext(), 22/10/2026, mesmo recibo recente; cada frase é independente.
 * Alterar texto/gabarito após medir exige nova versão e preservação do snapshot anterior.
 */
object BrandBattery {
    const val VERSION = 1

    private const val APPS = "transporte.aplicativos"
    private const val DELIVERY = "alimentacao.delivery"

    private fun register(
        cents: Long,
        category: String? = null,
        payment: ChatPayment = ChatPayment.Pix,
        account: String? = if (payment == ChatPayment.Pix || payment == ChatPayment.Debit) "account-1" else null,
        date: LocalDate = ChatBattery.today,
    ) = ChatBattery.Expected.Registers(cents, 1, AmountRole.Single, date, category, payment, account)

    private fun income(cents: Long, date: LocalDate = ChatBattery.today) =
        ChatBattery.Expected.Registers(cents, accountId = "account-1", date = date, nature = Nature.Income)

    private fun c(id: String, group: String, text: String, expected: ChatBattery.Expected) = GradedCase(id, group, text, expected)

    val cases: List<GradedCase> = listOf(
        // 7 aplicativos de transporte, incluindo marca com número colado ("99pop").
        c("b001", "Apps de transporte", "cabify 18,40 no pix", register(1840, APPS)),
        c("b002", "Apps de transporte", "bolt 22,90 no cartão", register(2290, APPS, ChatPayment.Card)),
        c("b003", "Apps de transporte", "indrive 15 ontem no pix", register(1500, APPS, date = LocalDate.of(2026, 10, 21))),
        c("b004", "Apps de transporte", "uber para o aeroporto 67,30 no cartão", register(6730, APPS, ChatPayment.Card)),
        c("b005", "Apps de transporte", "uber moto 9,50 no pix", register(950, APPS)),
        c("b006", "Apps de transporte", "99pop 12,80 no pix", register(1280, APPS)),
        c("b007", "Apps de transporte", "99taxi 20 no cartão", register(2000, APPS, ChatPayment.Card)),
        // 8 aplicativos de entrega; "uber eats" não pode virar transporte e "almoço no ifood" depende do modelo.
        c("b008", "Apps de entrega", "rappi 63,20 no cartão", register(6320, DELIVERY, ChatPayment.Card)),
        c("b009", "Apps de entrega", "ifood 38 no crédito", register(3800, DELIVERY, ChatPayment.Card)),
        c("b010", "Apps de entrega", "uber eats 52,90 no cartão", register(5290, DELIVERY, ChatPayment.Card)),
        c("b011", "Apps de entrega", "aiqfome 29,90 no pix", register(2990, DELIVERY)),
        c("b012", "Apps de entrega", "ifood 71,40 dia 20 no cartão", register(7140, DELIVERY, ChatPayment.Card, date = LocalDate.of(2026, 10, 20))),
        c("b013", "Apps de entrega", "keeta 33,50 no pix", register(3350, DELIVERY)),
        c("b014", "Apps de entrega", "99food 24 no pix", register(2400, DELIVERY)),
        c("b015", "Apps de entrega", "almoço no ifood 44 no pix", register(4400, DELIVERY)),
        // 6 categorias em que o termo só existe na filha: a pai não pode ser a escolha.
        c("b016", "Filha única", "camiseta 65 no cartão", register(6500, "compras.roupas", ChatPayment.Card)),
        c("b017", "Filha única", "tênis 249 no cartão", register(24900, "compras.roupas", ChatPayment.Card)),
        c("b018", "Filha única", "veterinário 120 no pix", register(12000, "pet.veterinario")),
        c("b019", "Filha única", "dentista 200 no pix", register(20000, "saude.consultas")),
        c("b020", "Filha única", "gasolina 150 no débito", register(15000, "transporte.combustivel", ChatPayment.Debit)),
        c("b021", "Filha única", "ingresso 80 no cartão", register(8000, "lazer.eventos", ChatPayment.Card)),
        // 2 categorias em que o termo bate só na pai: a regra da filha não pode inventar subcategoria.
        c("b022", "Só a pai", "pão 8,50 no pix", register(850, "alimentacao")),
        c("b023", "Só a pai", "geladeira 2.100 no cartão", register(210000, "casa", ChatPayment.Card)),
        // 4 rendas com abridor inequívoco, resolvidas pelo app sem chamar o modelo.
        c("b024", "Renda sem modelo", "freela 450 dia 15", income(45000, LocalDate.of(2026, 10, 15))),
        c("b025", "Renda sem modelo", "recebi 1.200 de comissão", income(120000)),
        c("b026", "Renda sem modelo", "bônus 800 mês que vem", income(80000, LocalDate.of(2026, 11, 22))),
        c("b027", "Renda sem modelo", "comissão 320 ontem", income(32000, LocalDate.of(2026, 10, 21))),
    )

    init {
        check(cases.map { it.id }.toSet().size == cases.size) { "ids repetidos" }
    }
}
