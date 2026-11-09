package app.accord.core.sync

import app.accord.core.Money
import app.accord.core.space.*
import kotlinx.serialization.json.*
import java.time.LocalDate

/** Validação canônica do fake; o PostgreSQL aplica as mesmas regras na transação do espaço. */
object SavingsServiceRules {
    fun validate(current: List<RemoteEntityState>, changes: List<EntityChangeEnvelope>, actor: String, today: LocalDate): String? = try {
        validateStates(current, changes, actor, today)
    } catch (_: Exception) { "Dados inválidos de cofrinho." }

    private fun validateStates(current: List<RemoteEntityState>, changes: List<EntityChangeEnvelope>, actor: String, today: LocalDate): String? {
        if (current.none { it.type == "SavingsPocket" } && changes.none { it.type in setOf("SavingsPocket", "SavingsMove") }) return null
        val before = current.associate { EntityKey(it.type, it.id) to Json.parseToJsonElement(it.state).jsonObject }
        val after = before + changes.associate { EntityKey(it.type, it.id) to Json.parseToJsonElement(it.state).jsonObject }
        val pockets = after.filterKeys { it.type == "SavingsPocket" }
        val pocketIds = pockets.keys.map { it.id }.toSet()
        if (changes.filter { it.type !in setOf("SavingsPocket", "SavingsMove") }.any { containsAccount(it.state, pocketIds) }) return "Resgate o cofrinho antes de pagar."
        if (changes.none { it.type in setOf("SavingsPocket", "SavingsMove") }) return null
        for (change in changes.filter { it.type == "SavingsPocket" }) {
            val key = EntityKey(change.type, change.id); val p = after.getValue(key); val old = before[key]
            if (!listOf("id","accountId","name","createdBy").all { p.string(it) } || !p.numeric("initialCents") || !p.numeric("cutoffEpochDay") ||
                !p.optionalNumber("goalCents") || !p.optionalNumber("deadlineEpochDay") || !p.boolean("archived")) return "Cadastro de cofrinho inválido."
            p.number("deadlineEpochDay")?.let(LocalDate::ofEpochDay)
            if (change.tombstone || p.text("id") != key.id || p.text("name").isNullOrBlank() || p.text("name")!!.length > 80 ||
                p.number("initialCents") == null || p.number("initialCents")!! < 0 || p.number("cutoffEpochDay") == null ||
                p.number("cutoffEpochDay")!! > today.toEpochDay() || p.number("goalCents")?.let { it <= 0 } == true ||
                after[EntityKey("Account", p.text("accountId") ?: "")] == null) return "Cadastro de cofrinho inválido."
            if (old == null && p.text("createdBy") != actor) return "Autoria inválida."
            if (old != null && listOf("accountId", "initialCents", "cutoffEpochDay", "createdBy").any { p[it] != old[it] }) return "Vínculo e saldo inicial são fixos."
            if (old?.flag("archived") == true && p != old) return "Cofrinho arquivado."
            val events = pocketEvents(after, key.id)
            val min = SavingsBalances.minimumFrom(Money(p.number("initialCents")!!), day(p, "cutoffEpochDay"), events, day(p, "cutoffEpochDay"))
            if (min.cents < 0) return "Saldo do cofrinho inválido."
            if (p.flag("archived") && AccountBalances.balance(Money(p.number("initialCents")!!), day(p, "cutoffEpochDay"), events).cents != 0L) return "Resgate o saldo antes de arquivar."
        }
        for (change in changes.filter { it.type == "SavingsMove" }) {
            val key = EntityKey(change.type, change.id); val m = after.getValue(key); val old = before[key]
            if (!listOf("id","pocketId","accountId","direction","authorId").all { m.string(it) } || !m.numeric("amountCents") || !m.numeric("epochDay") ||
                !m.boolean("voided")) return "Movimentação inválida."
            val pocketKey = EntityKey("SavingsPocket", m.text("pocketId") ?: "")
            val p = after[pocketKey] ?: return "Cofrinho não encontrado."
            val accountKey = EntityKey("Account", p.text("accountId") ?: "")
            val a = after[accountKey] ?: return "Conta não encontrada."
            if (m.text("id") != key.id || m.text("accountId") != p.text("accountId") || p.flag("archived") ||
                m.number("amountCents")?.let { it > 0 } != true || m.text("direction") !in setOf("Deposit", "Withdraw") ||
                m.number("epochDay") == null || m.number("epochDay")!! > today.toEpochDay() || day(m, "epochDay") < day(p, "cutoffEpochDay") ||
                change.tombstone != m.flag("voided")) return "Movimentação inválida."
            val changedKeys = changes.map { EntityKey(it.type, it.id) }.toSet()
            if (pocketKey !in changedKeys || accountKey !in changedKeys) return "Movimentação precisa das versões da conta e do cofrinho."
            if (before[accountKey]?.let { it != a } == true) return "Movimentação não pode alterar o cadastro da conta."
            if (old == null) {
                if (m.flag("voided") || m.text("authorId") != actor) return "Autoria ou estado inválido."
            } else if (old.flag("voided") || !m.flag("voided") || (m - "voided") != (old - "voided")) return "Movimento é imutável; apenas desfazer é permitido."
            val cutoff = a.number("balanceCutoffEpochDay")
            if (cutoff != null && m.number("epochDay")!! < cutoff) return "Data anterior ao saldo próprio da conta."
            val balance = SavingsBalances.minimumFrom(Money(p.number("initialCents")!!), day(p, "cutoffEpochDay"), pocketEvents(after, pocketKey.id), day(p, "cutoffEpochDay"))
            if (balance.cents < 0) return "Saldo insuficiente no cofrinho."
            val debitsParent = (old == null && m.text("direction") == "Deposit") || (old != null && m.text("direction") == "Withdraw")
            if (debitsParent) {
                val initial = a.number("initialBalanceCents") ?: return "Saldo próprio não informado."
                if (cutoff == null || SavingsBalances.minimumFrom(Money(initial), LocalDate.ofEpochDay(cutoff), accountEvents(after, accountKey.id), day(m, "epochDay")).cents < 0) return "Saldo próprio insuficiente."
            }
        }
        return null
    }

