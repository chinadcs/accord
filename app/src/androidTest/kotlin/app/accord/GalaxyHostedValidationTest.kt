package app.accord

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.clues.ClueDetector
import app.accord.core.space.*
import app.accord.core.sync.*
import app.accord.persistence.*
import app.accord.remote.SupabaseSpaceService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Opt-in: somente fixtures hospedadas, fornecidas no sandbox isolado pelo executor do PC.
 * Não usa credenciais administrativas no aparelho. Cada invocação reabre o mesmo Room persistido.
 */
class GalaxyHostedValidationTest {
    @Test fun phase() = runBlocking<Unit> {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        check(context.packageName == "app.accord.uivalidation")
        val file = File(context.filesDir, "galaxy-fixture.json")
        assumeTrue("Fixture hospedada opt-in ausente", file.isFile)
        val cfg = Json.parseToJsonElement(file.readText()).jsonObject
        fun value(key: String) = cfg.getValue(key).jsonPrimitive.content
        val session = AuthSession(value("accountId"), value("email"), value("accessToken"), value("refreshToken"), Long.MAX_VALUE)
        val spaceId = value("spaceId")
        val phase = InstrumentationRegistry.getArguments().getString("phase") ?: error("Fase ausente")
        val remote = SupabaseSpaceService(value("url"), value("publicKey"))
        val db = Room.databaseBuilder<AccordDatabase>(context, "galaxy-hosted-validation.db") { AccordDatabase_Impl() }
            .setDriver(BundledSQLiteDriver()).build()
        try {
            val space = RoomFinanceSpace.open(db, BudgetClock.system(), { Session(spaceId, session.accountId) })
            val store = SyncStore(db)
            val access = object : SyncAccess {
                override suspend fun check() = SessionCheck.Usable(session, spaceId)
                override suspend fun renew() = RenewResult.Expired
            }
            val engine = SyncEngine(store, remote, access)
            suspend fun execute(command: Command): Receipt {
                val result = space.execute(Envelope(CommandId(UUID.randomUUID().toString())), command)
                assertTrue("Comando ${command::class.simpleName} deve aplicar", result is CommandResult.Applied)
                return (result as CommandResult.Applied).receipt
            }
            suspend fun sync() = withTimeout(90_000) {
                // Wi-Fi/dados levam algum tempo para recuperar a rota após sair do modo avião.
                while (engine.syncNow() !is SyncOutcome.Completed) delay(2_000)
            }
            val day = LocalDate.now().minusDays(1)
            val output = buildJsonObject {
                put("phase", phase)
                when (phase) {
                    "seed" -> {
                        execute(AddAccount(BankRef.Other("Banco fictício"), "Conta física", Money(100000), day))
                        val a = (space.accounts() as QueryResult.Data).value.accounts.single()
                        execute(CreateSavingsPocket(a.id, "Reserva física", Money.ZERO, day, false))
                        execute(CreateCategory("Categoria física"))
                        execute(CreateIntention("Intenção física", Money(10000)))
                        repeat(2) { i -> execute(ConfigureCard(BankRef.Other("Banco fictício"), "Cartão físico $i", 8, 15, createNew = true)) }
                        val card = (space.cards() as QueryResult.Data).value.first { it.nickname == "Cartão físico 0" }
                        repeat(2) { execute(RegisterCardPurchase("Mercado fictício repetido", Classification.ToClassify, day,
                            Money(5000), invoice = YearMonth.now().plusMonths(1), cardId = card.id)) }
                        sync()
                    }
                    "sync" -> sync()
                    "offline" -> {
                        // O executor deve confirmar modo avião e Wi-Fi/dados desligados nos dois aparelhos.
                        val a = (space.accounts() as QueryResult.Data).value.accounts.single { it.nickname == "Conta física" }
                        val pocket = (space.savingsPockets() as QueryResult.Data).value.single()
                        val category = (space.categories() as QueryResult.Data).value.single { it.name == "Categoria física" }
                        execute(EditCategory(category.id, name = "Categoria ${value("device")}", expectedRevision = category.revision))
                        val balance = (space.accountBalances() as QueryResult.Data).value.single { it.accountId == a.id }
                        execute(MoveSavingsPocket(pocket.id, SavingsDirection.Deposit, Money(80000), day, pocket.revision, balance.revision))
                        val card = (space.cards() as QueryResult.Data).value.first { it.nickname == "Cartão físico 0" }
                        execute(ConfigureCard(card.bank, "Cartão ${value("device")}", 8, 15, cardId = card.id, expectedRevision = card.revision))
                        val intention = (space.intentions() as QueryResult.Data).value.single()
                        execute(RegisterCardPurchase("Conversão física", Classification.ToClassify, day, Money(10000),
                            invoice = YearMonth.now().plusMonths(1), originIntentionId = intention.id, cardId = card.id))
                        val source = (space.clueSource(LocalDate.now().minusDays(56), LocalDate.now()) as QueryResult.Data).value
                        val clue = ClueDetector.analyze(source, LocalDate.now()).clues.first()
                        execute(DismissClue(clue.key, clue.fingerprint, LocalDate.now()))
                        assertTrue(store.summary().awaitingOperations >= 5)
                        val before = store.summary().awaitingOperations
                        assertTrue("Sem rede o envio deve parar", engine.syncNow() is SyncOutcome.Stopped)
                        assertEquals(before, store.summary().awaitingOperations)
                    }
                    "conflicts" -> {
                        sync()
                        val conflicts = store.conflicts()
                        assertTrue("Devem existir conflitos de categoria, saldo, cartão e conversão", conflicts.size >= 4)
                        put("conflicts", conflicts.size)
                        for (conflict in conflicts) assertTrue(store.resolveWithRemote(conflict.operationId, conflict.seenRemoteVersions) is ResolveResult.Resolved)
                        sync()
                    }
                    "verify" -> {
                        sync()
                        val accountId = (space.accounts() as QueryResult.Data).value.accounts.single { it.nickname == "Conta física" }.id
                        val a = (space.accountBalances() as QueryResult.Data).value.single { it.accountId == accountId }
                        assertEquals(Money(20000), a.balance)
                        assertEquals(Money(80000), a.pocketsBalance)
                        assertEquals(Money(100000), a.totalBalance)
                        val pocket = (space.savingsPockets() as QueryResult.Data).value.single()
                        // A tentativa descartada pode permanecer no histórico local como desfeita.
                        val moves = (space.savingsMoves(pocket.id) as QueryResult.Data).value.filterNot { it.voided }
                        assertEquals(1, moves.size)
                        assertEquals(Money(80000), moves.single().amount)
                        assertEquals(2, (space.clueEvaluations() as QueryResult.Data).value.size)
                        val entries = (space.entries(EntryFilter(YearMonth.now(), YearMonth.now().plusMonths(1))) as QueryResult.Data).value.rows
                        assertEquals(1, entries.count { it.description == "Conversão física" })
                        assertEquals(0, store.summary().openConflicts)
                        assertEquals(0, store.summary().awaitingOperations)
                        val export = remote.exportData(session.accessToken, DataExportScope.Financial, spaceId)
                        assertTrue(export is RemoteResult.Ok)
                        val shared = Json.parseToJsonElement((export as RemoteResult.Ok).value).jsonObject.getValue("entities").jsonArray
                        assertEquals(1, shared.count { it.jsonObject.getValue("type").jsonPrimitive.content == "SavingsMove" })
                    }
                    else -> error("Fase desconhecida")
                }
                put("pending", store.summary().awaitingOperations)
                put("openConflicts", store.summary().openConflicts)
            }
            File(context.filesDir, "galaxy-result.json").writeText(output.toString())
        } finally { db.close() }
    }
}
