package app.accord.privacy

import app.accord.core.sync.DataExportScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DataExportBundle(
    val scope: DataExportScope,
    val createdAtMillis: Long,
    val profile: JsonObject,
    val local: JsonObject,
    val remote: JsonObject?,
) {
    val includesService: Boolean get() = remote != null
}

/** Arquivo de consulta, sem credenciais; não é entrada para restauração/aplicação de lançamentos. */
object DataArchive {
    private val json = Json { prettyPrint = true }

    fun write(output: OutputStream, bundle: DataExportBundle) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            fun document(name: String, content: JsonElement) = entry(name, json.encodeToString(JsonElement.serializer(), content))
            document("perfil.json", bundle.profile)
            document("aparelho.json", bundle.local)
            bundle.remote?.let { document("servico.json", it) }
            bundle.local.forEach { (table, value) ->
                if (value is JsonArray && value.isNotEmpty()) entry("tabelas/$table.csv", csv(value))
            }
            entry("LEIA-ME.txt", buildString {
                append("Cópia dos dados do Accord, sem senha. Quem acessar este arquivo poderá ler seu conteúdo.\n")
                append(if (bundle.includesService) "Inclui snapshot do serviço e dados deste aparelho, apresentados separadamente.\n"
                    else "CÓPIA LOCAL INCOMPLETA: não foi possível consultar o serviço.\n")
                append("Não contém os Chats locais de outros aparelhos, senhas, tokens nem PDFs/OFX/CSV completos.\n")
                append("Dados extraídos das importações estão nas tabelas financeiras.\n")
                append("Operações locais pendentes e versões em conflito conservam seus estados nas tabelas operation/sync_conflict.\n")
                append("Os snapshots podem corresponder a instantes diferentes. Salvo localmente não significa reconhecido pelo serviço.\n")
                append("Este arquivo é para consulta; não restaura nem reaplica lançamentos.\n")
            })
            // Escrito ao final: uma falha anterior não deixa manifesto indicando cópia concluída.
            document("manifesto.json", buildJsonObject {
                put("format", "accord-data-export"); put("version", 1)
                put("scope", bundle.scope.name); put("createdAtMillis", bundle.createdAtMillis)
                put("includesService", bundle.includesService); put("containsLocalData", true)
                put("encrypted", false); put("restorable", false)
            })
        }
    }

    fun csv(rows: JsonArray): String {
        val objects = rows.mapNotNull { it as? JsonObject }
        val columns = objects.flatMap { it.keys }.distinct()
        fun cell(value: String, guardFormula: Boolean = true): String {
            // Planilhas não devem interpretar texto do Chat/arquivo como fórmula.
            val safe = if (guardFormula && value.trimStart().firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        fun text(value: JsonElement?): String = when (value) {
            null, kotlinx.serialization.json.JsonNull -> ""
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            else -> value.toString()
        }
        return buildString {
            append(columns.joinToString(",") { cell(it) }); append('\n')
            objects.forEach { row ->
                append(columns.joinToString(",") {
                    val value = row[it]
                    cell(text(value), guardFormula = value !is JsonPrimitive || value.isString)
                })
                append('\n')
            }
        }
    }
}
