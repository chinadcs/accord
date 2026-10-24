---
status: proposed
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0023 — Serviço por RPC PostgreSQL, RLS e adaptador HTTP substituível

## Contexto

Associação, convite e versões precisam ser validados no serviço. O fornecedor está decidido,
mas escolher SDK, funções e fronteira de validação é uma decisão distinta. S2 propõe funções
transacionais e uma porta testável com fake.

## Proposta

Usar `RemoteSpaceService` em `:core.sync`, fake em `:remote` e adaptador Supabase com OkHttp e
kotlinx-serialization. Acessar funções RPC para criar espaço, gerar/consumir convite, enviar e
receber operações. Proteger tabelas com RLS; impedir escrita direta do cliente, passando pelas
funções autorizadas. Não exigir API HTTP própria ou Edge Functions no P0.

## Consequências

Uma suíte de contrato pode exercitar fake e serviço local. Autorização e atomicidade ficam
próximas dos dados, ao custo de lógica nas funções PostgreSQL e manutenção de serialização.
O limite de validação financeira no servidor continua aberto: apenas validar envelope,
referências e versão pode aceitar estado incoerente de um cliente defeituoso.

## Fontes e evidências

- [S2](../specs/S2-acesso-espaco-sincronizacao.md); Beads `accord-rsw.1`–`.3`;
  memória `s2-decisoes-de-implementacao`, explicitamente a validar.
- [Adaptador](../../remote/src/main/kotlin/app/accord/remote/SupabaseSpaceService.kt),
  [migração](../../supabase/migrations/20261024120000_espaco_e_sincronizacao.sql) e testes locais existem.
- Código e fechamentos dessas fatias não aprovam a proposta nem comprovam implantação hospedada.
  Rate limit, código ativo único e conta em um espaço permanecem propostas em S2.
- Acompanhamento do aceite: Beads `accord-awb`.
