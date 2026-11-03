package app.accord.core.importing

import kotlin.math.abs

/**
 * Reconciliation preview (S4 "Reconciliação"). Pure: no I/O, clock or SLM. Thresholds marked "Proposal" in the spec are parameters, to be calibrated
 * with real samples. Rules 1 to 7, 12 and 15 work from the extraction and the known state alone; with a [BillingContext] the invoice window,
 * installment schedule, invoice payment, boleto, charge and suggested-category rules (8 to 11, 13, 14) apply too.
 */
object Reconciler {
    const val SUPPORTED_CURRENCY = "BRL"
    /** Whether two descriptions share a word (accents and case ignored): how a candidate is ranked next to a movement. */
    fun sharesAWord(a: String, b: String): Boolean = BillingRules.sharesAWord(a, b)

    /** Proposal: how many days apart a manual entry may be and still be shown as a possible match. */
    const val DEFAULT_TOLERANCE_DAYS = 3

    fun preview(
        batchId: String,
        originId: String,
        extraction: ExtractionResult,
        known: KnownState = KnownState(),
        decisions: Decisions = Decisions(),
        toleranceDays: Int = DEFAULT_TOLERANCE_DAYS,
        billing: BillingContext? = null,
    ): Preview {
        val movements = extraction.movements
        val blockedByCurrency = extraction.metadata.currency != SUPPORTED_CURRENCY
        val isCard = extraction.metadata.kind == DocumentKind.Card
        val keys = movements.map { key(originId, it, movements) }
        val knownByKey = known.origins.filter { it.originId == originId }.associateBy { it.key }

        // FITID groups inside the file (rule 2) and against earlier records.
        val groups = movements.indices.filter { movements[it].fitid != null }.groupBy { movements[it].fitid!! }
        fun divergentWithin(i: Int): Boolean = groups[movements[i].fitid]?.let { g -> g.size > 1 && g.map { movements[it].date to movements[it].amountCents }.toSet().size > 1 } == true
        fun repeatedIdentical(i: Int): Boolean = groups[movements[i].fitid]?.let { g -> g.size > 1 && g.map { movements[it].date to movements[it].amountCents }.toSet().size == 1 } == true
        fun divergentAgainstEarlier(i: Int): Boolean = movements[i].fitid?.let { f ->
            known.origins.any { it.originId == originId && it.fitid == f && it.key != keys[i] && (it.date != movements[i].date || it.cents != movements[i].amountCents) }
        } == true

        val entries = known.entries.filter { it.originId == originId && !it.hasOrigin }
        val reasons = movements.map { mutableSetOf<DoubtReason>() }
        val knownMatch = BooleanArray(movements.size)
        val forced = arrayOfNulls<String>(movements.size)
        val investmentCredit = BooleanArray(movements.size)
        val chosenIncome = BooleanArray(movements.size)
        val chosenRefund = BooleanArray(movements.size)

        movements.forEachIndexed { i, m ->
            val r = reasons[i]
            if (MovementDoubt.AmountNotInterpreted in m.doubts || m.amountCents == null) r += DoubtReason.AmountNotInterpreted
            if (MovementDoubt.DateNotInterpreted in m.doubts || m.date == null) r += DoubtReason.DateNotInterpreted
            if (m.amountCents == 0L) r += DoubtReason.ZeroAmount
            if (!blockedByCurrency && m.currency != null && m.currency != SUPPORTED_CURRENCY) r += DoubtReason.ForeignCurrencyMovement
            if (divergentWithin(i) || divergentAgainstEarlier(i)) r += DoubtReason.FitidDivergent
            if (repeatedIdentical(i)) r += DoubtReason.PossibleRepetition
            knownByKey[keys[i]]?.let { earlier -> if (earlier.entryRemoved) r += DoubtReason.RemovedPreviously else knownMatch[i] = true }
            // Same origin key as a movement already effected (rule 1): the person already decided it, so FITID and repetition doubts do not come back.
            if (knownMatch[i]) r -= setOf(DoubtReason.FitidDivergent, DoubtReason.PossibleRepetition)
            if (!knownMatch[i] && !r.contains(DoubtReason.RemovedPreviously) && repeatedInOtherBatch(batchId, originId, m, keys[i], known)) r += DoubtReason.PossibleRepetition
            // The person looked at the doubt and says it is new: the three reasons that only mean "maybe it is not new" go away.
            if (keys[i] in decisions.confirmedNew) r -= setOf(DoubtReason.FitidDivergent, DoubtReason.PossibleRepetition, DoubtReason.RemovedPreviously)
            if (keys[i] in decisions.postponed) r += DoubtReason.ReviewLater
            if (keys[i] in decisions.refused) r += DoubtReason.CannotApply
            // Decisions of 02/11/2026: money coming back from an investment is income; money going in (aplicação) is an ordinary expense on the account.
            val investment = BillingRules.isInvestment(m.description)
            if (!knownMatch[i] && (m.amountCents ?: 0) > 0 && !r.contains(DoubtReason.RemovedPreviously)) {
                if (investment) investmentCredit[i] = true
                else if (keys[i] in decisions.incomeByKey) chosenIncome[i] = true
                else if (keys[i] in decisions.refundByKey) chosenRefund[i] = true
                else r += DoubtReason.CreditToClassify
            }
            decisions.forcedLinks[keys[i]]?.takeIf { id -> entries.any { it.id == id } }?.let { forced[i] = it }
        }

        // Billing rules (8 to 11, 13): clean debits that are not known and not decided by the person.
        val outcomes = arrayOfNulls<BillingRules.Outcome>(movements.size)
        if (billing != null && !blockedByCurrency) {
            movements.forEachIndexed { i, m -> if (reasons[i].isEmpty() && !knownMatch[i] && forced[i] == null) outcomes[i] = BillingRules.decide(m, isCard, billing, keys[i] in decisions.firstInstallmentShifts) }
        }

        // Matching with manual or Chat entries (rule 5): only for clean debits that billing did not decide.
        val exact = HashMap<Int, List<String>>()
        val near = HashMap<Int, List<String>>()
        val value = HashMap<Int, List<String>>()
        movements.forEachIndexed { i, m ->
            if (reasons[i].isNotEmpty() || knownMatch[i] || forced[i] != null || outcomes[i] != null || m.date == null || m.amountCents == null || m.amountCents >= 0) return@forEachIndexed
            val free = entries.filter { DistinctPair(keys[i], it.id) !in known.distinctPairs }
            exact[i] = free.filter { it.cents == m.amountCents && it.date == m.date }.map { it.id }.sorted()
            near[i] = free.filter { it.cents == m.amountCents && it.date != m.date && abs(java.time.temporal.ChronoUnit.DAYS.between(it.date, m.date)) <= toleranceDays }.map { it.id }.sorted()
            value[i] = free.filter { it.cents != m.amountCents && it.date == m.date && BillingRules.sharesAWord(it.description, m.description) }.map { it.id }.sorted()
        }
        fun wanted(entryId: String, except: Int) = exact.keys.any { it != except && (entryId in exact.getValue(it) || entryId in near.getValue(it) || entryId in value.getValue(it)) }

        // The facade already computed the estimated invoice and the to-confirm mark; the document decides (rule 8).
        fun linkEffect(entryId: String, key: String): Pair<ProposedEffect?, DoubtReason?> {
            if (billing == null) return null to null
            val info = entries.firstOrNull { it.id == entryId }?.card
            val doc = billing.documentInvoice
            return when {
                !isCard || info == null || doc == null -> ProposedEffect.LinkEntry(entryId) to null
                info.assignedDueMonth == doc -> ProposedEffect.LinkEntry(entryId, null, info.toConfirm) to null
                info.toConfirm && !info.chosenByPerson -> ProposedEffect.LinkEntry(entryId, doc, true) to null
                // The person looked at the divergence and chose the document's invoice for this very entry (S4 rule 8, explicit choice).
                decisions.invoiceCorrections[key] == entryId -> ProposedEffect.LinkEntry(entryId, doc) to null
                else -> null to DoubtReason.InvoiceDiverges
            }
        }

        val items = movements.mapIndexed { i, m ->
            val r = reasons[i]
            val outcome = outcomes[i]
            var linked: String? = forced[i]
            var effect: ProposedEffect? = null
            var candidates = emptyList<String>()
            var charge = false
            if (outcome != null) {
                r += outcome.reasons
                effect = outcome.effect
                charge = outcome.charge
                (effect as? ProposedEffect.LinkInstallment)?.let { linked = it.entryId }
            } else if (i in exact) {
                candidates = (exact.getValue(i) + near.getValue(i) + value.getValue(i)).distinct().sorted()
                val only = exact.getValue(i).singleOrNull()
                if (only != null && near.getValue(i).isEmpty() && value.getValue(i).isEmpty() && !wanted(only, i)) linked = only
                else if (candidates.isNotEmpty()) r += DoubtReason.PossibleMatch
            }
            if (outcome == null && linked != null) {
                val (linkedEffect, divergence) = linkEffect(linked!!, keys[i])
                if (divergence != null) { r += divergence; linked = null } else effect = linkedEffect
            }
            val normalized = BillingRules.upper(m.description)
            val chosen = decisions.categoryByKey[keys[i]]
            val category = if (charge) billing?.chargesCategoryId else chosen ?: decisions.defaultCategoryId
            val fromHistory = if (chosen == null) decisions.categorySuggestions.entries.firstOrNull { BillingRules.upper(it.key) == normalized }?.value else null
            // The couple's own history wins; the bank's label is the fallback. Neither is applied: the person accepts it.
            val fromBank = if (chosen == null && fromHistory == null) BankCategoryHints.labelOf(m)?.let { decisions.bankCategoryHints[it] } else null
            val suggestion = fromHistory ?: fromBank
            if (investmentCredit[i] && r.isEmpty()) effect = ProposedEffect.CreateIncome(IncomeBasis.InvestmentCredit, app.accord.core.space.IncomeCategorySeed.INVESTMENT)
            if (chosenIncome[i] && r.isEmpty()) effect = ProposedEffect.CreateIncome(IncomeBasis.ChosenByPerson, null, decisions.incomeByKey[keys[i]])
            if (chosenRefund[i] && r.isEmpty()) effect = ProposedEffect.CreateRefund(decisions.refundByKey.getValue(keys[i]), if (isCard) billing?.documentInvoice else null)
            val needsCategory = r.isEmpty() && !knownMatch[i] && linked == null && effect == null
            if (needsCategory && category == null) r += DoubtReason.CategoryUnknown
            if (!blockedByCurrency && keys[i] in decisions.dismissed) {
                return@mapIndexed ReconciledMovement(m, keys[i], Classification.AlreadyKnown, emptySet(), null, emptyList(), null, dismissed = true)
            }
            val classification = when {
                blockedByCurrency -> Classification.BlockedByCurrency
                r.isNotEmpty() -> Classification.Doubt
                knownMatch[i] -> Classification.AlreadyKnown
                linked != null || effect is ProposedEffect.PayInvoiceInFull || effect is ProposedEffect.PayBoleto -> Classification.SafeLink
                else -> Classification.New
            }
            if (classification == Classification.New && billing != null && effect == null && !investmentCredit[i] && !chosenIncome[i] && !chosenRefund[i]) {
                effect = ProposedEffect.CreateEntry(category!!, if (isCard) billing.documentInvoice else null)
            }
            ReconciledMovement(
                movement = m,
                key = keys[i],
                classification = classification,
                doubts = r.toSet(),
                linkedEntryId = linked.takeIf { classification == Classification.SafeLink },
                candidateEntryIds = candidates,
                categoryId = category.takeIf { classification == Classification.New && !investmentCredit[i] && !chosenIncome[i] && !chosenRefund[i] },
                effect = effect.takeIf { classification == Classification.New || classification == Classification.SafeLink },
                suggestedFirstInstallment = outcome?.suggestedFirst,
                suggestedCategoryId = suggestion.takeIf { r.contains(DoubtReason.CategoryUnknown) },
                suggestionSource = when {
                    !r.contains(DoubtReason.CategoryUnknown) -> null
                    fromHistory != null -> SuggestionSource.History
                    fromBank != null -> SuggestionSource.BankLabel
                    else -> null
                },
            )
        }
        return Preview(batchId, if (blockedByCurrency) BatchState.DiagnosticOnly else BatchState.InReview, items)
    }

