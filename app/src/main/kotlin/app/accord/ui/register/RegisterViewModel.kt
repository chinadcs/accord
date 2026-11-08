package app.accord.ui.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.AccountInfo
import app.accord.core.space.AssignmentState
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.EntityType
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.Decision
import app.accord.core.space.DraftSource
import app.accord.core.space.Envelope
import app.accord.core.space.InstallmentView
import app.accord.core.space.LocalDraft
import app.accord.core.space.Origin
import app.accord.core.space.PreviewResult
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.Classification
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

enum class RegisterPhase { Editing, Saving, Saved }

enum class OriginProblem { None, AlreadyConverted, NotFound }

/** O que a tela mostra sobre a intenção de origem. [simulatedInvoice] é a primeira fatura do cenário, para avisar se a atribuída pelo aplicativo for outra. */
data class ConversionOrigin(val name: String?, val problem: OriginProblem, val simulatedInvoice: YearMonth?)

data class RegisterUiState(
    val form: RegisterForm,
    val today: java.time.LocalDate = form.date,
    val autoFocusAmount: Boolean = false,
    val errors: Map<FormField, FormError> = emptyMap(),
    /** Erro que não pertence a nenhum campo (ex.: falha inesperada). O conteúdo digitado é preservado. */
    val generalError: String? = null,
    val categories: Map<String, CategoryNode> = emptyMap(),
    val incomeCategories: List<IncomeCategoryNode> = emptyList(),
    val accounts: List<AccountInfo> = emptyList(),
    val defaultAccountId: String? = null,
    /** Cronograma e fatura vindos da prévia da fachada, para compra no cartão. */
    val preview: List<InstallmentView> = emptyList(),
    val phase: RegisterPhase = RegisterPhase.Editing,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val recoveredDraft: Boolean = false,
    val askKeepOrDiscard: Boolean = false,
    /** Campo a receber foco depois de salvar com erro; [focusTick] muda a cada pedido para repetir o foco. */
    val focusField: FormField? = null,
    val focusTick: Int = 0,
    /** Modo conversão (S7): a intenção de origem, quando o formulário nasceu de "Assumir compromisso". */
    val origin: ConversionOrigin? = null,
) {
    val previewToConfirm: Boolean get() = preview.firstOrNull()?.assignment == AssignmentState.ToConfirm
    val saving: Boolean get() = phase == RegisterPhase.Saving
}

/**
 * Registro manual (tela 09), sempre disponível e sem depender do modelo nem da rede. Mantém o que
 * foi digitado (inclusive após o sistema encerrar o processo), guarda rascunho se a pessoa sair sem
 * salvar, mostra a prévia da fatura e das parcelas e garante que tocar duas vezes em Salvar não
 * cria cópia: o ID do comando nasce com o formulário.
 */
class RegisterViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val today = provider.clock.today()
    private val commandId: CommandId = CommandId(saved.get<String>(KEY_COMMAND) ?: UUID.randomUUID().toString().also { saved[KEY_COMMAND] = it })
    private var draftId: String? = saved[KEY_DRAFT]

    private val _state = MutableStateFlow(
        RegisterUiState(
            form = saved.get<String>(KEY_FORM)?.takeIf { '=' in it }?.let { FormCodec.decode(it, today) } ?: RegisterForm(
                date = today,
                // Atalhos do FAB e tela de Rendas definem tipo e meio antes de abrir o formulário.
                kind = if (saved.get<String>(ARG_KIND) == KIND_INCOME) FormKind.Income else FormKind.Expense,
                form = saved.get<String>(ARG_FORM)?.let { requested ->
                    when (requested.lowercase()) {
                        "pix" -> ExpenseForm.Pix
                        "debit" -> ExpenseForm.Debit
                        "cash" -> ExpenseForm.Cash
                        "boleto" -> ExpenseForm.Boleto
                        "card" -> ExpenseForm.Card
                        else -> ExpenseForm.Pix
                    }
                } ?: ExpenseForm.Pix,
            ),
            today = today,
            autoFocusAmount = saved.get<String>(KEY_FORM)?.contains('=') != true && saved.get<String>(KEY_DRAFT) == null,
        ),
    )
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _exit = MutableStateFlow(false)
    val exit: StateFlow<Boolean> = _exit.asStateFlow()

    private var previewJob: Job? = null

    init {
        loadReferences()
    }

    private fun loadReferences() {
        viewModelScope.launch {
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value
            val accounts = (space.accounts() as? QueryResult.Data)?.value
            val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
            val origin = _state.value.form.originIntentionId?.let { id ->
                val intention = (space.intention(id) as? QueryResult.Data)?.value
                val simulated = saved.get<String>(ARG_SIM_INVOICE)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ConversionOrigin(
                    name = intention?.name,
                    problem = when {
                        intention == null -> OriginProblem.NotFound
                        intention.conversion != null -> OriginProblem.AlreadyConverted
                        else -> OriginProblem.None
                    },
                    simulatedInvoice = simulated,
                )
            }
            if (categories == null || accounts == null) {
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            // Sem estado salvo da recriação do processo, retoma o rascunho mais recente, se houver.
            var recovered = false
            if (saved.get<String>(KEY_FORM)?.contains('=') != true) {
                (space.drafts() as? QueryResult.Data)?.value?.firstOrNull { it.source == DraftSource.Form }?.let { draft ->
                    draftId = draft.id
                    saved[KEY_DRAFT] = draft.id
                    val restored = FormCodec.fromFields(draft.fields, today)
                    _state.update { it.copy(form = restored) }
                    recovered = true
                }
            }
            _state.update {
                it.copy(
                    categories = categories.associateBy { c -> c.id },
                    incomeCategories = incomeCategories,
                    accounts = accounts.accounts,
                    defaultAccountId = accounts.myDefaultAccountId,
                    loading = false,
                    recoveredDraft = recovered,
                    origin = origin,
                    // O nome da intenção é a descrição de partida; a pessoa pode trocá-la.
                    form = withDefaultAccount(
                        if (origin?.name != null && it.form.description.isBlank()) it.form.copy(description = origin.name) else it.form,
                        accounts.myDefaultAccountId,
                    ),
                )
            }
            schedulePreview()
        }
    }

    /** Pix, débito, boleto e renda partem da conta padrão de quem registra, sempre corrigível. */
    private fun withDefaultAccount(form: RegisterForm, default: String?): RegisterForm =
        if (form.allowsAccount && form.accountId == null && default != null) form.copy(accountId = default) else form

    fun update(change: (RegisterForm) -> RegisterForm) {
        _state.update { current ->
            val next = withDefaultAccount(normalize(change(current.form)), current.defaultAccountId)
            // Corrigir um campo apaga só o erro dele; os demais continuam até serem corrigidos.
            val stillWrong = current.errors.filterKeys { field -> sameValue(field, current.form, next) }
            current.copy(form = next, errors = stillWrong, generalError = null)
        }
        saved[KEY_FORM] = FormCodec.encode(_state.value.form)
        schedulePreview()
    }

    /** Ao trocar de tipo ou forma, some o que não se aplica mais, para não salvar lixo escondido. */
    private fun normalize(f: RegisterForm): RegisterForm {
        var r = f
        // Conversão de intenção de compra só gera despesa (ADR 0048).
        if (r.originIntentionId != null) r = r.copy(kind = FormKind.Expense)
        if (!r.allowsInstallments) r = r.copy(installmentsText = "1", amountMeaning = null)
        if (r.kind != FormKind.Expense || r.form != ExpenseForm.Card || r.frequency != null) r = r.copy(invoice = null)
        if (!(r.kind == FormKind.Income || r.form == ExpenseForm.Boleto)) r = r.copy(settledDate = null, settledAmountText = "")
        if (r.kind == FormKind.Income) r = r.copy(categoryId = null) else r = r.copy(incomeCategoryId = null)
        if (!r.allowsAccount) r = r.copy(accountId = null)
        if (!r.asksAmountMeaning) r = r.copy(amountMeaning = null)
        return r
    }

    private fun sameValue(field: FormField, a: RegisterForm, b: RegisterForm): Boolean = when (field) {
        FormField.Description -> a.description == b.description
        FormField.Amount -> a.amountText == b.amountText
        FormField.Date -> a.date == b.date
        FormField.Category -> a.categoryId == b.categoryId
        FormField.Account -> a.accountId == b.accountId
        FormField.Installments -> a.installmentsText == b.installmentsText
        FormField.AmountMeaning -> a.amountMeaning == b.amountMeaning
        FormField.Invoice -> a.invoice == b.invoice
        FormField.SettledDate -> a.settledDate == b.settledDate
        FormField.SettledAmount -> a.settledAmountText == b.settledAmountText
    }

    /**
     * Cria uma categoria de renda pelo seletor e a escolhe no formulário. [onResult] recebe a mensagem de recusa
     * (nome em branco ou repetido, em português) ou nulo quando deu certo.
     */
    fun createIncomeCategory(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val space = provider.space()
            when (val result = space.execute(Envelope(CommandId(UUID.randomUUID().toString()), Origin.Manual), CreateIncomeCategory(name))) {
                is CommandResult.Applied -> {
                    val created = result.receipt.changes.firstOrNull { it.type == EntityType.IncomeCategory }?.id
                    val all = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
                    _state.update { it.copy(incomeCategories = all) }
                    update { f -> f.copy(incomeCategoryId = created ?: f.incomeCategoryId) }
                    onResult(null)
                }
                is CommandResult.Rejected -> onResult(result.errors.joinToString(" ") { it.message })
                else -> onResult("Não foi possível criar a categoria agora. Tente de novo.")
            }
        }
    }

    // ---- prévia (compra no cartão) ----

    private fun schedulePreview() {
        previewJob?.cancel()
        val form = _state.value.form
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val command = previewCommand(form)
            if (command == null) {
                _state.update { it.copy(preview = emptyList()) }
                return@launch
            }
            val result = provider.space().preview(command)
            _state.update { it.copy(preview = (result as? PreviewResult.Ready)?.after.orEmpty()) }
        }
    }

    /** Só a compra única no cartão tem prévia; ela não precisa de descrição nem de categoria. */
    private fun previewCommand(f: RegisterForm): RegisterCardPurchase? {
        if (!f.allowsInstallments || f.kind != FormKind.Expense) return null
        val amount = (app.accord.core.Money.parse(f.amountText) as? app.accord.core.MoneyParse.Ok)?.money ?: return null
        val count = f.installments ?: return null
        if (amount.cents <= 0 || count < 1 || (count > 1 && f.amountMeaning == null)) return null
        return RegisterCardPurchase(
            description = "prévia", classification = Classification.ToClassify, date = f.date, amount = amount,
            amountMeaning = f.amountMeaning.takeIf { count > 1 }, installments = count, invoice = f.invoice,
        )
    }

    // ---- salvar ----

    fun save() {
        val current = _state.value
        if (current.saving || current.phase == RegisterPhase.Saved) return
        when (val built = current.form.build()) {
            is RegisterForm.Built.Invalid -> _state.update {
                it.copy(errors = built.errors, focusField = built.errors.keys.first(), focusTick = it.focusTick + 1)
            }
            is RegisterForm.Built.Ok -> {
                _state.update { it.copy(phase = RegisterPhase.Saving, errors = emptyMap(), generalError = null) }
                viewModelScope.launch {
                    val result = provider.space().execute(Envelope(commandId, Origin.Manual, draftId), built.command)
                    handle(result)
                }
            }
        }
    }

    private fun handle(result: CommandResult) {
        when (result) {
            is CommandResult.Applied -> finish(result.receipt.operationId, result.receipt.undoable)
            // Repetir o mesmo ID devolve o resultado original: conta como salvo, sem cópia.
            is CommandResult.AlreadyApplied -> finish(result.receipt.operationId, result.receipt.undoable)
            is CommandResult.Rejected -> {
                val fieldErrors = result.errors.mapNotNull { e -> RegisterForm.fieldOf(e)?.let { it to RegisterForm.errorOf(e) } }
                val unmatched = result.errors.filter { RegisterForm.fieldOf(it) == null }
                _state.update {
                    it.copy(
                        phase = RegisterPhase.Editing,
                        errors = fieldErrors.toMap().toSortedMap(compareBy { f -> f.ordinal }),
                        generalError = unmatched.firstOrNull()?.message,
                        focusField = fieldErrors.minByOrNull { (f, _) -> f.ordinal }?.first,
                        focusTick = it.focusTick + 1,
                    )
                }
            }
            is CommandResult.DecisionNeeded -> _state.update {
                if (result.decision is Decision.AmountMeaningRequired) {
                    it.copy(
                        phase = RegisterPhase.Editing, errors = mapOf(FormField.AmountMeaning to FormError.MeaningRequired),
                        focusField = FormField.AmountMeaning, focusTick = it.focusTick + 1,
                    )
                } else {
                    it.copy(phase = RegisterPhase.Editing, generalError = "Falta uma decisão para concluir.")
                }
            }
            is CommandResult.StaleVersion -> _state.update { it.copy(phase = RegisterPhase.Editing) }
        }
    }

    private fun finish(operationId: String, undoable: Boolean) {
        // Aberto a partir de uma mensagem do Chat: ela deixa de oferecer "Tentar de novo".
        saved.get<String>(KEY_CHAT_MESSAGE)?.let { messageId -> viewModelScope.launch { provider.onManualSaved(messageId) } }
        draftId = null
        saved.remove<String>(KEY_DRAFT)
        provider.feedback.post(FeedbackEvent(R.string.feedback_saved, undoOperationId = operationId.takeIf { undoable }))
        _state.update { it.copy(phase = RegisterPhase.Saved) }
        _exit.value = true
    }

    // ---- sair sem salvar ----

    /** Há algo digitado que ainda não foi salvo. */
    val isDirty: Boolean
        get() = _state.value.form.let {
            it.description.isNotBlank() || it.amountText.isNotBlank() || it.categoryId != null || it.installmentsText != "1" ||
                it.frequency != null || it.invoice != null || it.settledDate != null
        }

    fun requestBack() {
        if (isDirty && _state.value.phase != RegisterPhase.Saved) _state.update { it.copy(askKeepOrDiscard = true) } else _exit.value = true
    }

    fun keepEditing() = _state.update { it.copy(askKeepOrDiscard = false) }

    fun saveDraftAndExit() {
        viewModelScope.launch {
            val form = _state.value.form
            provider.space().saveDraft(
                LocalDraft(
                    id = draftId.orEmpty(), source = DraftSource.Form, fields = FormCodec.toFields(form),
                    missing = (form.build() as? RegisterForm.Built.Invalid)?.errors?.keys?.map { it.name }.orEmpty(),
                    reason = "Formulário não salvo",
                ),
            )
            _exit.value = true
        }
    }

    fun discardAndExit() {
        viewModelScope.launch {
            draftId?.let { provider.space().discardDraft(it) }
            _exit.value = true
        }
    }

    /** Descarta o rascunho recuperado e recomeça com o formulário em branco. */
    fun dismissRecoveredDraft() {
        viewModelScope.launch {
            draftId?.let { provider.space().discardDraft(it) }
            draftId = null
            saved.remove<String>(KEY_DRAFT)
            saved.remove<String>(KEY_FORM)
            _state.update {
                it.copy(
                    form = withDefaultAccount(RegisterForm(date = today), it.defaultAccountId),
                    recoveredDraft = false, errors = emptyMap(), autoFocusAmount = true,
                )
            }
        }
    }

    /** Mês da fatura que a pessoa escolheu, ou o estimado pela prévia. */
    fun estimatedInvoice(): YearMonth? = _state.value.preview.firstOrNull()?.invoiceMonth

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_FORM = "form"
        const val KIND_INCOME = "income"
        const val KEY_FORM = "form"
        const val KEY_COMMAND = "commandId"
        const val KEY_DRAFT = "draftId"
        const val KEY_CHAT_MESSAGE = "chatMessage"
        const val ARG_SIM_INVOICE = "simInvoice"
        const val PREVIEW_DEBOUNCE_MS = 250L
    }
}
