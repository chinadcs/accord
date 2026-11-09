package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.Money
import app.accord.core.space.Classification
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.IconAssociationView
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.core.space.PaymentMethod
import app.accord.ui.entries.EntriesFilterState
import app.accord.ui.entries.EntriesScreen
import app.accord.ui.entries.EntriesStatus
import app.accord.ui.entries.EntriesUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import app.accord.ui.vendors.ChooseIconFlow
import app.accord.ui.vendors.IconActions
import app.accord.ui.vendors.IconChoices
import app.accord.ui.vendors.IconLibrary
import app.accord.ui.vendors.LocalIconActions
import app.accord.ui.vendors.LocalIconChoices
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * accord-nq8.6: revisão visual do seletor e da lista nos dois temas e em fonte ampliada, com estado fixo. As capturas (tela inteira, inclusive o
 * diálogo) vão para /sdcard/Download/icones/ e as medições, para o log "ICONMEASURE". Medidas do AVD, não do Galaxy S20 FE nem do S23 FE.
 */
class IconVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private class NoActions : IconActions {
        override suspend fun set(scope: IconScope, target: String, icon: IconRef) = true
        override suspend fun clear(scope: IconScope, target: String) = true
    }

    @Before fun preload() {
        IconLibrary.load(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "icones").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Thread.sleep(300)
    }

    /** [fontScale] nulo usa o do sistema (necessário no diálogo, que tem janela própria e ignora a densidade da composição). */
    private fun host(theme: ThemePreference, fontScale: Float?, choices: IconChoices = IconChoices.Empty, content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides if (fontScale == null) density else Density(density.density, fontScale)) {
            AccordTheme(theme) {
                CompositionLocalProvider(LocalIconActions provides NoActions(), LocalIconChoices provides choices) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun picker(theme: ThemePreference, fontScale: Float?, tag: String) {
        host(theme, fontScale) { ChooseIconFlow("entry-1", "Padaria do Zé") {} }
        compose.onNodeWithTag("icon-picker-grid").assertIsDisplayed()
        shot("seletor-marcas-$tag")
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").assertIsDisplayed().assertHasClickAction()
        shot("seletor-busca-$tag")
        compose.onNodeWithTag("icon-picker-search").performTextReplacement("zzzzqqqq")
        shot("seletor-vazio-$tag")
        compose.onNodeWithTag("icon-picker-search").performTextReplacement("")
        compose.onNodeWithTag("icon-tab-banks").performClick()
        shot("seletor-bancos-$tag")
        compose.onNodeWithTag("icon-tab-generic").performClick()
        shot("seletor-genericos-$tag")
        compose.onNodeWithTag("icon-tab-brands").performClick()
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").performClick()
        compose.onNodeWithText("Usar este ícone nos próximos?").assertIsDisplayed()
        shot("pergunta-$tag")
    }

    private fun list(theme: ThemePreference, fontScale: Float?, tag: String) {
        val day = LocalDate.of(2026, 10, 20)
        fun row(id: String, description: String) = EntryRow(
            id, EntryKind.DirectPayment, description, null, Money(2_500), day, null, PaymentMethod.Pix, null, null, setOf(EntrySituation.Paid), "person",
        )
        val rows = listOf(
            row("a", "Padaria do Zé"), row("b", "padaria do ze"), row("c", "UBER *TRIP"), row("d", "Spotify Premium"), row("e", "Netflix 55,90"),
            row("f", "Nubank fatura"), row("g", "Mercearia"), row("h", "Uber + Spotify"),
        )
        val month = YearMonth.of(2026, 10)
        val state = EntriesUiState(
            filter = EntriesFilterState(month, month), rows = rows, total = rows.size,
            summary = EntrySummary(Money(20_000), Money(2_500), Money.ZERO, Money(2_500)), status = EntriesStatus.Ready,
        )
        val choices = IconChoices(
            listOf(
                IconAssociationView("r1", IconScope.Name, "padaria do ze", IconRef(IconSource.Generic, "pizza"), 1),
                IconAssociationView("r2", IconScope.Entry, "b", IconRef(IconSource.Generic, "coffee"), 1),
            ),
        )
        host(theme, fontScale, choices) { EntriesScreen(state, PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
        shot("lista-$tag")
    }

    @Test fun seletorClaro() = picker(ThemePreference.Light, 1f, "claro")

    @Test fun seletorEscuro() = picker(ThemePreference.Dark, 1f, "escuro")

    /** O diálogo tem janela própria e ignora a escala de densidade da composição: rodar com `settings put system font_scale 2.0`. */
    @Test fun seletorFonteSistema200() = picker(ThemePreference.Light, null, "fonte200")

    @Test fun listaClaro() = list(ThemePreference.Light, 1f, "claro")

    @Test fun listaEscuro() = list(ThemePreference.Dark, 1f, "escuro")

    @Test fun listaFonteSistema200() = list(ThemePreference.Light, null, "fonte200")

    /** Medidas no AVD: carga do catálogo, busca nas ≈3,5 mil marcas e primeira composição do seletor. */
    @Test fun medidas() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coldStart = System.nanoTime()
        val fresh = app.accord.ui.vendors.IconLibrary.build(
            context.assets.open(IconLibrary.BRANDS_ASSET).bufferedReader(Charsets.UTF_8).use { it.readLines() }.asSequence(),
            context.assets.open(IconLibrary.GENERIC_ASSET).bufferedReader(Charsets.UTF_8).use { it.readLines() }.asSequence(),
            app.accord.ui.banks.BankCatalog.load(context),
        )
        val loadMs = (System.nanoTime() - coldStart) / 1_000_000
        fun searchMs(q: String): Long {
            val start = System.nanoTime(); fresh.brands.search(q); return (System.nanoTime() - start) / 1_000_000
        }
        val searches = listOf("", "u", "uber", "mercado pago", "zzzz").associateWith { searchMs(it) }
        val matchStart = System.nanoTime()
        repeat(200) { fresh.matcher.match("UBER *TRIP HELP.U 12/03 São Paulo") }
        val matchUs = (System.nanoTime() - matchStart) / 200 / 1_000
        val openStart = System.nanoTime()
        host(ThemePreference.Light, 1f) { ChooseIconFlow("entry-1", "Padaria do Zé") {} }
        compose.onNodeWithTag("icon-picker-grid").assertIsDisplayed()
        val openMs = (System.nanoTime() - openStart) / 1_000_000
        val text = "marcas=${fresh.brands.brands.size} cargaMs=$loadMs buscaMs=$searches reconhecerUs=$matchUs abrirSeletorMs=$openMs"
        android.util.Log.i("ICONMEASURE", text)
        File(context.getExternalFilesDir(null), "icones-medidas.txt").writeText(text)
        assertTrue(fresh.brands.brands.size > 3000)
    }
}
