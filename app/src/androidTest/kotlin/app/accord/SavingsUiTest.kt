package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.*
import app.accord.core.sync.*
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavingsUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<AccordApplication>()
    private var scenario:ActivityScenario<MainActivity>?=null
    private var original:AppContainer?=null
    private var provider:AppContainer?=null
    private var ownerId:String?=null
    private fun <T> ok(result:RemoteResult<T>)=(result as RemoteResult.Ok).value
    @After fun close() {
        scenario?.close()
        provider?.let { runBlocking { it.access.signOut(confirmPending=true) } }
        original?.let { app.container=it }
        ownerId?.let { id -> app.databaseList().filter { it.startsWith("accord-$id-") && it.endsWith(".db") }.forEach(app::deleteDatabase) }
    }

    @Test fun criarRetomarFormularioGuardarResgatarEArquivarPelosFormulariosReais()=runBlocking<Unit> {
        val service=FakeSpaceService()
        val owner=ok(service.signUp("pocket-ui-${UUID.randomUUID()}@exemplo.test","senha-ficticia")); ownerId=owner.accountId
        val spaceId=UUID.randomUUID().toString(); ok(service.createSpace(owner.accessToken,spaceId,"Casa"))
        val snapshot=AccessSnapshot(session=StoredSession(owner.accountId,owner.email,owner.accessToken,owner.refreshToken,owner.accessExpiresAtMillis),
            bindings=mapOf(owner.accountId to SpaceBinding(owner.accountId,spaceId,"Casa",firstStepsDismissed=true)))
        original=app.container
        val container=AppContainer(app,InMemoryAccessStore(snapshot),service); container.appLock.setEnabled(false); container.awaitReady()
        provider=container; app.container=container
        val space=container.space()
        space.execute(Envelope(CommandId(UUID.randomUUID().toString())),AddAccount(BankRef.Other("Banco fictício"),"Conta de teste",Money(100000),container.clock.today()))
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Cofrinhos").performScrollTo().performClick()
        compose.onNodeWithText("Criar cofrinho").performScrollTo().performClick()
        compose.onNode(hasText("Conta de teste") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Nome do cofrinho").performScrollTo().performTextInput("Viagem")
        compose.onNodeWithText("Alvo em reais (opcional)").performScrollTo().performTextInput("600")
        compose.onNodeWithText("Saldo inicial em reais (em branco: zero)").performScrollTo().performTextInput("500")
        compose.onNodeWithText("Sim, transferir da conta para o cofrinho").performScrollTo().performClick()
        scenario!!.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Nome do cofrinho").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nome do cofrinho").assertTextContains("Viagem")
        fun save() { compose.onNode(hasText("Salvar") and hasAnyAncestor(isDialog())).performScrollTo().performClick() }
        save()
        compose.waitUntil(15_000) { runBlocking { (space.savingsPockets() as QueryResult.Data).value.size==1 } }
        val p=(space.savingsPockets() as QueryResult.Data).value.single()
        fun balance()=(runBlocking { space.accountBalances() } as QueryResult.Data).value.single { it.accountId==p.accountId }
        assertEquals(Money(50000),balance().balance); assertEquals(Money(100000),balance().totalBalance)
        compose.onNodeWithText("Viagem").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.onNodeWithText("Valor em reais").performScrollTo().performTextInput("200")
        save()
        compose.waitUntil(15_000) { balance().pocketsBalance==Money(70000) }
        assertEquals(Money(30000),balance().balance); assertEquals(Money(100000),balance().totalBalance)
        compose.onNodeWithText("Resgatar").performScrollTo().performClick()
        compose.onNodeWithText("Valor em reais").performScrollTo().performTextInput("700")
        save()
        compose.waitUntil(15_000) { balance().pocketsBalance==Money.ZERO }
        compose.onNodeWithText("Arquivar cofrinho").performScrollTo().performClick()
        save()
        compose.waitUntil(15_000) { runBlocking { (space.savingsPockets() as QueryResult.Data).value.single().archived } }
        assertEquals(Money(100000),balance().balance)
        assertEquals(3,(space.savingsMoves(p.id) as QueryResult.Data).value.size)
    }
}
