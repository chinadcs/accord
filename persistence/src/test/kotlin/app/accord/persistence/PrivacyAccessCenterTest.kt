package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.chat.ChatMessage
import app.accord.core.space.Session
import app.accord.core.sync.*
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import java.util.UUID
import kotlin.test.*

class PrivacyAccessCenterTest {
    private val databases = mutableListOf<TestDatabase>()
    @AfterTest fun close() { databases.forEach { it.close() } }
    private fun <T> ok(result: RemoteResult<T>): T = assertIs<RemoteResult.Ok<T>>(result).value

    private inner class Factory(private val remote: FakeSpaceService) : SpaceRuntimeFactory {
        val rooms = mutableMapOf<Pair<String,String>,TestDatabase>()
        val erased = mutableListOf<String>()
        override suspend fun open(accountId: String, spaceId: String, access: SyncAccess): SpaceRuntime {
            val db = rooms.getOrPut(accountId to spaceId) { TestDatabase.create().also(databases::add) }
            val identity = Session(spaceId,accountId)
            val space = RoomFinanceSpace.open(db.db, BudgetClock.system(), { identity })
            val store = SyncStore(db.db)
            return SpaceRuntime(space,store,SyncEngine(store,remote,access),RoomChatStore(db.db),identity,dataExport=RoomDataExport(db.db),
                resetLocal={ RoomDataExport(db.db).clearForReset(); space.reseed() })
        }
        override fun close(accountId: String, spaceId: String) = Unit
        override suspend fun eraseAccountData(accountId: String) { erased += accountId }
        override suspend fun archiveSpace(accountId: String, spaceId: String) { rooms[accountId to spaceId]?.let { RoomDataExport(it.db).clearFinancial() } }
        override suspend fun archivedChatData(accountId: String, spaceId: String): String? = rooms[accountId to spaceId]?.let { RoomDataExport(it.db).snapshot(DataExportScope.Personal).toString() }
    }

    private fun snapshot(user: AuthSession, space: String) = AccessSnapshot(
        session=StoredSession(user.accountId,user.email,user.accessToken,user.refreshToken,user.accessExpiresAtMillis),
        bindings=mapOf(user.accountId to SpaceBinding(user.accountId,space,"Casa",firstStepsDismissed=true)))

    @Test fun `comprovante sobrevive reinicio e resposta perdida apos remover Auth`() = runBlocking<Unit> {
        val remote = FakeSpaceService(); val user=ok(remote.signUp("recovery@teste.exemplo","senha-ficticia-1"))
        val space=UUID.randomUUID().toString(); ok(remote.createSpace(user.accessToken,space,"Casa"))
        val factory=Factory(remote); val store=InMemoryAccessStore(snapshot(user,space))
        var center=AccessCenter(remote,store,factory,{YearMonth.of(2026,11)})
        center.restore(); remote.loseNextResponse()
        assertIs<RemoteResult.Failed>(center.prepareAccountDeletion("senha-ficticia-1"))
        val pending=assertNotNull(store.read().pendingAccountDeletion)
        assertEquals(64,pending.proof.length)
        assertFalse(pending.proof in pending.toString())
        assertTrue(center.status.value.accountDeletionPending)
        val restored=InMemoryAccessStore(AccessSnapshotCodec.decode(AccessSnapshotCodec.encode(store.read())))
        center=AccessCenter(remote,restored,factory,{YearMonth.of(2026,11)}); center.restore()
        remote.loseNextResponse()
        assertIs<RemoteResult.Failed>(center.finishAccountDeletion())
        assertEquals(0,remote.accountCount()); assertNotNull(restored.read().pendingAccountDeletion)
        assertIs<RemoteResult.Ok<String>>(center.finishAccountDeletion())
        assertNull(restored.read().session); assertNull(restored.read().pendingAccountDeletion)
        assertEquals(listOf(user.accountId),factory.erased)
        assertTrue(center.status.value.accountDeletionCompleted)
    }

    @Test fun `senha incorreta nao deixa aparelho preso em exclusao`() = runBlocking<Unit> {
        val remote=FakeSpaceService(); val user=ok(remote.signUp("wrong@teste.exemplo","senha-ficticia-1"))
        val space=UUID.randomUUID().toString(); ok(remote.createSpace(user.accessToken,space,"Casa"))
        val store=InMemoryAccessStore(snapshot(user,space)); val center=AccessCenter(remote,store,Factory(remote),{YearMonth.of(2026,11)})
        center.restore()
        assertEquals(RemoteFailure.InvalidCredentials,assertIs<RemoteResult.Failed>(center.prepareAccountDeletion("errada")).failure)
        assertNull(store.read().pendingAccountDeletion); assertFalse(center.status.value.accountDeletionPending)
        assertEquals(space,center.status.value.space?.spaceId)
    }

