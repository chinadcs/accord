package app.accord.persistence

import app.accord.core.chat.CategorySuggester
import app.accord.core.chat.CategorySuggestion
import app.accord.core.chat.CategorySuggestionReason
import app.accord.core.chat.CategorySuggestionRequest
import app.accord.core.importing.BatchSource
import app.accord.core.importing.CategoryAutofill
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-uxmg: o sugeridor roda nas despesas ao iniciar o lote e a primeira escolha fica guardada como decisão de categoria. */
class CategoryAutofillTest : SpaceTestBase() {
    private val groceries get() = seed("alimentacao.mercado")
    private val fuel get() = seed("transporte.combustivel")

    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private fun mv(ordinal: Int, cents: Long, description: String, category: String? = null) = ExtractedMovement(
        ordinal, listOfNotNull("FITID" to "F$ordinal", "TRNAMT" to cents.toString(), category?.let { "CATEGORIA" to it }),
        LocalDate.of(2026, 10, 20), cents, "BRL", description, null, emptySet(),
    )

    private suspend fun Handle.startBatch(vararg movements: ExtractedMovement): String {
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
        val extraction = ExtractionResult(
            "sha-auto", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Account, null, null, null), movements.toList(), 0, emptyList(),
        )
        imports().start(StartImport("b1", "extrato.ofx", 10, extraction, BatchSource(DocumentKind.Account, account))).value()
        return "b1"
    }

    private class Fake(private val answers: Map<String, CategorySuggestion>) : CategorySuggester {
        val asked = mutableListOf<String>()
        override suspend fun suggest(request: CategorySuggestionRequest): CategorySuggestion {
            asked += request.text
            return answers[request.text] ?: CategorySuggestion()
        }
    }

    private fun suggested(vararg ids: String) = CategorySuggestion(ids.toList(), CategorySuggestionReason.Suggested)

    private suspend fun Handle.categoryOf(batch: String, ordinal: Int) =
        imports().review(batch).value()!!.preview.items.first { it.movement.ordinal == ordinal }

    @Test fun `saves the first suggestion of each expense as the category decision, only expenses`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(mv(1, -50_00, "MERCADO CENTRAL"), mv(2, -80_00, "POSTO IPIRANGA"), mv(3, 900_00, "SALARIO"))
        val fake = Fake(mapOf("MERCADO CENTRAL" to suggested(groceries, fuel), "POSTO IPIRANGA" to suggested(fuel), "SALARIO" to suggested(groceries)))

        val operationsBefore = h.space.operations().value().size
        val saved = CategoryAutofill.run(batch, h.imports(), h.space, fake, LocalDate.of(2026, 11, 3))

        assertEquals(2, saved)
        assertEquals(listOf("MERCADO CENTRAL", "POSTO IPIRANGA"), fake.asked, "credit is not asked")
        assertEquals(groceries, h.categoryOf(batch, 1).categoryId)
        assertEquals(fuel, h.categoryOf(batch, 2).categoryId)
        assertNull(h.categoryOf(batch, 3).categoryId, "a credit never gets a category by this path")
        assertEquals(operationsBefore, h.space.operations().value().size, "nothing reaches the budget before confirming")
    }

    @Test fun `an abstention leaves the movement as it was`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(mv(1, -50_00, "COISA ESTRANHA"))
        val saved = CategoryAutofill.run(batch, h.imports(), h.space, Fake(emptyMap()), LocalDate.of(2026, 11, 3))
        assertEquals(0, saved)
        assertNull(h.categoryOf(batch, 1).categoryId)
        assertTrue(h.categoryOf(batch, 1).doubts.isNotEmpty())
    }

    @Test fun `a suggester that fails does not break the import`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(mv(1, -50_00, "MERCADO"))
        val broken = CategorySuggester { error("modelo indisponível") }
        assertEquals(0, CategoryAutofill.run(batch, h.imports(), h.space, broken, LocalDate.of(2026, 11, 3)))
        assertEquals(1, h.imports().review(batch).value()!!.preview.items.size)
    }

    @Test fun `an id outside the catalog is ignored and the person can still change what was saved`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(mv(1, -50_00, "MERCADO"), mv(2, -10_00, "PADARIA"))
        val fake = Fake(mapOf("MERCADO" to suggested("nao-existe"), "PADARIA" to suggested(groceries)))
        assertEquals(1, CategoryAutofill.run(batch, h.imports(), h.space, fake, LocalDate.of(2026, 11, 3)))
        val revision = h.imports().review(batch).value()!!.info.revision
        h.imports().decide(batch, ImportDecision.Category(2, fuel), revision).value()
        assertEquals(fuel, h.categoryOf(batch, 2).categoryId)
    }

    @Test fun `a category written in the file wins over the model, and an unknown one is left to the person`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(
            mv(1, -50_00, "MERCADO", category = "Combustível"),
            mv(2, -10_00, "PADARIA", category = "Pet > Alimentação"),
            mv(3, -20_00, "OUTRA", category = "Categoria que não existe"),
            mv(4, 30_00, "CREDITO", category = "Mercado"),
        )
        val fake = Fake(mapOf("MERCADO" to suggested(groceries), "PADARIA" to suggested(groceries)))

        assertEquals(2, CategoryAutofill.run(batch, h.imports(), h.space, fake, LocalDate.of(2026, 11, 3)))
        assertEquals(fuel, h.categoryOf(batch, 1).categoryId, "the file beats the model")
        assertEquals(seed("pet.alimentacao"), h.categoryOf(batch, 2).categoryId)
        assertNull(h.categoryOf(batch, 3).categoryId)
        assertNull(h.categoryOf(batch, 4).categoryId, "credits ignore the column")
        assertEquals(listOf("OUTRA"), fake.asked, "the model is only asked where the file said nothing usable")
    }

    @Test fun `with no suggester at all the file category is still saved`() = runBlocking<Unit> {
        val h = open()
        val batch = h.startBatch(mv(1, -50_00, "MERCADO", category = "Mercado"), mv(2, -10_00, "OUTRA"))
        assertEquals(1, CategoryAutofill.run(batch, h.imports(), h.space, null, LocalDate.of(2026, 11, 3)))
        assertEquals(groceries, h.categoryOf(batch, 1).categoryId)
        assertNull(h.categoryOf(batch, 2).categoryId)
    }
}
