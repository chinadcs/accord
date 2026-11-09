package app.accord.security

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPolicyTest {
    @Test fun `inicia bloqueado e nao restaura autenticacao de outro processo`() {
        val policy = AppLockPolicy()
        assertTrue(policy.state.locked)
        policy.authenticated()
        assertFalse(policy.state.locked)
        assertTrue(AppLockPolicy().state.locked)
    }

    @Test fun `tolerancia termina exatamente em um minuto`() {
        val policy = AppLockPolicy()
        policy.authenticated()
        policy.background(1_000)
        policy.foreground(60_999, deviceLocked = false)
        assertFalse(policy.state.locked)
        policy.background(70_000)
        policy.foreground(130_000, deviceLocked = false)
        assertTrue(policy.state.locked)
    }

    @Test fun `tela bloqueada invalida tolerancia imediatamente`() {
        val policy = AppLockPolicy()
        policy.authenticated()
        policy.background(100)
        policy.screenOff()
        policy.foreground(101, deviceLocked = false)
        assertTrue(policy.state.locked)
    }

    @Test fun `android ainda bloqueado nunca libera conteudo`() {
        val policy = AppLockPolicy()
        policy.authenticated()
        policy.foreground(100, deviceLocked = true)
        assertTrue(policy.state.locked)
    }

    @Test fun `callback de autenticacao limpa tolerancia antiga`() {
        val policy = AppLockPolicy()
        policy.background(100)
        policy.authenticated()
        policy.foreground(100_000, deviceLocked = false)
        assertFalse(policy.state.locked)
    }

    @Test fun `desativacao explicita persiste politica e ativar bloqueia`() {
        val policy = AppLockPolicy(enabled = false)
        policy.background(0)
        policy.screenOff()
        policy.foreground(300_000, deviceLocked = true)
        assertFalse(policy.state.locked)
        policy.setEnabled(true)
        assertTrue(policy.state.locked)
    }

    @Test fun `eventos duplicados nao estendem tolerancia e retrocesso bloqueia`() {
        val policy = AppLockPolicy()
        policy.authenticated()
        policy.background(100)
        policy.background(50_000)
        policy.foreground(60_100, deviceLocked = false)
        assertTrue(policy.state.locked)
        policy.authenticated()
        policy.background(100)
        policy.foreground(99, deviceLocked = false)
        assertTrue(policy.state.locked)
    }
}
