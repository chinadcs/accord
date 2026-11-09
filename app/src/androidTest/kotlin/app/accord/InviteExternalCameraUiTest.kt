package app.accord

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.accord.core.sync.AccessState
import app.accord.core.sync.EntryStage
import app.accord.ui.space.InviteScanner
import app.accord.ui.space.SpaceScreen
import app.accord.ui.space.SpaceTab
import app.accord.ui.space.SpaceUi
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic invitation only; no camera or clipboard content is read by these tests. */
class InviteExternalCameraUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(openCamera: () -> Boolean = { false }, onJoin: () -> Unit = {}) {
        compose.setContent {
            var ui by remember { mutableStateOf(SpaceUi(tab = SpaceTab.Join, code = "ABCD2345")) }
            AccordTheme {
                SpaceScreen(
                    ui, AccessState(stage = EntryStage.NoSpace), PaddingValues(), true,
                    onTab = { ui = ui.copy(tab = it) }, onName = {}, onCode = { ui = ui.copy(code = it) },
                    onCreate = {}, onJoin = onJoin, onGenerate = {}, onRefresh = {}, onCopy = {},
                    onContinue = {}, onBack = null, onOpenCamera = openCamera,
                )
            }
        }
    }

    @Test fun guideExplainsCopyPasteAndClosingPreservesCode() {
        show()
        compose.onNodeWithText("ABCD2345").performClick().performTextReplacement("EFGH2345")
        compose.onNodeWithTag("space-scan-qr").performScrollTo().performClick()
        compose.onNodeWithText("Aponte a câmera do celular", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Voltar ao código").performClick()
        compose.onNodeWithText("EFGH2345").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Usar câmera do celular").assertIsDisplayed()
    }

    @Test fun successfulExternalOpeningDoesNotJoinAndPreservesCode() {
        var opens = 0
        var joins = 0
        show(openCamera = { opens++; true }, onJoin = { joins++ })
        compose.onNodeWithTag("space-scan-qr").performClick()
        compose.onNodeWithText("Abrir câmera").performClick()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(0, joins) }
        compose.onNodeWithText("ABCD2345").assertIsDisplayed()
    }

    @Test fun unavailableCameraKeepsRecoveryAndManualCode() {
        show()
        compose.onNodeWithTag("space-scan-qr").performClick()
        compose.onNodeWithText("Abrir câmera").performClick()
        compose.onNodeWithText("Não foi possível abrir a câmera.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Voltar ao código").performClick()
        compose.onNodeWithText("ABCD2345").assertIsDisplayed()
    }

    @Test fun externalIntentDoesNotCaptureImageOrRequestResult() {
        var launched: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        assertTrue(InviteScanner.openCamera(context))
        val intent = checkNotNull(launched)
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.extras == null)
    }

    @Test fun missingCameraAndDeniedOpeningAreRecoverable() {
        val missing = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        val denied = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw SecurityException() }
        }
        assertFalse(InviteScanner.openCamera(missing))
        assertFalse(InviteScanner.openCamera(denied))
        val permissions = checkNotNull(ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
            .getPackageInfo(BuildConfig.APPLICATION_ID, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions)
        assertFalse(permissions.any { it == android.Manifest.permission.CAMERA })
    }
}
