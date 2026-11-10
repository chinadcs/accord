---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0057 — Bloqueio local com credencial do Android

## Contexto

O usuário aprovou a proteção local do app no plano da sessão. Login do serviço e autorização do
espaço não impedem leitura de uma sessão já aberta por quem pega o aparelho desbloqueado.

## Decisão

Bloqueio local por padrão, biometria forte ou credencial do Android, sem PIN próprio. Usar APIs
nativas BiometricPrompt/KeyguardManager (minSdk 29), sem dependência adicional. Estado desbloqueado
somente em memória do processo; preferência de ativação só no aparelho. Relógio monotônico para
um minuto de tolerância; tela bloqueada exige autenticação imediatamente. Desativação exige nova
autenticação; sem credencial existe escolha explícita entre configurar Android e desativar.

Manter árvore de navegação e rascunhos montada, oculta e sem semântica/foco acessível atrás da tela
de bloqueio. FLAG_SECURE protege capturas/recentes. Cancelar/falhar autenticação mantém bloqueado.
Sincronização e operações já iniciadas continuam, sem efeito de autenticação remota.

## Consequências

Não é criptografia adicional do banco nem política de revogação offline (ADRs 0016/0023).
Credenciais permanecem sob o Android. FLAG_SECURE também impede capturas comuns do app.
Compatibilidade API 29 usa setDeviceCredentialAllowed; API 30+ usa allowedAuthenticators.

## Fontes e evidências

- [S8](../specs/plans/S8-protecao-dados-conversas-cofrinhos.md), Beads `accord-0grs.1`.
- Plano aprovado pelo usuário e pedido de implementação nesta sessão.
- [Android: biometria](https://developer.android.com/identity/sign-in/biometric-auth).
- Implementado em `app.accord.security` e na entrada `MainActivity`, com configuração em Mais.
- Validação local: 172 testes JVM do app (sete da política), zero falhas/ignorados, APK debug e
  três testes instrumentados `AppLockUiTest` no AVD Accord_Rapido_API_33. Testes de UI simulam o
  callback de sucesso, não comprovam autenticação do sistema. Galaxy/biometria/PIN/TalkBack e
  recentes físicos pendentes em `accord-0grs.2`. Nenhuma instalação pessoal foi alterada.
