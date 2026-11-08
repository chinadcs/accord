package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.accord.core.sync.AccessState
import app.accord.core.sync.EntryStage
import app.accord.core.sync.InviteView
import app.accord.core.sync.Participant
import app.accord.core.sync.ParticipantsView
import app.accord.core.sync.SpaceView
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.ui.access.AccessPanel
import app.accord.ui.access.AccessScreen
import app.accord.ui.access.AccessUi
import app.accord.ui.access.CreateForm
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.firststeps.FirstStepsScreen
import app.accord.ui.firststeps.StepActions
import app.accord.ui.firststeps.StepRow
import app.accord.ui.firststeps.StepText
import app.accord.ui.firststeps.StepsContent
import app.accord.ui.space.SpaceScreen
import app.accord.ui.space.SpaceTab
import app.accord.ui.space.SpaceUi
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Telas 01, 02 e 03 com estado sintético: estados, mensagens e semântica para o TalkBack. */
class AccessUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showAccess(initial: AccessUi = AccessUi(), log: MutableList<String> = mutableListOf()): MutableList<String> {
        compose.setContent {
            var ui by remember { mutableStateOf(initial) }
            AccordTheme {
                AccessScreen(
                    ui, PaddingValues(),
                    onEmail = { ui = ui.copy(email = it) }, onPassword = { ui = ui.copy(password = it) },
                    onToggleShow = { ui = ui.copy(showPassword = !ui.showPassword) },
                    onSignIn = { log += "entrar:${ui.email}" }, onCancel = { log += "cancelar" },
                    onOpenCreate = { ui = ui.copy(panel = AccessPanel.Create, create = CreateForm()) },
                    onOpenHelp = { ui = ui.copy(panel = AccessPanel.Help) },
                    onClosePanel = { ui = ui.copy(panel = AccessPanel.None) },
                    onCreateEmail = { ui = ui.copy(create = ui.create.copy(email = it)) },
                    onCreatePassword = { ui = ui.copy(create = ui.create.copy(password = it)) },
                    onCreateConfirmation = { ui = ui.copy(create = ui.create.copy(confirmation = it)) },
                    onToggleShowPasswords = { ui = ui.copy(create = ui.create.copy(showPasswords = !ui.create.showPasswords)) },
                    onSignUp = { log += "criar" },
                )
            }
        }
        return log
    }

    @Test
    fun loginTemUmaAcaoPrincipalEOsDoisCaminhosSecundarios() {
        showAccess()
        compose.onNodeWithText("Controle financeiro do casal").assertIsDisplayed()
        compose.onNodeWithText("E-mail").assertIsDisplayed()
        compose.onNodeWithText("Senha").assertIsDisplayed()
        compose.onNodeWithText("Entrar").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Criar acesso").assertIsDisplayed()
        compose.onNodeWithText("Ajuda para acessar").assertIsDisplayed()
    }

    @Test
    fun senhaComecaOcultaMostrarOcultarPreservaOQueFoiDigitadoEAnunciaOEstado() {
        showAccess()
        compose.onNodeWithText("Senha").performTextInput("segredo-1")
        compose.onNodeWithContentDescription("Mostrar senha").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Senha oculta")).assertExists()
        compose.onNodeWithContentDescription("Mostrar senha").performClick()
        compose.onNodeWithContentDescription("Ocultar senha").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Senha visível")).assertExists()
        compose.onNodeWithText("segredo-1").assertIsDisplayed()
    }

    @Test
    fun entrarBloqueadoEnquantoAutenticaComCancelarDisponivel() {
        showAccess(AccessUi(email = "ana@x.test", working = true))
        compose.onNodeWithText("Entrando…").assertIsDisplayed()
        compose.onNodeWithText("Entrando…").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun errosDeCampoFicamJuntoDoCampoECredencialRecusadaNaoDizQualEstaErrado() {
        showAccess(AccessUi(emailError = R.string.access_error_email_format, passwordError = R.string.access_error_password_required))
        compose.onNodeWithText("Confira o e-mail: ele precisa ter o formato nome@exemplo.com.").assertIsDisplayed()
        compose.onNodeWithText("Informe a senha.").assertIsDisplayed()
    }

    @Test
    fun mensagensDeRedeECredencialSaoDistintasEPreservamOEmail() {
        showAccess(AccessUi(email = "ana@x.test", message = ScreenMessage(MessageKind.Error, R.string.access_msg_credentials)))
        compose.onNodeWithText("E-mail ou senha incorretos.").assertIsDisplayed()
        compose.onNodeWithText("ana@x.test").assertIsDisplayed()
    }

    @Test
    fun semConexaoExplicaQueAEntradaNaoFoiConcluidaENadaFoiApagado() {
        showAccess(AccessUi(email = "ana@x.test", message = ScreenMessage(MessageKind.Attention, R.string.access_msg_network)))
        compose.onNodeWithText(
            "Sem conexão. A entrada não foi concluída e seu e-mail foi mantido. Nada foi apagado deste aparelho.",
        ).assertIsDisplayed()
    }

    @Test
    fun ajudaExplicaARecuperacaoAssistidaSemPedirEmail() {
        showAccess()
        compose.onNodeWithText("Ajuda para acessar").performClick()
        compose.onNodeWithText("No piloto, a recuperação de senha é assistida pelo responsável, fora do aplicativo.").assertIsDisplayed()
        compose.onNodeWithText("Nenhum e-mail ou link é enviado. Não compartilhe sua senha no Chat.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("E-mail").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Entendi").performClick()
        compose.onNodeWithText("Entrar").assertIsDisplayed()
    }

    @Test
    fun criarAcessoAbreOPainelComConfirmacaoESemPromessaDeEmail() {
        showAccess()
        compose.onNodeWithText("Criar acesso").performClick()
        compose.onNodeWithText("Um acesso para entrar no espaço da casa. Nenhum e-mail de confirmação é enviado.").assertIsDisplayed()
        compose.onNodeWithText("Confirmar senha").assertIsDisplayed()
    }

    @Test
    fun renovacaoExplicaQueOQueFoiSalvoContinuaGuardado() {
        showAccess(AccessUi(renewal = true, email = "ana@x.test"))
        compose.onNodeWithText("Entre novamente para continuar").assertIsDisplayed()
        compose.onNodeWithText("Entre novamente para continuar. O que foi salvo neste aparelho continua guardado.").assertIsDisplayed()
    }

    // ---- tela 02 ----

    private fun showSpace(ui: SpaceUi = SpaceUi(), access: AccessState = AccessState(stage = EntryStage.NoSpace), firstRun: Boolean = true, log: MutableList<String> = mutableListOf()) {
        compose.setContent {
            AccordTheme {
                SpaceScreen(
                    ui, access, PaddingValues(), firstRun,
                    onTab = { log += "aba:$it" }, onName = {}, onCode = {}, onCreate = { log += "criar" }, onJoin = { log += "entrar" },
                    onGenerate = { log += "gerar" }, onRefresh = { log += "verificar" }, onCopy = { log += "copiar:$it" },
                    onContinue = { log += "continuar" }, onBack = null,
                )
            }
        }
    }

    private val withSpace = AccessState(
        stage = EntryStage.FirstSteps, space = SpaceView("e1", "Nosso orçamento"),
        participants = ParticipantsView(listOf(Participant("a", "ana", 1)), 1_700_000_000_000, "a"),
    )

    @Test
    fun semEspacoOferecerCriarOuEntrarComUmaAcaoPorModo() {
        showSpace()
        compose.onNodeWithText("Espaço da casa").assertIsDisplayed()
        // "Criar espaço" é a aba e também a única ação principal do modo.
        assertEquals(2, compose.onAllNodesWithText("Criar espaço").fetchSemanticsNodes().size)
        compose.onNodeWithText("Entrar com código").assertIsDisplayed()
        compose.onNodeWithText("Nome do espaço").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Entrar no espaço").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun modoEntrarExplicaQueNinguemCompartilhaSenha() {
        showSpace(SpaceUi(tab = SpaceTab.Join))
        compose.onNodeWithText("Peça o QR ou o código a quem está no espaço. Você entra com a sua própria conta; ninguém precisa compartilhar senha.").assertIsDisplayed()
        compose.onNodeWithText("Entrar no espaço").assertIsDisplayed()
    }

    @Test
    fun codigoInvalidoEExplicadoSemCriarEspacoECodigoDigitadoFicaVisivel() {
        showSpace(
            SpaceUi(tab = SpaceTab.Join, code = "ZZZZZZZZ", message = ScreenMessage(MessageKind.Error, R.string.space_msg_invalid_invite)),
        )
        compose.onNodeWithText("Esse código é inválido, expirou ou foi invalidado. Peça um novo a quem criou o espaço. Nenhum espaço foi criado.").assertIsDisplayed()
        compose.onNodeWithText("ZZZZZZZZ").assertIsDisplayed()
    }

    @Test
    fun criacaoPendenteMostraQueOMesmoEspacoSeraRecuperado() {
        showSpace(access = AccessState(stage = EntryStage.NoSpace, pendingSpaceName = "Casa"))
        compose.onNodeWithText("Pedido de criação de \"Casa\" ainda sem resposta. Ao tentar de novo, o mesmo espaço é recuperado.").assertIsDisplayed()
    }

    @Test
    fun espacoCriadoMostraParticipantesComUltimaVerificacaoSemGarantirAceite() {
        showSpace(access = withSpace)
        compose.onNodeWithText("Nosso orçamento").assertIsDisplayed()
        compose.onNodeWithText("Você: conectado · 1 de 4 pessoas no espaço").assertIsDisplayed()
        compose.onNodeWithText("Restam 3 vagas.").assertIsDisplayed()
        compose.onNodeWithText("Última verificação:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Gerar código de convite").assertIsDisplayed()
        compose.onNodeWithText("Convidar depois e continuar", substring = true).assertDoesNotExist()
    }

    @Test
    fun codigoGeradoApareceAgrupadoComValidadeCopiarELidoCaractereACaractere() {
        val log = mutableListOf<String>()
        showSpace(
            access = withSpace.copy(invite = InviteView(validUntilMillis = 1_800_000_000_000, expired = false, code = "ABCDEFGH")),
            log = log,
        )
        compose.onNodeWithText("ABCD EFGH").assertIsDisplayed()
        compose.onNodeWithTag("invite-qr").assertExists()
        compose.onNodeWithTag("invite-revoke").assertExists()
        compose.onNodeWithText("O código aparece só agora. Se perder, gere outro: o anterior deixa de valer.").assertIsDisplayed()
        compose.onNodeWithText("Válido até", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Código de convite: A, B, C, D, E, F, G, H").assertExists()
        compose.onNodeWithText("Copiar código").performScrollTo().performClick()
        assertEquals(listOf("copiar:ABCDEFGH"), log)
        compose.onNodeWithText("Gerar outro código").assertExists()
    }

    @Test
    fun conviteExpiradoPedeOutroCodigo() {
        showSpace(access = withSpace.copy(invite = InviteView(1_700_000_000_000, expired = true)))
        compose.onNodeWithText("O código expirou. Gere outro.").assertIsDisplayed()
    }

    private fun people(n: Int) = withSpace.copy(
        participants = ParticipantsView(List(n) { Participant("p$it", "pessoa$it", it.toLong()) }, 1_700_000_000_000, "p0"),
    )

    @Test
    fun comTresPessoasAindaRestaUmaVagaEOConviteContinuaOferecido() {
        showSpace(access = people(3))
        compose.onNodeWithText("Você: conectado · 3 de 4 pessoas no espaço").assertIsDisplayed()
        compose.onNodeWithText("Resta 1 vaga.").assertIsDisplayed()
        compose.onNodeWithText("Gerar código de convite").assertIsDisplayed()
    }

    @Test
    fun comQuatroPessoasNaoHaMaisConviteENenhumaAcaoDeGerar() {
        showSpace(access = people(4))
        compose.onNodeWithText("Você: conectado · 4 de 4 pessoas no espaço").assertIsDisplayed()
        compose.onNodeWithText("O espaço já está com todas as vagas ocupadas; não há mais convite.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Gerar código de convite").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun invalidarCodigoEOfereceSoComConviteAtivoENaoVencido() {
        showSpace(access = withSpace.copy(invite = InviteView(1_800_000_000_000, expired = false)))
        compose.onNodeWithTag("invite-revoke").assertIsDisplayed()
    }

    @Test
    fun entrarComCodigoOfereceLerQrECodigoDigitadoContinuaDisponivel() {
        showSpace(SpaceUi(tab = SpaceTab.Join), access = AccessState(stage = EntryStage.NoSpace))
        compose.onNodeWithTag("space-scan-qr").assertIsDisplayed()
        compose.onNodeWithText("Ler QR Code").assertIsDisplayed()
        compose.onNodeWithText("Código de convite", substring = true).assertExists()
    }

    @Test
    fun continuarSoApareceNoFluxoDeEntradaENaoQuandoAbertoPeloMais() {
        showSpace(access = withSpace, firstRun = false)
        compose.onNodeWithText("Continuar para primeiros passos").assertDoesNotExist()
    }

    // ---- tela 03 ----

    @Test
    fun convidadoVeVoceEntrouEmEOQueJaExisteEOsPassosSaoSoOsDele() {
        compose.setContent {
            AccordTheme {
                FirstStepsScreen(
                    StepsContent.Ready(
                        rows = listOf(rows[1].copy(id = StepId.AccountsAndMyDefault, title = R.string.steps_accounts, detail = StepText(R.string.steps_accounts_pending), action = R.string.steps_accounts_action)),
                        guest = StepsContent.Guest("Casa Silva", 12, cardConfigured = true, limitsTotal = null),
                    ),
                    PaddingValues(),
                    StepActions({}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        compose.onNodeWithText("Você entrou em Casa Silva").assertIsDisplayed()
        compose.onNodeWithText("Categorias: 12 grupos já definidos.").assertIsDisplayed()
        compose.onNodeWithText("Cartão: já configurado.").assertIsDisplayed()
        compose.onNodeWithText("Limites: ainda não definidos.").assertIsDisplayed()
        compose.onNodeWithText("Vamos começar").assertIsDisplayed()
        compose.onNodeWithText("Cartão padrão", substring = true).assertDoesNotExist()
    }

    private fun showSteps(rows: List<StepRow>, log: MutableList<String> = mutableListOf()) {
        compose.setContent {
            AccordTheme {
                FirstStepsScreen(
                    StepsContent.Ready(rows), PaddingValues(),
                    StepActions(
                        onCategories = { log += "categorias" }, onCard = { log += "cartao" }, onAccounts = { log += "contas" },
                        onLimits = { log += "limites" }, onSpace = { log += "espaco" }, onStart = { log += "comecar" },
                        onRegisterManually = { log += "manual" },
                    ),
                )
            }
        }
    }

    private val rows = listOf(
        StepRow(StepId.InitialCategories, StepStatus.Done, R.string.steps_categories, StepText(R.string.steps_categories_done, listOf(10)), R.string.steps_categories_action),
        StepRow(StepId.DefaultCard, StepStatus.Pending, R.string.steps_card, StepText(R.string.steps_card_pending), R.string.steps_card_action_pending),
        StepRow(StepId.DeviceModel, StepStatus.Unavailable, R.string.steps_model, StepText(R.string.steps_model_unavailable), null),
    )

    @Test
    fun primeirosPassosMostramConcluidoPendenteEIndisponivelEmTextoSemPontuacao() {
        showSteps(rows)
        compose.onNodeWithText("Vamos começar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Categorias iniciais, Concluído. 10 categorias, editáveis.").assertExists()
        compose.onNodeWithContentDescription("Cartão padrão, Pendente. Sem ele, cada registro pede o pagamento.").assertExists()
        compose.onNodeWithContentDescription("Modelo no aparelho, Indisponível. Chega em uma etapa futura. Sem ele, use o registro manual.").assertExists()
    }

    @Test
    fun comecarARegistrarEoRegistroManualEstaoSempreDisponiveis() {
        val log = mutableListOf<String>()
        showSteps(rows, log)
        compose.onNodeWithText("Começar a registrar").performScrollTo().performClick()
        compose.onNodeWithText("Registrar manualmente").performScrollTo().performClick()
        compose.onNodeWithText("Configurar").performScrollTo().performClick()
        assertEquals(listOf("comecar", "manual", "cartao"), log)
    }
}
