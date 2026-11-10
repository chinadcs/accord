package app.accord.ui

import app.accord.ui.vendors.BrandCatalog
import app.accord.ui.vendors.SvgPathNormalizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O `PathParser` do Compose erra os flags de arco compactados nas repetições implícitas; o catálogo entrega o caminho já normalizado (accord-a2eg). */
class SvgPathNormalizerTest {
    private val tsv = File("src/main/assets/vendors/simple-icons.tsv")

    @Test
    fun arcoImplicitoComFlagsCompactadosViraArcosExplicitos() {
        assertEquals(
            "M 20.317 4.3698a 19.7913 19.7913 0 0 0 -4.8851 -1.5152a .0741 .0741 0 0 0 -.0785 .0371z",
            SvgPathNormalizer.normalize("M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371z"),
        )
    }

    @Test
    fun comandosSaoExplicitosEParMRepetidoViraL() {
        assertEquals("M 1 2L 3 4L 5 6h 7v 8z", SvgPathNormalizer.normalize("M1 2 3 4 5 6h7v8z"))
        assertEquals("m 1 2l 3 4", SvgPathNormalizer.normalize("m1,2,3,4"))
        assertEquals("C 1 2 3 4 5 6C 7 8 9 10 11 12", SvgPathNormalizer.normalize("C1 2 3 4 5 6 7 8 9 10 11 12"))
    }

    @Test
    fun numerosColadosEExpoentesSaoLidosPeloPontoESinal() {
        assertEquals("L 1.5 .5L -2 1e-3", SvgPathNormalizer.normalize("L1.5.5-2 1e-3"))
    }

    @Test
    fun ehIdempotenteEPreservaTextoForaDaGramatica() {
        val d = "M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371z"
        assertEquals(SvgPathNormalizer.normalize(d), SvgPathNormalizer.normalize(SvgPathNormalizer.normalize(d)))
        assertEquals("sem caminho", SvgPathNormalizer.normalize("sem caminho"))
        assertEquals("M1", SvgPathNormalizer.normalize("M1"))
    }

    @Test
    fun todoArcoDoCatalogoSaiComSeteParametrosEFlagsZeroOuUm() {
        val catalog = BrandCatalog.parse(tsv.readText(Charsets.UTF_8).lineSequence())
        assertTrue(catalog.brands.size > 3000)
        val token = Regex("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
        catalog.brands.forEach { brand ->
            val tokens = token.findAll(brand.pathData).map { it.value }.toList()
            var i = 0
            while (i < tokens.size) {
                if (tokens[i].equals("a", ignoreCase = true)) {
                    // Cada arco explícito: letra + 7 números, com os flags 4º e 5º iguais a 0 ou 1.
                    val params = tokens.subList(i + 1, minOf(i + 8, tokens.size))
                    assertEquals(7, params.size, brand.slug)
                    assertTrue(params.none { it[0].isLetter() }, brand.slug)
                    assertTrue(params[3] in setOf("0", "1") && params[4] in setOf("0", "1"), "${brand.slug}: ${params}")
                    i += 8
                } else {
                    i++
                }
            }
        }
    }

    @Test
    fun oDiscordSaiSemArcoImplicito() {
        val catalog = BrandCatalog.parse(tsv.readText(Charsets.UTF_8).lineSequence())
        val discord = checkNotNull(catalog.find("discord"))
        // Original: arcos encadeados sem repetir o `a`; agora cada um tem o seu comando.
        val original = tsv.readLines(Charsets.UTF_8).first { it.startsWith("discord\t") }.split('\t')[7]
        val before = Regex("[aA]").findAll(original).count()
        val arcs = Regex("[aA]").findAll(discord.pathData).count()
        assertTrue(arcs > before, "arcos: $arcs, antes: $before")
        assertTrue(Regex("[aA][^aAa-zA-Z]*").findAll(discord.pathData).all { m -> m.value.trim().split(' ').size == 8 })
    }
}
