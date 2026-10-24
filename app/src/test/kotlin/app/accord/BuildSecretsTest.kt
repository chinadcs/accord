package app.accord

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse

/** O que o build leva para o aplicativo é só a URL e a chave pública; nunca a chave secreta do serviço. */
class BuildSecretsTest {
    @Test
    fun `a chave embutida no build não é secreta nem tem papel de serviço`() {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        assertFalse(key.startsWith("sb_secret_"), "a chave secreta não pode entrar no aplicativo")
        val parts = key.split('.')
        if (parts.size == 3) {
            val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))) }.getOrDefault("")
            assertFalse("service_role" in payload, "JWT com papel de serviço no aplicativo")
        }
    }
}