    /** Origin records a confirmation would persist: new movements and safe links. Doubts, blocked and already known ones are not effected now. */
    fun originRecords(preview: Preview, originId: String): List<KnownOrigin> = preview.items
        .filter { it.classification == Classification.New || it.classification == Classification.SafeLink }
        .map { KnownOrigin(it.key, originId, it.movement.fitid, it.movement.date, it.movement.amountCents, it.movement.currency, it.movement.description, preview.batchId) }

    /**
     * Deterministic origin key (rule 1). With a FITID: origin, FITID, day, cents and currency. Without one: origin, day, cents, currency, description with
     * spaces normalised, installment hint and the order among identical lines of the same document. The invoice due date is not part of it.
     */
    fun key(originId: String, movement: ExtractedMovement, movements: List<ExtractedMovement>): String {
        fun day(m: ExtractedMovement) = m.date?.toString() ?: ("raw:" + m.originalFields.firstOrNull { it.first == "DTPOSTED" }?.second.orEmpty())
        fun cents(m: ExtractedMovement) = m.amountCents?.toString() ?: ("raw:" + m.originalFields.firstOrNull { it.first == "TRNAMT" }?.second.orEmpty())
        fun base(m: ExtractedMovement): String {
            val f = m.fitid
            return if (f != null) "ofx|$originId|$f|${day(m)}|${cents(m)}|${m.currency.orEmpty()}"
            else "doc|$originId|${day(m)}|${cents(m)}|${m.currency.orEmpty()}|${m.description.replace(Regex("\\s+"), " ").trim()}|${m.installment?.let { "${it.number}/${it.total}" }.orEmpty()}"
        }
        val index = movements.indexOfFirst { it.ordinal == movement.ordinal }.let { if (it < 0) movements.size else it }
        val mine = base(movement)
        val ordinal = movements.take(index).count { base(it) == mine } + 1
        return if (ordinal == 1 && movement.fitid != null) mine else "$mine|#$ordinal"
    }

    private fun repeatedInOtherBatch(batchId: String, originId: String, m: ExtractedMovement, key: String, known: KnownState) = known.origins.any {
        it.originId == originId && it.batchId != batchId && it.key != key && it.date == m.date && it.cents == m.amountCents && it.currency == m.currency &&
            it.description.replace(Regex("\\s+"), " ").trim() == m.description
    }
}
