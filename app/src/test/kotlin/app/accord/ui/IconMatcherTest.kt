package app.accord.ui

import app.accord.core.space.CategorySeed
import app.accord.core.space.IconRef
import app.accord.core.space.IconSource
import app.accord.ui.banks.BankCatalog
import app.accord.ui.vendors.BrandCatalog
import app.accord.ui.vendors.IconMatcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reconhecimento automático e conservador de marca ou banco pelo nome do lançamento (ADR 0047). */
class IconMatcherTest {
    private val vendors = File("src/main/assets/vendors")
    private val brands = BrandCatalog.parse(File(vendors, "simple-icons.tsv").readText(Charsets.UTF_8).lineSequence())
    private val banks = BankCatalog.parse(File("src/main/assets/banks/catalog.tsv").readText(Charsets.UTF_8))
    private val generic = IconMatcher.parseGeneric(File(vendors, "generic-words.txt").readText(Charsets.UTF_8).lineSequence())
    private val matcher = IconMatcher(brands, banks, generic)

    private fun brand(slug: String) = IconRef(IconSource.Brand, slug)

    @Test
    fun reconheceMarcaLiteralEmDescricoesComPontuacaoEAcentos() {
        listOf("Uber", "UBER *TRIP", "corrida de Uber", "CÓRRIDA de UBER", "UBER   *TRIP HELP.U", "Uber* Trip", "UBER DO BRASIL TECNOLOGIA", "uber 12/03")
            .forEach { assertEquals(brand("uber"), matcher.match(it), it) }
        listOf("Spotify Premium", "spotify.com", "SPOTIFY / mensalidade", "DL *SPOTIFY", "SPOTIFY P1AB23", "Spotify - assinatura família")
            .forEach { assertEquals(brand("spotify"), matcher.match(it), it) }
    }

    @Test
    fun reconheceOutrasMarcasDoCatalogoCompleto() {
        assertEquals(brand("netflix"), matcher.match("Netflix 55,90"))
        assertEquals(brand("ifood"), matcher.match("iFood pedido 1234"))
        assertEquals(brand("youtube"), matcher.match("YouTube Premium"))
        // O Simple Icons 16.35.0 não tem Mercado Livre: loja sem logo no catálogo não ganha logo de outra empresa (accord-vt5).
        assertNull(matcher.match("Mercado Livre 137 no cartão"))
        // Mercado Pago existe como marca e como banco local: vale o logo do banco.
        assertEquals(IconRef(IconSource.Bank, "10573521"), matcher.match("Mercado Pago"))
    }

    @Test
    fun descritorDeFaturaDoUberReconheceAMarcaColadaAoRestoDoNome() {
        // Descritores reais do cartão: "Uber Rides São Paulo" chega como uma palavra só (accord-v67i).
        listOf("Dl *Uberridessao Paulobra", "Dl*uberridessao Paulobra", "Uberridessao", "Uber Uber *Trip Help.usao Paulobra")
            .forEach { assertEquals(brand("uber"), matcher.match(it), it) }
    }

    @Test
    fun noveNoveFoodNaoTemLogoMasTemIniciaisProprias() {
        val description = "99food *42.434.021 Fiabelo Horizontbra"
        assertNull(matcher.match(description))
        assertEquals("99", matcher.initialsFor(description))
        assertEquals("99", matcher.initialsFor("99food *Palabrasa Barbelo Horizontbra"))
        assertNull(matcher.initialsFor("Uber Trip"))
        assertNull(matcher.initialsFor("99 Taxi"))
    }

    @Test
    fun naoConfundeMarcaComParteDeOutraPalavra() {
        listOf("Uberlândia", "uberizar", "SpotifyAB", "Superuber", "mercado", "Uberaba", "Spotfy", "Sputnik", "Uberlandia Ride").forEach {
            assertNull(matcher.match(it), it)
        }
    }

    @Test
    fun semEvidenciaDeMarcaNaoHaLogo() {
        listOf("", "   ", "streaming de música", "corrida de aplicativo", "compras do mês", "pizza", "12345").forEach {
            assertNull(matcher.match(it), it)
        }
    }

    @Test
    fun palavrasComunsEMarcasCurtasNuncaReconhecemSozinhas() {
        // "Academia", "Metrô" e "Pix" são palavras do dia a dia e de meio de pagamento, não as marcas homônimas.
        listOf("Academia", "mensalidade academia", "Metrô", "recarga metro", "Pix", "pix 50", "Target", "Vivo").forEach {
            assertNull(matcher.match(it), it)
        }
        // Continuam no catálogo, só não são reconhecidas sozinhas: aparecem na busca do seletor.
        assertTrue(brands.search("academia").any { it.slug == "academia" })
    }

    @Test
    fun descricaoComDuasMarcasNaoEscolheUmaArbitrariamente() {
        assertNull(matcher.match("Uber + Spotify"))
        assertNull(matcher.match("SPOTIFY.COM / Uber *TRIP"))
    }

    @Test
    fun fraseMaisLongaVenceAContidaNela() {
        val eats = brands.search("uber eats").firstOrNull { it.title.equals("Uber Eats", ignoreCase = true) }
        if (eats != null) assertEquals(brand(eats.slug), matcher.match("UBER EATS pedido"))
        assertEquals(brand("uber"), matcher.match("Uber Trip"))
    }

    @Test
    fun bancoDoCatalogoLocalEReconhecidoEPrefereOLogoDoBancoSobreAMarcaHomonima() {
        val nubank = matcher.match("Nubank fatura")
        assertEquals(IconSource.Bank, nubank?.source)
        assertEquals("Nubank", banks.find(nubank!!.key)?.name?.substringBefore(" ").let { it })
    }

    @Test
    fun bancoSemLogoOuComNomeComumNaoReconhece() {
        assertNull(matcher.match("caixa"))
        assertNull(matcher.match("Original"))
    }

    @Test
    fun nomesDasCategoriasIniciaisNaoViramMarca() {
        // Rede de segurança: o que o app já oferece como categoria nunca pode escolher logo sozinho.
        val names = CategorySeed.items.map { it.name }
        names.forEach { assertNull(matcher.match(it), it) }
    }
}
