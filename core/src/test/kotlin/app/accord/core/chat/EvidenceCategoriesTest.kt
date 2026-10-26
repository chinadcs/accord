package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceCategoriesTest {
    @Test
    fun `filhas contextuais exigem evidencia com casos positivos e identidades preservadas`() {
        val context = ChatBattery.realisticContext()
        fun ids(text: String) = EvidenceCategories.allowed(context, text).map { it.id }
        assertFalse("alimentacao.restaurantes" in ids("refeição pronta 27"))
        assertTrue("alimentacao" in ids("refeição pronta 27"))
        assertTrue("alimentacao.restaurantes" in ids("refeição no restaurante 27"))
        assertFalse("casa.manutencao" in ids("eletrodoméstico novo 850"))
        assertTrue("casa.manutencao" in ids("conserto do eletrodoméstico 180"))
        val custom = context.copy(categories = context.categories + CategoryItem("custom", "custom-id", "Manutenção"))
        assertTrue(EvidenceCategories.allowed(custom, "despesa 40").any { it.id == "custom-id" })
        EvidenceCategories.allowed(context, "despesa 40").forEach { assertEquals(context.categoryById(it.id), it) }
    }
}
