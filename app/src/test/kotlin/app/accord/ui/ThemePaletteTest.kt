package app.accord.ui

import app.accord.ui.theme.ThemePalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePaletteTest {
    @Test
    fun `restam três paletas`() {
        assertEquals(listOf("Accord", "Catppuccin", "MidnightDusk"), ThemePalette.entries.map { it.name })
    }

    @Test
    fun `paleta removida ou ausente volta à Accord`() {
        for (stored in listOf("Nord", "Tako", "TidalWave", "TokyoNight", "", null)) {
            assertEquals(ThemePalette.Accord, ThemePalette.fromStored(stored), "gravado: $stored")
        }
        assertEquals(ThemePalette.MidnightDusk, ThemePalette.fromStored("MidnightDusk"))
    }
}
