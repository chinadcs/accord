package app.accord.core.chat


/** Experimento de verbalizadores: refs opacas derivadas dos nomes visíveis, sem mudar IDs de domínio. */
object SemanticRefs {
    const val VERSION = 2
    fun apply(context: ChatContext, version: Int = VERSION): ChatContext {
        require(version in 1..VERSION)
        val used = mutableSetOf<String>()
        fun slug(text: String) = ChatText.fold(text).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return context.copy(categories = context.categories.map { category ->
            val parent = category.parentId?.let { context.categoryById(it)?.name }
            val base = "c_" + (listOfNotNull(parent, category.name).joinToString("_") { slug(it) } + if (parent == null && version >= 2) "_geral" else "").take(60)
            var ref = base
            var suffix = 2
            while (!used.add(ref)) { ref = "${base}_${suffix++}" }
            category.copy(ref = ref)
        })
    }
}
