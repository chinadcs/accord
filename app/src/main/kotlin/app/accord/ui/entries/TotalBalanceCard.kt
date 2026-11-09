package app.accord.ui.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.core.Money
import app.accord.core.space.TotalBalance
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

private const val ABSENT = "—"
private const val MIN_TEXT_CONTRAST = 4.5f

/**
 * Saldo total acumulado até o fim do mês (renda recebida menos despesa comprometida), com Receita e Despesa só desse
 * mês. Renda prevista e quitação de fatura não entram. Mora no Início (`accord-wecg`, ADR 0071); antes ficava em Lançamentos.
 */
@Composable
fun TotalBalanceCard(
    balance: TotalBalance?,
    modifier: Modifier = Modifier,
) {
    val panel = Accord.colors.financeSurface
    val onPanel = Accord.colors.onFinanceSurface
    val incomeColor = readableOn(
        panel, onPanel, listOf(Accord.colors.success, Accord.colors.successContainer, Accord.colors.onSuccessContainer),
    )
    val expenseColor = readableOn(
        panel, onPanel,
        listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
    )
    val title = stringResource(R.string.entries_balance_title)
    val incomeLabel = stringResource(R.string.entries_balance_income)
    val expenseLabel = stringResource(R.string.entries_balance_expense)
    val totalBalance = balance?.total?.balance
    val income = balance?.month?.receivedIncomes
    val expense = balance?.month?.committedExpenses
    val stacked = LocalDensity.current.fontScale >= 1.3f

    Surface(modifier = modifier.fillMaxWidth(), color = panel, contentColor = onPanel, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s5),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = onPanel.copy(alpha = 0.8f))
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
            ) {
                Text(totalBalance.formatOrAbsent(), style = Accord.typography.amountHero)
            }
            HorizontalDivider(color = onPanel.copy(alpha = 0.14f))
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    TotalLine(incomeLabel, income, incomeColor)
                    TotalLine(expenseLabel, expense, expenseColor)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    TotalColumn(incomeLabel, income, incomeColor, Modifier.weight(1f))
                    TotalColumn(expenseLabel, expense, expenseColor, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TotalColumn(label: String, amount: Money?, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(amount.formatOrAbsent(), style = Accord.typography.amountRow, color = color)
    }
}

@Composable
private fun TotalLine(label: String, amount: Money?, color: Color) {
    AdaptiveRow(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        start = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        end = { Text(amount.formatOrAbsent(), style = Accord.typography.amountRow, color = color, textAlign = TextAlign.End) },
    )
}

private fun Money?.formatOrAbsent(): String = this?.format() ?: ABSENT

/** Primeira cor com contraste de texto sobre o painel; o painel é carvão nos dois temas, então o tom claro do tema escuro nem sempre existe no claro. */
private fun readableOn(background: Color, fallback: Color, candidates: List<Color>): Color =
    candidates.firstOrNull { contrast(it, background) >= MIN_TEXT_CONTRAST } ?: fallback

private fun contrast(a: Color, b: Color): Float {
    val hi = maxOf(a.luminance(), b.luminance())
    val lo = minOf(a.luminance(), b.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
}
