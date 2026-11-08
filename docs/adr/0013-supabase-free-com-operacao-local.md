---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0013 — Supabase Auth e PostgreSQL Free com operação local

## Contexto

O casal precisa compartilhar finanças com contas individuais e busca infraestrutura sem
mensalidade. Serviço hospedado pode ficar indisponível ou mudar condições; não pode ser o único
lugar onde uma alteração ainda não reconhecida existe. A comparação histórica de fornecedores
não está detalhada nas fontes.

## Decisão

Usar Supabase hospedado no plano Free, com Auth e PostgreSQL, mantendo banco/fila persistidos
no aparelho. Falha, pausa ou limite do serviço preservam dados locais e operações pendentes.
Se exigir pagamento, rever com o usuário, sem cobrança automática. Não usar o serviço para Chat
ou arquivos bancários completos.

> **Revisão (08/11/2026):** ponto "não usar o serviço para Chat" revisado pelo [ADR 0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md); arquivos bancários seguem fora. Este registro continua como histórico.

## Consequências

Gratuidade permanente não é garantida. Condições comerciais precisam ser consultadas quando
uma nova decisão depender delas. Ambiente local de desenvolvimento não comprova configuração
do projeto hospedado. RPC/SDK e desenho concreto de permissões estão propostos no
[ADR 0023](0023-servico-rpc-rls-e-adaptador-http.md).

## Fontes e evidências

- [Plano §§14/15](../plano-desenvolvimento.md), [S2](../specs/S2-acesso-espaco-sincronizacao.md).
- Beads `accord-rsw`; [ambiente local](../../supabase/README.md) existente. Este ADR não cria
  projeto hospedado nem reverifica preços; preserva a decisão histórica, de data não recuperada.
