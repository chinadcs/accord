package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.OperationResult
import app.accord.core.sync.RemoteEntityState
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O diagnóstico ajuda a entender uma falha sem expor a vida financeira nem credenciais (S2 "Diagnóstico"). */
class SyncDiagnosticsTest : SpaceTestBase() {
    private val secretDescription = "MARCADOR-SECRETO-9F2"
    private val secretRemote = "OUTRO-SEGREDO-71C"

    @Test
    fun `diagnóstico traz operações, estados, causas, tentativas e versões e nenhum dado financeiro`() = runBlocking<Unit> {
        val h = open()
        val account = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco $secretDescription"), "Conta")).applied().changes.single().id
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied()
        val id = h.space.execute(
            h.env(),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, secretDescription, Classification.Category(seed("alimentacao.delivery")),
                LocalDate.of(2026, 10, 22), brl(7777),
            ),
        ).applied().changes.single().id
        val store = SyncStore(h.database.db)

        val first = store.nextBatch(10)
        store.recordResults(first.map { op ->
            OperationResult.Applied(op.operationId, op.changes.map { app.accord.core.sync.AppliedVersion(it.type, it.id, 1) }, 1, false)
        })
        // Um registro que ainda não saiu por falta de rede: a causa e as tentativas ficam, os dados não.
        h.space.execute(
            h.env(),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, "Outro $secretDescription", Classification.Category(seed("alimentacao.mercado")),
                LocalDate.of(2026, 10, 23), brl(4321),
            ),
        ).applied()
        h.space.execute(h.env(), CorrectEntry(id, amount = brl(8888))).applied()
        val pending = store.nextBatch(10)
        val edit = pending.single { it.changes.single().id == id }
        store.recordBatchFailure(pending.map { it.operationId }, "NoNetwork")

        store.recordResults(
            listOf(
                OperationResult.Conflict(
                    edit.operationId,
                    listOf(RemoteEntityState(EntityType.Movement.name, id, 2, """{"description":"$secretRemote"}""", false, "outra-conta", 1, 5)),
                ),
            ),
        )

        val text = store.diagnostics()
        assertTrue(text.startsWith("accord-diagnostico v1"))
        assertTrue("instalacao=" in text && "cursor=" in text)
        assertTrue("op id=${edit.operationId} estado=CONFLICT comando=CorrectEntry" in text, text)
        assertTrue("Movement/base:1" in text, text)
        assertTrue("conflito op=${edit.operationId} situacao=OPEN entidades=Movement(remota v2)" in text, text)
        assertTrue("estado=PENDING comando=RegisterDirectPayment tentativas=1 falha=NoNetwork" in text, text)
        for (forbidden in listOf(secretDescription, secretRemote, "7777", "8888", "4321", "outra-conta", "senha", "token", "@")) {
            assertFalse(forbidden in text, "o diagnóstico vazou '$forbidden':\n$text")
        }
    }
}
