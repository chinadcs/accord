package app.accord.ui.space

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore

/**
 * Abre a câmera externa para a pessoa ler o QR, copiar o código e colá-lo no Accord (ADR 0065).
 * Não captura imagem, recebe resultado nem pede permissão de câmera para o Accord.
 */
object InviteScanner {
    fun openCamera(context: Context): Boolean = try {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
