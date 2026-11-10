package app.accord.persistence

import app.accord.core.Money
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.importing.BatchSource
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Classification
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.KnownNature
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-j0s.2: as previsões do mês pela fachada. Hoje é 24/10/2026 (relógio fixo da base de teste); fechamento 8, vencimento 15. */
class ForecastTest : SpaceTestBase() {
    private val food get() = seed("alimentacao")
    private val groceries get() = seed("alimentacao.mercado")
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.pay(date: java.time.LocalDate, reais: Long, category: String = groceries, kind: DirectPaymentKind = DirectPaymentKind.Cash) =
        space.execute(env(), RegisterDirectPayment(kind, "gasto", Classification.Category(category), date, Money(reais * 100))).applied().changes.single().id

    private suspend fun Handle.cardOnce(date: java.time.LocalDate, reais: Long, category: String = groceries) =
        space.execute(env(), RegisterCardPurchase("compra", Classification.Category(category), date, Money(reais * 100))).applied().changes.single().id

    /** Julho a setembro de gastos à vista na categoria: R$ 400 + R$ 500 + R$ 480 em 92 dias. O 1º de julho cobre o mês inteiro. */
    private suspend fun Handle.history() {
        pay(d(2026, 7, 1), 400)
        pay(d(2026, 8, 10), 500)
        pay(d(2026, 9, 10), 480)
    }

    private suspend fun Handle.category(month: java.time.YearMonth, id: String = food) =
        space.forecasts(month).value().categories.single { it.categoryId == id }

    @Test fun `the estimate of the current month is the historical pace for the days left`() = runBlocking<Unit> {
        val h = open()
        h.history()
        h.pay(d(2026, 10, 5), 300)
        val c = h.category(oct)
        // R$ 1.380 em 92 dias × 8 dias restantes (24 a 31/10, hoje incluído) = R$ 120.
        assertEquals(30_000, c.knownCents)
        assertEquals(Estimate.Value(12_000), c.estimate)
        assertEquals(42_000, c.totalCents)
        assertEquals(Excess.NoLimit, c.excess)
    }

    @Test fun `known always equals the net of the category in the month budget`() = runBlocking<Unit> {
        val h = open()
        h.history()
        h.pay(d(2026, 10, 5), 300)
        h.cardOnce(d(2026, 10, 12), 100)
        h.space.execute(h.env(), RegisterCardPurchase("tv", Classification.Category(groceries), d(2026, 10, 12), brl(300), amountMeaning = AmountMeaning.Total, installments = 3)).applied()
        h.space.execute(h.env(), RegisterBoleto("conta", Classification.Category(groceries), d(2026, 11, 3), brl(80))).applied()
        for (month in listOf(oct, nov, ym(2026, 9))) {
            val budget = h.space.budget(month).value()
            val forecast = h.space.forecasts(month).value()
            budget.categories.forEach { b -> assertEquals(b.net.cents, forecast.categories.single { it.categoryId == b.categoryId }.knownCents, "$month ${b.categoryId}") }
        }
    }

    @Test fun `a purchase on the card feeds the card channel and the next invoice carries its own pace`() = runBlocking<Unit> {
        val h = open()
        h.history()
        // Faturas de agosto, setembro e outubro (9/7 a 8/10, o dia do fechamento entra na que fecha): R$ 200 + R$ 300 + R$ 270 em 92 dias.
        h.cardOnce(d(2026, 7, 20), 200)
        h.cardOnce(d(2026, 8, 20), 300)
        h.cardOnce(d(2026, 9, 20), 270)
        h.cardOnce(d(2026, 10, 12), 100)
        val c = h.category(nov)
        assertEquals(10_000, c.knownCents, "a compra de 12/10 está na fatura que vence em novembro")
        // Cartão: R$ 770 em 92 dias × 16 dias (24/10 a 8/11) = 133,91. Demais meios: R$ 1.380 em 92 dias × 30 dias de novembro = R$ 450.
        val card = c.explanation.single { it.channel == app.accord.core.forecast.Channel.Card }
        val other = c.explanation.single { it.channel == app.accord.core.forecast.Channel.Other }
        assertEquals(13_391, card.estimateCents)
        assertEquals(45_000, other.estimateCents)
        assertEquals(Estimate.Value(58_391), c.estimate)
        assertEquals(68_391, c.totalCents)
    }

