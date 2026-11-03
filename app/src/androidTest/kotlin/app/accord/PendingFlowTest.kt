package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.importing.BatchSource
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.OfxReader
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Fila de pendências de ponta a ponta (S4.5): uma dúvida guardada num lote confirmado aparece em Revisar, é decidida, aplicada e some da fila.
 * A categoria é escolhida pela mesma sessão que a tela usa (o seletor de categoria tem teste próprio); o resto passa pela interface.
 */
@RunWith(AndroidJUnit4::class)
class PendingFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val clear get() = "MERCADO $tag"
    private val doubt get() = "PADARIA $tag"
    private var batchId = ""

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("pend-acc-$tag")), AddAccount(BankRef.Other("Banco"), "Conta $tag")) as CommandResult.Applied)
            .receipt.changes.single().id
        space.execute(Envelope(CommandId("pend-def-$tag")), SetMyDefaultAccount(account))

        val day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        fun line(fitid: String, amount: String, name: String) = "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>$day<TRNAMT>$amount<FITID>$fitid<NAME>$name</STMTTRN>"
        val ofx = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nSECURITY:NONE\r\nENCODING:USASCII\r\nCHARSET:NONE\r\nCOMPRESSION:NONE\r\nOLDFILEUID:NONE\r\nNEWFILEUID:NONE\r\n\r\n" +
            "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>$day<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>" +
            "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>999<ACCTID>1</BANKACCTFROM>" +
            "<BANKTRANLIST><DTSTART>$day<DTEND>$day" + line("A$tag", "-12.34", clear) + line("B$tag", "-9.00", doubt) +
            "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
        val bytes = ofx.toByteArray()
        val extraction = (OfxReader.read(bytes) as ReadOutcome.Extracted).result
        val imports = container.imports()
        val started = (imports.start(StartImport("pend-$tag", "extrato-$tag.ofx", bytes.size.toLong(), extraction, BatchSource(DocumentKind.Account, account))) as QueryResult.Data).value
        batchId = (started as StartOutcome.Started).info.id
        val groceries = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        imports.decide(batchId, ImportDecision.Category(1, groceries), 1)
        val token = (imports.review(batchId) as QueryResult.Data).value!!.token
        check((imports.confirm(batchId, "pend-confirm-$tag", token) as QueryResult.Data).value is ConfirmOutcome.Applied)
        // A dúvida (sem categoria) ficou guardada; a categoria escolhida agora é uma decisão local sobre ela.
        val revision = (imports.batches() as QueryResult.Data).value.first { it.id == batchId }.revision
        check((imports.decide(batchId, ImportDecision.Category(2, groceries), revision) as QueryResult.Data).value is app.accord.core.importing.DecideOutcome.Applied)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun duvidaGuardadaApareceEmRevisarEResolveSemDuplicar() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Revisar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revisar pendências")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revisar pendências").performScrollTo().performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Dúvida resolvida, falta aplicar")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(doubt, substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dúvida resolvida, falta aplicar").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Resolver pendência")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Aplicar decisão").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Vira um lançamento novo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").performClick()

        // Volta à fila, que já não traz a dúvida.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Dúvida resolvida, falta aplicar")).fetchSemanticsNodes().isEmpty() }

        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val created = runBlocking { (container.space().movements(YearMonth.now()) as QueryResult.Data).value.filter { it.description == doubt } }
        assertEquals(1, created.size)
        assertEquals(emptyList<Any>(), runBlocking { (container.imports().pendings() as QueryResult.Data).value.filter { it.batchId == batchId } })
    }
}
