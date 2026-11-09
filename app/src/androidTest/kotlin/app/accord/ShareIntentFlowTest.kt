package app.accord

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.share.SharedContent
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "Compartilhar para o Accord" (accord-jcd9): texto cai no campo do Chat sem ser enviado; arquivo cai na leitura da importação. */
@RunWith(AndroidJUnit4::class)
class ShareIntentFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container

    @Before
    fun prepare() = runBlocking<Unit> {
        TestModel.install(container)
        container.chat.filterNotNull().first()
        container.consumeShare()
        container.access.runtime.value?.chatStore?.clear()
    }

    @After
    fun close() {
        scenario?.close()
        container.consumeShare()
        runBlocking { container.access.runtime.value?.chatStore?.clear() }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun textoCompartilhadoChegaAoCampoDoChatSemSerEnviado() {
        val send = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Mercado\n45,90 no Pix")
        scenario = ActivityScenario.launch(send)
        waitForText("Mercado 45,90 no Pix")
        compose.onNodeWithText("Mercado 45,90 no Pix").assertExists()
        assertNull(container.pendingShare.value)
    }

    @Test
    fun arquivoOfxCompartilhadoAbreALeituraDaImportacao() {
        val day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val ofx = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nSECURITY:NONE\r\nENCODING:USASCII\r\nCHARSET:NONE\r\nCOMPRESSION:NONE\r\nOLDFILEUID:NONE\r\nNEWFILEUID:NONE\r\n\r\n" +
            "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>$day<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>" +
            "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>999<ACCTID>1</BANKACCTFROM>" +
            "<BANKTRANLIST><DTSTART>$day<DTEND>$day<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>$day<TRNAMT>-12.34<FITID>SHARE1<NAME>Compartilhado</STMTTRN></BANKTRANLIST>" +
            "</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
        val file = File(ApplicationProvider.getApplicationContext<AccordApplication>().cacheDir, "compartilhado.ofx").apply { writeText(ofx) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Uri de arquivo só porque o teste entrega o conteúdo dentro do processo; no aparelho vem um content:// do app de origem.
        container.openShare(SharedContent.File(Uri.fromFile(file)))
        waitForText("OFX")
        compose.onNodeWithText("Tipo do documento", substring = true).assertExists()
        assertNull(container.pendingShare.value)
        file.delete()
    }
}
