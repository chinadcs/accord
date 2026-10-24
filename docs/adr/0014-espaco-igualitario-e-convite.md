---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0014 — Espaço de duas pessoas, igualitário e autorizado no serviço

## Contexto

Compartilhar orçamento não significa compartilhar senha. Restringir acesso só pelas telas
deixa o banco exposto a chamadas diretas; dar administração a uma pessoa contraria o uso igualitário.

## Decisão

Cada pessoa tem conta própria de e-mail/senha. Um espaço recebe no máximo duas pessoas, com
mesmos poderes sobre dados e configurações, sem gastos privados ou cotas individuais. A segunda
entra por código de 8 caracteres alfanuméricos maiúsculos, uso único e validade de 3 dias,
validado e consumido no serviço/banco. Conhecer um ID não concede autorização.

## Consequências

É necessário verificar associação em leituras/escritas e impedir consumo concorrente indevido.
Limite de tentativas, normalização, código ativo único e uma conta em no máximo um espaço
continuam propostas de S2; não recebem aceite neste registro. A política detalhada de acesso
local também permanece aberta. Credenciais administrativas ficam fora do aplicativo.

## Fontes e evidências

- [Plano §§2/14](../plano-desenvolvimento.md), [S2](../specs/S2-acesso-espaco-sincronizacao.md).
- Memória Beads `decisoes-23-10-fatura-chat-convite` (complemento de 23/10/2026);
  `accord-rsw.1`/`.2` documentam implementação. Data única da decisão-base não recuperada.
- [Testes de permissão locais](../../supabase/tests/database/01_permissoes.test.sql) existem;
  não foram executados nesta entrega e não comprovam o serviço hospedado.
