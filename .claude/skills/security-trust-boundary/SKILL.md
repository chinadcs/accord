---
name: security-trust-boundary
description: 'Auditar fronteiras de confiança do Accord: associação ao espaço, convite, sessão, credenciais, dados
  locais e saídas de SLM/importação. Usar para autorização e privacidade, não para presumir papéis ou mecanismos
  de outro produto.'
---

# security-trust-boundary — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Quem pode agir e sobre qual dado

Ambos têm acesso igual no espaço do casal; isso não autoriza uma conta não associada. Testar
acesso direto ao serviço/banco, leitura/escrita com IDs alterados e convite sem autorização.
Código de convite não é uma chave administrativa nem substitui conta própria.

E-mail/senha via Supabase, sem envio de e-mails no piloto. Confirm Email deve ser desativado;
não afirmar posse do endereço. Ajuda orienta atendimento manual fora do app. Nenhuma chave
administrativa, senha de banco ou credencial de operação entra no binário/configuração pública.

SLM/arquivo/resposta remota são dados não confiáveis. Validação de ações e regras financeiras
não podem ser substituídas por prompt. Chat é somente local; não sincronizar transcrições nem
mandá-las para IA externa. PDFs/OFX completos não são enviados ou retidos permanentemente.

Revisar diagnósticos, arquivos temporários e fixtures para exposição de conversa/dados bancários.
Não supor que política de armazenamento/acesso offline já está resolvida. Explicitar ameaça,
fronteira, teste positivo/negativo e o que permanece sem verificação.
