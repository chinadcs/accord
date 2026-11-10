package app.accord

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.platform.testTag
import app.accord.ui.components.FlowBar
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionMinHeight
import app.accord.ui.components.SectionStatus
import app.accord.ui.components.SummaryRow
import app.accord.ui.theme.AccordTheme
import com.composables.icons.lucide.R as Lucide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Componentes compartilhados da remodelagem da Início (ADR 0078, `accord-vm79`); ainda não usados pela HomeScreen. */
class HomeComponentsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun setCard(status: androidx.compose.runtime.MutableState<SectionStatus>, onClick: (() -> Unit)?) = compose.setContent {
        AccordTheme {
            HomeSectionCard(
                title = "Margem", status = status.value, loadingDescription = "Calculando a margem…",
                failedMessage = "Não foi possível calcular a margem.", icon = Lucide.drawable.lucide_ic_info,
                onClick = onClick, onClickLabel = "Ver limites",
                modifier = Modifier.testTag("card"),
            ) { Text("R$ 126,66") }
        }
    }

    @Test fun alturaMinimaFicaEstavelEntreCarregandoFalhaEPronto() {
        val status = androidx.compose.runtime.mutableStateOf(SectionStatus.Loading)
        setCard(status, onClick = {})
        val heights = mutableListOf<Float>()
        for (s in listOf(SectionStatus.Loading, SectionStatus.Failed, SectionStatus.Ready)) {
            compose.runOnIdle { status.value = s }
            heights += compose.onNodeWithTag("card").getUnclippedBoundsInRoot().height.value
        }
        assertTrue("alturas $heights", heights.all { it >= SectionMinHeight.value })
        assertEquals("alturas $heights", 1, heights.map { Math.round(it) }.distinct().size)
    }

    @Test fun soOProntoComAcaoEhBotaoEFalhaNaoTemAlvoAninhado() {
        val status = androidx.compose.runtime.mutableStateOf(SectionStatus.Loading)
        var clicks = 0
        setCard(status, onClick = { clicks++ })
        compose.onNodeWithContentDescription("Calculando a margem…").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { status.value = SectionStatus.Failed }
        compose.onNodeWithText("Não foi possível calcular a margem.").assertIsDisplayed()
        compose.onNodeWithText("R$ 126,66").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { status.value = SectionStatus.Ready }
        compose.onNode(hasClickAction()).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        compose.onNode(hasClickAction()).performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("Margem", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
    }

    @Test fun prontoSemAcaoNaoViraBotao() {
        val status = androidx.compose.runtime.mutableStateOf(SectionStatus.Ready)
        setCard(status, onClick = null)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onNodeWithText("R$ 126,66").assertIsDisplayed()
    }

    @Test fun linhaDeResumoTemAlvoDeSessentaDpEAcaoComRotulo() {
        var clicks = 0
        compose.setContent { AccordTheme {
            Column {
                SummaryRow("3 pendências para revisar", icon = Lucide.drawable.lucide_ic_list_checks, supporting = "neste aparelho",
                    onClick = { clicks++ }, onClickLabel = "Abrir Revisar")
                SummaryRow("Só leitura", supporting = "sem ação")
            }
        } }
        val row = compose.onNode(hasClickAction())
        row.assertHeightIsAtLeast(60.dp)
        row.assertTextContains("3 pendências para revisar", substring = true)
        row.assertTextContains("neste aparelho", substring = true)
        row.performClick()
        assertEquals(1, clicks)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test fun barraExpoeSoOTextoEquivalenteEMantemAlturaFixa() {
        compose.setContent { AccordTheme {
            Column {
                FlowBar(solidCents = 234_000, scaleCents = 500_000, description = "R$ 2.340,00 de R$ 5.000,00 usados (47%)", modifier = Modifier.testTag("uso"))
                FlowBar(solidCents = 600_000, dashedCents = 200_000, scaleCents = 1_000_000,
                    description = "Entradas: R$ 6.000,00 recebidos e R$ 2.000,00 previstos", modifier = Modifier.testTag("entradas"))
            }
        } }
        compose.onNodeWithContentDescription("R$ 2.340,00 de R$ 5.000,00 usados (47%)").assertExists()
        compose.onNodeWithContentDescription("Entradas: R$ 6.000,00 recebidos e R$ 2.000,00 previstos").assertExists()
        compose.onNodeWithTag("uso").assertHeightIsEqualTo(8.dp)
        compose.onNodeWithTag("entradas").assertHeightIsEqualTo(8.dp)
    }
}

