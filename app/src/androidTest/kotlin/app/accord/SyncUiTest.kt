package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.space.CategoryNode
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.sync.ConflictChoice
import app.accord.core.sync.ConflictEntity
import app.accord.core.sync.ConflictVersion
import app.accord.core.sync.ConflictView
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.ProblemKind
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.ServiceState
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncStage
import app.accord.core.sync.SyncStatus
import app.accord.core.sync.SyncSummary
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.sync.AttentionRow
import app.accord.ui.sync.ConflictLookup
import app.accord.ui.sync.ConflictRowUi
import app.accord.ui.sync.ConflictScreen
import app.accord.ui.sync.ConflictUi
import app.accord.ui.sync.SyncScreen
import app.accord.ui.sync.SyncUi
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Tela 26 com estado sintético: honestidade sobre a fila, a causa de cada parada e as ações de recuperação. */
class SyncUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val last = LocalDate.of(2026, 10, 22).atTime(10, 30).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()
    private val now = last + 60_000

    private fun status(
        awaiting: Int = 0, conflicts: Int = 0, rejected: Int = 0, lastSync: Long? = last,
        stage: SyncStage = SyncStage.Idle, connection: ConnectionState = ConnectionState.Online,
        session: SessionState = SessionState.Valid, service: ServiceState = ServiceState.Responding,
        update: Boolean = false, failure: RemoteFailure? = null,
    ) = SyncStatus(SyncSummary(awaiting, conflicts, rejected, lastSync, 7), stage, connection, session, service, update, failure)

    private fun showSync(status: SyncStatus?, ui: SyncUi = SyncUi(), log: MutableList<String> = mutableListOf()) {
        compose.setContent {
            AccordTheme {
                SyncScreen(
                    status, ui, PaddingValues(), now,
                    onBack = { log += "voltar" }, onSyncNow = { log += "sincronizar" }, onOpenEntry = { log += "abrir:$it" },
                    onOpenConflict = { log += "conflito:$it" }, onRenew = { log += "renovar" }, onRetry = { log += "tentar:$it" },
                    onAskDiscard = { log += "descartar?:$it" }, onDiscard = { log += "descartar" }, onCancelDiscard = { log += "manter" },
                )
            }
        }
    }

    @Test
    fun mostraUltimaSincronizacaoFilaEAvisoDeVisaoDesatualizada() {
        showSync(status(awaiting = 3))
        compose.onNodeWithText("Sincronização").assertIsDisplayed()
        compose.onNodeWithText("Última sincronização concluída: 22/10, 10h30").assertIsDisplayed()
        compose.onNodeWithText("3 operações locais por enviar").assertIsDisplayed()
        compose.onNodeWithText("A contagem é de operações, não de despesas: uma correção também conta.").assertIsDisplayed()
        compose.onNodeWithText("A visão do espaço pode estar desatualizada. O que está salvo aqui ainda não foi enviado.").assertIsDisplayed()
        compose.onNodeWithText("Sincronizar agora").assertIsEnabled()
    }

    @Test
    fun filaVaziaNuncaDizTudoSincronizadoENaoFalaDoOutroAparelho() {
        showSync(status())
        compose.onNodeWithText("Nenhuma operação por enviar neste aparelho").assertIsDisplayed()
        compose.onNodeWithText("Nada pendente. O outro aparelho pode ter registros que ainda não chegaram.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Tudo sincronizado", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("A visão do espaço pode estar desatualizada", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun semNenhumaSincronizacaoConcluidaAvisaQueAVisaoPodeEstarDesatualizada() {
        showSync(status(lastSync = null))
        compose.onNodeWithText("Nenhuma sincronização foi concluída neste aparelho.").assertIsDisplayed()
        compose.onNodeWithText("A visão do espaço pode estar desatualizada.").assertIsDisplayed()
    }

    @Test
    fun umaSemanaDepoisAvisaQueEsteAparelhoNaoAfirmaEstarEmDia() {
        showSync(status(lastSync = now - 8L * 24 * 60 * 60 * 1000))
        compose.onNodeWithText("Passou uma semana desde o último sucesso.", substring = true).assertIsDisplayed()
    }

    @Test
    fun semConexaoPreservaAFilaEPermiteTentarDeNovo() {
        showSync(status(awaiting = 2, connection = ConnectionState.Offline, failure = RemoteFailure.NoNetwork))
        compose.onNodeWithText("Sem conexão. A fila está preservada e a visão compartilhada pode estar incompleta.").assertIsDisplayed()
        compose.onNodeWithText("A última tentativa ficou sem conexão.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sincronizar agora").assertIsEnabled()
        compose.onNodeWithText("2 operações locais por enviar").assertIsDisplayed()
    }

    @Test
    fun sessaoExpiradaPreservaAFilaEOferecereEntrarDeNovo() {
        val log = mutableListOf<String>()
        showSync(status(awaiting = 1, session = SessionState.NeedsRenewal), log = log)
        compose.onNodeWithText("Sua sessão expirou.").assertIsDisplayed()
        compose.onNodeWithText("Entre de novo para continuar enviando. A fila local e os registros foram preservados.").assertIsDisplayed()
        compose.onNodeWithText("Sincronizar agora").assertIsNotEnabled()
        compose.onNodeWithText("Entrar de novo").performClick()
        assertEquals(listOf("renovar"), log)
    }

    @Test
    fun servicoPausadoIndisponivelEQuotaTemCausasEMensagensProprias() {
        showSync(status(awaiting = 1, service = ServiceState.Paused, failure = RemoteFailure.ServicePaused))
        compose.onNodeWithText("O serviço do espaço está pausado.").assertIsDisplayed()
        compose.onNodeWithText("Não há troca automática para plano pago", substring = true).assertIsDisplayed()
    }

    @Test
    fun servicoIndisponivelSeparaEstadoDaRede() {
        showSync(status(awaiting = 1, service = ServiceState.Unavailable, failure = RemoteFailure.ServiceUnavailable))
        compose.onNodeWithText("O serviço compartilhado está indisponível.").assertIsDisplayed()
        compose.onNodeWithText("Rede e sessão estão normais.", substring = true).assertIsDisplayed()
    }

    @Test
    fun quotaEsgotadaNaoMigraNemCobraSozinha() {
        showSync(status(service = ServiceState.QuotaExceeded, failure = RemoteFailure.QuotaExceeded))
        compose.onNodeWithText("O limite do plano gratuito foi atingido.").assertIsDisplayed()
        compose.onNodeWithText("Não migramos para plano pago nem ativamos cobrança sozinhos.", substring = true).assertIsDisplayed()
    }

    @Test
    fun protocoloNaoSuportadoPedeAtualizacaoComAFilaPreservada() {
        showSync(status(awaiting = 1, rejected = 1, update = true))
        compose.onNodeWithText("Atualize o aplicativo.").assertIsDisplayed()
        compose.onNodeWithText("Sincronizar agora").assertIsNotEnabled()
    }

    @Test
    fun enviandoMostraAEtapaESegueDisponivelParaRegistrar() {
        showSync(status(awaiting = 2, stage = SyncStage.Sending))
        compose.onNodeWithText("Enviando as operações locais…").assertIsDisplayed()
        compose.onNodeWithText("Registro e consulta continuam disponíveis enquanto sincroniza.").assertIsDisplayed()
        compose.onNodeWithText("Sincronizar agora").assertIsNotEnabled()
    }

    @Test
    fun conflitoTemRevisaoEExplicaQueAVisaoNaoEstaAtualizada() {
        val log = mutableListOf<String>()
        showSync(
            status(conflicts = 1),
            SyncUi(conflicts = listOf(ConflictRowUi("op-1", R.string.op_correction, "Delivery", deferred = false))),
            log,
        )
        compose.onNodeWithText("1 conflito de edição").assertIsDisplayed()
        compose.onNodeWithText("Correção · Delivery").performScrollTo().performClick()
        assertEquals(listOf("conflito:op-1"), log)
        compose.onNodeWithText(
            "Os registros locais continuam disponíveis. Enquanto o conflito não for resolvido, a visão do espaço não está plenamente atualizada.",
        ).assertExists()
    }

    @Test
    fun operacaoRecusadaContinuaGuardadaComCausaERetentaOuDescarteExplicito() {
        val log = mutableListOf<String>()
        showSync(
            status(rejected = 1),
            SyncUi(attention = listOf(AttentionRow("op-9", ProblemKind.Rejected, R.string.op_new_expense, "Mercado", "ProtocolUnsupported", "m9"))),
            log,
        )
        compose.onNodeWithText("Nova despesa · Mercado").assertIsDisplayed()
        compose.onNodeWithText("Recusada pelo serviço: versão do protocolo não suportada. Continua guardada neste aparelho.").assertIsDisplayed()
        compose.onNodeWithText("Abrir o lançamento").performScrollTo().performClick()
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        compose.onNodeWithText("Descartar").performScrollTo().performClick()
        assertEquals(listOf("abrir:m9", "tentar:op-9", "descartar?:op-9"), log)
    }

    @Test
    fun descartarPedeConfirmacaoEExplicaOQueAcontece() {
        val log = mutableListOf<String>()
        showSync(status(rejected = 1), SyncUi(confirmDiscard = "op-9"), log)
        compose.onNodeWithText("Descartar esta operação?").assertIsDisplayed()
        compose.onNodeWithText("A operação deixa de ser enviada.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Manter").performClick()
        assertEquals(listOf("manter"), log)
    }
}
