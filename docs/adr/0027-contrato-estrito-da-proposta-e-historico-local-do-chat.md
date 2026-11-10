---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0027 — Contrato estrito da proposta do Chat e histórico local por coordenador

## Contexto

O SLM propõe e o aplicativo valida ([ADR 0003](0003-slm-propoe-app-valida-calcula.md)); a pendência é
híbrida ([ADR 0006](0006-pendencias-hibridas-do-chat.md)); o histórico fica só no aparelho
([ADR 0005](0005-chat-local-e-transferencia-manual.md)). Faltava o desenho que une isso: que formato o
interpretador devolve, onde cada regra mora e como uma mensagem sobrevive a falha, cancelamento e morte do
processo sem criar uma segunda despesa. Nenhum motor está escolhido, então o contrato precisa valer para
qualquer um e ser testável sem modelo.

## Decisão

Proposta de implementação, com as regras de produto já confirmadas em 23–24/10/2026:

- **Porta do interpretador** devolve texto bruto de uma proposta em JSON estrito de ações limitadas
  (registrar, corrigir, responder, esclarecer, consulta, intenção, não suportada, não interpretável). Cada
  texto é um Trecho literal da mensagem e cada referência é uma `ref` curta do contexto. O esquema não tem
  campo para mês, fatura, parcela calculada, total, saldo nem confiança.
- **Validador** em `:core.chat` trata a saída como não confiável: formato, ancoragem de cada Trecho no texto,
  leitura determinística de dinheiro, parcelas e datas pelo app, referências, completude e roteamento. O papel
  do valor segue marcadores do texto ("X em Nx" é total; "N parcelas de X" é parcela, e divergência pergunta).
  O meio de pagamento proposto precisa aparecer na frase: cartão nunca é presumido.
- **Coordenador** (`ChatCoordinator`) é a máquina de estados da mensagem, com fila serial, cancelamento por
  tentativa, falha preservando o texto e o identificador de operação gravado antes da chamada à fachada. Ao
  reabrir, `Interpretando` vira `Interrompida`, `Aplicando` repete com o mesmo identificador e `Enviada`
  volta à fila. Resposta digitada passa pelo interpretador; resposta por toque monta o comando direto.
- **Histórico** (`ChatStore`, Room) mora no banco do par (conta, espaço), nas tabelas `chat_*`, fora da fila,
  dos estados de entidade e do protocolo. Desfazer vale só no recibo mais recente, ativo e não fechado pela
  mensagem seguinte; correção e resposta por toque criam recibo próprio e não fecham o anterior.
- **Respostas exibidas** são modelos de texto do app; o modelo não redige frases ao casal.
- **Transferência entre aparelhos**: arquivo JSON legível com dono (conta|espaço), sem senha e sem criptografia
  no P0, que só entra no mesmo dono e não duplica por ID.

> **Revisão (08/11/2026):** o ponto "histórico só no aparelho" foi revisado pelo [ADR 0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md); o contrato estrito da proposta segue valendo. Este registro continua como histórico.

## Consequências

O contrato é testável com interpretador fake e uma bateria fictícia versionada que depois mede o modelo real
pelo mesmo validador. O custo é um segundo parser de saída e a limitação a um conjunto pequeno de ações.
"Aceito, porém errado" continua possível (categoria presumida por estabelecimento); a mitigação é medi-lo na
Etapa 0, o recibo com Editar/Desfazer e nunca aceitar por autoavaliação. Mudar o conjunto de ações ou
sincronizar histórico exigiria novo ADR. O arquivo de transferência sem criptografia expõe conversas a quem
o obtiver; o aviso é obrigatório.

## Fontes e evidências

- [S3](../specs/plans/S3-chat-slm-modelo.md) §§3–9 e 12; [tela 04](../design/04-chat.md); decisões do usuário
  de 23–24/10/2026 em memórias Beads (`decisao-23-10-chat-datas`, `decisao-24-10-desfazer-chat`,
  `decisao-24-10-historico-chat`, `pendencia-chat-hibrida`).
- Implementação em `core.chat`, `persistence` (`RoomChatStore`) e `app/ui/chat`, com testes JVM e instrumentados
  no emulador. Isso não aprova a proposta nem comprova qualidade de modelo, latência ou uso nos dois celulares.
- Aceito em 24/10/2026 (ver "Aceite"); registro em `accord-awb`.

## Aceite

Aceito pelo usuário em 24/10/2026, por instrução explícita ("aprove todos os adrs"), sem revisão ponto a ponto. O aceite vale para a escolha registrada; não comprova implantação hospedada, qualidade do modelo nem validação nos dois celulares. Itens descritos acima como abertos, pendentes ou a calibrar continuam abertos.
