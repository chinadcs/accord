package app.accord.core.chat


/** Verbalizadores legíveis para o classificador; as identidades de domínio continuam opacas. */
object FriendlyCategories {
    fun apply(context: ChatContext): ChatContext {
        val used = mutableSetOf<String>()
        return context.copy(categories = context.categories.map { category ->
            val parent = category.parentId?.let { context.categoryById(it)?.name }
            val base = InterpreterPrompt.sanitize(listOfNotNull(parent, category.name).joinToString(" > ") + if (parent == null) " (geral)" else "")
            var ref = base
            var suffix = 2
            while (!used.add(ref)) ref = "$base (${suffix++})"
            category.copy(ref = ref, name = InterpreterPrompt.sanitize(category.name), hint = category.hint?.let(InterpreterPrompt::sanitize))
        })
    }
}
