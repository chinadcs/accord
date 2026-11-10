package app.accord.ui.receivables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.AccountInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.CreateReceivable
import app.accord.core.space.DebtorNode
import app.accord.core.space.Envelope
import app.accord.core.space.EntityType
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.QueryResult
import app.accord.core.space.ReceivableSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID

enum class ScheduleMode { WhenPossible, Single, Installments }

/** O formulário como a pessoa o preencheu: valores em texto, para voltar do jeito que estava (rascunho preservado ao girar ou voltar). */
data class ReceivableForm(
    val commandId: String,
    val amount: String = "",
    val accountId: String? = null,
    val debtorName: String = "",
    val date: LocalDate,
    val incomeCategoryId: String? = null,
    val mode: ScheduleMode = ScheduleMode.WhenPossible,
    val installments: Int = 2,
    val start: LocalDate,
    val rows: List<SlotRow> = emptyList(),
    val noOutflow: Boolean = false,
    val counts: Boolean = false,
    val expenseCategoryId: String? = null,
    val note: String = "",
)

data class ReceivableFormState(
    val form: ReceivableForm,
    val accounts: List<AccountInfo> = emptyList(),
    val debtors: List<DebtorNode> = emptyList(),
    val incomeCategories: List<IncomeCategoryNode> = emptyList(),
    val categories: Map<String, CategoryNode> = emptyMap(),
    val working: Boolean = false,
    val message: String? = null,
) {
    val total: Money? get() = (Money.parse(form.amount) as? MoneyParse.Ok)?.money?.takeIf { it.cents > 0 }
}

internal object ReceivableFormCodec {
    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
    private fun dec(text: String) = URLDecoder.decode(text, "UTF-8")

    fun encode(f: ReceivableForm): String = listOf(
        "commandId" to f.commandId, "amount" to f.amount, "account" to f.accountId.orEmpty(), "name" to f.debtorName,
        "date" to f.date.toString(), "incomeCategory" to f.incomeCategoryId.orEmpty(), "mode" to f.mode.name,
        "installments" to f.installments.toString(), "start" to f.start.toString(),
        "rows" to f.rows.joinToString(";") { "${it.date}|${enc(it.amount)}" },
        "noOutflow" to f.noOutflow.toString(), "counts" to f.counts.toString(),
        "expenseCategory" to f.expenseCategoryId.orEmpty(), "note" to f.note,
    ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }

    fun decode(text: String): ReceivableForm? = runCatching {
        val m = text.split('&').associate { val p = it.split('=', limit = 2); p[0] to dec(p.getOrElse(1) { "" }) }
        ReceivableForm(
            commandId = m.getValue("commandId"), amount = m.getValue("amount"), accountId = m["account"]?.ifEmpty { null },
            debtorName = m.getValue("name"), date = LocalDate.parse(m.getValue("date")), incomeCategoryId = m["incomeCategory"]?.ifEmpty { null },
            mode = ScheduleMode.valueOf(m.getValue("mode")), installments = m.getValue("installments").toInt(), start = LocalDate.parse(m.getValue("start")),
            rows = m.getValue("rows").split(';').filter { it.isNotEmpty() }.map { row ->
                val (date, amount) = row.split('|', limit = 2)
                SlotRow(LocalDate.parse(date), dec(amount))
            },
            noOutflow = m.getValue("noOutflow").toBoolean(), counts = m.getValue("counts").toBoolean(),
            expenseCategoryId = m["expenseCategory"]?.ifEmpty { null }, note = m.getValue("note"),
        )
    }.getOrNull()
}

/**
 * "Novo valor a receber" (spec 39). Também é o destino do "Alguém me deve isso" do Registrar despesa, que entrega valor, conta e data.
 * Salvar cria a saída (se houver), o devedor (se novo) e o cronograma numa operação só, desfazível.
 */
class ReceivableFormViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val today = provider.clock.today()

    private val _state = MutableStateFlow(
        ReceivableFormState(
            saved.get<String>(FORM)?.let(ReceivableFormCodec::decode) ?: ReceivableForm(
                commandId = UUID.randomUUID().toString(),
                amount = saved.get<String>("amount").orEmpty(),
                accountId = saved.get<String>("account")?.ifEmpty { null },
                date = saved.get<String>("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today,
                start = today,
            ),
        ),
    )
    val state: StateFlow<ReceivableFormState> = _state.asStateFlow()

    /** Emitido uma vez quando o valor a receber foi salvo. */
    private val _saved = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            val space = provider.space()
            val accounts = (space.accounts() as? QueryResult.Data)?.value
            val debtors = (space.receivables() as? QueryResult.Data)?.value?.debtors.orEmpty().map { it.debtor }
            val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
            val categories = (space.categories() as? QueryResult.Data)?.value.orEmpty().associateBy { it.id }
            _state.update { s ->
                val usable = accounts?.accounts.orEmpty()
                val account = s.form.accountId?.takeIf { id -> usable.any { it.id == id } }
                    ?: accounts?.myDefaultAccountId?.takeIf { id -> usable.any { it.id == id } }
                    ?: usable.singleOrNull()?.id
                s.copy(
                    form = s.form.copy(accountId = account), accounts = usable, debtors = debtors,
                    incomeCategories = incomeCategories, categories = categories,
                )
            }
        }
    }

    private fun update(change: (ReceivableForm) -> ReceivableForm) {
        _state.update { s ->
            val next = change(s.form)
            saved[FORM] = ReceivableFormCodec.encode(next)
            s.copy(form = next, message = null)
        }
    }

    fun setAmount(text: String) = update { it.copy(amount = text) }
    fun setAccount(id: String) = update { it.copy(accountId = id) }
    fun setName(text: String) = update { it.copy(debtorName = text.take(80)) }
    fun setDate(date: LocalDate) = update { it.copy(date = date) }
    fun setIncomeCategory(id: String?) = update { it.copy(incomeCategoryId = id) }
    fun setNoOutflow(value: Boolean) = update { it.copy(noOutflow = value) }
    fun setCounts(value: Boolean) = update { it.copy(counts = value) }
    fun setExpenseCategory(id: String) = update { it.copy(expenseCategoryId = id) }
    fun setNote(text: String) = update { it.copy(note = text) }

    /** Trocar de modo reconstrói as linhas: "quando der" não tem nenhuma, "data única" tem uma, "parcelar" divide em N. */
    fun setMode(mode: ScheduleMode) = update { f ->
        when (mode) {
            ScheduleMode.WhenPossible -> f.copy(mode = mode, rows = emptyList())
            ScheduleMode.Single -> f.copy(mode = mode, rows = listOf(SlotRow(f.start, f.amount)))
            ScheduleMode.Installments -> f.copy(mode = mode, rows = split(f, f.installments))
        }
    }

    fun setInstallments(count: Int) = update { f ->
        val n = count.coerceIn(2, MAX_INSTALLMENTS)
        f.copy(installments = n, rows = split(f, n))
    }

    fun setStart(date: LocalDate) = update { f ->
        val moved = if (f.mode == ScheduleMode.Installments) f.copy(start = date, rows = f.rows.mapIndexed { i, r -> r.copy(date = date.plusMonths(i.toLong())) })
        else f.copy(start = date, rows = f.rows.mapIndexed { i, r -> if (i == 0) r.copy(date = date) else r })
        moved
    }

    /** "Dividir": refaz os valores igualmente (a sobra de centavos vai na última), mantendo as datas já escolhidas. */
    fun splitEvenly() = update { f -> f.copy(rows = split(f, f.rows.size.coerceAtLeast(1), keepDates = true)) }

    fun setSlotDate(index: Int, date: LocalDate) = update { f -> f.copy(rows = f.rows.mapIndexed { i, r -> if (i == index) r.copy(date = date) else r }) }
    fun setSlotAmount(index: Int, text: String) = update { f -> f.copy(rows = f.rows.mapIndexed { i, r -> if (i == index) r.copy(amount = text) else r }) }
    fun addSlot() = update { f ->
        val last = f.rows.lastOrNull()?.date ?: f.start
        f.copy(rows = f.rows + SlotRow(last.plusMonths(1), ""))
    }
    fun removeSlot(index: Int) = update { f -> f.copy(rows = f.rows.filterIndexed { i, _ -> i != index }) }

    private fun split(f: ReceivableForm, count: Int, keepDates: Boolean = false): List<SlotRow> {
        val total = (Money.parse(f.amount) as? MoneyParse.Ok)?.money?.takeIf { it.cents >= count }
        val amounts = total?.let { Installments.splitTotal(it, count) }
        return List(count) { i ->
            SlotRow(
                if (keepDates) f.rows.getOrNull(i)?.date ?: f.start.plusMonths(i.toLong()) else f.start.plusMonths(i.toLong()),
                amounts?.get(i)?.let(::plain).orEmpty(),
            )
        }
    }

    /** Soma do cronograma em centavos; linhas ilegíveis contam zero (a validação do salvar as recusa). */
    fun scheduleSum(): Long = _state.value.form.rows.sumOf { (Money.parse(it.amount) as? MoneyParse.Ok)?.money?.cents ?: 0 }

    fun createIncomeCategory(name: String, parentId: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val space = provider.space()
            when (val result = space.execute(Envelope(CommandId(UUID.randomUUID().toString())), CreateIncomeCategory(name, parentId))) {
                is CommandResult.Applied -> {
                    val created = result.receipt.changes.firstOrNull { it.type == EntityType.IncomeCategory }?.id
                    val all = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
                    _state.update { it.copy(incomeCategories = all) }
                    created?.let(::setIncomeCategory)
                    onResult(null)
                }
                is CommandResult.Rejected -> onResult(result.errors.joinToString(" ") { it.message })
                else -> onResult("Não foi possível criar a categoria agora. Tente de novo.")
            }
        }
    }

    fun save(resources: (Int) -> String) {
        val s = _state.value
        if (s.working) return
        val f = s.form
        val total = s.total ?: return fail(resources(R.string.receivable_form_invalid_amount))
        if (f.debtorName.isBlank()) return fail(resources(R.string.receivable_form_invalid_name))
        if (f.accountId == null) return fail(resources(R.string.receivable_form_invalid_account))
        val expense = f.counts && !f.noOutflow
        if (expense && f.expenseCategoryId == null) return fail(resources(R.string.receivable_form_invalid_expense))
        val slots = f.rows.map { row ->
            val amount = (Money.parse(row.amount) as? MoneyParse.Ok)?.money?.takeIf { it.cents > 0 }
                ?: return fail(resources(R.string.receivable_form_invalid_slot))
            ReceivableSlot(row.date, amount)
        }
        if (slots.sumOf { it.amount.cents } > total.cents) return fail(resources(R.string.receivable_form_invalid_sum))
        val command = CreateReceivable(
            debtorName = f.debtorName, total = total, accountId = f.accountId, date = f.date, schedule = slots,
            incomeCategoryId = f.incomeCategoryId, noOutflow = f.noOutflow, countsInBudget = f.counts,
            expenseCategoryId = f.expenseCategoryId.takeIf { expense }, note = f.note,
        )
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                val receipt = when (val result = provider.space().execute(Envelope(CommandId(f.commandId)), command)) {
                    is CommandResult.Applied -> result.receipt
                    is CommandResult.AlreadyApplied -> result.receipt
                    is CommandResult.Rejected -> return@launch fail(result.errors.joinToString(" ") { it.message })
                    else -> return@launch fail(resources(R.string.receivable_form_failed))
                }
                saved[FORM] = null
                provider.feedback.post(FeedbackEvent(R.string.receivables_saved, undoOperationId = receipt.operationId.takeIf { receipt.undoable }))
                _saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(resources(R.string.receivable_form_failed))
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(working = false, message = message) }
    }

    private fun plain(money: Money): String = java.math.BigDecimal.valueOf(money.cents, 2).toPlainString().replace('.', ',')

    private companion object {
        const val FORM = "receivableForm"
        const val MAX_INSTALLMENTS = 60
    }
}
