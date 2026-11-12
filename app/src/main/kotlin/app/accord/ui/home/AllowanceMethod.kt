package app.accord.ui.home

import app.accord.ui.components.accordDialogColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.SpendingAllowance
import app.accord.ui.theme.AccordSpacing

/**
 * Como a margem para gastar é calculada (ADR 0068/0078): o mesmo texto na Início ("Como é calculado") e, sem números,
 * no Orçamento. Com [value] mostra também a cobertura real (categorias incluídas e fora do cálculo); sem ele, só a regra.
 * Só explica; não lê nem altera dados.
 */
@Composable
fun AllowanceMethodText(value: SpendingAllowance?, modifier: Modifier = Modifier) {
    val body = MaterialTheme.typography.bodyMedium
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Text(stringResource(R.string.allowance_method), style = body)
        Text(stringResource(R.string.allowance_method_coverage_rule), style = body)
        value?.let {
            Text(stringResource(R.string.allowance_coverage, it.limitedCategories, it.excludedCategories, it.unclassifiedCount), style = body)
        }
        Text(stringResource(R.string.allowance_method_unclassified), style = body)
        Text(stringResource(R.string.allowance_method_provisional), style = body)
        Text(stringResource(R.string.allowance_local), style = body)
    }
}

/** "Como é calculado" aberto pela Início, sem rota nova; rola em fonte ampliada e fecha pelo botão ou fora do diálogo. */
@Composable
fun AllowanceMethodDialog(value: SpendingAllowance, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allowance_method_title)) },
        text = { AllowanceMethodText(value, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.allowance_method_close)) } },
    )
}
