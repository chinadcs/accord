package app.accord.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Pouca altura disponível, inclusive em janela dividida ou com teclado no app em retrato. */
const val COMPACT_HEIGHT_DP = 480

@Composable
fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
