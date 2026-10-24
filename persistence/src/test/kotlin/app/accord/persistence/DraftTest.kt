package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.DraftSource
import app.accord.core.space.Envelope
import app.accord.core.space.LocalDraft
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DraftTest : SpaceTestBase() {
    private suspend fun Handle.withDefaultAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private val oct = ym(2026, 10)

    private fun pix(amount: Money) = RegisterDirectPayment(
        DirectPaymentKind.Pix, "Mercado", Classification.Category(seed("alimentacao.mercado")), d(2026, 10, 20), amount,
    )

    private fun draft(id: String = "") = LocalDraft(
        id = id,
        source = DraftSource.Chat,
        fields = mapOf("description" to "Mercado", "date" to "2026-10-20", "kind" to "Pix & débito = x"),
        missing = listOf("amount"),
        reason = "Falta o valor",
    )

    @Test
    fun `registro sem valor é rejeitado e o rascunho guardado não gera operação nem altera totais`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val before = h.space.operations().value().size
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), pix(Money.ZERO)))

        val id = h.space.saveDraft(draft()).value()
        assertTrue(id.isNotBlank())
        assertEquals(before, h.space.operations().value().size)
        assertEquals(Money.ZERO, h.space.budget(oct).value().committed)
        val saved = h.space.drafts().value().single()
        assertEquals(id, saved.id)
        assertEquals(listOf("amount"), saved.missing)
        assertEquals("Pix & débito = x", saved.fields["kind"]) // valores com símbolos sobrevivem à gravação
    }

    @Test
    fun `salvar com o mesmo ID atualiza o rascunho`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.saveDraft(draft()).value()
        h.space.saveDraft(draft(id).copy(fields = mapOf("description" to "Padaria"), missing = emptyList())).value()
        val all = h.space.drafts().value()
        assertEquals(1, all.size)
        assertEquals("Padaria", all.single().fields["description"])
    }

    @Test
    fun `efetivar o rascunho com valor grava um lançamento e descarta o rascunho na mesma transação`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.saveDraft(draft()).value()

        h.space.execute(Envelope(CommandId("efetiva"), draftId = id), pix(Money(5_000))).applied()

        assertEquals(1, h.space.movements(oct).value().size)
        assertEquals(emptyList(), h.space.drafts().value())
    }

    @Test
    fun `se a gravação falha o rascunho permanece e o lançamento não existe`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.saveDraft(draft()).value()
        h.fault = { error("falha injetada") }
        assertFailsWith<IllegalStateException> { h.space.execute(Envelope(CommandId("efetiva"), draftId = id), pix(Money(5_000))) }
        h.fault = null
        assertEquals(emptyList(), h.space.movements(oct).value())
        assertEquals(1, h.space.drafts().value().size)
    }

    @Test
    fun `comando rejeitado não descarta o rascunho`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.saveDraft(draft()).value()
        assertIs<CommandResult.Rejected>(h.space.execute(Envelope(CommandId("ruim"), draftId = id), pix(Money.ZERO)))
        assertEquals(1, h.space.drafts().value().size)
    }

    @Test
    fun `descartar remove só o rascunho`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.saveDraft(draft()).value()
        h.space.discardDraft(id).value()
        assertEquals(emptyList(), h.space.drafts().value())
    }

    @Test
    fun `rascunhos não aparecem na fila de operações lida pelo S2`() = runBlocking<Unit> {
        val h = open()
        h.space.saveDraft(draft()).value()
        assertEquals(emptyList(), h.space.operations().value())
    }
}
