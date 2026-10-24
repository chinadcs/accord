package app.accord

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.CategorySeed
import app.accord.core.space.DeterministicIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * O Room gerado para JVM precisa funcionar no Android (banco real do aparelho, driver embutido).
 * Este teste roda no emulador ou aparelho; não substitui a validação no S20 FE e no S23 FE.
 */
@RunWith(AndroidJUnit4::class)
class FacadeOnDeviceTest {
    @Test
    fun registraEConsultaNoBancoDoAparelho() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(AppContainer.DATABASE_NAME)
        val container = AppContainer(context)
        val space = container.space()

        val categories = (space.categories() as QueryResult.Data).value
        assertEquals(CategorySeed.items.size, categories.size)

        val account = (space.execute(
            Envelope(CommandId("acc")), AddAccount(BankRef.Other("Banco"), "Conta"),
        ) as CommandResult.Applied).receipt.changes.single().id
        space.execute(Envelope(CommandId("def")), SetMyDefaultAccount(account))

        val category = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        val command = RegisterDirectPayment(
            DirectPaymentKind.Pix, "Mercado", Classification.Category(category),
            LocalDate.of(2026, 10, 20), Money(5_000),
        )
        assertTrue(space.execute(Envelope(CommandId("pix-1")), command) is CommandResult.Applied)
        assertTrue(space.execute(Envelope(CommandId("pix-1")), command) is CommandResult.AlreadyApplied)

        val budget = (space.budget(java.time.YearMonth.of(2026, 10)) as QueryResult.Data).value
        assertEquals(Money(5_000), budget.committed)
        assertEquals(Money(5_000), budget.paid)
    }
}
