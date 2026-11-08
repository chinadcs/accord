package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CreateIntention
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditIntention
import app.accord.core.space.Envelope
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.IntentionSituation
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S7 / ADR 0048: conversão de intenção em compromisso, no mesmo aparelho. Valores fictícios; hoje é 24/10/2026. */
class IntentionConversionTest : SpaceTestBase() {
    private val nov = ym(2026, 11)
    private val months = (0..5).map { nov.plusMonths(it.toLong()) } // novembro/2026 a abril/2027

    private suspend fun Handle.account() {
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
    }

    private suspend fun Handle.tv(): String = space.execute(env(), CreateIntention("Televisão", brl(1200), nov)).applied().changes.single().id

    private fun purchase(origin: String?, id: CommandId? = null) = RegisterCardPurchase(
        description = "Televisão", classification = Classification.Category(seed("eletronicos.equipamentos")), date = d(2026, 10, 24),
        amount = brl(1200), amountMeaning = AmountMeaning.Total, installments = 6, invoice = nov, originIntentionId = origin,
    )

    private fun CommandResult.errorCodes() = assertIs<CommandResult.Rejected>(this).errors.map { it.code }

    // ---- cenário 10: conversão única ----

    @Test fun `converter, tocar de novo e repetir uma vez so criam um compromisso, uma operacao e R$ 200 por fatura`() = runBlocking<Unit> {
        val h = open()
        val intention = h.tv()
        val before = h.space.operations().value().size
        val command = CommandId("converter-tv")

        val first = h.space.execute(Envelope(command), purchase(intention)).applied()
        val again = h.space.execute(Envelope(command), purchase(intention))
        assertIs<CommandResult.AlreadyApplied>(again)
        assertEquals(first.changes, again.receipt.changes, "o mesmo resultado, não outro compromisso")

        assertEquals(before + 1, h.space.operations().value().size, "uma única operação na fila")
        for (m in months) assertEquals(brl(200), h.space.commitments(m).value().knownTotal, "fatura de $m, uma vez só")
        assertEquals(brl(0), h.space.commitments(ym(2026, 10)).value().knownTotal)

        val view = h.space.intention(intention).value()!!
        assertEquals(IntentionSituation.Converted, view.situation)
        assertEquals(EntityType.CardPurchase, view.conversion!!.type)
        assertEquals(first.changes.single().id, view.conversion!!.id)

        // "Assumir compromisso" some: uma segunda conversão, em outro toque de verdade, é recusada sem gravar.
        assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), purchase(intention)).errorCodes())
        assertEquals(before + 1, h.space.operations().value().size)
        for (m in months) assertEquals(brl(200), h.space.commitments(m).value().knownTotal)
    }

    // ---- cenário 11: conversão cancelada ----

    @Test fun `cancelar a conversao mantem a intencao planejada, sem vinculo e com os totais intactos`() = runBlocking<Unit> {
        val h = open()
        val intention = h.tv()
        val totals = months.map { h.space.commitments(it).value() }
        // Abrir o registro e prever não grava nada.
        h.space.preview(purchase(intention))
        val view = h.space.intention(intention).value()!!
        assertEquals(IntentionSituation.Planned, view.situation)
        assertNull(view.conversion)
        assertEquals(totals, months.map { h.space.commitments(it).value() })
    }

    @Test fun `intencao inexistente e recusada e nada e gravado`() = runBlocking<Unit> {
        val h = open()
        val before = h.space.operations().value().size
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), purchase("nao-existe")).errorCodes())
        assertEquals(before, h.space.operations().value().size)
        assertTrue(months.all { h.space.commitments(it).value().items.isEmpty() })
    }

    @Test fun `desfazer o compromisso mantem o vinculo e a intencao nao pode ser convertida de novo`() = runBlocking<Unit> {
        val h = open()
        val intention = h.tv()
        val receipt = h.space.execute(h.env(), purchase(intention)).applied()
        assertTrue(receipt.undoable)
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertTrue(months.all { h.space.commitments(it).value().items.isEmpty() }, "o compromisso saiu do orçamento")

        val view = h.space.intention(intention).value()!!
        assertEquals(IntentionSituation.Converted, view.situation, "ADR 0048: excluir o compromisso mantém o vínculo")
        assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), purchase(intention)).errorCodes())
    }

    @Test fun `os quatro tipos de compromisso raiz convertem e uma intencao so converte uma vez`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val pix = h.space.execute(h.env(), CreateIntention("Cadeira")).applied().changes.single().id
        val boleto = h.space.execute(h.env(), CreateIntention("Sofá")).applied().changes.single().id
        val series = h.space.execute(h.env(), CreateIntention("Academia")).applied().changes.single().id
        val category = Classification.Category(seed("casa"))

        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "Cadeira", category, d(2026, 10, 24), brl(300), originIntentionId = pix)).applied()
        h.space.execute(h.env(), RegisterBoleto("Sofá", category, d(2026, 11, 10), brl(900), originIntentionId = boleto)).applied()
        h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Academia", seed("saude"), brl(100), frequency = Frequency.Monthly, firstDate = d(2026, 11, 5), originIntentionId = series),
        ).applied()

        val views = h.space.intentions().value().associateBy { it.id }
        assertEquals(EntityType.Movement, views.getValue(pix).conversion!!.type)
        assertEquals(EntityType.Boleto, views.getValue(boleto).conversion!!.type)
        assertEquals(EntityType.Recurrence, views.getValue(series).conversion!!.type)
        assertTrue(h.space.intentions().value().all { it.situation == IntentionSituation.Converted })
    }

    @Test fun `editar o compromisso depois nao apaga o vinculo e editar a intencao convertida nao mexe no compromisso`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val intention = h.space.execute(h.env(), CreateIntention("Cadeira", brl(300))).applied().changes.single().id
        val entry = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Cadeira", Classification.Category(seed("casa")), d(2026, 10, 24), brl(300), originIntentionId = intention),
        ).applied().changes.single().id
        val totals = h.space.budget(ym(2026, 10)).value()

        h.space.execute(h.env(), CorrectEntry(entry, description = "Cadeira de escritório")).applied()
        assertEquals(entry, h.space.intention(intention).value()!!.conversion!!.id, "o vínculo sobrevive à edição do compromisso")

        // A intenção convertida continua editável, sem efeito no compromisso.
        h.space.execute(h.env(), EditIntention(intention, "Cadeira nova", brl(350), null, null)).applied()
        assertEquals(totals, h.space.budget(ym(2026, 10)).value())
        assertEquals(IntentionSituation.Converted, h.space.intention(intention).value()!!.situation)
    }
}