    @Test fun `reconectar arquiva somente Chat e outra conta nao pode abri lo`() = runBlocking<Unit> {
        val remote=FakeSpaceService(); val a=ok(remote.signUp("archive-a@teste.exemplo","senha-ficticia-1")); val b=ok(remote.signUp("archive-b@teste.exemplo","senha-ficticia-2"))
        val space=UUID.randomUUID().toString(); ok(remote.createSpace(a.accessToken,space,"Casa"))
        val invite=ok(remote.generateInvite(a.accessToken,space)); ok(remote.joinWithCode(b.accessToken,invite.code))
        val factory=Factory(remote); val store=InMemoryAccessStore(snapshot(a,space)); val center=AccessCenter(remote,store,factory,{YearMonth.of(2026,11)})
        center.restore(); center.runtime.value!!.chatStore!!.saveMessage(ChatMessage("msg","mensagem fictícia",100))
        val request=UUID.randomUUID().toString()
        ok(remote.privacy(a.accessToken,PrivacyCommand(PrivacyAction.RequestSpaceDeletion,request,password="senha-ficticia-1",spaceId=space)))
        ok(remote.privacy(b.accessToken,PrivacyCommand(PrivacyAction.ApproveSpaceDeletion,request,password="senha-ficticia-2")))
        center.reconcilePrivacy()
        assertNull(center.runtime.value); assertNull(center.status.value.space)
        assertEquals(1,center.status.value.archivedSpaces.size)
        assertTrue(center.archivedChatData(space)!!.contains("mensagem fictícia"))
        center.signOut(true); center.signIn(b.email,"senha-ficticia-2")
        assertNull(center.archivedChatData(space)); assertTrue(center.status.value.archivedSpaces.isEmpty())
    }

    @Test fun `reset aprovado por todos limpa dados e fila locais, preserva Chat e so roda uma vez`() = runBlocking<Unit> {
        val remote=FakeSpaceService(); val a=ok(remote.signUp("reset-a@teste.exemplo","senha-ficticia-1")); val b=ok(remote.signUp("reset-b@teste.exemplo","senha-ficticia-2"))
        val space=UUID.randomUUID().toString(); ok(remote.createSpace(a.accessToken,space,"Casa"))
        val invite=ok(remote.generateInvite(a.accessToken,space)); ok(remote.joinWithCode(b.accessToken,invite.code))
        val factory=Factory(remote); val store=InMemoryAccessStore(snapshot(a,space)); val center=AccessCenter(remote,store,factory,{YearMonth.of(2026,11)})
        center.restore()
        val runtime=center.runtime.value!!
        runtime.chatStore!!.saveMessage(ChatMessage("msg","mensagem fictícia",100))
        assertIs<app.accord.core.space.CommandResult.Applied>(runtime.space.execute(app.accord.core.space.Envelope(app.accord.core.space.CommandId("cartao-reset")),
            app.accord.core.space.ConfigureCard(null,"Cartão do reset",8,15)))
        assertEquals(1,runtime.syncStore.summary().awaitingOperations)
        assertIs<SyncOutcome.Completed>(runtime.engine.syncNow())
        assertTrue(remote.entityCount(space)>0)
        // Alteração ainda não enviada: o reset a descarta, e o aviso da tela diz isso.
        runtime.space.execute(app.accord.core.space.Envelope(app.accord.core.space.CommandId("cartao-pendente")),
            app.accord.core.space.ConfigureCard(null,"Cartão pendente",8,15))
        assertEquals(1,runtime.syncStore.summary().awaitingOperations)

        val request=UUID.randomUUID().toString()
        assertIs<RemoteResult.Ok<String>>(center.privacySpaceAction(PrivacyAction.RequestSpaceReset,request,"senha-ficticia-1"))
        assertEquals(0L,store.read().bindings[a.accountId]!!.resetEpoch,"sem unanimidade nada é limpo")
        assertEquals(1,runtime.syncStore.summary().awaitingOperations)
        ok(remote.privacy(b.accessToken,PrivacyCommand(PrivacyAction.ApproveSpaceDeletion,request,password="senha-ficticia-2")))
        assertEquals(0,remote.entityCount(space))

        // O aparelho de quem aprovou primeiro aprende na próxima leitura (antes de cada sincronização).
        assertIs<RemoteResult.Ok<String>>(center.reconcilePrivacy())
        assertEquals(1L,store.read().bindings[a.accountId]!!.resetEpoch)
        assertSame(runtime,center.runtime.value,"o espaço continua aberto, sem recriar a fachada")
        assertEquals(0,runtime.syncStore.summary().awaitingOperations,"fila descartada")
        assertEquals(0L,runtime.syncStore.cursor())
        val local=RoomDataExport(factory.rooms[a.accountId to space]!!.db)
        assertEquals(0,local.snapshot(DataExportScope.Financial)["operation"]!!.let { (it as kotlinx.serialization.json.JsonArray).size })
        assertTrue(local.snapshot(DataExportScope.Personal)["chat_message"].toString().contains("msg"),"Chat preservado")
        assertNotNull(center.status.value.space,"espaço, membros e convite preservados")
        // Nova leitura com a mesma época não limpa de novo.
        runtime.space.execute(app.accord.core.space.Envelope(app.accord.core.space.CommandId("cartao-depois")),
            app.accord.core.space.ConfigureCard(null,"Cartão depois do reset",8,15))
        center.reconcilePrivacy()
        assertEquals(1,runtime.syncStore.summary().awaitingOperations,"mesma época não apaga o que veio depois")
    }
}
