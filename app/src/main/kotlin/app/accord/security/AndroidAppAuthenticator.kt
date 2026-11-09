package app.accord.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import app.accord.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Biometria/credencial somente no Android; nenhum PIN, senha ou material biométrico passa pelo app. */
class AndroidAppAuthenticator(private val activity: Activity) {
    private val keyguard get() = activity.getSystemService(KeyguardManager::class.java)
    val configured: Boolean get() = keyguard.isDeviceSecure
    val deviceLocked: Boolean get() = keyguard.isDeviceLocked
    private val mutableConfigured = MutableStateFlow(configured)
    val configuration = mutableConfigured.asStateFlow()
    private var cancellation: CancellationSignal? = null

    fun refresh() { mutableConfigured.value = configured }

    fun openSettings() {
        activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    @Suppress("DEPRECATION")
    fun authenticate(onResult: (Boolean) -> Unit) {
        if (cancellation != null) return
        if (!configured) { onResult(false); return }
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.security_auth_title))
            .setSubtitle(activity.getString(R.string.security_auth_subtitle))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
        } else {
            builder.setDeviceCredentialAllowed(true)
        }
        val signal = CancellationSignal()
        cancellation = signal
        try {
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cancellation !== signal) return
                    cancellation = null
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cancellation !== signal) return
                    cancellation = null
                    onResult(false)
                }
            })
        } catch (_: RuntimeException) {
            cancellation = null
            onResult(false)
        }
    }

    fun cancel() {
        val previous = cancellation
        cancellation = null
        previous?.cancel()
    }
}
