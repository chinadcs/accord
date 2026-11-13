---
status: accepted
recorded_at: 2026-11-12
decision_date: 2026-11-13
---

# ADR 0092 — Hub "Meu dinheiro" e padronização das telas de contas, cofrinhos, cartão/fatura, rendas, quem me deve e compromissos

## Contexto

Contas, Cofrinhos, Cartão e faturas, Rendas, Quem me deve e Compromissos futuros são telas separadas, alcançadas por
quatro linhas do grupo "Orçamento e contas" de Mais (`MoreScreen.kt:171-178`, [ADR 0077](0077-reagrupamento-da-aba-mais.md))
e por links soltos. Cresceram por acréscimos, com padrões diferentes de cabeçalho, ações, diálogos, datas digitadas
("AAAA-MM-DD"), carregamento e erro; ações raras ficam ao lado das frequentes. Em 12/11/2026 o usuário pediu a remodelagem
(`accord-5vzf`, fluxo `remodelar-tela`; precedentes [0077](0077-reagrupamento-da-aba-mais.md),
[0086](0086-remodelagem-da-tela-orcamento.md), [0089](0089-remodelagem-das-telas-de-lancamentos.md)), junto com o novo
modelo de saldo do [ADR 0091](0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md). As decisões abaixo são do
usuário; os layouts e riscos são análise de UI/UX da sessão, com padrões de mercado de conhecimento geral, sem fonte
primária.

## Decisão

Aceito pelo usuário em 13/11/2026 ("Aceito, pode fatiar", `accord-5vzf`), com as variantes escolhidas por tela em 12/11/2026 (item 10). Nenhuma regra financeira muda além
do ADR 0091 e das exceções abaixo.

1. **Hub "Meu dinheiro".** Tela nova, com ícone a confirmar no AAR Lucide 2.2.1 (candidatos `vault`/`coins`; **não** `wallet`,
   que é da aba Orçamento, [ADR 0086](0086-remodelagem-da-tela-orcamento.md) §9). Conteúdo: seletor de mês; topo com Saldo
   previsto (principal), Saldo atual e "dos quais R$ x em cofrinhos"; seção Contas (cada conta com saldo e cofrinhos);
   seção Cartões (fatura atual e limite livre estimado); seção do mês com atalhos-resumo para Rendas (recebido/esperado),
   Quem me deve (a receber) e Compromissos (total do mês, como despesa). Aviso "N lançamentos sem conta" com ação.
