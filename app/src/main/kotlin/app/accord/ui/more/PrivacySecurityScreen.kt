package app.accord.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.privacy.PrivacyContent
import app.accord.privacy.PrivacyViewModel
import app.accord.security.AndroidAppAuthenticator
import app.accord.security.AppLockStore
import app.accord.security.SecurityContent
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing

/**
 * Privacidade e segurança (ADR 0077): uma tela com duas seções, Segurança (bloqueio local) e Privacidade e dados. Só dentro do
 * grafo de navegação, com conta e espaço; os gates sem espaço seguem com `PrivacyScreen`, sem a seção Segurança.
 * Uma única rolagem na coluna: os conteúdos das seções não rolam por conta própria.
 */
@Composable
fun PrivacySecurityScreen(
    padding: PaddingValues,
    lock: AppLockStore,
    authenticator: AndroidAppAuthenticator,
    privacy: PrivacyViewModel,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.privacy_security_title), onBack)
        SectionTitle(R.string.security_title)
        SecurityContent(lock, authenticator)
        HorizontalDivider()
        SectionTitle(R.string.privacy_title)
        PrivacyContent(privacy)
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}
