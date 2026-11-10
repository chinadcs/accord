---
status: superseded
recorded_at: 2026-11-08
decision_date: null
---

# ADR 0050 — Voto de joinha no recibo do Chat: tabela local, sem texto, retenção de 30 dias

## Contexto

O usuário decidiu em 25/10/2026 que o recibo do Chat ganha joinha para cima e para baixo, para
medir o acerto no uso real (substitui frases com gabarito da Etapa 0). A privacidade impede
sincronizar mensagens e transcrições ([ADR 0005](0005-chat-local-e-transferencia-manual.md)); o
voto não pode revelar o conteúdo da frase, valores ou nomes.

## Decisão (proposta)

Nova tabela `chat_feedback` no banco do par (conta, espaço), **fora da fila de sincronização e do
estado das entidades**: um voto vigente por recibo (`receiptId`), com voto (UP/DOWN), campo opcional
do joinha para baixo (valor, categoria, data, pagamento, entendeu outra coisa) e instante. Nada do
texto da frase, de valores, categorias, contas ou bancos. Tocar de novo no mesmo voto o retira;
corrigir um lançamento cria outro recibo, com voto próprio. Retenção de 30 dias (definida pelo
usuário), com expurgo ao abrir o Chat; limpar o histórico apaga os votos; o arquivo de transferência
do histórico não os leva. Sinais de editar/desfazer/corrigir não são gravados: derivam do estado dos
recibos na leitura, para não contar o mesmo recibo duas vezes.

Aceite pende do usuário (nova tabela local e retenção própria).

> **Substituído (08/11/2026)** pelo [ADR 0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md), que aceita coletar o conteúdo do Chat com consentimento. O joinha continua, mas o voto passa a ser parte da coleta (com texto) e a retenção é de 12 meses. O que já foi implementado continua como está até a etapa 3.

## Consequências

O voto mede utilidade percebida, não correção: erro não notado vira joinha positivo e recibo sem
voto não prova acerto; o outro celular não é observado. Migração Room 16→17 (aditiva). O relatório
do uso real (sete dias, `docs/poc/pipeline-frases-livres.md` §6) lê voto, recibo, mensagem e tentativa
no aparelho; pesos e cortes não mudam automaticamente com votos.

## Fontes e evidências

- Beads `accord-8q5`; [tela 04](../design/04-chat.md), [S3 §14](../specs/plans/S3-chat-slm-modelo.md).
- Implementado em 08/11/2026 (`ChatFeedbackRow`, `RoomChatStore`, `ChatCoordinator.rate`, joinha em
  `ChatScreen`): `ChatFeedbackTest` (6, JVM) e `ChatFeedbackUiTest` (2, emulador). Não validado nos
  dois celulares nem com TalkBack.
