package app.accord.core.chat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opcional. `ACCORD_VOCAB_OUT=<arquivo>` grava os termos do vocabulário do perfil `ACCORD_VOCAB_PROFILE` (padrão 17), um por linha.
 * `ACCORD_OOV_FILE=<arquivo>` confere que toda frase cujo identificador começa com `o-` fica FORA do vocabulário (sem candidata
 * de categoria) e imprime os identificadores que violam. Nunca imprime o texto.
 */
class OutOfVocabularyTest {
    private val version get() = System.getenv("ACCORD_VOCAB_PROFILE")?.toInt() ?: RoutedInterpreter.VERSION
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    @Test
    fun dump() {
        val out = System.getenv("ACCORD_VOCAB_OUT") ?: return
        File(out).writeText(SeedCategoryVocabulary.allTerms(version).sorted().joinToString("\n"), Charsets.UTF_8)
    }

    @Test
    fun conferir() {
        val path = System.getenv("ACCORD_OOV_FILE") ?: return
        val routed = RoutedInterpreter({ _, _, _ -> error("sem modelo") }, PromptStyle.ChatMlNoThink, RoutedOptions(version))
        val parsed = BatteryFile.parse(File(path).readText(Charsets.UTF_8))
        val violators = parsed.cases.filter { it.id.startsWith("o-") }
            .filter { SeedCategoryVocabulary.candidates(ctx, routed.purpose(it.text, ctx), version) != null }.map { it.id }
        println("OOV perfil $version: ${parsed.cases.count { it.id.startsWith("o-") }} frases, ${violators.size} dentro do vocabulário: $violators")
        assertTrue(violators.isEmpty(), "frases que o vocabulário do perfil $version já conhece: $violators")
    }
}
