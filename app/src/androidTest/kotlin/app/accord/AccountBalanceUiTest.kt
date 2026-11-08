package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.Money
import app.accord.core.space.*
import app.accord.ui.more.AccountsContent
import app.accord.ui.more.AccountsScreen
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AccountBalanceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialTransferAndAdjustmentSaveThroughTheRealForms() {
        val provider = androidx.test.core.app.ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = kotlinx.coroutines.runBlocking { provider.space() }
        fun execute(command: Command) = kotlinx.coroutines.runBlocking {
            (space.execute(Envelope(CommandId(java.util.UUID.randomUUID().toString())), command) as CommandResult.Applied).receipt
        }
        val tag = java.util.UUID.randomUUID().toString().take(6)
        val a = execute(AddAccount(BankRef.Other("Banco"), "Origem $tag")).changes.single().id
        val b = execute(AddAccount(BankRef.Other("Banco"), "Destino $tag")).changes.single().id
        execute(SetAccountBalance(b, Money.ZERO, LocalDate.now()))
        val vm = app.accord.ui.more.AccountsViewModel(provider, androidx.lifecycle.SavedStateHandle())
        compose.setContent { AccordTheme { app.accord.ui.more.AccountsRoute(vm, PaddingValues(), {}) } }
        compose.waitUntil(10_000) { vm.content.value is AccountsContent.Ready }
        fun open(mode: String) { compose.runOnIdle { vm.openBalance(mode, a) } }
        fun fill(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
        fun save() {
            val label = androidx.test.core.app.ApplicationProvider.getApplicationContext<AccordApplication>().getString(R.string.accounts_save)
            compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.balanceAction.value == null }
        }
        fun balance(id: String) = kotlinx.coroutines.runBlocking {
            (space.accountBalances() as QueryResult.Data).value.single { it.accountId == id }.balance
        }
        open("initial")
        fill("Valor (R$)", "100,00")
        save()
        org.junit.Assert.assertEquals(Money(10000), balance(a))
        open("transfer")
        compose.onNode(hasText("Destino $tag") and hasClickAction()).performScrollTo().performClick()
        fill("Valor (R$)", "30,00")
        save()
        org.junit.Assert.assertEquals(Money(7000), balance(a))
        org.junit.Assert.assertEquals(Money(3000), balance(b))
        open("adjust")
        fill("Saldo conferido (R$)", "-10,00")
        save()
        org.junit.Assert.assertEquals(Money(-1000), balance(a))
    }

    @Test fun unknownBalanceIsNotZeroAndWalletOffersTransfer() {
        val wallet = AccountInfo("wallet", BankRef.Other("Carteira"), "Dinheiro", isWallet = true)
        var requested = ""
        compose.setContent {
            AccordTheme {
                AccountsScreen(AccountsContent.Ready(AccountsView(emptyList(), null, wallet)), null, PaddingValues(),
                    onBack = {}, onRetry = {}, onAdd = {}, onEdit = {}, onSetDefault = {}, onChange = { _, _ -> }, onSave = {}, onCancel = {},
                    onBalance = { action, id -> requested = "$action/$id" })
            }
        }
        compose.onNodeWithText("Saldo inicial não informado").assertIsDisplayed()
        compose.onNodeWithText("Transferir entre contas").performScrollTo().performClick()
        assertEquals("transfer/wallet", requested)
        compose.onNodeWithText("Ajustar saldo").assertDoesNotExist()
    }

    @Test fun negativeBalanceShowsExactValueAndOffersAdjustment() {
        val bank = AccountInfo("bank", BankRef.Other("Banco"), "Conta")
        val value = AccountBalanceView("bank", Money(0), LocalDate.of(2026, 10, 1), Money(-12345))
        var requested = ""
        compose.setContent {
            AccordTheme {
                AccountsScreen(AccountsContent.Ready(AccountsView(listOf(bank), "bank"), listOf(value)), null, PaddingValues(),
                    onBack = {}, onRetry = {}, onAdd = {}, onEdit = {}, onSetDefault = {}, onChange = { _, _ -> }, onSave = {}, onCancel = {},
                    onBalance = { action, id -> requested = "$action/$id" })
            }
        }
        compose.onNodeWithText(Money(-12345).format()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ajustar saldo").performScrollTo().performClick()
        assertEquals("adjust/bank", requested)
    }
}