    private fun containsAccount(state: String, ids: Set<String>): Boolean {
        fun walk(value: JsonElement): Boolean = when (value) {
            is JsonObject -> value.any { (key, child) -> (key in setOf("accountId", "fromAccountId", "toAccountId", "defaultAccountId") && (child as? JsonPrimitive)?.contentOrNull in ids) || walk(child) }
            is JsonArray -> value.any(::walk)
            else -> false
        }
        return walk(Json.parseToJsonElement(state))
    }

    private fun pocketEvents(states: Map<EntityKey, JsonObject>, id: String): List<BalanceEvent> = states.filterKeys { it.type == "SavingsMove" }.values
        .filter { !it.flag("voided") && it.text("pocketId") == id }.map { event(it, "epochDay", if (it.text("direction") == "Deposit") 1 else -1, "amountCents") }

    /** Mesmo conjunto de eventos do BalanceReader; entradas previstas e compras no cartão não entram. */
    fun accountEvents(states: Map<EntityKey, JsonObject>, id: String): List<BalanceEvent> = buildList {
        val wallet = states[EntityKey("Account", id)]?.flag("isWallet") == true
        states.forEach { (key, r) ->
            if (r.flag("voided")) return@forEach
            val mine = r.text("accountId") == id
            when (key.type) {
                "Movement" -> if (mine || (r.text("accountId") == null && r.text("kind") == "Cash" && wallet)) add(event(r, "epochDay", -1, "amountCents"))
                "Income" -> if (mine && r.number("receivedEpochDay") != null) add(event(r, "receivedEpochDay", 1, if (r.number("receivedCents") == null) "expectedCents" else "receivedCents"))
                "Boleto" -> if (mine && r.number("paidEpochDay") != null) add(event(r, "paidEpochDay", -1, if (r.number("paidCents") == null) "expectedCents" else "paidCents"))
                "InvoicePayment" -> if (mine) add(event(r, "epochDay", -1, "amountCents"))
                "AccountMove" -> {
                    if (r.text("toAccountId") == id) add(event(r, "epochDay", 1, "amountCents"))
                    if (r.text("fromAccountId") == id) add(event(r, "epochDay", -1, "amountCents"))
                }
                "SavingsMove" -> if (mine) add(event(r, "epochDay", if (r.text("direction") == "Deposit") -1 else 1, "amountCents"))
                "Recurrence" -> if (r.text("form") == "OffCard") (r["settlements"] as? JsonArray).orEmpty().map { it.jsonObject }.filter { it.text("accountId") == id }.forEach { add(event(it, "epochDay", if (r.text("kind") == "Income") 1 else -1, "cents")) }
            }
        }
    }
    private fun event(r: JsonObject, date: String, sign: Int, amount: String) = BalanceEvent(day(r, date), Money(Math.multiplyExact(sign.toLong(), r.number(amount)!!)), BalanceSource.TransferIn)
    private fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.string(key: String): Boolean = (get(key) as? JsonPrimitive)?.isString == true
    private fun JsonObject.numeric(key: String): Boolean = (get(key) as? JsonPrimitive)?.let { !it.isString && it.longOrNull != null } == true
    private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull != null } == true
    private fun JsonObject.optionalNumber(key: String): Boolean = key in this && (get(key) == JsonNull || numeric(key))
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true
    private fun day(r: JsonObject, key: String) = LocalDate.ofEpochDay(r.number(key)!!)
}
