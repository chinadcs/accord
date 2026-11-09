package app.accord.privacy

import app.accord.core.sync.DataExportScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataArchiveTest {
    private fun archive(remote: JsonObject?): Map<String, String> {
        val output = ByteArrayOutputStream()
        DataArchive.write(output, DataExportBundle(DataExportScope.Financial, 123, JsonObject(emptyMap()),
            Json.parseToJsonElement("""{"account":[{"id":"conta","balance":-12345}]}""").let { it as JsonObject }, remote))
        return ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes().toString(Charsets.UTF_8)) } }
        }
    }

    @Test fun `copia local informa incompletude e nao afirma restauracao`() {
        val entries = archive(null)
        assertTrue("INCOMPLETA" in entries.getValue("LEIA-ME.txt"))
        assertTrue("\"includesService\": false" in entries.getValue("manifesto.json"))
        assertTrue("\"restorable\": false" in entries.getValue("manifesto.json"))
        assertFalse("servico.json" in entries)
        assertTrue("\"-12345\"" in entries.getValue("tabelas/account.csv"))
    }

    @Test fun `snapshot remoto e local permanecem separados`() {
        val entries = archive(JsonObject(emptyMap()))
        assertTrue("servico.json" in entries && "aparelho.json" in entries)
        assertTrue("\"includesService\": true" in entries.getValue("manifesto.json"))
        assertEquals("manifesto.json", entries.keys.last())
    }

    @Test fun `csv protege formulas em texto e preserva valor numerico negativo`() {
        val rows = Json.parseToJsonElement("""[{"text":"=1+1","cents":-200,"description":"A, \"B\"\nC"}]""").jsonArray
        val csv = DataArchive.csv(rows)
        assertTrue("\"'=1+1\"" in csv)
        assertTrue("\"-200\"" in csv && "'-200" !in csv)
        assertTrue("\"A, \"\"B\"\"\nC\"" in csv)
    }
}
