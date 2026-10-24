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
}
