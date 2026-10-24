package app.accord

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.sync.AccessSnapshot
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * O fluxo de entrada de ponta a ponta no aparelho: contas e espaço no servidor fake em memória,
 * banco real do aparelho e a interface de verdade. Cada teste usa e-mails novos.
 */
@RunWith(AndroidJUnit4::class)
class AccessFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val service = FakeSpaceService()
    private val tag = UUID.randomUUID().toString().take(6)
    private val email = "ana-$tag@x.test"
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var container: AppContainer
    private lateinit var store: InMemoryAccessStore

    @Before
    fun freshApp() {
        store = InMemoryAccessStore(AccessSnapshot())
        launch(store)
    }

    private fun launch(accessStore: InMemoryAccessStore) {
        scenario?.close()
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        container = AppContainer(app, accessStore, service)
        runBlocking { container.access.restore() }
        app.container = container
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario?.close()
        // Devolve ao aplicativo de teste a conta de desenvolvimento das demais suítes.
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        app.container = DevIdentity.container(app)
    }

    private fun waitForText(text: String, timeoutMs: Long = 25_000) {
        try {
            compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: $text")
            val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot())
            for (i in 0 until roots.fetchSemanticsNodes().size) roots[i].printToLog("TREE")
            throw e
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun tap(text: String) {
        scrollTo(text)
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst().performClick()
    }

    private fun tapLast(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performScrollTo().performClick()
    }

    private fun dialogButton(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performClick()
    }

    private fun type(label: String, value: String) {
        compose.onNodeWithText(label).performTextInput(value)
    }

    @Test
    fun cadastroEspacoConviteEPrimeirosPassosAteOChat() {
        waitForText("Controle financeiro do casal")
        tap("Criar acesso")
        waitForText("Nenhum e-mail de confirmação é enviado")
        type("E-mail", email)
        type("Senha", "senha-1234")
        type("Confirmar senha", "senha-1234")
        tapLast("Criar acesso")

        waitForText("Espaço do casal")
        waitForText("Nome do espaço")
        type("Nome do espaço", "Nosso orçamento")
        tapLast("Criar espaço")
        waitForText("Você: conectado")
        waitForText("Outra pessoa: aguardando")
        assertTrue(service.sentEmails.isEmpty())

        tap("Gerar código de convite")
        waitForText("O código aparece só agora")
        waitForText("Válido até")
        waitForText("Convidar depois e continuar")
        assertEquals(1, service.spaceCount())

        tap("Convidar depois e continuar")
        waitForText("Vamos começar")
        waitForText("10 categorias, editáveis.")
        waitForText("Sem ele, cada registro pede o pagamento.")
        tap("Começar a registrar")
        waitForText("Registrar manualmente")
        waitForText("Orçamento")
    }

    @Test
    fun credencialRecusadaEFalhaDeRedeTemMensagensDiferentesEPreservamOEmail() {
        waitForText("Controle financeiro do casal")
        type("E-mail", email)
        type("Senha", "senha-errada")
        tapLast("Entrar")
        waitForText("E-mail ou senha incorretos.")

        service.online = false
        tapLast("Entrar")
        waitForText("Sem conexão. A entrada não foi concluída e seu e-mail foi mantido.")
        compose.onNodeWithText(email).assertExists()
        service.online = true
    }

    @Test
    fun entrarComCodigoDaOutraPessoaLevaAoMesmoEspaco() = runBlocking {
        // A primeira pessoa já criou o espaço e gerou o código, direto no serviço fake.
        val first = (service.signUp("primeira-$tag@x.test", "senha-1234") as app.accord.core.sync.RemoteResult.Ok).value
        service.createSpace(first.accessToken, UUID.randomUUID().toString(), "Casa dos dois")
        val code = (service.generateInvite(first.accessToken, (service.currentSpace(first.accessToken) as app.accord.core.sync.RemoteResult.Ok).value!!.spaceId)
            as app.accord.core.sync.RemoteResult.Ok).value.code

        waitForText("Controle financeiro do casal")
        tap("Criar acesso")
        type("E-mail", email)
        type("Senha", "senha-1234")
        type("Confirmar senha", "senha-1234")
        tapLast("Criar acesso")
        waitForText("Entrar com código")
        tap("Entrar com código")
        waitForText("ninguém precisa compartilhar senha")
        type("Código de convite", code.lowercase())
        tapLast("Entrar no espaço")
        waitForText("Você entrou no espaço Casa dos dois")
        assertEquals(2, service.memberCount(service.storedSpaceIds().single()))
    }

    @Test
    fun sairPedeDecisaoEOEmailVoltaPreenchidoNoLogin() {
        waitForText("Controle financeiro do casal")
        runBlocking {
            container.access.signUp(email, "senha-1234", "senha-1234")
            container.access.createSpace("Casa")
            container.access.dismissFirstSteps()
        }
        waitForText("Registrar manualmente")
        compose.onNodeWithText("Mais").performClick()
        waitForText("Conectado como $email")
        tap("Sair")
        waitForText("Sair da conta?")
        waitForText("Os dados deste aparelho continuam guardados para esta conta.")
        dialogButton("Sair assim mesmo")
        waitForText("Controle financeiro do casal")
        compose.onNodeWithText(email).assertExists()
    }

    @Test
    fun reiniciarOAplicativoRetomaOndeParouSemUsarARede() {
        waitForText("Controle financeiro do casal")
        runBlocking {
            container.access.signUp(email, "senha-1234", "senha-1234")
            container.access.createSpace("Casa")
        }
        waitForText("Vamos começar")
        service.online = false
        launch(store)
        waitForText("Vamos começar")
        waitForText("10 categorias, editáveis.")
        service.online = true
    }
}
