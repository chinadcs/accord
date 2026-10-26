package app.accord.core.chat

import app.accord.core.Money
import java.time.LocalDate

/**
 * Cem frases sintéticas adicionais, com gabaritos definidos antes da primeira inferência.
 * Não substituem desenvolvimento, adicional v1 ou privadas. Contexto: realisticContext(), 22/10/2026.
 * Cada frase é independente, com o mesmo recibo recente; não simula retomada de pergunta.
 * Alterar texto/gabarito após medir exige nova versão e preservação do snapshot anterior.
 */
object ExpandedBattery {
    const val VERSION = 1

    private fun register(
        cents: Long,
        category: String? = null,
        payment: ChatPayment = ChatPayment.Pix,
        account: String? = if (payment == ChatPayment.Pix || payment == ChatPayment.Debit) "account-1" else null,
        date: LocalDate = ChatBattery.today,
        installments: Int = 1,
        role: AmountRole = AmountRole.Single,
    ) = ChatBattery.Expected.Registers(cents, installments, role, date, category, payment, account)

    private fun income(cents: Long, account: String = "account-1", date: LocalDate = ChatBattery.today) =
        ChatBattery.Expected.Registers(cents, accountId = account, date = date, nature = Nature.Income)

    private fun c(id: String, group: String, text: String, expected: ChatBattery.Expected) = GradedCase(id, group, text, expected)
    private fun ask(field: QuestionField) = ChatBattery.Expected.Asks(field)
    private fun reply(value: StandardReply) = ChatBattery.Expected.Replies(value)
    private fun correct(change: EntryChange) = ChatBattery.Expected.Corrects(change)