    @Test fun `installments, boletos, fees and what is still to classify never feed the pace`() = runBlocking<Unit> {
        val h = open()
        h.history()
        val base = h.category(oct).explanation.single { it.channel == app.accord.core.forecast.Channel.Other }.estimateCents
        h.space.execute(h.env(), RegisterCardPurchase("tv", Classification.Category(groceries), d(2026, 9, 3), brl(900), amountMeaning = AmountMeaning.Total, installments = 3)).applied()
        h.space.execute(h.env(), RegisterBoleto("conta", Classification.Category(groceries), d(2026, 9, 5), brl(700))).applied()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "juros", Classification.Category(seed("servicos.tarifas-e-juros")), d(2026, 9, 9), brl(600))).applied()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "sem categoria", Classification.ToClassify, d(2026, 9, 11), brl(500))).applied()
        assertEquals(base, h.category(oct).explanation.single { it.channel == app.accord.core.forecast.Channel.Other }.estimateCents)
    }

    @Test fun `the limit turns the total into a possible excess and a month already over shows only the known`() = runBlocking<Unit> {
        val h = open()
        h.history()
        h.pay(d(2026, 10, 5), 300)
        h.space.execute(
            h.env(),
            app.accord.core.space.DefineLimits(
                oct, app.accord.core.LimitScope.OnlyThisMonth,
                listOf(app.accord.core.space.CategoryLimitEdit(food, app.accord.core.LimitEdit(total = app.accord.core.TotalChange.Set(brl(400))))),
            ),
        ).applied()
        assertEquals(Excess.Possible(2_000), h.category(oct).excess)
        val september = h.category(ym(2026, 9))
        assertEquals(Estimate.NotApplicable, september.estimate)
        assertEquals(48_000, september.knownCents)
        assertEquals(Excess.NoLimit, september.excess)
    }

    @Test fun `a space that began recently has no complete period and says so`() = runBlocking<Unit> {
        val h = open()
        h.pay(d(2026, 10, 5), 300)
        val c = h.category(oct)
        assertTrue(c.estimate is Estimate.Unavailable)
        assertEquals(30_000, c.totalCents)
        assertTrue(ConfidenceReason.InsufficientHistory in (c.confidence as Confidence.Low).reasons)
    }

    @Test fun `entries to classify are shown apart and lower the confidence of every category`() = runBlocking<Unit> {
        val h = open()
        h.history()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "sem categoria", Classification.ToClassify, d(2026, 10, 6), brl(50))).applied()
        val forecast = h.space.forecasts(oct).value()
        assertEquals(1, forecast.toClassify.count)
        assertEquals(5_000, forecast.toClassify.cents)
        assertEquals(0, forecast.categories.single { it.categoryId == food }.knownCents, "a classificar não entra em nenhuma categoria")
        forecast.categories.forEach { assertTrue(ConfidenceReason.ToClassifyInPeriod in (it.confidence as Confidence.Low).reasons, it.categoryId) }
    }

    @Test fun `an invoice link to confirm lowers only the confidence of its own category`() = runBlocking<Unit> {
        val h = open()
        h.history()
        // Compra na véspera do fechamento (7/10): fatura estimada, a conferir.
        h.cardOnce(d(2026, 10, 7), 80)
        val forecast = h.space.forecasts(oct).value()
        assertTrue(ConfidenceReason.OpenPendings in (forecast.categories.single { it.categoryId == food }.confidence as Confidence.Low).reasons)
        val other = forecast.categories.first { it.categoryId != food }
        assertTrue(ConfidenceReason.OpenPendings !in ((other.confidence as? Confidence.Low)?.reasons.orEmpty()))
    }

    @Test fun `the detail adds up to the known and separates the natures`() = runBlocking<Unit> {
        val h = open()
        h.pay(d(2026, 10, 5), 300)
        h.cardOnce(d(2026, 10, 3), 100)
        h.space.execute(h.env(), RegisterCardPurchase("tv", Classification.Category(groceries), d(2026, 9, 3), brl(300), amountMeaning = AmountMeaning.Total, installments = 3)).applied()
        h.space.execute(h.env(), RegisterBoleto("conta", Classification.Category(groceries), d(2026, 10, 20), brl(80))).applied()
        val detail = assertNotNull(h.space.forecastDetail(food, oct).value())
        assertEquals(detail.forecast.knownCents, detail.known.sumOf { it.cents })
        assertEquals(40_000, detail.known.single { it.nature == KnownNature.Realized }.cents)
        assertEquals(10_000, detail.known.single { it.nature == KnownNature.Installments }.cents)
        assertEquals(8_000, detail.known.single { it.nature == KnownNature.Boletos }.cents)
        assertEquals(detail.forecast.knownCents, detail.bySubcategory.values.sumOf { it.cents })
        assertEquals("forecast-v1", detail.ruleVersion)
    }

    @Test fun `a confirmed refund appears as a negative line and lowers the known`() = runBlocking<Unit> {
        val h = open()
        val purchase = h.pay(d(2026, 10, 5), 300)
        h.space.execute(h.env(), app.accord.core.space.RegisterRefund(purchase, Money(5_000), app.accord.core.space.RefundStatus.Confirmed, d(2026, 10, 9))).applied()
        val detail = assertNotNull(h.space.forecastDetail(food, oct).value())
        assertEquals(-5_000, detail.known.single { it.nature == KnownNature.Credits }.cents)
        assertEquals(25_000, detail.forecast.knownCents)
    }

    @Test fun `a subcategory or unknown id has no forecast detail`() = runBlocking<Unit> {
        val h = open()
        assertNull(h.space.forecastDetail(groceries, oct).value())
        assertNull(h.space.forecastDetail("nao-existe", oct).value())
    }

    // ---- accord-j0s.7: marca de encargo e cobertura ----

    @Test fun `a direct payment marked as charge never feeds the pace, whatever the category is called`() = runBlocking<Unit> {
        val h = open()
        h.history()
        val other = app.accord.core.forecast.Channel.Other
        val base = h.category(oct).explanation.single { it.channel == other }.estimateCents
        // Encargo numa categoria que não é a semente de tarifas: só a marca o tira do ritmo.
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "multa", Classification.Category(groceries), d(2026, 9, 9), brl(600), isCharge = true)).applied()
        assertEquals(base, h.category(oct).explanation.single { it.channel == other }.estimateCents)
        // Sem a marca, o mesmo gasto entra no ritmo.
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "mercado", Classification.Category(groceries), d(2026, 9, 9), brl(600))).applied()
        assertTrue(h.category(oct).explanation.single { it.channel == other }.estimateCents!! > base!!)
    }

    @Test fun `a charge without category goes to services fees and interest and is not left to classify`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Cash, "juros", Classification.ToClassify, d(2026, 10, 6), brl(15), isCharge = true)).applied().changes.single().id
        val forecast = h.space.forecasts(oct).value()
        assertEquals(0, forecast.toClassify.count)
        val services = h.category(oct, seed("servicos"))
        assertEquals(1_500, services.knownCents)
        val entry = h.space.entry(id).value()
        assertNotNull(entry)
    }

    private fun mv(ordinal: Int, date: java.time.LocalDate, cents: Long, description: String) =
        ExtractedMovement(ordinal, listOf("FITID" to "F$ordinal", "TRNAMT" to cents.toString()), date, cents, "BRL", description, null, emptySet())

    /** Lote de extrato confirmado em que só o primeiro movimento é claro: o segundo fica como dúvida aberta. */
    private suspend fun Handle.confirmedBatchWithOpenDoubt(id: String, first: java.time.LocalDate, second: java.time.LocalDate) {
        val importing = (space as RoomFinanceSpace).importSession()
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
        val extraction = ExtractionResult(
            "sha-$id",
            DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Account, null, null, null),
            listOf(mv(1, first, -1234, "MERCADO"), mv(2, second, -900, "PADARIA")),
            0,
            emptyList(),
        )
        val info = (importing.start(StartImport(id, "$id.ofx", 10, extraction, BatchSource(DocumentKind.Account, account))).value() as StartOutcome.Started).info
        importing.decide(id, ImportDecision.Category(1, groceries), info.revision)
        val review = checkNotNull(importing.review(id).value())
        assertIs<ConfirmOutcome.Applied>(importing.confirm(id, "c-$id", review.token).value())
    }

    private fun ConfidenceReason.isIn(c: app.accord.core.forecast.CategoryForecast) = (c.confidence as? Confidence.Low)?.reasons?.contains(this) == true

    @Test fun `an open doubt in a confirmed batch that touches the month makes the coverage incomplete`() = runBlocking<Unit> {
        val h = open()
        h.history()
        h.confirmedBatchWithOpenDoubt("b1", d(2026, 10, 3), d(2026, 10, 4))
        assertTrue(ConfidenceReason.IncompleteCoverage.isIn(h.category(oct)))
        // O mesmo lote não toca setembro: a cobertura de lá não é afetada por ele.
        assertFalse(ConfidenceReason.IncompleteCoverage.isIn(h.category(ym(2026, 7))))
    }

    @Test fun `without any import the coverage is not flagged, since no known gap exists`() = runBlocking<Unit> {
        val h = open()
        h.history()
        assertFalse(ConfidenceReason.IncompleteCoverage.isIn(h.category(oct)))
    }
}
