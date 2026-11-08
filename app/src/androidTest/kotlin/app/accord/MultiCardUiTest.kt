package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.accord.core.Money
import app.accord.core.space.*
import app.accord.ui.card.*
import app.accord.ui.theme.AccordTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class MultiCardUiTest {
    @get:Rule val compose = createComposeRule()
    private val provider get() = ApplicationProvider.getApplicationContext<AccordApplication>().container
    private fun execute(command: Command) = runBlocking {
        (provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString())), command) as CommandResult.Applied).receipt
    }
    private fun cards() = runBlocking { (provider.space().cards() as QueryResult.Data).value }

    @Test fun createSelectAndDefaultUseTheRealCardEditor() {
        val tag = UUID.randomUUID().toString().take(6)
        val first = execute(ConfigureCard(BankRef.Other("Banco UI"), "Primeiro $tag", 8, 15)).changes.single { it.type == EntityType.Card }.id
        val vm = CardViewModel(provider, SavedStateHandle())
        compose.setContent { AccordTheme { CardRoute(vm, PaddingValues(), {}, { _, _ -> }, {}) } }
        compose.waitUntil(10_000) { vm.content.value is CardContent.Ready }
        compose.onNodeWithText("Adicionar cartão").performScrollTo().performClick()
        compose.runOnIdle { vm.change { it.copy(bank = BankRef.Other("Banco UI 2")) } }
        fun fill(resource: Int, text: String) {
            val label = ApplicationProvider.getApplicationContext<AccordApplication>().getString(resource)
            compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
        }
        fill(R.string.card_nickname, "Segundo $tag")
        fill(R.string.card_closing_day, "20")
        fill(R.string.card_due_day, "28")
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<AccordApplication>().getString(R.string.card_save)).performScrollTo().performClick()
        compose.waitUntil(10_000) { vm.edit.value == null && (vm.content.value as? CardContent.Ready)?.data?.card?.nickname == "Segundo $tag" }
        val second = cards().single { it.nickname == "Segundo $tag" }
        assertNotNull(second.accountId)
        compose.onNodeWithText("Usar como meu cartão padrão").performScrollTo().performClick()
        compose.waitUntil(10_000) { cards().single { it.id == second.id }.isDefault }
        compose.onNodeWithText("Primeiro $tag").performScrollTo().performClick()
        compose.waitUntil(10_000) { (vm.content.value as? CardContent.Ready)?.data?.card?.id == first }
        assertTrue(cards().single { it.id == second.id }.isDefault)
        capture("cards")
    }

    @Test fun invoicePaysOnlySelectedCardFromTheChosenAccount() {
        val tag = UUID.randomUUID().toString().take(6)
        val month = YearMonth.now().plusMonths(8)
        val a = execute(ConfigureCard(BankRef.Other("Azul $tag"), "Azul $tag", 8, 15)).changes.single { it.type == EntityType.Card }.id
        val b = execute(ConfigureCard(BankRef.Other("Verde $tag"), "Verde $tag", 20, 28, createNew = true)).changes.single { it.type == EntityType.Card }.id
        val otherAccount = execute(AddAccount(BankRef.Other("Conta alternativa"), "Alternativa $tag")).changes.single().id
        for (card in listOf(a, b)) execute(RegisterCardPurchase("Teste $tag", Classification.ToClassify, LocalDate.now(), Money(8000), invoice = month, cardId = card))
        execute(RegisterInvoiceTotal(month, Money(8000), cardId = b))
        val vm = InvoiceViewModel(provider, SavedStateHandle(mapOf(InvoiceViewModel.ARG_MONTH to month.toString(), "cardId" to b)))
        compose.setContent { AccordTheme { InvoiceRoute(vm, PaddingValues(), {}, {}) } }
        compose.waitUntil(10_000) { vm.content.value is InvoiceContent.Ready }
        compose.onNodeWithText("Verde $tag").assertExists()
        compose.runOnIdle { vm.openPay() }
        compose.waitUntil(10_000) { vm.dialog.value?.accounts?.any { it.id == otherAccount } == true }
        assertEquals(cards().single { it.id == b }.accountId, vm.dialog.value?.accountId)
        compose.onNodeWithText("Alternativa $tag").performScrollTo().performClick()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<AccordApplication>().getString(R.string.linked_confirm)).performClick()
        compose.waitUntil(10_000) { (vm.content.value as? InvoiceContent.Ready)?.invoice?.paid == true }
        runBlocking {
            val space = provider.space()
            assertFalse((space.invoice(month, a) as QueryResult.Data).value.paid)
            val payment = (space.entries(EntryFilter(YearMonth.now(), month, cardId = b)) as QueryResult.Data).value.rows.single { it.kind == EntryKind.InvoicePayment }
            assertEquals(otherAccount, (space.entry(payment.id) as QueryResult.Data).value!!.accountId)
        }
        capture("invoice")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.filesDir, "multicard-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
