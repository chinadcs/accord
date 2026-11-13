package app.accord.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.register.FormError
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.LocalDate

/** Campos de formulário compartilhados (spec 20): valor em destaque, seletor, escolhas de data e o texto do erro. Moveram-se de `RegisterScreen.kt` sem mudar o comportamento. */

@Composable
internal fun errorText(error: FormError?): String? = when (error) {
    null -> null
    FormError.Required -> stringResource(R.string.error_required)
    FormError.AmountEmpty -> stringResource(R.string.error_amount_empty)
    FormError.AmountNegative -> stringResource(R.string.error_amount_negative)
    FormError.AmountAmbiguous -> stringResource(R.string.error_amount_ambiguous)
    FormError.AmountInvalid -> stringResource(R.string.error_amount_invalid)
    FormError.AmountTooLarge -> stringResource(R.string.error_amount_too_large)
    FormError.MustBePositive -> stringResource(R.string.error_must_be_positive)
    FormError.InstallmentsInvalid -> stringResource(R.string.error_installments_invalid)
    FormError.MeaningRequired -> stringResource(R.string.error_meaning_required)
    FormError.AccountRequired -> stringResource(R.string.error_account_required)
    FormError.AccountNotAllowed -> stringResource(R.string.error_account_not_allowed)
    FormError.CategoryRequired -> stringResource(R.string.error_category_required)
    FormError.ScopeRequired -> stringResource(R.string.error_scope_required)
    is FormError.FromFacade -> error.message
}

/** Campo de texto com o erro junto dele; o erro também chega ao TalkBack como parte do campo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: FormError?,
    focus: FocusRequester,
    bring: BringIntoViewRequester,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    emphasized: Boolean = false,
    leadingIcon: Int? = null,
    modifier: Modifier = Modifier,
) {
    val message = errorText(error)
    OutlinedTextField(
        value = value,
        // shortcut: teclado numérico de alguns aparelhos só tem ".", sem ","; o ponto recém-digitado vira vírgula
        // (milhar digitado à mão não é aceito, só colado), rever se o teclado passar a oferecer a vírgula.
        onValueChange = { v ->
            onChange(if (keyboard == KeyboardType.Decimal && v.endsWith(".") && ',' !in v && '.' !in v.dropLast(1)) v.dropLast(1) + "," else v)
        },
        label = { Text(label, style = MaterialTheme.typography.titleMedium) },
        placeholder = hint?.let { { Text(it) } },
        isError = message != null,
        supportingText = message?.let { { Text(it) } },
        singleLine = true,
        leadingIcon = if (leadingIcon != null) ({ DecorativeIcon(leadingIcon) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = if (emphasized) {
            Accord.typography.amountInput.copy(textAlign = TextAlign.End)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        shape = if (emphasized) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(bring).focusRequester(focus),
    )
}

/** Campo que abre um seletor; a linha inteira é o alvo de toque e anuncia rótulo e valor. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickerField(
    label: String,
    value: String,
    error: FormError?,
    bring: BringIntoViewRequester,
    leadingContent: (@Composable () -> Unit)? = null,
    /** Verdadeiro enquanto o seletor aberto por este campo está na tela: ao fechar, o foco volta aqui. */
    open: Boolean = false,
    onClick: () -> Unit,
) {
    val message = errorText(error)
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AccordSpacing.control)
                .returnsFocus(open)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value, message).joinToString(". ") },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                leadingContent?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
                DecorativeIcon(Lucide.drawable.lucide_ic_chevron_down)
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = AccordSpacing.s4, top = AccordSpacing.s1)) }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun DateChoices(
    label: String,
    date: LocalDate,
    today: LocalDate,
    error: FormError?,
    bring: BringIntoViewRequester,
    onPickOther: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val yesterday = today.minusDays(1)
    val other = date != today && date != yesterday
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(bring),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            FilterChip(
                selected = date == today,
                onClick = { onSelectDate(today) },
                label = { Text(stringResource(R.string.register_date_today), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_sun, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
            FilterChip(
                selected = date == yesterday,
                onClick = { onSelectDate(yesterday) },
                label = { Text(stringResource(R.string.register_date_yesterday), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_history, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
            FilterChip(
                selected = other,
                onClick = onPickOther,
                label = {
                    Text(
                        if (other) stringResource(R.string.register_date_other_value, date.shortLabel())
                        else stringResource(R.string.register_date_other),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_calendar_days, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
        }
        errorText(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
