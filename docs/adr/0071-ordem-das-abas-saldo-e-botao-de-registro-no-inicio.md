---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0071 — Ordem das abas, saldo e botão de registro no Início

## Contexto

O [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md) fixou a barra em Lançamentos, Orçamento, Chat, Início e Mais.
O usuário decidiu em 09/11/2026 (`accord-wecg`) reorganizar a abertura: o Início passa a ser a primeira aba e a reunir o
saldo, o registro e as leituras de carga; a situação de sincronização fica só em Mais. Este registro substitui a ordem
do 0067 e as partes de Lançamentos e do registro manual do Início; o restante do 0067 (Início como abertura, Revisar
em Mais, indicadores de pendência) continua valendo.

## Decisão

1. **Ordem da barra:** Início, Lançamentos, Chat (Astroid central, sem rótulo visual), Orçamento e Mais.
2. **Saldo total no Início:** o card (saldo acumulado até o fim do mês atual, com Receita e Despesa só do mês; renda
   prevista e quitação de fatura não entram) sai de Lançamentos e vai para o Início, com o critério de data escolhido
   em Mais. Lançamentos mantém busca, filtros e a lista; o botão Filtros passa a ser um botão próprio, com a contagem de
   filtros ativos. O saldo não depende mais de filtro nem do mês navegado em Lançamentos.
3. **Esqueletos no Início:** enquanto saldo, margem, fluxo do mês, projeção de estouro, próximos 7 dias e pendências
   carregam, o Início mostra espaço reservado (skeleton) em vez de texto de carga, sem números. O leitor de tela recebe
   só a descrição do que carrega; com movimento reduzido o esqueleto fica parado. Falha continua com mensagem e
   “Tentar de novo”; carga ou falha nunca viram zero.
4. **Botão flutuante no Início:** o mesmo botão de registro de Orçamento e Lançamentos (Receita, Despesa e Despesa no
   cartão) aparece no Início, e o botão “Registrar manualmente” do corpo do Início é removido. Mais mantinha o seu botão, retirado em 10/11/2026 a pedido do usuário (`accord-r31b`, ADR 0077).
5. **Sincronização só em Mais:** a linha “Salvo no aparelho” / “Dados sincronizados até …”, com fila, conflitos e causa
   de parada, aparece apenas em Mais; sai de Início, Chat e Orçamento. A regra de não afirmar que o outro aparelho
   está em dia permanece, e a tela Sincronização continua alcançável por Mais.

## Consequências

- Quem lê Início, Chat ou Orçamento não vê mais, ali, se o aparelho está offline, com fila ou com conflito; os
  indicadores de pendência nas abas Início e Mais e as mensagens de salvamento (“Salvo neste aparelho”) continuam.
  Se isso esconder problemas na prática, rever com o usuário.
- Testes que presumiam o saldo em Lançamentos, o botão de registro no Início ou o indicador fora de Mais foram
  trocados. Capturas visuais do saldo passam a ser do Início.
- Validado só no emulador; Galaxy, TalkBack e movimento reduzido reais seguem em `accord-4uu2`.

## Fontes e evidências

- Em 10/11/2026 o [ADR 0077](0077-reagrupamento-da-aba-mais.md) substituiu apenas a composição de Mais (a linha de
  sincronização passa a ter uma única entrada); o botão "Registrar manualmente" e a linha de sincronização em Mais continuam.
- `accord-wecg`: decisão do usuário de 09/11/2026. [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md),
  [Início](../specs/design/inicio.md), [07 — Lançamentos](../specs/design/07-lancamentos.md).
- Em 10/11/2026 o [ADR 0078](0078-remodelagem-da-tela-inicio.md) (aceito, `accord-4d5m`) substitui apenas a composição do corpo
  da Início (faixa de atenção, margem e previsão resumidas); abertura, ordem da barra, Saldo total, botão flutuante e sincronização só em Mais continuam.
