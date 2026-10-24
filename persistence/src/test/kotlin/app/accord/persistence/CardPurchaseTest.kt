package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfigureCard
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.Decision
import app.accord.core.space.ErrorCode
import app.accord.core.space.PreviewResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardPurchaseTest : SpaceTestBase() {
    private fun purchase(
        date: LocalDate,
        amount: Money = brl(100),
        category: String = "compras",
        installments: Int = 1,
        meaning: AmountMeaning? = null,
        invoice: YearMonth? = null,
        description: String = "Compra",
        classification: Classification = Classification.Category(seed(category)),
        isCharge: Boolean = false,
    ) = RegisterCardPurchase(
        description = description,
        classification = classification,
        date = date,
        amount = amount,
        amountMeaning = meaning,
        installments = installments,
        isCharge = isCharge,
        invoice = invoice,
    )

    private suspend fun Handle.register(command: RegisterCardPurchase) =
        space.execute(env(), command).applied().changes.single().id

    private suspend fun Handle.detail(id: String) = space.purchase(id).value()!!

    // ---- parcelas e meses (cenário 1) ----

    @Test
    fun `R$ 1200 em 6x com primeira fatura em outubro ocupa R$ 200 de outubro a março e nada em abril`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(
            purchase(d(2026, 9, 25), brl(1200), "eletronicos.equipamentos", 6, AmountMeaning.Total, invoice = ym(2026, 10)),
        )
        val months = listOf(ym(2026, 10), ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3))
        for (m in months) assertEquals(brl(200), h.space.budget(m).value().committed, "mês $m")
        assertEquals(Money.ZERO, h.space.budget(ym(2027, 4)).value().committed)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 9)).value().committed)

        val detail = h.detail(id)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), detail.installments.map { it.number })
        assertEquals(months, detail.installments.map { it.invoiceMonth })
        assertEquals(brl(1200), detail.total)
        assertEquals(d(2026, 9, 25), detail.purchaseDate)
    }

    @Test
    fun `parcelas pagas pela fatura não contam como pagas até a quitação`() = runBlocking<Unit> {
        val h = open()
        h.register(purchase(d(2026, 9, 25), brl(1200), installments = 6, meaning = AmountMeaning.Total, invoice = ym(2026, 10)))
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(200), b.committed)
        assertEquals(Money.ZERO, b.paid)
    }

    @Test
    fun `R$ 100 em 3x vira 33,33 33,33 e 33,34`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 5), brl(100), installments = 3, meaning = AmountMeaning.Total))
        assertEquals(listOf(3_333L, 3_333L, 3_334L), h.detail(id).installments.map { it.amount.cents })
        assertEquals(brl(100), h.detail(id).total)
    }

    @Test
    fun `valor da parcela informado gera total parcela vezes quantidade`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 5), brl(200), installments = 6, meaning = AmountMeaning.PerInstallment))
        assertEquals(brl(1200), h.detail(id).total)
        assertTrue(h.detail(id).installments.all { it.amount == brl(200) })
    }

    @Test
    fun `parcelado sem dizer se o valor é total ou da parcela pede decisão e não grava`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), purchase(d(2026, 10, 5), brl(1200), installments = 6))
        val decision = assertIs<CommandResult.DecisionNeeded>(r).decision
        assertEquals(Decision.AmountMeaningRequired(6, brl(1200)), decision)
        assertEquals(emptyList(), h.space.operations().value())
    }

    @Test
    fun `a prévia mostra o cronograma antes de salvar e não grava nada`() = runBlocking<Unit> {
        val h = open()
        val p = h.space.preview(purchase(d(2026, 10, 5), brl(1200), installments = 6, meaning = AmountMeaning.Total))
        val ready = assertIs<PreviewResult.Ready>(p)
        assertEquals(emptyList(), ready.before)
        assertEquals(6, ready.after.size)
        assertEquals(setOf(ym(2026, 10)), setOf(ready.after.first().invoiceMonth))
        assertEquals(6, ready.affectedMonths.size)
        assertEquals(emptyList(), h.space.operations().value())
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
    }

    // ---- estimativa e janela a conferir (cenário 2) ----

    @Test
    fun `fechamento 8 vencimento 15 estima a fatura e marca só a janela do fechamento`() = runBlocking<Unit> {
        val h = open()
        val cases = listOf(
            Triple(d(2026, 10, 5), ym(2026, 10), AssignmentState.Estimated),
            Triple(d(2026, 10, 7), ym(2026, 10), AssignmentState.ToConfirm),
            Triple(d(2026, 10, 8), ym(2026, 11), AssignmentState.ToConfirm),
            Triple(d(2026, 10, 9), ym(2026, 11), AssignmentState.ToConfirm),
            Triple(d(2026, 10, 22), ym(2026, 11), AssignmentState.Estimated),
        )
        for ((date, month, state) in cases) {
            val first = h.detail(h.register(purchase(date))).installments.single()
            assertEquals(month, first.invoiceMonth, "fatura de $date")
            assertEquals(state, first.assignment, "estado de $date")
        }
        assertEquals(1, h.space.budget(ym(2026, 10)).value().invoiceLinksToConfirm)
        assertEquals(2, h.space.budget(ym(2026, 11)).value().invoiceLinksToConfirm)
    }

    @Test
    fun `fatura informada pela pessoa fica confirmada sem selo`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 8), invoice = ym(2026, 10)))
        val first = h.detail(id).installments.single()
        assertEquals(ym(2026, 10), first.invoiceMonth)
        assertEquals(AssignmentState.Confirmed, first.assignment)
        assertEquals(0, h.space.budget(ym(2026, 10)).value().invoiceLinksToConfirm)
    }

    @Test
    fun `numa parcelada o selo vale só para a primeira parcela`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 8), brl(300), installments = 3, meaning = AmountMeaning.Total))
        val states = h.detail(id).installments.map { it.assignment }
        assertEquals(listOf(AssignmentState.ToConfirm, AssignmentState.Estimated, AssignmentState.Estimated), states)
    }

    // ---- corrigir fatura (3, 3b) ----

    @Test
    fun `corrigir compra a conferir de novembro para outubro move o valor, mantém a identidade e fecha a pendência`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 8), brl(100)))
        assertEquals(brl(100), h.space.budget(ym(2026, 11)).value().committed)

        h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, ym(2026, 10))).applied()

        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        val detail = h.detail(id)
        assertEquals(d(2026, 10, 8), detail.purchaseDate)
        assertEquals(AssignmentState.Confirmed, detail.installments.single().assignment)
        assertEquals(0, h.space.budget(ym(2026, 10)).value().invoiceLinksToConfirm)
    }

    @Test
    fun `corrigir a fatura da 1ª parcela de outubro para novembro desloca as seis e a prévia mostra antes e depois`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(
            purchase(d(2026, 9, 25), brl(1200), installments = 6, meaning = AmountMeaning.Total, invoice = ym(2026, 10)),
        )
        val command = CorrectInvoiceAssignment(id, 1, ym(2026, 11))

        val ready = assertIs<PreviewResult.Ready>(h.space.preview(command))
        assertEquals(ym(2026, 10), ready.before.first().invoiceMonth)
        assertEquals(ym(2026, 11), ready.after.first().invoiceMonth)
        assertEquals(ym(2027, 4), ready.after.last().invoiceMonth)
        assertEquals(ym(2027, 3), h.detail(id).installments.last().invoiceMonth) // prévia não grava

        h.space.execute(h.env(), command).applied()
        val after = h.detail(id)
        assertEquals(
            listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3), ym(2027, 4)),
            after.installments.map { it.invoiceMonth },
        )
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(brl(200), h.space.budget(ym(2027, 4)).value().committed)
        assertEquals(brl(200), h.space.budget(ym(2027, 1)).value().committed)
        assertEquals(id, after.id)
        assertEquals(6, after.installments.size)
    }

    @Test
    fun `só a primeira parcela muda de fatura — parcela do meio é rejeitada`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 9, 25), brl(1200), installments = 6, meaning = AmountMeaning.Total, invoice = ym(2026, 10)))
        val r = h.space.execute(h.env(), CorrectInvoiceAssignment(id, 3, ym(2027, 1)))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(r).errors.single().code)
        assertEquals(ym(2026, 12), h.detail(id).installments[2].invoiceMonth)
    }

    @Test
    fun `corrigir com versão antiga devolve a versão atual e não altera`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 8)))
        val current = h.detail(id).revision
        val r = h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, ym(2026, 10), expectedRevision = current - 1))
        val stale = assertIs<CommandResult.StaleVersion>(r)
        assertEquals(current, stale.currentRevision)
        assertEquals(ym(2026, 11), h.detail(id).installments.single().invoiceMonth)
    }

    @Test
    fun `corrigir a fatura gera uma operação com a compra e revisão seguinte`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 8)))
        val receipt = h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, ym(2026, 10))).applied()
        val change = receipt.changes.single()
        assertEquals(id, change.id)
        assertEquals(2L, change.revision)
        assertEquals(setOf(ym(2026, 10), ym(2026, 11)), receipt.affectedMonths)
    }

    // ---- cartão: um só, dias configuráveis (3c, 16a, 20) ----

    @Test
    fun `configurar o cartão duas vezes atualiza o mesmo cartão`() = runBlocking<Unit> {
        val h = open()
        val first = h.space.card().value()
        assertEquals(8, first.closingDay)
        assertEquals(15, first.dueDay)
        h.space.execute(h.env(), ConfigureCard(BankRef.Catalog("60701190", "Itaú Unibanco"), "Cartão Ana", 5, 12)).applied()
        h.space.execute(h.env(), ConfigureCard(BankRef.Catalog("60701190", "Itaú Unibanco"), "Cartão da casa", 6, 13)).applied()
        val card = h.space.card().value()
        assertEquals(first.id, card.id)
        assertEquals("Cartão da casa", card.nickname)
        assertEquals(6, card.closingDay)
    }

    @Test
    fun `mudar o fechamento não altera atribuições existentes mas vale para as novas`() = runBlocking<Unit> {
        val h = open()
        val old = h.register(purchase(d(2026, 10, 22)))
        assertEquals(ym(2026, 11), h.detail(old).installments.single().invoiceMonth)

        h.space.execute(h.env(), ConfigureCard(null, "Cartão", 25, 5)).applied()

        assertEquals(ym(2026, 11), h.detail(old).installments.single().invoiceMonth)
        // com fechamento 25 e vencimento 5, uma compra de 22/10 vai para a fatura que vence em 05/11
        val fresh = h.register(purchase(d(2026, 10, 10)))
        assertEquals(ym(2026, 11), h.detail(fresh).installments.single().invoiceMonth)
        val fresh2 = h.register(purchase(d(2026, 10, 25)))
        assertEquals(ym(2026, 12), h.detail(fresh2).installments.single().invoiceMonth)
    }

    @Test
    fun `fechamento 31 vencimento 10 usa o último dia dos meses curtos`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(h.env(), ConfigureCard(null, "Cartão", 31, 10)).applied()
        val a29 = h.detail(h.register(purchase(d(2026, 11, 29)))).installments.single()
        val a30 = h.detail(h.register(purchase(d(2026, 11, 30)))).installments.single()
        val a25 = h.detail(h.register(purchase(d(2026, 11, 25)))).installments.single()
        assertEquals(ym(2026, 12), a29.invoiceMonth)
        assertEquals(AssignmentState.ToConfirm, a29.assignment)
        assertEquals(ym(2027, 1), a30.invoiceMonth)
        assertEquals(AssignmentState.ToConfirm, a30.assignment)
        assertEquals(ym(2026, 12), a25.invoiceMonth)
        assertEquals(AssignmentState.Estimated, a25.assignment)
    }

    @Test
    fun `dias do cartão fora de 1 a 31 são rejeitados por campo`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), ConfigureCard(null, "Cartão", 0, 32))
        val errors = assertIs<CommandResult.Rejected>(r).errors.associate { it.field to it.code }
        assertEquals(ErrorCode.OutOfRange, errors["closingDay"])
        assertEquals(ErrorCode.OutOfRange, errors["dueDay"])
        assertEquals(8, h.space.card().value().closingDay)
    }

    // ---- encargo (9) ----

    @Test
    fun `tarifa confirmada sem categoria vai para Serviços Tarifas e juros e entra uma vez`() = runBlocking<Unit> {
        val h = open()
        h.register(purchase(d(2026, 10, 5), brl(1000), category = "compras", invoice = ym(2026, 10)))
        h.register(
            purchase(
                d(2026, 10, 5), brl(15), invoice = ym(2026, 10), isCharge = true,
                classification = Classification.ToClassify, description = "Anuidade",
            ),
        )
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(1015), b.committed)
        val servicos = b.categories.single { it.categoryId == seed("servicos") }
        assertEquals(brl(15), servicos.gross)
        assertEquals(brl(15), servicos.bySubcategory[seed("servicos.tarifas-e-juros")])
        assertEquals(Money.ZERO, b.toClassify.amount)
    }

    // ---- a classificar (30, 31) ----

    @Test
    fun `Amazon R$ 150 sem categoria entra no total de novembro numa linha própria e não altera categoria`() = runBlocking<Unit> {
        val h = open()
        val receipt = h.space.execute(
            h.env(),
            purchase(d(2026, 10, 22), brl(150), classification = Classification.ToClassify, description = "Amazon"),
        ).applied()
        val b = h.space.budget(ym(2026, 11)).value()
        assertEquals(brl(150), b.committed)
        assertEquals(brl(150), b.toClassify.amount)
        assertEquals(1, b.toClassify.count)
        assertTrue(b.categories.all { it.gross == Money.ZERO })
        assertEquals(1, h.space.operations().value().count { it.operationId == receipt.operationId })
    }

    @Test
    fun `classificar em Pet move o valor para Pet com total geral inalterado, mesma identidade`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 22), brl(150), classification = Classification.ToClassify, description = "Amazon"))
        val before = h.space.budget(ym(2026, 11)).value().committed

        h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("pet")))).applied()

        val b = h.space.budget(ym(2026, 11)).value()
        assertEquals(before, b.committed)
        assertEquals(Money.ZERO, b.toClassify.amount)
        assertEquals(brl(150), b.categories.single { it.categoryId == seed("pet") }.gross)
        assertEquals(id, h.detail(id).id)
        assertEquals(Classification.Category(seed("pet")), h.detail(id).classification)
    }

    @Test
    fun `classificar parcelada move as seis parcelas de uma vez`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(
            purchase(d(2026, 10, 22), brl(1200), classification = Classification.ToClassify, installments = 6, meaning = AmountMeaning.Total),
        )
        assertEquals(brl(200), h.space.budget(ym(2026, 12)).value().toClassify.amount)

        h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("eletronicos")))).applied()

        for (m in listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3), ym(2027, 4))) {
            val b = h.space.budget(m).value()
            assertEquals(Money.ZERO, b.toClassify.amount, "a classificar em $m")
            assertEquals(brl(200), b.categories.single { it.categoryId == seed("eletronicos") }.gross, "eletrônicos em $m")
        }
    }

    @Test
    fun `classificar com versão antiga devolve versão atual`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 22), classification = Classification.ToClassify))
        val r = h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("pet")), expectedRevision = 99))
        assertIs<CommandResult.StaleVersion>(r)
        assertEquals(Classification.ToClassify, h.detail(id).classification)
    }

    @Test
    fun `classificar lançamento que já tem categoria é rejeitado`() = runBlocking<Unit> {
        val h = open()
        val id = h.register(purchase(d(2026, 10, 22)))
        val r = h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("pet"))))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(r).errors.single().code)
    }

    // ---- desfazer ----

    @Test
    fun `desfazer compra parcelada a classificar retira todas as parcelas dos totais`() = runBlocking<Unit> {
        val h = open()
        val receipt = h.space.execute(
            h.env(),
            purchase(d(2026, 10, 22), brl(1200), classification = Classification.ToClassify, installments = 6, meaning = AmountMeaning.Total),
        ).applied()
        assertTrue(receipt.undoable)
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        for (m in listOf(ym(2026, 11), ym(2027, 4))) {
            assertEquals(Money.ZERO, h.space.budget(m).value().committed)
        }
        assertTrue(h.detail(receipt.changes.single().id).voided)
    }

    @Test
    fun `desfazer depois de classificar é rejeitado e preserva a edição posterior`() = runBlocking<Unit> {
        val h = open()
        val receipt = h.space.execute(h.env(), purchase(d(2026, 10, 22), classification = Classification.ToClassify)).applied()
        val id = receipt.changes.single().id
        h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("pet")))).applied()
        val r = h.space.execute(h.env(), Undo(receipt.operationId))
        assertEquals(ErrorCode.ChangedAfter, assertIs<CommandResult.Rejected>(r).errors.single().code)
        assertFalse(h.detail(id).voided)
        assertNotEquals(Classification.ToClassify, h.detail(id).classification)
    }

    // ---- validação ----

    @Test
    fun `valor menor que um centavo por parcela e dados inválidos são rejeitados`() = runBlocking<Unit> {
        val h = open()
        val tiny = h.space.execute(h.env(), purchase(d(2026, 10, 5), Money(2), installments = 3, meaning = AmountMeaning.Total))
        assertEquals("amount", assertIs<CommandResult.Rejected>(tiny).errors.single().field)

        val blank = h.space.execute(h.env(), purchase(d(2026, 10, 5), description = " ", installments = 0))
        val fields = assertIs<CommandResult.Rejected>(blank).errors.map { it.field }.toSet()
        assertEquals(setOf("description", "installments"), fields)

        val unknown = h.space.execute(h.env(), purchase(d(2026, 10, 5), classification = Classification.Category("nada")))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
        assertEquals(emptyList(), h.space.operations().value())
    }

    @Test
    fun `mesmo ID de comando não duplica a compra nem as parcelas`() = runBlocking<Unit> {
        val h = open()
        val env = h.env()
        val first = h.space.execute(env, purchase(d(2026, 10, 5), brl(300), installments = 3, meaning = AmountMeaning.Total)).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(env, purchase(d(2026, 10, 5), brl(300), installments = 3, meaning = AmountMeaning.Total)))
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(1, h.space.operations().value().count { it.operationId == first.operationId })
    }

    @Test
    fun `falha na gravação não deixa compra, parcelas nem operação`() = runBlocking<Unit> {
        val h = open()
        val env = h.env()
        h.fault = { error("falha injetada") }
        runCatching { h.space.execute(env, purchase(d(2026, 10, 5), brl(300), installments = 3, meaning = AmountMeaning.Total)) }
        h.fault = null
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(emptyList(), h.space.operations().value())
        h.space.execute(env, purchase(d(2026, 10, 5), brl(300), installments = 3, meaning = AmountMeaning.Total)).applied()
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
    }
}
