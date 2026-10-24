package app.accord.ui.register

import app.accord.core.Frequency
import app.accord.core.space.AmountMeaning
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth

/**
 * Serialização do formulário para sobreviver à recriação do processo (SavedStateHandle) e para o
 * rascunho local. Só guarda o que a pessoa digitou ou escolheu; nada financeiro é calculado aqui.
 */
object FormCodec {
    private fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8)
    private fun dec(v: String) = URLDecoder.decode(v, Charsets.UTF_8)

    /** Campos legíveis, um por chave, para o rascunho local. */
    fun toFields(f: RegisterForm): Map<String, String> = buildMap {
        put("kind", f.kind.name)
        put("form", f.form.name)
        put("description", f.description)
        put("amount", f.amountText)
        put("date", f.date.toString())
        f.categoryId?.let { put("categoryId", it) }
        f.accountId?.let { put("accountId", it) }
        put("installments", f.installmentsText)
        f.amountMeaning?.let { put("amountMeaning", it.name) }
        f.frequency?.let { put("frequency", it.name) }
        f.invoice?.let { put("invoice", it.toString()) }
        f.settledDate?.let { put("settledDate", it.toString()) }
        put("settledAmount", f.settledAmountText)
        put("moreOptions", f.moreOptions.toString())
    }

    fun fromFields(fields: Map<String, String>, fallbackDate: LocalDate): RegisterForm = RegisterForm(
        kind = fields["kind"]?.let { runCatching { FormKind.valueOf(it) }.getOrNull() } ?: FormKind.Expense,
        form = fields["form"]?.let { runCatching { ExpenseForm.valueOf(it) }.getOrNull() } ?: ExpenseForm.Card,
        description = fields["description"].orEmpty(),
        amountText = fields["amount"].orEmpty(),
        date = fields["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fallbackDate,
        categoryId = fields["categoryId"],
        accountId = fields["accountId"],
        installmentsText = fields["installments"] ?: "1",
        amountMeaning = fields["amountMeaning"]?.let { runCatching { AmountMeaning.valueOf(it) }.getOrNull() },
        frequency = fields["frequency"]?.let { runCatching { Frequency.valueOf(it) }.getOrNull() },
        invoice = fields["invoice"]?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        settledDate = fields["settledDate"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        settledAmountText = fields["settledAmount"].orEmpty(),
        moreOptions = fields["moreOptions"]?.toBooleanStrictOrNull() ?: false,
    )

    /** Uma única string para o SavedStateHandle. */
    fun encode(f: RegisterForm): String = toFields(f).entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

    fun decode(text: String, fallbackDate: LocalDate): RegisterForm {
        val fields = if (text.isEmpty()) emptyMap() else text.split("&").associate { dec(it.substringBefore("=")) to dec(it.substringAfter("=")) }
        return fromFields(fields, fallbackDate)
    }
}
