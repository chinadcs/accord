package app.accord.ui.entries

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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

/** Só para o teste achar os ícones decorativos (sem texto): o sufixo é o recurso do ícone. */
const val BALANCE_ICON_TAG_PREFIX = "balance-icon-"
private const val ABSENT = "—"

/**
 * Saldo total acumulado até o fim do mês (renda recebida menos despesa comprometida), com Receita e Despesa só desse
 * mês. Renda prevista e quitação de fatura não entram. Mora no Início (`accord-wecg`, ADR 0071); antes ficava em Lançamentos.
 *
 * O subtítulo diz "acumulado até o fim do mês" (e não "até hoje") porque é o que o cálculo faz: soma tudo o que já está
 * comprometido até o último dia do mês atual. O cartão é um só nó para o leitor de tela, com o saldo negativo lido por
 * extenso; o título continua sendo cabeçalho. Receita e Despesa têm ícone decorativo, o texto é o equivalente.
 */
@Composable
fun TotalBalanceCard(
    balance: TotalBalance?,
    modifier: Modifier = Modifier,
    subtitle: String = stringResource(R.string.balance_subtitle),
    countExpectedIncome: Boolean = false,
) {
    val panel = Accord.colors.financeSurface
    val onPanel = Accord.colors.onFinanceSurface
    val incomeColor = Accord.colors.incomeOnFinance
    val expenseColor = Accord.colors.expenseOnFinance
    val title = stringResource(R.string.entries_balance_title)
    val expenseLabel = stringResource(R.string.entries_balance_expense)
    val incomeLabel = stringResource(R.string.entries_balance_income)
    val totalBalance = balance?.total?.let { if (countExpectedIncome) it.balanceWithExpected else it.balance }
    val income = balance?.month?.let { if (countExpectedIncome) it.incomes else it.receivedIncomes }
    val expense = balance?.month?.committedExpenses
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val reading = when {
        totalBalance == null -> stringResource(R.string.balance_reading_unavailable)
        totalBalance.isNegative -> stringResource(R.string.balance_reading_negative, Money(-totalBalance.cents).format())
        else -> stringResource(R.string.balance_reading_positive, totalBalance.format())
    }
    val description = stringResource(
        R.string.balance_description, title, subtitle, reading,
        incomeLabel, income.formatOrAbsent(), expenseLabel, expense.formatOrAbsent(),
    )

    Surface(modifier = modifier.fillMaxWidth(), color = panel, contentColor = onPanel, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s5)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = onPanel.copy(alpha = 0.8f),
                    modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onPanel.copy(alpha = 0.8f))
            }
            Text(totalBalance.formatOrAbsent(), style = Accord.typography.amountHero)
            HorizontalDivider(color = onPanel.copy(alpha = 0.14f))
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    TotalLine(incomeLabel, income, incomeColor, Lucide.drawable.lucide_ic_arrow_down_left)
                    TotalLine(expenseLabel, expense, expenseColor, Lucide.drawable.lucide_ic_arrow_up_right)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    TotalColumn(incomeLabel, income, incomeColor, Lucide.drawable.lucide_ic_arrow_down_left, Modifier.weight(1f))
                    TotalColumn(expenseLabel, expense, expenseColor, Lucide.drawable.lucide_ic_arrow_up_right, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Ícone de direção ao lado do rótulo: decorativo, porque o rótulo (Receita/Despesa do mês) diz o mesmo. */
@Composable
private fun LabelWithIcon(label: String, @DrawableRes icon: Int, tint: Color, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp).testTag(BALANCE_ICON_TAG_PREFIX + icon), tint = tint)
        Text(label, style = style)
    }
}

@Composable
private fun TotalColumn(label: String, amount: Money?, color: Color, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        LabelWithIcon(label, icon, color, MaterialTheme.typography.bodyMedium)
        Text(amount.formatOrAbsent(), style = Accord.typography.amountRow, color = color)
    }
}

@Composable
private fun TotalLine(label: String, amount: Money?, color: Color, @DrawableRes icon: Int) {
    AdaptiveRow(
        start = { LabelWithIcon(label, icon, color, MaterialTheme.typography.bodyLarge) },
        end = { Text(amount.formatOrAbsent(), style = Accord.typography.amountRow, color = color, textAlign = TextAlign.End) },
    )
}

private fun Money?.formatOrAbsent(): String = this?.format() ?: ABSENT
