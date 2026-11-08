package app.accord.ui.space

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Leitura do QR do convite pelo leitor de código do Google Play Services (ADR 0053): a câmera é do
 * leitor do sistema, então o Accord não pede permissão de câmera. Sem Play Services ou com a leitura
 * recusada, quem chama volta à digitação do código.
 */
object InviteScanner {
    sealed interface Result {
        data class Scanned(val raw: String?) : Result
        data object Canceled : Result
        data object Unavailable : Result
    }

    fun scan(context: Context, onResult: (Result) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        runCatching { GmsBarcodeScanning.getClient(context, options).startScan() }
            .onFailure { onResult(Result.Unavailable) }
            .onSuccess { task ->
                task.addOnSuccessListener { onResult(Result.Scanned(it.rawValue)) }
                    .addOnCanceledListener { onResult(Result.Canceled) }
                    .addOnFailureListener { onResult(Result.Unavailable) }
            }
    }
}
