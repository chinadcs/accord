package app.accord.core.importing

/**
 * The bank's own category label on a statement line (the card invoice prints one under each purchase) mapped to the initial categories of the
 * app. It is only a **suggestion** the person accepts explicitly (S4 rule 14, decision of 03/11/2026): nothing here classifies a movement. A label
 * that says nothing ("outros") or that has no clear counterpart has no entry, so no suggestion is made. The mapping is a proposal to adjust with use.
 */
object BankCategoryHints {
    /** Field kept in the original fields of a PDF line. */
    const val FIELD = "CATEGORY_HINT"

    /** Normalised label (no accents, upper case) to the key of an initial category (`core.space.CategorySeed`). */
    val seedKeyByLabel: Map<String, String> = mapOf(
        "SUPERMERCADO" to "alimentacao.mercado",
        "RESTAURANTE" to "alimentacao.restaurantes",
        "TRANSPORTE" to "transporte",
        "SAUDE" to "saude",
        "LAZER" to "lazer",
        "CASA" to "casa",
        "VESTUARIO" to "compras.roupas",
        "SERVICOS" to "servicos",
        "ELETRONICS" to "eletronicos",
        "ELETRONICOS" to "eletronicos",
    )

    fun normalize(label: String): String = BillingRules.upper(label)

    fun labelOf(movement: ExtractedMovement): String? =
        movement.originalFields.firstOrNull { it.first == FIELD }?.second?.takeIf { it.isNotBlank() }?.let(::normalize)
}
