package app.accord

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.accord.ui.category.CategoryContent
import app.accord.ui.category.CategoryScreen
import app.accord.ui.forecast.ForecastsActions
import app.accord.ui.forecast.ForecastsContent
import app.accord.ui.forecast.ForecastsScreen
import app.accord.ui.forecast.ForecastsUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/**
 * O espaço reservado à barra inferior (ou à barra de navegação do sistema) tem de recortar a lista, não só entrar no
 * `contentPadding`: este só vale no fim da rolagem, e um item levado à tela por rolagem de acessibilidade, foco ou teclado
 * ficaria sob a barra (mesmo defeito corrigido no Orçamento, accord-tisd). Telas de detalhe: Previsões e Categoria.
 */
class DetailBottomInsetUiTest {
    @get:Rule val compose = createComposeRule()

    private val reserved = 100.dp

    private fun assertListEndsAboveReservedSpace(screen: @Composable () -> Unit) {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AccordTheme { Box(Modifier.fillMaxSize().testTag("host")) { screen() } }
        }
        compose.waitForIdle()
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val limit = host.bottom - reserved.value * density
        assertTrue("A lista vai até ${list.bottom}px, mas o espaço reservado começa em ${limit}px", list.bottom <= limit + 1f)
    }

    @Test fun previsoesRecortaAListaNoEspacoDaBarra() = assertListEndsAboveReservedSpace {
        ForecastsScreen(
            ForecastsUiState(YearMonth.of(2026, 10), ForecastsContent.Loading), PaddingValues(bottom = reserved),
            ForecastsActions({}, {}, {}, {}, {}),
        )
    }

    @Test fun categoriaRecortaAListaNoEspacoDaBarra() = assertListEndsAboveReservedSpace {
        CategoryScreen(CategoryContent.Loading, YearMonth.of(2026, 10), PaddingValues(bottom = reserved), {}, {}, { _, _ -> })
    }
}
