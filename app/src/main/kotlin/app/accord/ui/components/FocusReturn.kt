package app.accord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Devolve o foco a este controle quando o painel que ele abriu fecha (spec 26). [open] é verdadeiro enquanto o painel está aberto;
 * deve vir antes de `clickable`/`selectable`, que tornam o controle focalizável.
 */
@Composable
fun Modifier.returnsFocus(open: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        // shortcut: ignora controle que saiu da composição junto com o painel; o foco cai no sistema.
        if (wasOpen && !open) runCatching { requester.requestFocus() }
        wasOpen = open
    }
    return focusRequester(requester)
}
