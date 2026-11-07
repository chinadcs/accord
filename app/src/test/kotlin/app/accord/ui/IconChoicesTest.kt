package app.accord.ui

import app.accord.core.space.IconAssociationView
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.ui.vendors.IconChoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Precedência da exibição (ADR 0047): escolha do lançamento, regra pelo nome, e só então o reconhecimento automático. */
class IconChoicesTest {
    private val uber = IconRef(IconSource.Brand, "uber")
    private val pizza = IconRef(IconSource.Generic, "pizza")

    private fun view(scope: IconScope, target: String, icon: IconRef) = IconAssociationView("id-$scope-$target", scope, target, icon, 1)

    @Test
    fun regraPeloNomeValeParaTodosOsLancamentosComOMesmoNomeSemCaixaNemAcento() {
        val choices = IconChoices(listOf(view(IconScope.Name, "padaria do ze", pizza)))
        assertEquals(pizza, choices.forEntry("a", "Padaria do Zé"))
        assertEquals(pizza, choices.forEntry("b", "  PADARIA   do ze "))
        assertNull(choices.forEntry("c", "Padaria da Ana"))
        assertTrue(choices.hasNameChoice("padaria do Zé"))
        assertFalse(choices.hasNameChoice("outra"))
    }

    @Test
    fun escolhaDoLancamentoVenceARegraPeloNome() {
        val choices = IconChoices(listOf(view(IconScope.Name, "padaria do ze", pizza), view(IconScope.Entry, "b", uber)))
        assertEquals(uber, choices.forEntry("b", "Padaria do Zé"))
        assertEquals(pizza, choices.forEntry("a", "Padaria do Zé"))
        assertTrue(choices.hasEntryChoice("b"))
        assertFalse(choices.hasEntryChoice("a"))
        assertFalse(choices.hasEntryChoice(null))
    }

    @Test
    fun semEscolhaOReconhecimentoAutomaticoDecide() {
        assertNull(IconChoices.Empty.forEntry("a", "Uber"))
        assertNull(IconChoices.Empty.forEntry(null, "Uber"))
    }
}