    val cases: List<GradedCase> = listOf(
        // 20 valores e meios: centavos, milhar, ordem variável, acentos e frases mais longas.
        c("x001", "Valores e meios", "café e pão 12,75 no pix", register(1275, "alimentacao")),
        c("x002", "Valores e meios", "almoço de hoje 28,50 no débito", register(2850, "alimentacao", ChatPayment.Debit)),
        c("x003", "Valores e meios", "paguei 9,99 em dinheiro pelo lanche", register(999, "alimentacao", ChatPayment.Cash)),
        c("x004", "Valores e meios", "no cartão, supermercado 214,08", register(21408, "alimentacao.mercado", ChatPayment.Card)),
        c("x005", "Valores e meios", "geladeira nova 2.399 no cartão", register(239900, "casa", ChatPayment.Card)),
        c("x006", "Valores e meios", "gastei 0,50 em bala no pix", register(50, "alimentacao")),
        c("x007", "Valores e meios", "mercado 1.045,67 no pix Itaú", register(104567, "alimentacao.mercado", account = "account-2")),
        c("x008", "Valores e meios", "pix Nubank de 43,21 para o mercado", register(4321, "alimentacao.mercado")),
        c("x009", "Valores e meios", "paguei no débito Itaú 76,90 de supermercado", register(7690, "alimentacao.mercado", ChatPayment.Debit, "account-2")),
        c("x010", "Valores e meios", "lanche de 17 reais no cartão", register(1700, "alimentacao", ChatPayment.Card)),
        c("x011", "Valores e meios", "anota aí: supermercado 93,02 em dinheiro", register(9302, "alimentacao.mercado", ChatPayment.Cash)),
        c("x012", "Valores e meios", "comprei um fogão de 1.850 no cartão", register(185000, "casa", ChatPayment.Card)),
        c("x013", "Valores e meios", "a compra da feira deu 67,35 e paguei no pix", register(6735, "alimentacao.mercado")),
        c("x014", "Valores e meios", "debito: mercado 58,60", register(5860, "alimentacao.mercado", ChatPayment.Debit)),
        c("x015", "Valores e meios", "paguei 31,80 no cartao pelo almoço", register(3180, "alimentacao", ChatPayment.Card)),
        c("x016", "Valores e meios", "entrega de comida 84,45 no pix", register(8445, "alimentacao.delivery")),
        c("x017", "Valores e meios", "supermercado 205 no débito da conta Nubank", register(20500, "alimentacao.mercado", ChatPayment.Debit)),
        c("x018", "Valores e meios", "hoje, no caminho de volta para casa, comprei comida por 26,70 e paguei em dinheiro", register(2670, "alimentacao", ChatPayment.Cash)),
        c("x019", "Valores e meios", "paguei com Pix da conta Itaú a compra de supermercado que ficou em 132,16", register(13216, "alimentacao.mercado", account = "account-2")),
        c("x020", "Valores e meios", "aspirador 699,90 no cartão à vista", register(69990, "casa", ChatPayment.Card)),
        // 10 parcelas: totais e valores por parcela têm gabaritos distintos.
        c("x021", "Parcelas", "notebook 360 em 3x no cartão", register(36000, "eletronicos.equipamentos", ChatPayment.Card, installments = 3, role = AmountRole.Total)),
        c("x022", "Parcelas", "televisão 2.100 em 7x no cartão", register(210000, "eletronicos.equipamentos", ChatPayment.Card, installments = 7, role = AmountRole.Total)),
        c("x023", "Parcelas", "geladeira 3.001 em 10x no cartão", register(300100, "casa", ChatPayment.Card, installments = 10, role = AmountRole.Total)),
        c("x024", "Parcelas", "celular 1.999,99 em 12x no cartão", register(199999, "eletronicos.equipamentos", ChatPayment.Card, installments = 12, role = AmountRole.Total)),
        c("x025", "Parcelas", "sofá 1.350 em 5x no cartão", register(135000, "casa", ChatPayment.Card, installments = 5, role = AmountRole.Total)),
        c("x026", "Parcelas", "notebook 3 parcelas de 120 no cartão", register(12000, "eletronicos.equipamentos", ChatPayment.Card, installments = 3, role = AmountRole.PerInstallment)),
        c("x027", "Parcelas", "televisão 7 parcelas de 300 no cartão", register(30000, "eletronicos.equipamentos", ChatPayment.Card, installments = 7, role = AmountRole.PerInstallment)),
        c("x028", "Parcelas", "fogão 4 parcelas de 249,90 no cartão", register(24990, "casa", ChatPayment.Card, installments = 4, role = AmountRole.PerInstallment)),
        c("x029", "Parcelas", "fone 2 parcelas de 89,50 no cartão", register(8950, "eletronicos.acessorios", ChatPayment.Card, installments = 2, role = AmountRole.PerInstallment)),
        c("x030", "Parcelas", "computador 6 parcelas de 415,25 no cartão", register(41525, "eletronicos.equipamentos", ChatPayment.Card, installments = 6, role = AmountRole.PerInstallment)),
        // 12 datas: hoje fixo, relativos e dia passado mais recente; futuro de despesa pede data.
        c("x031", "Datas", "supermercado 48,25 ontem no pix", register(4825, "alimentacao.mercado", date = LocalDate.of(2026, 10, 21))),
        c("x032", "Datas", "ontem paguei 19,40 de lanche no débito", register(1940, "alimentacao", ChatPayment.Debit, date = LocalDate.of(2026, 10, 21))),
        c("x033", "Datas", "anteontem comprei ração 98 no cartão", register(9800, "pet.alimentacao", ChatPayment.Card, date = LocalDate.of(2026, 10, 20))),
        c("x034", "Datas", "mercado 71 dia 3 no pix", register(7100, "alimentacao.mercado", date = LocalDate.of(2026, 10, 3))),
        c("x035", "Datas", "mercado 82 dia 28 no débito", register(8200, "alimentacao.mercado", ChatPayment.Debit, date = LocalDate.of(2026, 9, 28))),
        c("x036", "Datas", "delivery 59 dia 22 no cartão", register(5900, "alimentacao.delivery", ChatPayment.Card)),
        c("x037", "Datas", "gasolina 145 dia 1 no pix", register(14500, "transporte.combustivel", date = LocalDate.of(2026, 10, 1))),
        c("x038", "Datas", "remédio 37 dia 30 no cartão", register(3700, "saude.medicamentos", ChatPayment.Card, date = LocalDate.of(2026, 9, 30))),
        c("x039", "Datas", "hoje paguei 16,20 em dinheiro por um café", register(1620, "alimentacao", ChatPayment.Cash)),
        c("x040", "Datas", "mercado 99 mês que vem no débito", ask(QuestionField.Date)),
        c("x041", "Datas", "mercado 61 em 23/10/2026 no cartão", ask(QuestionField.Date)),
        c("x042", "Datas", "delivery 46 anteontem no pix Itaú", register(4600, "alimentacao.delivery", account = "account-2", date = LocalDate.of(2026, 10, 20))),
        // 14 categorias: finalidade explícita versus estabelecimento sem produto.
        c("x043", "Categorias", "camiseta 79 no pix", register(7900, "compras.roupas")),
        c("x044", "Categorias", "presente de aniversário 125 no cartão", register(12500, "compras.presentes", ChatPayment.Card)),
        c("x045", "Categorias", "consulta médica 250 no pix", register(25000, "saude.consultas")),
        c("x046", "Categorias", "medicamento 64,80 no débito", register(6480, "saude.medicamentos", ChatPayment.Debit)),
        c("x047", "Categorias", "exame de sangue 115 no cartão", register(11500, "saude.consultas", ChatPayment.Card)),
        c("x048", "Categorias", "ração do gato 89,90 no pix", register(8990, "pet.alimentacao")),
        c("x049", "Categorias", "consulta no veterinário 190 no cartão", register(19000, "pet.veterinario", ChatPayment.Card)),
        c("x050", "Categorias", "coleira do cachorro 35 no débito", register(3500, "pet.acessorios", ChatPayment.Debit)),
        c("x051", "Categorias", "gasolina do carro 210 em dinheiro", register(21000, "transporte.combustivel", ChatPayment.Cash)),
        c("x052", "Categorias", "ingresso para show 180 no cartão", register(18000, "lazer.eventos", ChatPayment.Card)),
        c("x053", "Categorias", "corte de cabelo 45 no pix", register(4500, "servicos.servicos-pessoais")),
        c("x054", "Categorias", "conserto do telhado 650 no pix", register(65000, "casa.manutencao")),
        c("x055", "Categorias", "Mercado Livre 137 no cartão", register(13700, payment = ChatPayment.Card)),
        c("x056", "Categorias", "comprei na Amazon por 218 no pix", register(21800)),
        // 8 rendas: não presumir despesa nem categoria de gasto ou meio de pagamento.
        c("x057", "Rendas", "recebi meu salário de 4.850", income(485000)),
        c("x058", "Rendas", "entrou salário 3.245,60 na conta Itaú", income(324560, "account-2")),
        c("x059", "Rendas", "recebi 450 de freela na conta Nubank", income(45000)),
        c("x060", "Rendas", "recebi 1.250 ontem", income(125000, date = LocalDate.of(2026, 10, 21))),
        c("x061", "Rendas", "salário 5.300 mês que vem na conta Itaú", income(530000, "account-2", LocalDate.of(2026, 11, 22))),
        c("x062", "Rendas", "recebi 75,50 hoje na conta Nubank", income(7550)),
        c("x063", "Rendas", "recebi 920 dia 27", income(92000, date = LocalDate.of(2026, 9, 27))),
        c("x064", "Rendas", "recebi 680 anteontem na conta Itaú", income(68000, "account-2", LocalDate.of(2026, 10, 20))),
        // 10 dúvidas: valor, meio ou conta faltante não autorizam inventar dados.
        c("x065", "Ambiguidades", "comprei um teclado no cartão", ask(QuestionField.Amount)),
        c("x066", "Ambiguidades", "mercado 0,00 no débito", ask(QuestionField.Amount)),
        c("x067", "Ambiguidades", "café 12.50 no pix", ask(QuestionField.Amount)),
        c("x068", "Ambiguidades", "ração 135,90", ask(QuestionField.Payment)),
        c("x069", "Ambiguidades", "hoje comprei roupa por 89", ask(QuestionField.Payment)),
        c("x070", "Ambiguidades", "paguei 47 de almoço", ask(QuestionField.Payment)),
        c("x071", "Ambiguidades", "mercado 57 no pix da conta desconhecida", ask(QuestionField.Account)),
        c("x072", "Ambiguidades", "comprei pão no pix", ask(QuestionField.Amount)),
        c("x073", "Ambiguidades", "mercado 90 no débito da conta nova", ask(QuestionField.Account)),
        c("x074", "Ambiguidades", "recebi meu salário", ask(QuestionField.Amount)),
        // 8 correções independentes, todas apontam para receipt-1/entry-1 do contexto.
        c("x075", "Correções", "corrige o valor para 57,35", correct(EntryChange.Amount(Money(5735)))),
        c("x076", "Correções", "o valor certo é 1.234,56", correct(EntryChange.Amount(Money(123456)))),
        c("x077", "Correções", "troca a categoria para Transporte", correct(EntryChange.Category("transporte"))),
        c("x078", "Correções", "coloca na categoria Pet", correct(EntryChange.Category("pet"))),
        c("x079", "Correções", "muda para Eletrônicos", correct(EntryChange.Category("eletronicos"))),
        c("x080", "Correções", "na verdade foi no cartão", correct(EntryChange.Payment(ChatPayment.Card, null))),
        c("x081", "Correções", "corrija para débito na conta Itaú", correct(EntryChange.Payment(ChatPayment.Debit, "account-2"))),
        c("x082", "Correções", "corrige para pix na conta Nubank", correct(EntryChange.Payment(ChatPayment.Pix, "account-1"))),
        // 8 consultas e intenções: nenhum lançamento financeiro é autorizado.
        c("x083", "Consultas e intenções", "quanto gastei em alimentação este mês?", reply(StandardReply.Query)),
        c("x084", "Consultas e intenções", "qual foi meu gasto com remédios?", reply(StandardReply.Query)),
        c("x085", "Consultas e intenções", "quanto falta no limite de transporte?", reply(StandardReply.Query)),
        c("x086", "Consultas e intenções", "qual o total da fatura agora?", reply(StandardReply.Query)),
        c("x087", "Consultas e intenções", "quero comprar um notebook de 3.500 em 10x", reply(StandardReply.Intent)),
        c("x088", "Consultas e intenções", "estou pensando em gastar 250 num passeio", reply(StandardReply.Intent)),
        c("x089", "Consultas e intenções", "pretendo comprar uma geladeira por 2.800", reply(StandardReply.Intent)),
        c("x090", "Consultas e intenções", "se eu comprar um celular de 1.600 em 8x?", reply(StandardReply.Intent)),
        // 10 operações fora do Chat P0, com resposta e atalho para a área correspondente.
        c("x091", "Fora do Chat", "assinatura de música 21,90 por mês", reply(StandardReply.UnsupportedRecurrence)),
        c("x092", "Fora do Chat", "academia 129 todo mês no cartão", reply(StandardReply.UnsupportedRecurrence)),
        c("x093", "Fora do Chat", "paguei um boleto de 342,18", reply(StandardReply.UnsupportedBoleto)),
        c("x094", "Fora do Chat", "quitei o boleto da internet de 109", reply(StandardReply.UnsupportedBoleto)),
        c("x095", "Fora do Chat", "recebi um estorno de 75 do cartão", reply(StandardReply.UnsupportedRefund)),
        c("x096", "Fora do Chat", "estorno da compra de tênis 189,90", reply(StandardReply.UnsupportedRefund)),
        c("x097", "Fora do Chat", "paguei a fatura inteira de 2.150", reply(StandardReply.UnsupportedInvoicePayment)),
        c("x098", "Fora do Chat", "quitei a fatura deste mês", reply(StandardReply.UnsupportedInvoicePayment)),
        c("x099", "Fora do Chat", "cancelei as parcelas do notebook", reply(StandardReply.UnsupportedCancellation)),
        c("x100", "Fora do Chat", "cancelamento das parcelas do sofá", reply(StandardReply.UnsupportedCancellation)),
    )
}
