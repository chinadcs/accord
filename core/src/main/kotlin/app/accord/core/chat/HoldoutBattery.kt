package app.accord.core.chat

import app.accord.core.Money
import java.time.LocalDate

/**
 * Frases fictícias novas, congeladas antes de avaliar os finalistas. Não são exemplos do prompt.
 * A bateria de desenvolvimento continua em ChatBattery; estas notas são reportadas separadamente.
 */
object HoldoutBattery {
    const val VERSION = 1

    val cases: List<GradedCase> = listOf(
        GradedCase("h01", "Valores", "supermercado 87,40 no débito", ChatBattery.Expected.Registers(8740, categoryId = "alimentacao.mercado", payment = ChatPayment.Debit, accountId = "account-1")),
        GradedCase("h02", "Categorias", "entrega de pizza 72 no cartão", ChatBattery.Expected.Registers(7200, categoryId = "alimentacao.delivery", payment = ChatPayment.Card)),
        GradedCase("h03", "Categorias", "água mineral 6 em dinheiro", ChatBattery.Expected.Registers(600, categoryId = "alimentacao", payment = ChatPayment.Cash)),
        GradedCase("h04", "Categorias", "micro-ondas 480 no cartão", ChatBattery.Expected.Registers(48000, categoryId = "casa", payment = ChatPayment.Card)),
        GradedCase("h05", "Valores", "notebook 2.400 em 8x no cartão", ChatBattery.Expected.Registers(240000, 8, AmountRole.Total, categoryId = "eletronicos.equipamentos", payment = ChatPayment.Card)),
        GradedCase("h06", "Valores", "computador 4 parcelas de 310 no cartão", ChatBattery.Expected.Registers(31000, 4, AmountRole.PerInstallment, categoryId = "eletronicos.equipamentos", payment = ChatPayment.Card)),
        GradedCase("h07", "Categorias", "Shopee 94 no pix", ChatBattery.Expected.Registers(9400, payment = ChatPayment.Pix, accountId = "account-1")),
        GradedCase("h08", "Categorias", "gastei 23 no pix", ChatBattery.Expected.Registers(2300, payment = ChatPayment.Pix, accountId = "account-1")),
        GradedCase("h09", "Categorias", "ração 112 no débito", ChatBattery.Expected.Registers(11200, categoryId = "pet.alimentacao", payment = ChatPayment.Debit, accountId = "account-1")),
        GradedCase("h10", "Categorias", "gasolina 180 no pix", ChatBattery.Expected.Registers(18000, categoryId = "transporte.combustivel", payment = ChatPayment.Pix, accountId = "account-1")),
        GradedCase("h11", "Categorias", "remédio 42 no cartão", ChatBattery.Expected.Registers(4200, categoryId = "saude.medicamentos", payment = ChatPayment.Card)),
        GradedCase("h12", "Datas", "mercado 63 anteontem no pix", ChatBattery.Expected.Registers(6300, date = LocalDate.of(2026, 10, 20), categoryId = "alimentacao.mercado", payment = ChatPayment.Pix, accountId = "account-1")),
        GradedCase("h13", "Datas", "mercado 54 dia 9 no cartão", ChatBattery.Expected.Registers(5400, date = LocalDate.of(2026, 10, 9), categoryId = "alimentacao.mercado", payment = ChatPayment.Card)),
        GradedCase("h14", "Datas", "recebi 2.750 hoje", ChatBattery.Expected.Registers(275000, nature = Nature.Income, accountId = "account-1")),
        GradedCase("h15", "Datas", "recebi 800 mês que vem", ChatBattery.Expected.Registers(80000, date = LocalDate.of(2026, 11, 22), nature = Nature.Income, accountId = "account-1")),
        GradedCase("h16", "Datas", "delivery 51 no cartão ontem", ChatBattery.Expected.Registers(5100, date = LocalDate.of(2026, 10, 21), categoryId = "alimentacao.delivery", payment = ChatPayment.Card)),
        GradedCase("h17", "Categorias", "passeio 46 em dinheiro", ChatBattery.Expected.Registers(4600, categoryId = "lazer.passeios", payment = ChatPayment.Cash)),
        GradedCase("h18", "Pagamento", "comida 18", ChatBattery.Expected.Asks(QuestionField.Payment)),
        GradedCase("h19", "Valores", "comprei um casaco", ChatBattery.Expected.Asks(QuestionField.Amount)),
        GradedCase("h20", "Valores", "lanchinho 0 no cartão", ChatBattery.Expected.Asks(QuestionField.Amount)),
        GradedCase("h21", "Conversa", "quero trocar por Saúde", ChatBattery.Expected.Corrects(EntryChange.Category("saude"))),
        GradedCase("h22", "Conversa", "na verdade foi dinheiro", ChatBattery.Expected.Corrects(EntryChange.Payment(ChatPayment.Cash, null))),
        GradedCase("h23", "Conversa", "o valor certo é 78,20", ChatBattery.Expected.Corrects(EntryChange.Amount(Money(7820)))),
        GradedCase("h24", "Consultas", "quanto ficou em transporte?", ChatBattery.Expected.Replies(StandardReply.Query)),
        GradedCase("h25", "Intenções", "estou pensando em comprar um celular de 900", ChatBattery.Expected.Replies(StandardReply.Intent)),
        GradedCase("h26", "Fora do Chat", "mensalidade da academia 99", ChatBattery.Expected.Replies(StandardReply.UnsupportedRecurrence)),
        GradedCase("h27", "Fora do Chat", "quitei o boleto de 215", ChatBattery.Expected.Replies(StandardReply.UnsupportedBoleto)),
        GradedCase("h28", "Fora do Chat", "a loja devolveu 65 de estorno", ChatBattery.Expected.Replies(StandardReply.UnsupportedRefund)),
        GradedCase("h29", "Fora do Chat", "quitei a fatura do cartão", ChatBattery.Expected.Replies(StandardReply.UnsupportedInvoicePayment)),
        GradedCase("h30", "Fora do Chat", "cancelamento das parcelas da câmera", ChatBattery.Expected.Replies(StandardReply.UnsupportedCancellation)),
    )
}
