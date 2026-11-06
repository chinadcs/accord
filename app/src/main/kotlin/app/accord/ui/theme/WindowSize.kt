package app.accord.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Abaixo disto a janela é a paisagem de um celular: barra inferior, compositor e cabeçalho disputam a altura. */
const val COMPACT_HEIGHT_DP = 480

@Composable
fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
