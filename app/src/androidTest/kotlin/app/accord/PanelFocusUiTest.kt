package app.accord

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.accord.ui.components.PickerField
import app.accord.ui.register.AccountDialog
import app.accord.ui.theme.AccordTheme
import org.junit.Rule
import org.junit.Test

/** Spec 26: ao fechar o painel de escolha, o foco volta ao campo que o abriu. */
@OptIn(ExperimentalFoundationApi::class)
class PanelFocusUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fecharOPainelDeContaDevolveOFocoAoCampo() {
        compose.setContent {
            AccordTheme {
                // Em modo toque o clicável não recebe foco do Compose; o foco por teclado/interruptor é o que o painel devolve.
                LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
                var open by remember { mutableStateOf(false) }
                Column {
                    PickerField(label = "Conta", value = "Nubank", error = null, bring = remember { BringIntoViewRequester() }, open = open) { open = true }
                    if (open) AccountDialog(emptyList(), null, onPick = {}, onDismiss = { open = false })
                }
            }
        }
        compose.onNodeWithContentDescription("Conta. Nubank").performClick()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Conta. Nubank").assertIsFocused()
    }
}
