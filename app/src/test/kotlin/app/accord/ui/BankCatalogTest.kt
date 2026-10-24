package app.accord.ui

import app.accord.core.space.BankRef
import app.accord.ui.banks.BankCatalog
import app.accord.ui.banks.displayName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankCatalogTest {
    private val sample = "60701190\t341\tItaú\t1\n00000000\t001\tBanco do Brasil\t1\n54647259\t644\t321 SCD S.A.\t0\n\nlinha-quebrada\n"

    @Test
    fun linhasInvalidasSaoIgnoradasEOsCamposVemDoCatalogo() {
        val catalog = BankCatalog.parse(sample)
        assertEquals(3, catalog.entries.size)
        val itau = assertNotNull(catalog.find("60701190"))
        assertEquals("341", itau.compe)
        assertTrue(itau.hasLogo)
        assertEquals(false, catalog.find("54647259")!!.hasLogo)
    }

    @Test
    fun buscaIgnoraAcentoECaixaEAceitaCodigo() {
        val catalog = BankCatalog.parse(sample)
        assertEquals(listOf("Itaú"), catalog.search("ITAU").map { it.name })
        assertEquals(listOf("Banco do Brasil"), catalog.search("001").map { it.name })
        assertEquals(3, catalog.search("").size)
        assertTrue(catalog.search("inexistente").isEmpty())
    }

    @Test
    fun referenciaDoCatalogoUsaIspbENuncaONomeParaIdentificar() {
        val ref = BankCatalog.parse(sample).find("60701190")!!.toRef()
        assertEquals(BankRef.Catalog("60701190", "Itaú"), ref)
        assertEquals("Itaú", ref.displayName())
        assertEquals("Outro", BankRef.Other("Outro").displayName())
    }

    @Test
    fun catalogoEmpacotadoTemEntradasUnicasELogosPresentes() {
        val assets = File("src/main/assets/banks")
        val catalog = BankCatalog.parse(File(assets, "catalog.tsv").readText(Charsets.UTF_8))
        assertEquals(catalog.entries.size, catalog.entries.map { it.ispb }.toSet().size)
        assertNotNull(catalog.find("60701190"), "Itaú Unibanco precisa estar no catálogo (prioridade do piloto)")
        catalog.entries.filter { it.hasLogo }.forEach { assertTrue(File(assets, "logos/${it.ispb}.png").exists(), "logo de ${it.name}") }
        assertNull(catalog.find("00000001"))
    }
}
