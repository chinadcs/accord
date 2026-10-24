package app.accord.core

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM/yyyy", PT_BR)

/** `Outubro/2026`, em português. Os textos exibidos a quem usa o aplicativo nascem em pt-BR. */
fun YearMonth.label(): String = format(MONTH_FORMAT).replaceFirstChar { it.titlecase(PT_BR) }
