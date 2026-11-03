package app.accord

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.AccessSnapshot
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import app.accord.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Dois aparelhos de verdade (dois contêineres, dois bancos do Android) falando com o servidor fake: a
 * tela de Sincronização, o conflito do exemplo da tela 19 e o agendamento em segundo plano.
 */
@RunWith(AndroidJUnit4::class)
class SyncFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val service = FakeSpaceService()
    private val tag = UUID.randomUUID().toString().take(6)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var ana: AppContainer
    private lateinit var bruno: AppContainer

    private fun done(outcome: app.accord.core.sync.AccessOutcome) {
        check(outcome is app.accord.core.sync.AccessOutcome.Done) { "resultado inesperado: $outcome" }
    }

    private fun context() = ApplicationProvider.getApplicationContext<AccordApplication>()

    @Before
    fun couple() = runBlocking {
        ana = AppContainer(context(), InMemoryAccessStore(AccessSnapshot()), service)
        bruno = AppContainer(context(), InMemoryAccessStore(AccessSnapshot()), service)
        // A leitura inicial do estado salvo termina antes de qualquer ação (a tela só aparece depois dela).
        ana.awaitReady()
        bruno.awaitReady()
        done(ana.access.signUp("ana-$tag@x.test", "senha-1234", "senha-1234"))
        done(ana.access.createSpace("Casa"))
        ana.access.generateInvite()
        val code = checkNotNull(ana.access.status.value.invite?.code)
        done(bruno.access.signUp("bruno-$tag@x.test", "senha-1234", "senha-1234"))
        done(bruno.access.joinWithCode(code))
        ana.access.dismissFirstSteps()
        bruno.access.dismissFirstSteps()
        for (device in listOf(ana, bruno)) {
            val space = device.space()
            val account = (space.execute(Envelope(CommandId("acc-${device.personId}")), AddAccount(BankRef.Other("Banco"), "Conta")) as CommandResult.Applied)
                .receipt.changes.single().id
            space.execute(Envelope(CommandId("def-${device.personId}")), SetMyDefaultAccount(account))
        }
        ana.sync.syncNow()
        bruno.sync.syncNow()
        ana.sync.syncNow()
        Unit
    }

    @After
    fun close() {
        scenario?.close()
        // Devolve ao aplicativo de teste a conta de desenvolvimento das demais suítes.
        val app = context()
        app.container = DevIdentity.container(app)
    }

    private fun launch(container: AppContainer) {
        context().container = container
        scenario = ActivityScenario.launch(MainActivity::class.java)
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

    private fun waitGone(text: String, timeoutMs: Long = 25_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun tap(text: String) {
        // A tela pode ainda estar carregando o conteúdo: espera o texto existir antes de tocar.
        compose.waitUntil(15_000) {
            runCatching { compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true)) }.isSuccess
        }
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst().performClick()
    }

    private fun dialogButton(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performClick()
    }

    private suspend fun AppContainer.pix(description: String, reais: Int, category: String): String {
        val space = space()
        val spaceId = access.status.value.space!!.spaceId
        return (space.execute(
            Envelope(CommandId("pix-$description-$tag")),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, description, Classification.Category(DeterministicIds.seedCategory(spaceId, category)),
                YearMonth.now().atDay(2), Money(reais * 100L),
            ),
        ) as CommandResult.Applied).receipt.changes.single().id
    }

    private suspend fun AppContainer.amountOf(id: String): Money =
        (space().entry(id) as QueryResult.Data).value!!.amount

    @Test
    fun telaMostraAFilaHonestaESincronizarAgoraReconheceSoOQueOServicoAplicou() = runBlocking {
        ana.pix("Mercado $tag", 50, "alimentacao.mercado")
        // Offline na abertura: o ciclo automático falha e a fila continua visível até a pessoa tentar.
        service.online = false
        launch(ana)
        compose.onNodeWithText("Mais").performClick()
        waitForText("Sincronização")
        tap("Sincronização")
        waitForText("1 operação local por enviar")
        waitForText("A visão do casal pode estar desatualizada. O que está salvo aqui ainda não foi enviado.")
        service.online = true
        tap("Sincronizar agora")
        waitForText("Nenhuma operação por enviar neste aparelho")
        waitForText("Última sincronização concluída:")
        waitForText("Sincronizado. Só as operações reconhecidas foram marcadas como enviadas.")
        assertTrue(compose.onAllNodesWithText("Tudo sincronizado", substring = true).fetchSemanticsNodes().isEmpty())
        // O outro aparelho só vê o lançamento depois do próprio ciclo.
        bruno.sync.syncNow()
        assertEquals(Money(5_000), (bruno.space().budget(YearMonth.now()) as QueryResult.Data).value.committed)
    }

    @Test
    fun semConexaoAFilaFicaPreservadaEATelaExplicaACausa() = runBlocking {
        ana.pix("Padaria $tag", 12, "alimentacao.mercado")
        service.online = false
        ana.sync.syncNow()
        launch(ana)
        compose.onNodeWithText("Mais").performClick()
        waitForText("Sincronização")
        tap("Sincronização")
        waitForText("Sem conexão. A fila está preservada e a visão compartilhada pode estar incompleta.")
        waitForText("1 operação local por enviar")
        service.online = true
    }

    @Test
    fun conflitoDaTela19EscolheSuaEdicaoELeva80EmDeliveryParaOsDoisAparelhos() = runBlocking {
        val id = ana.pix("Delivery $tag", 68, "alimentacao.delivery")
        ana.sync.syncNow()
        bruno.sync.syncNow()
        val spaceId = ana.access.status.value.space!!.spaceId
        // Os dois editam offline: Bruno muda a categoria, Ana muda o valor.
        bruno.space().execute(
            Envelope(CommandId("b-cat-$tag")),
            CorrectEntry(id, category = Classification.Category(DeterministicIds.seedCategory(spaceId, "alimentacao.restaurantes"))),
        )
        ana.space().execute(Envelope(CommandId("a-val-$tag")), CorrectEntry(id, amount = Money(8_000)))
        bruno.sync.syncNow()

        service.online = false
        launch(ana)
        compose.onNodeWithText("Mais").performClick()
        waitForText("Sincronização")
        tap("Sincronização")
        waitForText("1 operação local por enviar")
        service.online = true
        tap("Sincronizar agora")
        waitForText("1 conflito de edição")
        waitForText("Operações reconhecidas. O conflito continua aguardando sua escolha.")

        tap("Revisar conflito")
        waitForText("Escolher versão")
        waitForText("Sua edição")
        waitForText("Edição da outra pessoa")
        waitForText(Money(8_000).format())
        waitForText(Money(6_800).format())
        waitForText("Escolha uma versão. Nenhuma está marcada")
        // Nada é escolhido sozinho: Aplicar só vale depois de uma escolha explícita.
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
        compose.onAllNodes(hasText("Manter esta versão") and hasClickAction()).onFirst().performClick()
        waitForText("✓ Mantendo esta versão")
        tap("Aplicar decisão")
        waitForText("Nenhuma operação por enviar neste aparelho")
        waitGone("1 conflito de edição")

        ana.sync.syncNow()
        bruno.sync.syncNow()
        assertEquals(Money(8_000), ana.amountOf(id))
        assertEquals(Money(8_000), bruno.amountOf(id))
        assertEquals(0, ana.sync.summary()!!.openConflicts)
    }

    @Test
    fun revisarDepoisMantemOConflitoAbertoEEleApareceEmRevisar() = runBlocking {
        val id = ana.pix("Contas $tag", 40, "alimentacao.mercado")
        ana.sync.syncNow()
        bruno.sync.syncNow()
        bruno.space().execute(Envelope(CommandId("b-val-$tag")), CorrectEntry(id, amount = Money(4_100)))
        ana.space().execute(Envelope(CommandId("a-val2-$tag")), CorrectEntry(id, amount = Money(4_200)))
        bruno.sync.syncNow()
        ana.sync.syncNow()
        assertEquals(1, ana.sync.summary()!!.openConflicts)

        launch(ana)
        compose.onNodeWithText("Revisar").performClick()
        waitForText("Conflitos de edição")
        tap("Revisar conflito")
        waitForText("Escolher versão")
        tap("Revisar depois")
        waitForText("Conflitos de edição")
        assertEquals(1, ana.sync.summary()!!.openConflicts)
        assertEquals(Money(4_200), ana.amountOf(id)) // versão local pendente continua visível
    }

    @Test
    fun registrarEnfileiraEnvioEmSegundoPlanoQueEsperaPorConexao() = runBlocking {
        val scheduler = SyncScheduler(context())
        scheduler.cancelAll()
        scheduler.enqueue()
        val infos = WorkManager.getInstance(context()).getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_ONE_TIME).get()
        val info = infos.single { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.SUCCEEDED }
        assertNotNull(info)
        assertEquals(androidx.work.NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        scheduler.schedulePeriodic()
        val periodic = WorkManager.getInstance(context()).getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_PERIODIC).get()
        assertTrue(periodic.isNotEmpty())
        assertEquals(androidx.work.NetworkType.CONNECTED, periodic.first().constraints.requiredNetworkType)
        scheduler.cancelAll()
    }

    @Test
    fun cicloSemEspacoAbertoNaoFazNadaENaoQuebra() = runBlocking {
        val empty = AppContainer(context(), InMemoryAccessStore(AccessSnapshot()), service)
        assertEquals(null, empty.sync.syncNow())
    }
}
