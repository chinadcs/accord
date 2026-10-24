package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
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

/** Modo conflito da tela 19 com estado sintético: duas versões, nenhuma pré-selecionada, escolha explícita. */
class ConflictUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val last = LocalDate.of(2026, 10, 22).atTime(10, 30).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()

    // ---- modo conflito da tela 19 ----

    private fun movement(amountCents: Long, category: String) =
        """{"id":"m1","kind":"Pix","description":"Delivery","classificationId":"$category","epochDay":${LocalDate.of(2026, 10, 22).toEpochDay()},"amountCents":$amountCents,"accountId":null,"spentBy":null,"authorId":"a","origin":"Manual","voided":false}"""

    private val lookup = ConflictLookup(
        categories = listOf(
            CategoryNode("food", null, "Alimentação", 0, "utensils", null),
            CategoryNode("delivery", "food", "Delivery", 0, "bike", null),
            CategoryNode("rest", "food", "Restaurantes", 1, "chef-hat", null),
        ).associateBy { it.id },
    )

    private val view = ConflictView(
        operationId = "op-1", commandType = "CorrectEntry", deferred = false, createdMillis = 1,
        entities = listOf(
            ConflictEntity(
                EntityRef(EntityType.Movement, "m1"),
                local = ConflictVersion(movement(8_000, "delivery"), false, null, "a", 1),
                remote = ConflictVersion(movement(6_800, "rest"), false, 2, "b", last),
                differingFields = listOf("amountCents", "classificationId"),
            ),
        ),
        heldOperationIds = emptyList(),
    )

    private fun showConflict(ui: ConflictUi, log: MutableList<String> = mutableListOf()) {
        compose.setContent {
            AccordTheme {
                ConflictScreen(
                    ui, PaddingValues(), onSelect = { log += "escolher:$it" }, onApply = { log += "aplicar" },
                    onDefer = { log += "depois" }, onBack = { log += "voltar" },
                )
            }
        }
    }

    @Test
    fun duasVersoesLadoALadaSemNenhumaPreSelecionadaEAplicarDesativado() {
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup))
        compose.onNodeWithText("Escolher versão").assertIsDisplayed()
        compose.onNodeWithText("Sua edição").assertIsDisplayed()
        compose.onNodeWithText("Edição da outra pessoa").assertIsDisplayed()
        compose.onNodeWithText(Money(8_000).format()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Money(6_800).format()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alimentação > Delivery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alimentação > Restaurantes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Escolha uma versão. Nenhuma está marcada e os campos divergentes estão destacados.").performScrollTo().assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Manter esta versão").fetchSemanticsNodes().size)
        compose.onNodeWithText("Aplicar decisão").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Revisar depois").performScrollTo().assertIsEnabled()
    }

    @Test
    fun camposDivergentesTemTextoEAcessibilidadeNaoSoCor() {
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup))
        assertEquals(4, compose.onAllNodesWithText("diferente").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Valor, ${Money(8_000).format()}, diferente").assertExists()
        compose.onNodeWithContentDescription("Valor, ${Money(6_800).format()}, diferente").assertExists()
        assertEquals(2, compose.onAllNodesWithContentDescription("Descrição, Delivery").fetchSemanticsNodes().size)
    }

    @Test
    fun escolherUmaVersaoHabilitaAplicarEMarcaEmTextoSemMesclar() {
        val log = mutableListOf<String>()
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup, selected = ConflictChoice.Local), log)
        compose.onNodeWithText("✓ Mantendo esta versão").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Revisar depois").performScrollTo().performClick()
        assertEquals(listOf("aplicar", "depois"), log)
    }

    @Test
    fun tocarEmManterEscolheSemAplicar() {
        val log = mutableListOf<String>()
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup), log)
        compose.onAllNodesWithText("Manter esta versão")[1].performScrollTo().performClick()
        assertEquals(listOf("escolher:Remote"), log)
    }

    @Test
    fun versaoMudouDuranteARevisaoPedeNovaComparacao() {
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup, stale = true))
        compose.onNodeWithText(
            "A versão mudou durante a revisão: a outra pessoa editou de novo. Atualize a comparação antes de aplicar. Nada foi sobrescrito.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
    }

    @Test
    fun semConexaoAvisaQueADecisaoUsaOsDadosDesteAparelho() {
        showConflict(ConflictUi(loading = false, view = view, lookup = lookup, offline = true))
        compose.onNodeWithText("Sem conexão. Você decide com os dados deste aparelho; conflitos remotos podem pedir nova revisão ao sincronizar.").assertIsDisplayed()
    }

    @Test
    fun entidadeQueAindaNaoExisteNoServicoDizIsso() {
        val local = view.entities.single().copy(remote = null, differingFields = emptyList())
        showConflict(ConflictUi(loading = false, view = view.copy(entities = listOf(local)), lookup = lookup))
        compose.onNodeWithText("Ainda não existe no serviço.").assertIsDisplayed()
    }

    @Test
    fun pendenciaJaResolvidaMostraMensagemSemReaplicar() {
        showConflict(ConflictUi(loading = false, view = null, finished = false, message = ScreenMessage(MessageKind.Neutral, R.string.conflict_already)))
        compose.onNodeWithText("Esta pendência já foi resolvida.").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Aplicar decisão")).fetchSemanticsNodes().isEmpty())
    }
}
