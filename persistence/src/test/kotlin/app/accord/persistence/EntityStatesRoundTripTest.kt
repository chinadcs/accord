package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.TotalChange
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommitmentKind
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CreateCategory
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.core.space.SetIconAssociation
import app.accord.core.space.CreateIntention
import app.accord.core.space.EditIntention
import app.accord.core.space.ClearIconAssociation
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DefineLimits
import app.accord.core.space.DeleteEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditRecurrence
import app.accord.core.space.EntityType
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.PayOccurrence
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RenameIncomeCategory
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.sync.PullPage
import app.accord.core.sync.RemoteEntityState
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O estado completo de cada tipo de entidade vai e volta pelo protocolo sem perder nada: um segundo
 * aparelho que só recebe os estados chega aos mesmos totais, listas e faturas (S2 "Fronteira de versão").
 */
class EntityStatesRoundTripTest : SpaceTestBase() {
    private val months = (0..7).map { YearMonth.of(2026, 9).plusMonths(it.toLong()) }

    /** Entrega ao aparelho [to] todos os estados gravados nas operações de [from], em ordem, como o serviço faria. */
    private suspend fun replicate(from: Handle, to: Handle): Int {
        val sync = from.database.db.syncDao()
        val operations = sync.operationsIn(OpState.OPEN)
        val changes = sync.changesOfAll(operations.map { it.operationId }).groupBy { it.operationId }
        val versions = mutableMapOf<String, Long>()
        var cursor = 0L
        val states = mutableListOf<RemoteEntityState>()
        for (op in operations) {
            for (change in changes[op.operationId].orEmpty().sortedBy { it.entityType + it.entityId }) {
                val key = change.entityType + "/" + change.entityId
                val version = (versions[key] ?: 0) + 1
                versions[key] = version
                cursor++
                states += RemoteEntityState(change.entityType, change.entityId, version, change.state, change.tombstone, "ana", 0, cursor)
            }
        }
        // O serviço entrega só o estado mais recente de cada entidade, em ordem de cursor.
        val latest = states.groupBy { it.type + "/" + it.id }.values.map { it.maxBy { s -> s.version } }.sortedBy { it.cursor }
        val received = SyncStore(to.database.db).applyPage(PullPage(latest, cursor, hasMore = false))
        assertTrue(received.conflicts == 0)
        return latest.size
    }

    /** A revisão é local ao aparelho e pode diferir; todo o resto precisa ser igual. */
    private fun same(expected: Any?, actual: Any?, message: String = "") {
        fun norm(v: Any?) = v.toString().replace(Regex("revision=[0-9]+"), "revision=_")
        assertEquals(norm(expected), norm(actual), message)
    }

