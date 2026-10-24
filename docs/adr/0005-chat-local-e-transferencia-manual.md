---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0005 — Chat local com transferência manual do histórico

## Contexto

O orçamento é compartilhado, mas a conversa não precisa integrar o serviço de sincronização.
Isso separa dados financeiros estruturados de mensagens e transcrições. Portabilidade manual
do histórico foi posteriormente solicitada sem criar outro serviço.

## Decisão

Manter o Chat local, associado à conta, sem sincronizar mensagens/transcrições. Histórico não
tem prazo automático; limpar histórico não altera lançamentos e sair não o apaga. Permitir
exportar/importar por arquivo escolhido no seletor do sistema, sem serviço e, no P0, sem senha,
avisando que quem acessar o arquivo pode ler as conversas. Importar histórico não altera finanças.

## Consequências

Outra pessoa continua vendo os dados financeiros compartilhados, não a conversa de origem.
A cópia exportada sai da proteção de acesso do app; a advertência é parte da decisão.
Retenção automática de 90 dias permanece evolução, não configuração aprovada.

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S3](../specs/S3-chat-slm-modelo.md).
- Beads `accord-8ur`, `accord-yzr`, `accord-h7t`, `accord-or4`; memória
  `decisao-24-10-historico-chat`. A decisão inicial de Chat local antecede esses complementos.
- Histórico/exportação ainda pertencem a S3; `accord-pzv` guarda a evolução de retenção.
