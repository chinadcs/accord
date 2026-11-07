package app.accord.ui.vendors

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.accord.core.space.IconRef
import app.accord.core.space.IconSource
import app.accord.ui.banks.BankCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Catálogos de ícones carregados uma vez por processo: marcas (Simple Icons), bancos (logos-bancos-br) e o reconhecedor
 * que os usa. Tudo vem dos ativos do aplicativo; nada é buscado na rede (ADR 0021, ADR 0047).
 */
class IconLibrary(val brands: BrandCatalog, val banks: BankCatalog, val matcher: IconMatcher) {
    /** Uma referência é conhecida quando existe no catálogo local; desconhecida (ex.: marca de versão mais nova) cai no ícone padrão. */
    fun isKnown(ref: IconRef): Boolean = when (ref.source) {
        IconSource.Brand -> brands.find(ref.key) != null
        IconSource.Bank -> banks.find(ref.key) != null
        IconSource.Generic -> true
    }

    companion object {
        const val BRANDS_ASSET = "vendors/simple-icons.tsv"
        const val GENERIC_ASSET = "vendors/generic-words.txt"

        @Volatile private var cached: IconLibrary? = null

        fun cachedOrNull(): IconLibrary? = cached

        fun build(brandLines: Sequence<String>, genericLines: Sequence<String>, banks: BankCatalog): IconLibrary {
            val brands = BrandCatalog.parse(brandLines)
            return IconLibrary(brands, banks, IconMatcher(brands, banks, IconMatcher.parseGeneric(genericLines)))
        }

        /** Leitura bloqueante (alguns milhões de caracteres): chamar fora da thread de interface. */
        fun load(context: Context): IconLibrary = cached ?: synchronized(this) {
            cached ?: run {
                val assets = context.applicationContext.assets
                val brands = assets.open(BRANDS_ASSET).bufferedReader(Charsets.UTF_8).use { r -> BrandCatalog.parse(r.lineSequence()) }
                val generic = assets.open(GENERIC_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val banks = BankCatalog.load(context.applicationContext)
                IconLibrary(brands, banks, IconMatcher(brands, banks, IconMatcher.parseGeneric(generic.lineSequence())))
            }.also { cached = it }
        }
    }
}

/** A biblioteca de ícones, ou nulo enquanto carrega (a primeira tela mostra o ícone padrão e troca quando ela chega). */
@Composable
fun rememberIconLibrary(): State<IconLibrary?> {
    val context = LocalContext.current.applicationContext
    return produceState(IconLibrary.cachedOrNull(), context) {
        if (value == null) value = withContext(Dispatchers.Default) { IconLibrary.load(context) }
    }
}
