package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.sync.AccessSnapshot
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import app.accord.ui.theme.ThemePreference
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
        // O passo de notificações dos Primeiros passos é obrigatório (S5.5): estes fluxos já chegam com a permissão concedida.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ApplicationProvider.getApplicationContext<AccordApplication>().packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        store = InMemoryAccessStore(AccessSnapshot())
        launch(store)
    }

    private fun launch(accessStore: InMemoryAccessStore) {
        scenario?.close()
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        container = AppContainer(app, accessStore, service)
        runBlocking { container.awaitReady() }
        if (UiAuditSyntheticEvidence.enabled) container.theme.set(UiAuditSyntheticEvidence.theme ?: ThemePreference.Light)
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

    /** Dispensa o aviso de recibo (snackbar) que ficou sobre o rodapé, como a pessoa faria. */
    private fun dismissReceipt(text: String) {
        waitForText(text)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun tap(text: String) {
        waitForText(text)
        scrollTo(text)
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
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

    private fun auditCapture(name: String) {
        if (UiAuditSyntheticEvidence.enabled) UiAuditSyntheticEvidence.capture(
            compose, name, UiAuditSyntheticEvidence.theme ?: ThemePreference.Light, "window",
        )
    }

    @Test
    fun primeirosPassosTemCartaoVazioApelidosOpcionaisELimitesOpcionais() {
        runBlocking {
            container.access.signUp(email, "senha-1234", "senha-1234")
        }
        waitForText("Espaço da casa")
        runBlocking { container.access.createSpace("Casa") }
        waitForText("Você: conectado")
        tap("Continuar para primeiros passos")
        waitForText("Vamos começar")
        tap("Configurar")
        waitForText("Dia de fechamento")
        auditCapture("03_cartao_configuracao")
        listOf("Dia de fechamento", "Dia de vencimento").forEach { label ->
            assertEquals("", compose.onNodeWithText(label).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        }
        assertTrue(compose.onAllNodesWithText("Faturas").fetchSemanticsNodes().isEmpty())
        tap("Selecionar banco")
        compose.onNode(hasText("Outro banco (informar", substring = true) and hasClickAction()).performClick()
        type("Nome do banco", "Banco Casa")
        dialogButton("Usar este nome")
        type("Dia de fechamento", "10")
        type("Dia de vencimento", "20")
        tap("Salvar cartão")
        waitForText("Cartão salvo no aparelho.")
        runBlocking {
            val card = (container.space().card() as app.accord.core.space.QueryResult.Data).value
            assertEquals("Banco Casa", card.nickname)
            assertEquals(10, card.closingDay)
            assertEquals(20, card.dueDay)
        }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Vamos começar")
        tap("Gerenciar contas")
        waitForText("Adicionar conta")
        auditCapture("03_contas_configuracao")
        tap("Adicionar conta")
        tap("Selecionar banco")
        compose.onNode(hasText("Outro banco (informar", substring = true) and hasClickAction()).performClick()
        type("Nome do banco", "Banco Casa")
        dialogButton("Usar este nome")
        tap("Salvar conta")
        waitForText("Minha conta padrão")
        runBlocking {
            val accounts = (container.space().accounts() as app.accord.core.space.QueryResult.Data).value
            assertEquals("Banco Casa", accounts.accounts.single().nickname)
            assertEquals(accounts.accounts.single().id, accounts.myDefaultAccountId)
        }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Vamos começar")
        tap("Definir limites")
        waitForText("Vale a partir deste mês e nos próximos.")
        auditCapture("03_limites_configuracao")
        assertTrue(compose.onAllNodesWithText("Somente", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Definir depois").performClick()
        waitForText("Vamos começar")
        tap("Definir limites")
        waitForText("Total das categorias")
        scrollTo("Limite de Alimentação (R$)")
        compose.onNodeWithText("Limite de Alimentação (R$)").performTextReplacement("500,00")
        compose.onNodeWithText("Salvar limites").performClick()
        waitForText("Vamos começar")
        runBlocking {
            val space = container.space()
            val food = (space.categories() as app.accord.core.space.QueryResult.Data).value.single { it.name == "Alimentação" && it.parentId == null }.id
            val month = container.clock.currentMonth()
            for (offset in 0L..2L) {
                val limits = (space.limits(month.plusMonths(offset)) as app.accord.core.space.QueryResult.Data).value
                assertEquals(app.accord.core.Money(50_000), limits.single { it.categoryId == food }.setting.total)
            }
            val previous = (space.limits(month.minusMonths(1)) as app.accord.core.space.QueryResult.Data).value
            assertTrue(previous.none { it.setting.total != null })
        }
        // O aviso "Limite atualizado" volta com a tela e cobre os botões do rodapé até ser dispensado.
        dismissReceipt("Limite atualizado")
        tap("Começar a registrar")
        waitForText("Mensagem do Chat")
        waitForText("Orçamento")
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        assertTrue(compose.onAllNodesWithText("Primeiros passos").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cadastroEspacoConviteEPrimeirosPassosAteOChat() {
        waitForText("Controle financeiro colaborativo")
        tap("Criar acesso")
        waitForText("Nenhum e-mail de confirmação é enviado")
        type("E-mail", email)
        type("Senha", "senha-1234")
        type("Confirmar senha", "senha-1234")
        tapLast("Criar acesso")

        waitForText("Espaço da casa")
        waitForText("Nome do espaço")
        type("Nome do espaço", "Nosso orçamento")
        tapLast("Criar espaço")
        waitForText("Você: conectado")
        waitForText("1 de 4 pessoas no espaço")
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
        // O Chat inicia o uso (sem rótulo na barra): o campo de mensagem e a raiz Orçamento confirmam a chegada.
        waitForText("Mensagem do Chat")
        waitForText("Orçamento")
    }

    @Test
    fun barraInferiorFuncionaDepoisDeRegistrarManualmenteNosPrimeirosPassos() {
        // accord-6hu: "Registrar manualmente" remove os Primeiros passos (início do grafo); sair do formulário e usar a barra tem de funcionar.
        runBlocking { container.access.signUp(email, "senha-1234", "senha-1234") }
        waitForText("Espaço da casa")
        runBlocking { container.access.createSpace("Casa") }
        waitForText("Você: conectado")
        tap("Continuar para primeiros passos")
        waitForText("Vamos começar")
        waitForText("10 categorias, editáveis.")
        tap("Registrar manualmente")
        waitForText("Salvar")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        waitForText("Mensagem do Chat")
        compose.onNodeWithText("Lançamentos").performClick()
        waitForText("Saldo total")
        compose.onNodeWithText("Orçamento").performClick()
        waitForText("Quando houver registros, o orçamento aparece aqui.")
        assertTrue(compose.onAllNodesWithText("Saldo total").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Chat").performClick()
        waitForText("Mensagem do Chat")
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        // Em Mais "Orçamento" também é título de grupo: a aba é o último nó.
        compose.onAllNodesWithText("Orçamento").onLast().performClick()
        waitForText("Quando houver registros, o orçamento aparece aqui.")
        compose.onNodeWithContentDescription("Chat").performClick()
        waitForText("Mensagem do Chat")
    }

    @Test
    fun credencialRecusadaEFalhaDeRedeTemMensagensDiferentesEPreservamOEmail() {
        waitForText("Controle financeiro colaborativo")
        type("E-mail", email)
        type("Senha", "senha-errada")
        tapLast("Entrar")
        waitForText("E-mail ou senha incorretos.")
        auditCapture("01_erro_credencial")

        service.online = false
        tapLast("Entrar")
        waitForText("Sem conexão. A entrada não foi concluída e seu e-mail foi mantido.")
        auditCapture("01_erro_rede")
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

        waitForText("Controle financeiro colaborativo")
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
        waitForText("Controle financeiro colaborativo")
        runBlocking {
            container.access.signUp(email, "senha-1234", "senha-1234")
            container.access.createSpace("Casa")
            container.access.dismissFirstSteps()
        }
        waitForText("Mensagem do Chat")
        compose.onNodeWithText("Mais").performClick()
        waitForText("Conectado como $email")
        tap("Sair")
        waitForText("Sair da conta?")
        waitForText("Os dados deste aparelho continuam guardados para esta conta.")
        dialogButton("Sair assim mesmo")
        waitForText("Controle financeiro colaborativo")
        compose.onNodeWithText(email).assertExists()
    }

    @Test
    fun reiniciarOAplicativoRetomaOndeParouSemUsarARede() {
        waitForText("Controle financeiro colaborativo")
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
