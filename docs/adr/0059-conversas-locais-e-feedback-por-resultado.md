---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-08
---

# ADR 0059 — Conversas locais e feedback por resultado

## Contexto

No plano aprovado, o usuário substituiu a conversa única por conversas criáveis, renomeáveis e
excluíveis, retomando a última utilizada. Também escolheu múltiplos motivos opcionais no joinha
negativo e revisão das regras antes de qualquer mudança do interpretador.

## Decisão

- Identidade estável de conversa local por conta/espaço, com nome, seleção, rascunho e posição de
  leitura persistidos. Cada mensagem aponta sua conversa; perguntas/recibos/tentativas seguem a
  mensagem. O coordenador continua único por banco, com fila serial, e monta contexto somente
  da conversa da mensagem, inclusive após trocar de tela durante uma interpretação.
- Exclusão é serializada com aplicação de comandos e cancela tentativas ainda não aplicadas.
  Apaga mensagens, recibos, tentativas, votos e rascunho daquela conversa, preservando finanças
  e perguntas abertas estruturadas para Revisar. Não mantém o texto da mensagem apagada.
  Limpar todo o histórico segue a mesma proteção. Coleta enviada permanece em Privacidade,
  com aviso na confirmação, conforme escolha explícita do usuário.
- Migração Room 20→21 aditiva: histórico existente em Histórico anterior. Transferência do Chat
  versão 2 preserva conversas; lê versão 1 e mescla por IDs sem executar ações financeiras.
- Joinha aceita conjunto de motivos, incluindo Não era lançamento. Um voto vigente por recibo,
  associado ao identificador de operação avaliado, para não atribuir avaliação a outra versão.
  Votos antigos de motivo único são preservados. Sem alteração automática de regra/peso/corte.
  Retenção local atual de 30 dias permanece; coleta opcional/consentida e retenção remota seguem
  ADR 0054, cuja implementação e texto jurídico continuam em `accord-g5r8`.

## Consequências

Complementa ADRs 0005/0027 e 0054 sem compartilhar conversas entre pessoas/aparelhos. Recibos
continuam apontando registros financeiros compartilhados; exclusão de Chat não equivale a
excluir lançamento nem apagar coleta. Pergunta sem mensagem de origem é resolvida em Revisar,
não usada como contexto de outra conversa. O SLM opcional segue adiado (ADR 0036).

## Fontes e evidências

- [S8](../specs/S8-protecao-dados-conversas-cofrinhos.md), Beads `accord-0grs.4`.
- Decisões na conversa do plano, antes da autorização de implementação.
- Implementado em 09/11/2026: Room 21, seleção/nome/rascunho/posição por conversa, contexto
  isolado, exclusão serializada com a fila e votos de múltiplos motivos vinculados ao resultado.
  Revisar retoma rascunho no formulário, categoria no lançamento e correções com seus alvos
  estruturados (com conclusão explícita), sem reter a mensagem apagada. Leitor do espaço
  encerrado agrupa o histórico por conversa e continua sem comandos financeiros.
- JVM: 706 core, 672 persistence, 147 remote e 175 app, sem falha/skip. Os sete testes novos de
  persistence cobrem migração 20→21, metadados, contexto, exclusão durante resposta tardia,
  preservação/classificação sem mensagem, rascunho e transferência v1/v2 sem reaplicar finanças.
  AVD API 33: `ChatConversationsUiTest` (criar, renomear, alternar, recriar e excluir), aprovado.
  Validação física/TalkBack/fonte nos dois Galaxy continua em `accord-0grs.2`.
- Automação física de 09/11/2026 (`accord-0grs.2.1`): conversas aprovadas em retrato no S23;
  feedback com múltiplos motivos, recriação e retenção de 30 dias aprovado no Room Android.
  Paisagem/200% e teclado têm falhas em diagnóstico (`accord-9zr3`); TalkBack falado foi
  adiado pelo usuário. [Relatório local](../s23-validacao-automatica-2026-11-09.md).
