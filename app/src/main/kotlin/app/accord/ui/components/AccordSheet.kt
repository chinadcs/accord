package app.accord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Painel inferior comum (spec 20), extraído de `CategoryEditSheet`: título com ✕ e o rodapé [footer] fixos, só o miolo rola, e o teclado não cobre
 * o rodapé. [blockHide] recusa o arrasto para baixo (alterações não salvas); a pergunta de descarte fica com quem chama, em [onRequestClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccordSheet(
    title: String,
    onRequestClose: () -> Unit,
    blockHide: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val blocked by rememberUpdatedState(blockHide)
    ModalBottomSheet(
        onDismissRequest = onRequestClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !blocked }),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = accordSheetColor(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onRequestClose, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.register_picker_close))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3), content = content)
            footer()
        }
    }
}
