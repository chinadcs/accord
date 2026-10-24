package app.accord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.theme.AccordSpacing

/** Esqueleto de uma tela raiz: margem de 16 dp, título como cabeçalho para o TalkBack e rolagem. */
@Composable
fun RootScaffold(title: String, padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Conteúdo provisório das raízes que S1 não implementa, sempre com acesso ao registro manual. */
@Composable
fun ProvisionalRoot(title: String, message: String, padding: PaddingValues, onRegister: () -> Unit) {
    RootScaffold(title, padding) {
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRegister, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }
    }
}