    private suspend fun scenario(h: Handle) {
        val account = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco A"), "Conta A")).applied().changes.single().id
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied()
        val delivery = seed("alimentacao.delivery")
        val pix = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "Delivery", Classification.Category(delivery), d(2026, 10, 22), brl(68))).applied().changes.single().id
        h.space.execute(h.env(), CorrectEntry(pix, description = "Delivery noite")).applied()
        h.space.execute(h.env(), RegisterRefund(pix, brl(8), RefundStatus.Confirmed, d(2026, 10, 25))).applied()
        val other = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "Lanche", Classification.Category(delivery), d(2026, 10, 23), brl(20))).applied().changes.single().id
        h.space.execute(h.env(), DeleteEntry(other)).applied()
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Equipamento", Classification.Category(seed("eletronicos")), d(2026, 9, 25), brl(1200), AmountMeaning.Total, 6, invoice = YearMonth.of(2026, 10)),
        ).applied()
        val boleto = h.space.execute(
            h.env(),
            RegisterBoleto("Condomínio", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.space.execute(h.env(), PayBoleto(boleto, Settlement(d(2026, 11, 3)))).applied()
        val salary = h.space.execute(h.env(), CreateIncomeCategory("Freelas")).applied().changes.single().id
        val income = h.space.execute(h.env(), RegisterIncome("Salário", brl(5000), d(2026, 10, 5), incomeCategoryId = salary)).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(income, Settlement(d(2026, 10, 6)))).applied()
        h.space.execute(h.env(), RenameIncomeCategory(salary, "Trabalho avulso")).applied()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Delivery noite", IconRef(IconSource.Brand, "ifood"))).applied()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Entry, pix, IconRef(IconSource.Generic, "pizza"))).applied()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Academia", IconRef(IconSource.Bank, "60701190"))).applied()
        h.space.execute(h.env(), ClearIconAssociation(IconScope.Name, "Academia")).applied()
        val tv = h.space.execute(h.env(), CreateIntention("Televisão", brl(1200), YearMonth.of(2026, 11), "Sala")).applied().changes.single().id
        h.space.execute(h.env(), EditIntention(tv, "Televisão 55", brl(1100), YearMonth.of(2026, 11), "Sala", expectedRevision = 1)).applied()
        h.space.execute(h.env(), CreateIntention("Viagem")).applied()
        val gym = h.space.execute(
            h.env(),
            CreateRecurrence(
                kind = RecurrenceKind.Expense, description = "Academia", categoryId = seed("saude"), amount = brl(100),
                form = RecurrenceForm.OffCard, frequency = Frequency.Monthly, firstDate = d(2026, 9, 5),
            ),
        ).applied().changes.single().id
        h.space.execute(h.env(), EditRecurrence(gym, 2, RecurrenceScope.OnlyThis, RecurrenceFields(amount = brl(120)))).applied()
        h.space.execute(h.env(), PayOccurrence(gym, 1, Settlement(d(2026, 10, 7)))).applied()
        h.space.execute(h.env(), RegisterInvoiceTotal(YearMonth.of(2026, 10), brl(200))).applied()
        h.space.execute(h.env(), PayInvoice(YearMonth.of(2026, 10), brl(200), d(2026, 10, 15))).applied()
        val pet = h.space.execute(h.env(), CreateCategory("Ração premium", seed("pet"))).applied().changes.single().id
        h.space.execute(
            h.env(),
            DefineLimits(
                YearMonth.of(2026, 10), LimitScope.FromThisMonth,
                listOf(CategoryLimitEdit(seed("alimentacao"), LimitEdit(TotalChange.Set(brl(1200)), mapOf(delivery to brl(400), seed("alimentacao.mercado") to brl(500))))),
            ),
        ).applied()
        assertTrue(pet.isNotBlank())
    }

    @Test
    fun `um aparelho que só recebe os estados chega aos mesmos totais, listas, faturas e limites`() = runBlocking<Unit> {
        val a = open("ana")
        val b = open("ana")
        scenario(a)
        val count = replicate(a, b)
        assertTrue(count >= 12, "esperava estados de vários tipos, vieram $count")

        for (month in months) {
            same(a.space.budget(month).value(), b.space.budget(month).value(), "orçamento de $month")
            same(a.space.limits(month).value(), b.space.limits(month).value(), "limites de $month")
            same(a.space.incomes(month).value(), b.space.incomes(month).value(), "rendas de $month")
            same(a.space.occurrences(month).value(), b.space.occurrences(month).value(), "recorrências de $month")
            same(a.space.commitments(month, CommitmentKind.All).value(), b.space.commitments(month, CommitmentKind.All).value(), "compromissos de $month")
            same(a.space.invoice(month).value(), b.space.invoice(month).value(), "fatura de $month")
        }
        same(a.space.categories().value(), b.space.categories().value())
        same(a.space.incomeCategories().value(), b.space.incomeCategories().value())
        same(a.space.iconAssociations().value(), b.space.iconAssociations().value())
        same(a.space.intentions().value(), b.space.intentions().value())
        assertEquals(2, b.space.intentions().value().size, "intenção com valor e intenção sem valor chegam")
        assertNull(b.space.intentions().value().single { it.name == "Viagem" }.estimatedValue, "sem valor continua sem valor, não zero")
        assertEquals(2, b.space.iconAssociations().value().size, "ícone por nome e por lançamento chegam; o removido não aparece")
        assertTrue(b.space.incomeCategories().value().any { it.name == "Trabalho avulso" }, "categoria criada e renomeada chega ao outro aparelho")
        assertTrue(b.space.incomes(YearMonth.of(2026, 10)).value().items.any { it.incomeCategoryId != null }, "renda categorizada chega com a categoria")
        same(a.space.accounts().value(), b.space.accounts().value())
        same(a.space.card().value(), b.space.card().value())
        same(a.space.invoices().value(), b.space.invoices().value())
        val filter = app.accord.core.space.EntryFilter(months.first(), months.last())
        fun rows(h: Handle) = runBlocking { h.space.entries(filter, pageSize = 500).value().rows.map { Triple(it.id, it.description, it.amount) } }
        assertEquals(rows(a), rows(b))
        assertTrue(rows(b).isNotEmpty())
        assertTrue(b.space.operations().value().isEmpty(), "receber não gera operação de saída")
    }

    @Test
    fun `todo tipo de entidade da fachada tem estado sincronizável`() {
        EntityType.entries.forEach { type -> assertTrue(type.name in app.accord.core.sync.SharedTypes.all) }
    }
}
