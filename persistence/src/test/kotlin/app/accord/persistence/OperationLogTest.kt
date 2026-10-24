package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationLogTest : SpaceTestBase() {
    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    @Test
    fun `salvo no aparelho difere de reconhecido e nada é apresentado como sincronizado antes do S2`() = runBlocking<Unit> {
        val h = open()
        h.account()
        assertTrue(h.space.operations().value().none { it.acknowledged })
    }

    @Test
    fun `reconhecer a operação a marca como reconhecida e guarda a versão do serviço`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val first = h.space.operations().value().first()
        h.space.recordAcknowledgement(first.operationId, mapOf(EntityRef(EntityType.Account, account) to 10L)).value()
        val ops = h.space.operations().value()
        assertTrue(ops.first().acknowledged)
        assertTrue(ops.drop(1).none { it.acknowledged })
    }

    @Test
    fun `a versão reconhecida vira a versão base da próxima operação sobre a mesma entidade`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val ops = h.space.operations().value()
        // antes de reconhecer, a versão base é vazia
        assertNull(ops.last().changes.single().baseVersion)

        h.space.recordAcknowledgement(ops.last().operationId, mapOf(EntityRef(EntityType.Preferences, ops.last().changes.single().id) to 7L)).value()
        val prefs = ops.last().changes.single().id
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied().let {
            assertEquals(7L, it.changes.single().baseVersion)
            assertEquals(prefs, it.changes.single().id)
            assertEquals(2L, it.changes.single().revision)
        }
        assertEquals(7L, h.space.operations().value().last().changes.single().baseVersion)
    }

    @Test
    fun `operação desconhecida no reconhecimento é falha identificada e nada muda`() = runBlocking<Unit> {
        val h = open()
        h.account()
        assertIs<QueryResult.Failure>(h.space.recordAcknowledgement("nada", emptyMap()))
        assertTrue(h.space.operations().value().none { it.acknowledged })
    }

    @Test
    fun `operações são imutáveis — desfazer e correções entram como novas`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val r = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Cash, "Troco", Classification.ToClassify, d(2026, 10, 1), brl(5)),
        )
        assertIs<CommandResult.Applied>(r)
        val before = h.space.operations().value()
        h.space.execute(h.env(), app.accord.core.space.Undo(r.receipt.operationId)).applied()
        val after = h.space.operations().value()
        assertEquals(before, after.take(before.size))
        assertEquals(before.size + 1, after.size)
        assertEquals(after.map { it.sequence }.sorted(), after.map { it.sequence })
    }
}
