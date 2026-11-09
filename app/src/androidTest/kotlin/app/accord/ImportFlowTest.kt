package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.importing.BatchSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Importação de ponta a ponta no banco do aparelho (S4.4): do lote em revisão, por Revisar, até o resultado, passando pelo resumo de
 * confirmação. A seleção de arquivo é do sistema operacional e não é automatizada aqui: o lote nasce pela mesma sessão que a tela usa.
 */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6).uppercase()
    private val description get() = "MERCADO $tag"
    private var batchId = ""
    // ICU de versões diferentes do Android pode escolher singular ou plural para zero em pt-BR.
    private fun counts(pattern: String) = SemanticsMatcher("contagens de importação: $pattern") { node ->
        Regex(pattern).matches(node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty())
    }

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("imp-acc-$tag")), AddAccount(BankRef.Other("Banco"), "Conta $tag")) as CommandResult.Applied)
            .receipt.changes.single().id
        space.execute(Envelope(CommandId("imp-def-$tag")), SetMyDefaultAccount(account))

        val day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val ofx = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nSECURITY:NONE\r\nENCODING:USASCII\r\nCHARSET:NONE\r\nCOMPRESSION:NONE\r\nOLDFILEUID:NONE\r\nNEWFILEUID:NONE\r\n\r\n" +
            "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>$day<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>" +
            "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>999<ACCTID>1</BANKACCTFROM>" +
            "<BANKTRANLIST><DTSTART>$day<DTEND>$day<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>$day<TRNAMT>-12.34<FITID>F$tag<NAME>$description</STMTTRN></BANKTRANLIST>" +
            "</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
        val bytes = ofx.toByteArray()
        val extraction = (OfxReader.read(bytes) as ReadOutcome.Extracted).result
        val imports = container.imports()
        // Sobras de outros testes ficariam como "revisão em andamento" depois do descarte e confundiriam o que se quer provar.
        (imports.batches() as QueryResult.Data).value.filter { it.status == app.accord.core.importing.BatchStatus.InReview }.forEach { imports.discard(it.id) }
        val started = (imports.start(StartImport("imp-$tag", "extrato-$tag.ofx", bytes.size.toLong(), extraction, BatchSource(DocumentKind.Account, account))) as QueryResult.Data).value
        batchId = (started as StartOutcome.Started).info.id
        val groceries = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        imports.decide(batchId, ImportDecision.DefaultCategory(groceries), 1)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    /** accord-c9m.11: a conta escolhida errada se corrige na própria revisão, sem recomeçar; o lote segue em revisão, nada é confirmado. */
    @Test
    fun corrigirAContaDoDocumentoNaRevisaoMantemOLoteEmRevisao() {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val other = runBlocking {
            (container.space().execute(Envelope(CommandId("imp-acc2-$tag")), AddAccount(BankRef.Other("Banco"), "Reserva $tag")) as CommandResult.Applied)
                .receipt.changes.single().id
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar revisão").performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText(description).assertIsDisplayed() }.isSuccess }

        compose.onNodeWithText("Corrigir tipo, conta ou fatura").performScrollTo().performClick()
        compose.onNodeWithText("Tipo, conta e fatura do documento").assertIsDisplayed()
        compose.onNodeWithText("Reserva $tag · Banco").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Tipo, conta e fatura do documento")).fetchSemanticsNodes().isEmpty() }

        val info = runBlocking { (container.imports().batches() as QueryResult.Data).value.first { it.id == batchId } }
        assertEquals(other, info.source.originId)
        assertEquals(app.accord.core.importing.BatchStatus.InReview, info.status)
    }

    @Test
    fun revisarOferecaContinuarConfirmarEMostreOResultado() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar revisão").performScrollTo().performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revisar importação")).fetchSemanticsNodes().isNotEmpty() }
        // A transição entre telas ainda pode estar terminando: espera o movimento ficar visível.
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText(description).assertIsDisplayed() }.isSuccess }
        val summaryCounts = counts("1 movimento: 1 novo · 0 vínculos? · 0 dúvidas?")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(summaryCounts)
        compose.onNode(summaryCounts).assertIsDisplayed()

        compose.onNodeWithText("Confirmar itens conferidos").assertIsDisplayed().performClick()
        compose.onNodeWithText("1 lançamento novo").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Importação concluída")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(counts("1 novo · 0 vínculos? · 0 pendentes?")).assertIsDisplayed()
        compose.onNodeWithText("Salvo neste aparelho. Será enviado ao outro aparelho quando houver conexão.").assertIsDisplayed()

        // O lançamento existe, vindo de uma importação, e o resultado gravado não reaplica nada ao reabrir.
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val created = runBlocking {
            val space = container.space()
            (space.movements(java.time.YearMonth.now()) as QueryResult.Data).value.filter { it.description == description }
        }
        assertEquals(1, created.size)
        val again = runBlocking {
            container.imports().confirm(batchId, "again-$tag", "qualquer")
        }
        assertTrue(again is QueryResult.Data)
        assertEquals(1, runBlocking { (container.space().movements(java.time.YearMonth.now()) as QueryResult.Data).value.count { it.description == description } })
    }

    /** Descartar na revisão volta a Revisar já sem "Continuar revisão": o card se atualiza ao reaparecer. */
    @Test
    fun descartarNaRevisaoVoltaARevisarSemContinuarRevisao() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar revisão").performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText(description).assertIsDisplayed() }.isSuccess }

        compose.onNodeWithText("Descartar esta revisão").performScrollTo().performClick()
        compose.onNodeWithText("Descartar").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Importar arquivo")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isEmpty() }
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        assertTrue(runBlocking { (container.imports().batches() as QueryResult.Data).value.none { it.id == batchId } })
    }

    /** Descartar direto do card de Revisar, sem abrir a revisão. */
    @Test
    fun descartarPeloCardDeRevisarTiraOContinuarRevisao() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Descartar esta revisão").performScrollTo().performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Continuar revisão")).fetchSemanticsNodes().isEmpty() }
    }
}
