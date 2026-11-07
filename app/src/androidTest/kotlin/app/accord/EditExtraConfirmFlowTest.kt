package app.accord

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.Envelope
import app.accord.core.space.EntityType
import app.accord.core.space.ExtraReason
import app.accord.core.space.PayInvoice
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.ui.edit.EditContent
import app.accord.ui.edit.EditEntryViewModel
import app.accord.ui.edit.EditPrompt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * accord-skfc: item de fatura já quitada só muda depois da confirmação extra, no banco do aparelho.
 * A fatura é de um mês distante e único por execução para não quitar a de nenhum outro teste.
 */
@RunWith(AndroidJUnit4::class)
class EditExtraConfirmFlowTest {
    private val tag = UUID.randomUUID().toString().take(6)

    @Test
    fun itemDeFaturaQuitadaPedeConfirmacaoExtraEsoGravaDepoisDela() = runBlocking<Unit> {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val invoice = YearMonth.now().plusMonths(36L + (tag.hashCode().toLong() and 0x7fff) % 60)
        val category = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "eletronicos.equipamentos")

        val purchaseId = (space.execute(
            Envelope(CommandId("x-card-$tag")),
            RegisterCardPurchase("Cartão $tag", Classification.Category(category), LocalDate.now(), Money(30_000), AmountMeaning.Total, 1, invoice = invoice),
        ) as CommandResult.Applied).receipt.changes.first { it.type == EntityType.CardPurchase }.id
        val total = (space.invoice(invoice) as QueryResult.Data).value.knownTotal
        val registered = space.execute(Envelope(CommandId("x-total-$tag")), RegisterInvoiceTotal(invoice, total))
        assertTrue("total da fatura: $registered", registered is CommandResult.Applied)
        val account = (space.execute(Envelope(CommandId("x-account-$tag")), AddAccount(BankRef.Other("Banco"), "Conta $tag")) as CommandResult.Applied)
            .receipt.changes.first { it.type == EntityType.Account }.id
        val paid = space.execute(Envelope(CommandId("x-pay-$tag")), PayInvoice(invoice, total, LocalDate.now(), accountId = account))
        assertTrue("quitação: $paid", paid is CommandResult.Applied)

        val vm = EditEntryViewModel(container, SavedStateHandle(mapOf(EditEntryViewModel.ARG_ID to purchaseId)))
        withTimeout(8_000) { vm.state.first { it.content is EditContent.Ready } }
        vm.update { it.copy(description = "Novo $tag") }

        // sem confirmar, nada muda: a decisão volta com o motivo
        vm.save()
        val asked = withTimeout(8_000) { vm.state.first { it.prompt != null } }
        assertEquals(EditPrompt.Extra(listOf(ExtraReason.PaidInvoice)), asked.prompt)
        assertEquals(false, vm.exit.value)
        assertEquals("Cartão $tag", (space.purchase(purchaseId) as QueryResult.Data).value!!.description)

        // cancelar a pergunta mantém tudo como estava
        vm.dismissPrompt()
        assertNull(vm.state.value.prompt)
        assertEquals("Cartão $tag", (space.purchase(purchaseId) as QueryResult.Data).value!!.description)

        // confirmar grava e sai da edição
        vm.save()
        withTimeout(8_000) { vm.state.first { it.prompt is EditPrompt.Extra } }
        vm.confirmPrompt()
        withTimeout(8_000) { vm.exit.first { it } }
        assertEquals("Novo $tag", (space.purchase(purchaseId) as QueryResult.Data).value!!.description)
    }
}