2. **Acesso.** Tocar no card de saldo do Início abre o hub (card inteiro clicável, `Role.Button`, rótulo "abrir Meu
   dinheiro", chevron). Em Mais, as linhas Rendas / Cartões e faturas / Contas / Cofrinhos viram **uma** linha "Meu
   dinheiro". As abas da barra não mudam ([ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)). Links
   do Orçamento "Do mês" e os Primeiros passos continuam. Compromissos e Quem me deve seguem em Planejar, com resumo no hub.
3. **Padrão comum** nas telas Contas, Cofrinhos, Cartão e faturas (12), Detalhe da fatura (13), Rendas (28), Quem me deve
   (39), Compromissos futuros (10) e card do Início: `DetailHeader` com ação no slot `action`; valor-herói em `amountHero`;
   `AccordCard`; `MessageBanner` (precisa de variante com ação) para atenção/erro; esqueleto no carregamento; seletor de data
   (`DatePickerSheet`/`DateButton`) no lugar de "AAAA-MM-DD"; `accordDialogColor()` nos diálogos; receita verde e despesa
   vermelha sempre com sinal, ícone e rótulo ([ADR 0085](0085-remodelagem-de-cores-e-camadas.md)); valores tnum à direita.
4. **Menu ⋮** (descrição TalkBack com o nome do item) para ações raras: Editar conta / Definir como minha padrão / Corrigir
   saldo inicial / Ajustar saldo (Contas); Editar cartão / Usar como meu padrão (Cartão); Editar / Arquivar (Cofrinhos, com
   motivo visível quando desativado); Renomear / Cancelar valor a receber (Quem me deve); "Já estava paga (antes do app)" (Fatura).
5. **Fatura: ação por estado.** Antes de conferir: principal "Informar total cobrado"; "Registrar pagamento" secundário e
   ativo (pagamento sem total conferido segue "em revisão"). Conferida e não paga: principal "Registrar quitação integral",
   secundário "Corrigir total cobrado". Paga: "Paga em …", sem principal.
6. **Exceção ao [ADR 0009](0009-quitacao-integral-conferida.md).** "Já estava paga (antes do app)" (commit `5243934`,
   `accord-gm3n`, `invoice_already_settled`) fica no ⋮ e é documentada como **exceção** ao 0009: quita fatura anterior ao
   uso do app sem total conferido. Vale só para esse caso.
7. **"Limite livre estimado"** = limite − Σ faturas não pagas conhecidas, com a nota "pelo que o Accord conhece" (ressalva
   da spec 12: total conhecido não é o disponível no emissor). Usado > limite: sem valor negativo; estado "acima do limite
   em R$ x" com ícone e palavra ([ADR 0031](0031-limite-de-credito-com-alerta.md): só alerta, nunca bloqueio). O cálculo
   sai da UI (`CardScreen.kt:178`) para função pura no core.
8. **Ocultar valores** (ícone de olho) no Início e no hub; preferência por aparelho; TalkBack lê "valor oculto".
9. **Compromissos como despesa:** o total do mês aparece em vermelho, com sinal −, ícone e rótulo, no hub e na tela 10.
10. **Variantes (mockup A/B): escolha do usuário por tela em 12/11/2026** (`accord-5vzf`):
    - **Meu dinheiro (hub):** base **B** (seletor de mês, contas em linhas com "Total das contas", "Resumo de {mês}"
      previsto × realizado), com o **painel de saldo** e a **seção Cartões** (cards com barra de limite) da **A**. A fileira
      de ações rápidas da A não entra (aceite de 13/11/2026 sem incluí-la).
    - **Contas:** A. **Rendas:** A. **Compromissos:** A.
    - **Cartão e faturas:** A, com o **seletor suspenso de cartão** da B no lugar do carrossel.
    - **Detalhe da fatura:** A, com as **setas de mês** da B no lugar das abas; sem a grade Conhecido/Cobrado/Pago.
    - Cofrinhos, Quem me deve, card do Início e Mais eram iguais nas duas variantes.

    Descrição das variantes, para referência:
    - **A** (referência: bancos digitais BR): painel de saldo com herói; fileira de ações com rótulo; card por conta com
      saldo dentro; cartões em carrossel com semântica de seleção e barra de limite com texto; abas por mês na fatura;
      Rendas com herói "Recebido" e barra; lista cronológica.
    - **B** (referência: gestores de finanças BR): "Saldo previsto" e "Saldo atual" lado a lado; contas e cartões em linhas
      compactas, limite em texto; seletor suspenso de cartão; fatura com setas de mês e grade Conhecido/Cobrado/Pago;
      "Resumo de {mês}" previsto × realizado; Rendas em grupos "A receber"/"Recebidas"; Compromissos por tipo.
    - Comuns às duas: botão "Recebi" irmão da área de texto (não aninhado); um só "Adicionar renda"; "Resolvidos" e
      "Arquivados (n)" recolhidos; cofrinho em rota própria `savings/{id}` com histórico em cor **neutra** (transferências);
      lista de faturas curta (A) ou tabela (B).
11. **Mapa de destino resumido (nenhuma opção some):** Mais → "Meu dinheiro". Contas: Editar/Definir padrão/Ajustar → ⋮;
    "Informar saldo inicial" só se desconhecido, senão "Corrigir saldo inicial" no ⋮; Transferir → ícone (A) ou topo (B) e
    hub. Cofrinhos: Criar → principal; Guardar/Resgatar → detalhe; Editar/Arquivar → ⋮. Cartão: padrão → selo + ⋮; editar →
    ⋮; "a conferir" → banner com ação. Fatura: ver lançamentos → link; vínculos e divergências → banners; conta da quitação
    → rádio. Rendas e Quem me deve: ações atuais mantidas, Renomear/Cancelar no ⋮. Compromissos: filtros, cenário, próximos
    7 dias e nota mantidos; cartão e assinaturas em "Veja também".
12. **Correções de texto incluídas:** `savings_needs_account` ("Gerenciar contas" → "Contas"), `card_edit_title`
    ("Cartão padrão" → "Editar cartão"/"Novo cartão"), botão do diálogo de conta ("Salvar conta" → rótulo do ato);
    fallback ao primeiro cartão quando `cardId` é nulo e não há padrão (`CardViewModels.kt:238`).
13. **Substitui o [ADR 0077](0077-reagrupamento-da-aba-mais.md) só** no grupo "Orçamento e contas" da aba Mais (as quatro
    linhas viram uma). O resto do 0077 e o 0071 continuam. A remoção da opção "Contar receitas previstas" fica no ADR 0091.

**Fatias de implementação** (beads criados em 13/11/2026: épica `accord-97ba`, fatias `accord-97ba.1` a `.14` na ordem abaixo): 1 core saldo (ADR 0091) com testes JVM; 2 core
`CardOverview` (fatura atual, limite usado/livre, sem N×meses); 3 componentes comuns (mover `DatePickerSheet`/`PickerField`/
`DateButton` para `ui/components`, chip de seleção, barra de limite, `MessageBanner` com ação, ocultar valores); 4 hub
(`MoneyHubViewModel` com blocos independentes Loading/Ready/Failed, mês em `SavedStateHandle`, `ROUTE_MONEY_HUB`); 5 Início
clicável, olho e nova regra do card; 6 linha em Mais e remoção da opção de receitas previstas; 7 Contas e detalhe por conta;
8 Cofrinhos (rota própria, `ROUTE_SAVINGS`); 9 Cartão; 10 Fatura; 11 Rendas; 12 Quem me deve; 13 Compromissos; 14 validação
visual em retrato (claro/escuro, TalkBack, 48 dp) nos Galaxy.

## Consequências

- Mais perde três linhas; o hub vira ponto único de dinheiro, com custo de um toque a mais para quem ia direto a Cartões.
- **Testes instrumentados quebram** na fatia que os causa: `openMore` (`MoreFlowTest.kt:126-130`), `SavingsUiTest:48`,
  `UiAuditCaptureTest:136-196`, `UiAuditBankPickerLandscapeTest:79` e assinaturas posicionais de `VisualReviewS1eTest`,
  `AccountBalanceUiTest`, `MultiCardUiTest`, `RedesignIncomeUiTest`. Parâmetros novos com valor padrão no fim.
- Exige o ADR 0091 (saldo do hub) e o `MessageBanner` com ação; sem eles, o hub não tem o que mostrar.
- Riscos: previsto negativo e Carteira negativa (ver ADR 0091); "Limite livre estimado" pode parecer o limite real do
  emissor, daí a nota; esconder ações no ⋮ reduz descoberta; ícone candidato não conferido no AAR.
- Não feito: código, validação em Galaxy/TalkBack, conferência de ícones no Lucide 2.2.1.
- Specs de `docs/design` a atualizar: tratado por outro agente, fora deste registro.

## Fontes e evidências

- Decisão do usuário em 12/11/2026 (`accord-5vzf`); pedido de proposta, mockup A/B antes/depois com mapa de destino
  (Artifact privado: https://claude.ai/artifact/5ejL8bG2pScjv1m9MdoC5f).
- Spec do alvo: [40 — Meu dinheiro](../design/40-meu-dinheiro.md) e as seções "Alvo — remodelagem" das specs 10, 12, 13, 22, 28, 39 e Início.
- Código lido: `MoreScreen.kt:171-178`, `CardScreen.kt:178`, `CardViewModels.kt:238`; commit `5243934` (`accord-gm3n`).
- Relacionados: [0009](0009-quitacao-integral-conferida.md), [0031](0031-limite-de-credito-com-alerta.md),
  [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), [0077](0077-reagrupamento-da-aba-mais.md),
  [0085](0085-remodelagem-de-cores-e-camadas.md), [0086](0086-remodelagem-da-tela-orcamento.md),
  [0091](0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md).
- Estado: **aceito em 13/11/2026**, não implementado; padrões de mercado vêm de conhecimento geral, não de fonte primária. Nada implementado nem validado.
