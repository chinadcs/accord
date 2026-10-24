package app.accord.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/** Um arquivo do modelo, com tamanho e SHA-256 conferidos antes de qualquer "Pronto" (S3 §11). */
data class ModelFile(val name: String, val bytes: Long, val sha256: String)

/**
 * Manifesto de uma versão do modelo (S3 §11, Proposta): arquivos com bytes e hash, motor compatível e o
 * espaço necessário para instalar (medido na PoC, nunca presumido). O total do download não passa de
 * 2.000.000.000 bytes: quem excede é rejeitado, sem dividir os pesos para contornar o teto.
 */
data class ModelManifest(
    val version: String,
    val engine: String,
    val engineVersion: String,
    val files: List<ModelFile>,
    /** Espaço livre exigido para instalar, incluindo arquivos auxiliares do motor. */
    val installBytes: Long,
) {
    val downloadBytes: Long get() = files.sumOf { it.bytes }

    /** Convenção de exibição: GB decimal, 2 GB = 2.000.000.000 bytes. */
    companion object {
        const val MAX_DOWNLOAD_BYTES = 2_000_000_000L
        private val HEX = Regex("^[0-9a-f]{64}$")

        fun validate(manifest: ModelManifest): ManifestError? = when {
            manifest.version.isBlank() || '/' in manifest.version || '\\' in manifest.version || manifest.version.startsWith(".") ->
                ManifestError.InvalidVersion
            manifest.files.isEmpty() -> ManifestError.NoFiles
            manifest.files.any { it.bytes <= 0 } -> ManifestError.InvalidFile
            manifest.files.map { it.name }.toSet().size != manifest.files.size -> ManifestError.InvalidFile
            manifest.files.any { it.name.isBlank() || '/' in it.name || '\\' in it.name || it.name.startsWith(".") } -> ManifestError.InvalidFile
            manifest.files.any { !HEX.matches(it.sha256) } -> ManifestError.InvalidFile
            manifest.downloadBytes > MAX_DOWNLOAD_BYTES -> ManifestError.OverLimit(manifest.downloadBytes)
            manifest.installBytes < manifest.downloadBytes -> ManifestError.InstallSmallerThanDownload
            else -> null
        }
    }
}

sealed interface ManifestError {
    data object InvalidVersion : ManifestError
    data object NoFiles : ManifestError
    data object InvalidFile : ManifestError
    data class OverLimit(val bytes: Long) : ManifestError
    data object InstallSmallerThanDownload : ManifestError
    data object Unreadable : ManifestError
}

/** Manifesto em JSON, para o fixado no build ou o remoto assinado (autenticidade em aberto). */
object ManifestCodec {
    fun encode(manifest: ModelManifest): String = JsonObject(
        mapOf(
            "version" to JsonPrimitive(manifest.version),
            "engine" to JsonPrimitive(manifest.engine),
            "engineVersion" to JsonPrimitive(manifest.engineVersion),
            "installBytes" to JsonPrimitive(manifest.installBytes),
            "files" to JsonArray(
                manifest.files.map {
                    JsonObject(mapOf("name" to JsonPrimitive(it.name), "bytes" to JsonPrimitive(it.bytes), "sha256" to JsonPrimitive(it.sha256)))
                },
            ),
        ),
    ).toString()

    /** Nulo quando o texto não é um manifesto legível; a validação de regras é de [ModelManifest.validate]. */
    fun decode(text: String): ModelManifest? = try {
        val root = Json.parseToJsonElement(text).jsonObject
        ModelManifest(
            version = root.getValue("version").let { (it as JsonPrimitive).content },
            engine = (root.getValue("engine") as JsonPrimitive).content,
            engineVersion = (root.getValue("engineVersion") as JsonPrimitive).content,
            installBytes = (root.getValue("installBytes") as JsonPrimitive).long,
            files = (root.getValue("files") as JsonArray).map { element ->
                val file = element.jsonObject
                ModelFile(
                    name = (file.getValue("name") as JsonPrimitive).content,
                    bytes = (file.getValue("bytes") as JsonPrimitive).longOrNull ?: return null,
                    sha256 = (file.getValue("sha256") as JsonPrimitive).contentOrNull ?: return null,
                )
            },
        )
    } catch (_: Exception) {
        null
    }
}
