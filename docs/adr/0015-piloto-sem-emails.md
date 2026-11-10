---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0015 — Piloto sem envio de e-mails

## Contexto

O piloto de duas pessoas não terá infraestrutura de e-mail. Login por e-mail/senha e convite
por código continuam necessários, com uma alternativa explícita para problemas de acesso.

## Decisão

Desativar Confirm Email no piloto. Não enviar confirmação, recuperação, OTP, magic link nem
convite por e-mail; não configurar SMTP ou comprar domínio. E-mail identifica login sem
comprovar posse. Ajuda orienta atendimento manual fora do app; ações administrativas ocorrem
em ambiente confiável, nunca com chave administrativa no celular.

## Consequências

Não há recuperação automática por e-mail nem prova de posse do endereço. Atendimento manual
precisa verificar a pessoa por canal próprio. Revogação de sessões após redefinição e troca
de senha provisória ainda precisam ser verificadas no serviço. E-mails futuros exigem nova decisão.

## Fontes e evidências

- [Plano §§14/15](../specs/plans/plano-desenvolvimento.md), [S2](../specs/plans/S2-acesso-espaco-sincronizacao.md).
- [Configuração local](../../supabase/config.toml) e [roteiro manual](../../supabase/README.md)
  existem. `accord-rsw.9` acompanha documentação/fechamento; não é encerrado por este ADR.
- Data original não recuperada; configuração local não comprova a hospedada.
