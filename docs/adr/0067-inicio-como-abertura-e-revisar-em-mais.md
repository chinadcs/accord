---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0067 — Início como abertura e Revisar em Mais

> Ordem da barra, botão de registro e conteúdo do Início foram substituídos em parte pelo [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) (09/11/2026).

## Contexto

O aplicativo abria no Chat e oferecia Revisar como uma das cinco abas. O usuário decidiu em
09/11/2026 criar Início como abertura e substituir Revisar na barra, mantendo o Chat central.
A decisão está registrada em `accord-b6og`; nesta sessão o usuário pediu iniciar seu desenvolvimento.
Este registro formaliza essa decisão antes da implementação, substituindo a regra “Chat inicia
o uso” do plano, índice de telas e arquitetura. Não há ADR anterior dedicado a essa regra.

## Decisão

Após a configuração, abrir em **Início**. A barra fica, nesta ordem: **Lançamentos, Orçamento,
Chat, Início e Mais**. Chat mantém o ícone Astroid central sem rótulo visual, com nome acessível.
O primeiro uso continua em Primeiros passos; ao concluir, seguir para Início. Se a pessoa
escolher registro manual no checklist, o formulário abre sobre Início.

Revisar torna-se destino de Mais, com voltar para a origem. Início também oferece acesso
direto a Revisar. As abas Início e Mais sinalizam pendências conhecidas no aparelho, usando
a mesma fila de pendências já existente, sem nova contagem financeira ou acesso remoto.
Carga/falha não significam zero pendências. Ver uma pendência não a resolve.

Esta fatia entrega navegação, tela de abertura e atalhos para Chat, registro manual e Revisar.
Os painéis de próximos dias, projeção, fluxo de caixa e disponibilidade para gastar seguem
nos beads dependentes, sem valores simulados ou controles inativos nesta base.

## Consequências

A base das pilhas passa a ser Início, inclusive depois do checklist. Trocar abas preserva
rascunhos, filtros e mês; detalhes continuam voltando à origem. Notificações mantêm seu destino
financeiro específico. A leitura do indicador é local e não garante conhecer trabalho offline
de outros aparelhos. Não muda banco, protocolo, sincronização ou regras monetárias.

Testes que pressupunham abertura no Chat ou Revisar na barra precisam navegar explicitamente
a esses destinos. Validação em emulador não comprova TalkBack nem validação nos Galaxy.

## Evidência de implementação

Implementado localmente em `accord-b6og`: abertura e saída do checklist no Início, Revisar como
destino com voltar, fila local compartilhada pelos indicadores, acesso manual e Chat central.
Em 09/11/2026 passaram build debug, 186 testes JVM do app e dez testes específicos de
navegação/Início no AVD API 33. Seis capturas Compose claro/escuro foram inspecionadas.

A rodada ampliada executou 25 testes instrumentados distintos: 22 passaram e três falharam.
As falhas ficaram em `accord-tza0` (premissa de conta única no teste de acesso) e `accord-h4gm`
(dois timeouts de `BudgetFlowTest`, causa ainda não isolada). Não declarar a suíte inteira verde.
Captura pela UIAutomation da atividade real foi bloqueada por `FLAG_SECURE`; as imagens de QA
foram produzidas em host Compose de teste, com dados sintéticos, preservando a proteção do app.
Galaxy e TalkBack continuam em `accord-4uu2`. Painéis financeiros não integram esta entrega.

## Fontes

- `accord-b6og`: decisão do usuário de 09/11/2026 e pedido de desenvolvimento nesta sessão.
- Acervo local: [índice](../design/README.md), [Início](../design/inicio.md),
  [Espaço](../design/02-espaco-do-casal.md), [Chat](../design/04-chat.md),
  [Lançamentos](../design/07-lancamentos.md), [Revisar](../design/14-retomar-controle.md)
  e [Mais](../design/22-mais-e-configuracoes.md).
- Em 10/11/2026 o [ADR 0078](0078-remodelagem-da-tela-inicio.md) (aceito, `accord-4d5m`) reorganiza só o corpo da Início; Início como abertura e Revisar em Mais continuam.
