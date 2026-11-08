package app.accord.ui

import app.accord.ui.vendors.Brand
import app.accord.ui.vendors.BrandCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ativo do Simple Icons: integridade contra o manifesto, formato dos desenhos e busca (ADR 0047, ADR 0021). */
class BrandCatalogTest {
    private val assets = File("src/main/assets/vendors")
    private val manifest = Json.parseToJsonElement(File(assets, "catalog.json").readText(Charsets.UTF_8)).jsonObject
    // Git pode converter quebras de linha no Windows; o ativo é UTF-8 com LF.
    private val tsv = File(assets, manifest.getValue("asset").jsonPrimitive.content).readText(Charsets.UTF_8).replace("\r\n", "\n")
    private val catalog = BrandCatalog.parse(tsv.lineSequence())

    @Test
    fun ativoCorrespondeAoManifestoDeProveniencia() {
        assertEquals("Simple Icons", manifest.getValue("library").jsonPrimitive.content)
        assertEquals("16.35.0", manifest.getValue("version").jsonPrimitive.content)
        assertEquals(8, manifest.getValue("columns").jsonArray.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(tsv.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(manifest.getValue("assetSha256").jsonPrimitive.content, hash)
        assertEquals(manifest.getValue("count").jsonPrimitive.content.toInt(), tsv.lines().count { it.isNotBlank() })
        assertEquals(manifest.getValue("count").jsonPrimitive.content.toInt(), catalog.brands.size, "nenhuma linha foi descartada na leitura")
    }

    @Test
    fun cadaMarcaTemChaveUnicaCorValidaEUmUnicoDesenho() {
        val slugs = catalog.brands.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size, "slug é a chave e não se repete")
        assertTrue(slugs.all { Regex("[a-z0-9_]+").matches(it) })
        assertTrue(catalog.brands.all { it.color in 0..0xFFFFFF })
        // Um caminho só, sem comandos de outra natureza: começa por movimento e só usa letras de caminho SVG, números e separadores.
        val pathChars = Regex("[MmLlHhVvCcSsQqTtAaZz0-9eE.,\\- ]+")
        assertTrue(catalog.brands.all { it.pathData.startsWith("M") || it.pathData.startsWith("m") })
        assertTrue(catalog.brands.all { pathChars.matches(it.pathData) }, "caminho com caractere fora do padrão SVG")
    }

    @Test
    fun marcasDoPedidoOriginalContinuamNoCatalogoComOrigem() {
        for ((slug, title) in listOf("uber" to "Uber", "spotify" to "Spotify", "netflix" to "Netflix", "ifood" to "iFood")) {
            val brand = assertNotNull(catalog.find(slug), slug)
            assertEquals(title, brand.title)
            assertTrue(brand.source.startsWith("http"), "origem de $slug")
        }
        assertNull(catalog.find("UBER"))
        assertNull(catalog.find("../uber"))
        assertNull(catalog.find(""))
    }

    @Test
    fun buscaIgnoraAcentoCaixaEOrdenaPelaAproximacao() {
        assertEquals("Uber", catalog.search("UBER").first().title, "título exato primeiro")
        assertEquals("Spotify", catalog.search("SpOtIfY").first().title)
        assertTrue(catalog.search("ube").any { it.slug == "uber" }, "início de palavra: ${catalog.search("ube").size}")
        assertTrue(catalog.search("zzzzqqqq").isEmpty())
        assertEquals(catalog.brands.size, catalog.search("   ").size, "sem consulta devolve tudo")
        val twoWords = catalog.search("mercado pago")
        assertTrue(twoWords.isNotEmpty() && twoWords.all { "mercado" in it.searchKeyForTest() && "pago" in it.searchKeyForTest() }, "duas palavras: ${twoWords.map { it.slug }}")
    }

    @Test
    fun avisoDeLicencaEDisclaimerAcompanhamOAtivo() {
        assertTrue(File(assets, "LICENSE-simple-icons.txt").readText(Charsets.UTF_8).contains("CC0 1.0 Universal"))
        assertTrue(File(assets, "DISCLAIMER-simple-icons.md").readText(Charsets.UTF_8).contains("Brand Guidelines"))
        assertTrue(File(assets, "NOTICE.md").readText(Charsets.UTF_8).contains("16.35.0"))
    }

    private fun app.accord.ui.vendors.Brand.searchKeyForTest() = (listOf(title) + aliases + slug).joinToString(" ").lowercase()

    @Test
    fun soMarcasQuaseBrancasCaemNaTintaEscuraDaPlaca() {
        val hidden = catalog.brands.filterNot { it.officialColorVisible }
        assertTrue(hidden.size in 50..250, "esperava poucas marcas claras demais, vieram ${hidden.size}")
        for (slug in listOf("uber", "spotify", "netflix", "android", "ifood")) assertTrue(assertNotNull(catalog.find(slug)).officialColorVisible, slug)
        assertTrue(hidden.all { it.contrastOnWhite < Brand.MIN_CONTRAST_ON_PLATE })
    }
}
