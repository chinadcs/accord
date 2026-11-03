package app.accord.core.importing

import app.accord.core.CardCycle
import app.accord.core.space.CategorySeed
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-c9m.14: the bank's own category label is a suggestion the person accepts, never a classification (decision of 03/11/2026). */
class BankCategoryHintsTest {
    private val hints = mapOf("SUPERMERCADO" to "cat-mercado", "SAUDE" to "cat-saude")

    private fun card(vararg hintLabels: String?) = ExtractionResult(
        "sha", DeclaredMetadata(SourceFormat.Pdf, "itau-cartao-fatura-v1", "BRL", null, null, DocumentKind.Card, null, null, null),
        hintLabels.mapIndexed { i, label ->
            ExtractedMovement(
                i + 1, listOfNotNull("LINE" to "x", label?.let { BankCategoryHints.FIELD to it }), LocalDate.of(2026, 10, 7 + i), -1000L - i, "BRL", "LOJA $i", null, emptySet(),
            )
        },
        0, emptyList(),
    )

    private val billing = BillingContext(CardCycle(8, 15), java.time.YearMonth.of(2026, 11), "fees")

    private fun run(e: ExtractionResult, decisions: Decisions = Decisions(), known: KnownState = KnownState()) =
        Reconciler.preview("b1", "card", e, known, decisions.copy(bankCategoryHints = hints), billing = billing)

    @Test fun `a bank label with a counterpart is only a suggestion on a doubt and nothing is classified`() {
        val item = run(card("supermercado")).items.single()
        assertEquals(Classification.Doubt, item.classification)
        assertTrue(DoubtReason.CategoryUnknown in item.doubts)
        assertEquals("cat-mercado", item.suggestedCategoryId)
        assertEquals(SuggestionSource.BankLabel, item.suggestionSource)
        assertNull(item.categoryId, "nothing is applied before the person accepts")
        assertNull(item.effect)
    }

    @Test fun `labels are compared without accents or case`() {
        assertEquals("cat-saude", run(card("saúde")).items.single().suggestedCategoryId)
        assertEquals("cat-saude", run(card("SAÚDE")).items.single().suggestedCategoryId)
    }

    @Test fun `a label that says nothing or has no counterpart makes no suggestion`() {
        val items = run(card("outros", "RETAIL", null)).items
        assertTrue(items.all { it.suggestedCategoryId == null && it.suggestionSource == null })
    }

    @Test fun `the history of the couple wins over the bank label`() {
        val item = run(card("supermercado"), Decisions(categorySuggestions = mapOf("LOJA 0" to "cat-casa"))).items.single()
        assertEquals("cat-casa", item.suggestedCategoryId)
        assertEquals(SuggestionSource.History, item.suggestionSource)
    }

    @Test fun `accepting the suggestion is the person's decision and then the movement is new`() {
        val e = card("supermercado")
        val key = Reconciler.key("card", e.movements.single(), e.movements)
        val accepted = run(e, Decisions(categoryByKey = mapOf(key to "cat-mercado"))).items.single()
        assertEquals(Classification.New, accepted.classification)
        assertEquals("cat-mercado", accepted.categoryId)
        assertNull(accepted.suggestionSource, "a chosen category is not a suggestion any more")
    }

    @Test fun `without a matching category in the space there is no suggestion`() {
        val item = Reconciler.preview("b1", "card", card("supermercado"), KnownState(), Decisions(), billing = billing).items.single()
        assertNull(item.suggestedCategoryId)
    }

    @Test fun `every mapped key is an initial category of the app`() {
        val keys = CategorySeed.items.map { it.key }.toSet()
        assertTrue(BankCategoryHints.seedKeyByLabel.values.all { it in keys }, "a typo in the mapping would silently suggest nothing")
    }

    @Test fun `the PDF layout keeps the bank label so the suggestion can use it`() {
        val text = PdfText(
            listOf(
                listOf(
                    "Resumo da fatura em R$", "Vencimento: 15/11/2026", "Emissão: 08/11/2026", "Banco Itaú S.A.",
                    "Lançamentos: compras e saques", "DATA ESTABELECIMENTO VALOR EM R$", "20/10 MERCADO BETA 50,50", "supermercado  BELO HORIZONT",
                    "Lançamentos no cartão 50,50", "Total dos lançamentos atuais 50,50",
                ),
            ),
        )
        val m = (PdfLayoutReader.read("%PDF".toByteArray(), { PdfExtraction.Text(text) }) as ReadOutcome.Extracted).result.movements.single()
        assertEquals("SUPERMERCADO", BankCategoryHints.labelOf(m))
    }
}
