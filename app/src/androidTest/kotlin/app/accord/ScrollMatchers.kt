package app.accord

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction

/** Rolagem vertical: a linha de chips de Lançamentos também rola, na horizontal, e `hasScrollAction()` sozinho acharia as duas. */
val hasVerticalScroll: SemanticsMatcher = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
