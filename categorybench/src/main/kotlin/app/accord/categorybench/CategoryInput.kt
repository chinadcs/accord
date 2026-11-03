package app.accord.categorybench

import kotlinx.serialization.Serializable

/** Private corpus interchange shared by the two benches. Never written to result logs. */
@Serializable
data class CategoryInput(
    val id: String, val label: String, val split: String, val group: String, val style: String, val text: String,
)
