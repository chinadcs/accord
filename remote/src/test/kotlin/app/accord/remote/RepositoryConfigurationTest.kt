package app.accord.remote

import app.accord.core.sync.SharedTypes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Garante, sem serviço no ar, que o repositório mantém o piloto sem e-mails e o banco alinhado ao app. */
class RepositoryConfigurationTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "supabase/config.toml").exists() }

    private fun File.lines() = readText().replace("\r\n", "\n")

    private val config = File(root, "supabase/config.toml").lines()
    private val migrations = File(root, "supabase/migrations").listFiles { f -> f.extension == "sql" }.orEmpty().sortedBy { it.name }

    private fun section(name: String): String {
        val start = config.indexOf("\n[$name]\n")
        assertTrue(start >= 0, "seção [$name] ausente em config.toml")
        val next = Regex("\n\\[").find(config, start + 2)?.range?.first ?: config.length
        return config.substring(start, next)
    }

    @Test
    fun `Confirm Email está desativado e não há SMTP configurado`() {
        val email = section("auth.email")
        assertTrue(Regex("(?m)^enable_confirmations = false$").containsMatchIn(email), "Confirm Email precisa ficar desativado")
        assertFalse(Regex("(?m)^\\[auth\\.email\\.smtp]").containsMatchIn(config), "SMTP não faz parte do piloto")
        assertFalse(Regex("(?m)^\\[auth\\.email\\.template").containsMatchIn(config), "nenhum modelo de e-mail é usado")
    }

    @Test
    fun `os tipos sincronizáveis do banco são exatamente os do aplicativo`() {
        val declared = migrations.flatMap { file ->
            Regex("(?m)^-- shared-types: (.+)$").findAll(file.lines()).map { it.groupValues[1] }.toList()
        }
        assertTrue(declared.isNotEmpty(), "migração sem a linha shared-types")
        assertEquals(SharedTypes.all, declared.last().split(',').map { it.trim() }.toSet())
    }

    @Test
    fun `toda tabela pública nasce com RLS habilitado`() {
        val sql = migrations.joinToString("\n") { it.lines() }
        val tables = Regex("(?m)^create table public\\.(\\w+)").findAll(sql).map { it.groupValues[1] }.toList()
        assertTrue(tables.isNotEmpty())
        for (table in tables) {
            assertTrue(
                Regex("alter table public\\.$table enable row level security").containsMatchIn(sql),
                "a tabela $table precisa de RLS habilitado",
            )
        }
    }

    private val textExtensions = setOf("kt", "kts", "xml", "toml", "sql", "md", "json", "properties", "yml", "yaml", "py", "ps1", "sh", "txt")
    private val skippedDirectories = setOf("build", ".git", ".gradle", ".beads", ".idea", "node_modules", ".temp", ".branches")

    private fun versionedTextFiles(): Sequence<File> = root.walkTopDown()
        .onEnter { it.name !in skippedDirectories }
        .filter { it.isFile && it.extension in textExtensions && it.name != "local.properties" }

    @Test
    fun `nenhuma chave secreta do serviço está no código nem na configuração versionada`() {
        val secretKey = Regex("sb_secret_[A-Za-z0-9_-]{8,}")
        val jwt = Regex("eyJ[A-Za-z0-9_-]{8,}[.]([A-Za-z0-9_-]{8,})[.][A-Za-z0-9_-]{8,}")
        val offenders = versionedTextFiles().flatMap { file ->
            val text = file.readText()
            buildList {
                if (secretKey.containsMatchIn(text)) add("${file.relativeTo(root)}: chave sb_secret_")
                jwt.findAll(text).forEach { m ->
                    val payload = runCatching { String(java.util.Base64.getUrlDecoder().decode(m.groupValues[1].padEnd((m.groupValues[1].length + 3) / 4 * 4, '='))) }.getOrDefault("")
                    if ("service_role" in payload) add("${file.relativeTo(root)}: JWT com papel service_role")
                }
            }.asSequence()
        }.toList()
        assertTrue(offenders.isEmpty(), "chave administrativa no repositório: $offenders")
    }

    @Test
    fun `o código de produção não registra log nem imprime dados`() {
        val sources = listOf("app/src/main", "persistence/src/main", "remote/src/main", "core/src/main")
        val offenders = sources.flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
                Regex("""\bLog[.][a-z][(]|android[.]util[.]Log|\bprintln[(]|\bprint[(]|printStackTrace""").containsMatchIn(file.readText())
            }.map { it.relativeTo(root).path }.toList()
        }
        assertTrue(offenders.isEmpty(), "log ou saída padrão no código de produção: $offenders")
    }

    @Test
    fun `a chave pública é a única credencial do serviço que o aplicativo conhece`() {
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue("SUPABASE_PUBLISHABLE_KEY" in build && "SUPABASE_URL" in build)
        assertFalse(Regex("SERVICE_ROLE|SECRET_KEY|serviceRole|secretKey", RegexOption.IGNORE_CASE).containsMatchIn(build), "o build do app não deve citar chave secreta")
    }
}
