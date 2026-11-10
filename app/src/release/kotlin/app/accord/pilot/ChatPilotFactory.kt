package app.accord.pilot

import android.content.Context

/** Release: sugestor local de categorias (ADR 0032) com o Chat por regras (ADR 0036); sem interpretador fake. */
fun createChatPilot(context: Context): ChatPilot = CategorySuggesterPilot(context)
