package app.accord.access

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.AccessStore
import app.accord.persistence.AccessSnapshotCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Estado de acesso guardado neste aparelho: tokens da sessão, vínculo (conta, espaço) e caches.
 * Tudo vai cifrado com uma chave do Android Keystore que não sai do aparelho. A senha nunca chega
 * aqui. Se a chave deixar de valer (restauração de backup, troca de tela de bloqueio), o estado é
 * lido como vazio: a pessoa entra de novo e o mesmo espaço e a mesma fila são reencontrados.
 */
class SecureAccessStore(context: Context) : AccessStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun read(): AccessSnapshot = mutex.withLock { withContext(Dispatchers.IO) { load() } }

    override suspend fun update(block: (AccessSnapshot) -> AccessSnapshot): AccessSnapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val next = block(load())
            // commit(): o estado de sessão precisa estar gravado antes de a chamada voltar.
            check(prefs.edit().putString(KEY, encrypt(AccessSnapshotCodec.encode(next))).commit()) { "Não foi possível gravar o acesso" }
            next
        }
    }

    private fun load(): AccessSnapshot = runCatching {
        prefs.getString(KEY, null)?.let { AccessSnapshotCodec.decode(decrypt(it)) }
    }.getOrNull() ?: AccessSnapshot()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(text: String): String {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS = "accord_access"
        const val KEY = "snapshot"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "accord_access_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
