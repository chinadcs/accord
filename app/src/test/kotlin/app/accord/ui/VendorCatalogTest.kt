package app.accord.ui

import app.accord.ui.vendors.Vendor
import app.accord.ui.vendors.VendorCatalog
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VendorCatalogTest {
    @Test
    fun reconheceMarcaLiteralEmDescricoesComPontuacaoEAcentos() {
        listOf("Uber", "UBER *TRIP", "corrida de Uber", "CÓRRIDA de UBER").forEach {
            assertEquals(Vendor.Uber, VendorCatalog.matchExplicitDescription(it), it)
        }
        listOf("Spotify Premium", "spotify.com", "SPOTIFY / mensalidade").forEach {
            assertEquals(Vendor.Spotify, VendorCatalog.matchExplicitDescription(it), it)
        }
    }

    @Test
    fun naoConfundeMarcaComParteDeOutraPalavra() {
        listOf("Uberlândia", "uberizar", "SpotifyAB", "Superuber", "mercado").forEach {
            assertNull(VendorCatalog.matchExplicitDescription(it), it)
        }
    }

    @Test
    fun descricaoComDuasMarcasNaoEscolheUmaArbitrariamente() {
        assertNull(VendorCatalog.matchExplicitDescription("Uber + Spotify"))
        assertNull(VendorCatalog.matchExplicitDescription("SPOTIFY.COM / Uber *TRIP"))
    }

    @Test
    fun idDoCatalogoExigeSlugExato() {
        assertEquals(Vendor.Uber, VendorCatalog.fromKey("uber"))
        assertEquals(Vendor.Spotify, VendorCatalog.fromKey("spotify"))
        listOf("UBER", "spotify.com", "../uber", "vendors/spotify", "unknown", "").forEach {
            assertNull(VendorCatalog.fromKey(it), it)
        }
    }

    @Test
    fun ativosEmpacotadosCorrespondemAoCatalogoEManifestoDeProveniencia() {
        val assets = File("src/main/assets/vendors")
        val manifest = Json.parseToJsonElement(File(assets, "catalog.json").readText(Charsets.UTF_8)).jsonObject
        assertEquals("Simple Icons", manifest.getValue("library").jsonPrimitive.content)
        assertEquals("16.35.0", manifest.getValue("version").jsonPrimitive.content)

        val icons = manifest.getValue("icons").jsonArray.map { it.jsonObject }
        val keys = icons.map { it.getValue("key").jsonPrimitive.content }
        assertEquals(keys.size, keys.toSet().size, "Cada marca deve ter um ID único")
        assertEquals(VendorCatalog.entries.map { it.key }.toSet(), keys.toSet())
        assertEquals(
            keys.map { "vendor_$it.xml" }.toSet(),
            File("src/main/res/drawable").listFiles().orEmpty()
                .filter { it.name.startsWith("vendor_") && it.extension == "xml" }
                .map { it.name }.toSet(),
        )

        icons.forEach { icon ->
            val key = icon.getValue("key").jsonPrimitive.content
            val vendor = requireNotNull(VendorCatalog.fromKey(key))
            assertEquals(vendor.displayName, icon.getValue("name").jsonPrimitive.content)
            assertTrue(icon.getValue("upstreamSvg").jsonPrimitive.content.contains("/16.35.0/icons/$key.svg"))

            val drawableName = icon.getValue("drawable").jsonPrimitive.content
            assertEquals("vendor_$key", drawableName)
            val drawable = File("src/main/res/drawable/$drawableName.xml")
            assertTrue(drawable.isFile, "Drawable local ausente: $drawableName")
            // Git pode converter quebras de linha no Windows; o manifesto usa UTF-8/LF.
            val canonicalBytes = drawable.readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
            val actualHash = MessageDigest.getInstance("SHA-256").digest(canonicalBytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals(icon.getValue("drawableSha256").jsonPrimitive.content, actualHash, drawableName)
        }

        val license = File(assets, "LICENSE-simple-icons.txt")
        val disclaimer = File(assets, "DISCLAIMER-simple-icons.md")
        assertTrue(license.isFile && license.readText(Charsets.UTF_8).contains("CC0 1.0 Universal"))
        assertTrue(disclaimer.isFile && disclaimer.readText(Charsets.UTF_8).contains("Brand Guidelines"))
    }
}
