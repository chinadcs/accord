# 40 — Meu dinheiro (hub, Contas e Cofrinhos)

> **Aceita, ainda não implementada (`accord-5vzf`, ADR 0091/0092 `accepted` em 13/11/2026): descreve o alvo, não o que está implementado.** Decisões do usuário em 12/11/2026 (`accord-5vzf`); ADRs [0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md) (saldo atual e previsto) e [0092](../adr/0092-hub-meu-dinheiro-e-padronizacao-das-telas-de-dinheiro.md) (hub e padronização). **Variante escolhida pelo usuário em 12/11/2026 (`accord-5vzf`):** hub = base B com o painel de saldo e a seção Cartões da A; Contas = A; as demais telas, em suas seções. Sem código, build, teste ou validação em emulador, Galaxy S20 FE/S23 FE ou TalkBack. As variantes consideradas (A: bancos digitais brasileiros como Nubank, Inter e C6; B: gestores de finanças como Mobills e Organizze) vêm de conhecimento geral, sem fonte primária; as não escolhidas ficam só como alternativa registrada.

**Prioridade:** fora da numeração P0/P1/P2 (acréscimo posterior). **Beads:** `accord-5vzf`. **Substitui, quando aceita:** as quatro linhas de Mais (Rendas, Cartões e faturas, Contas, Cofrinhos) por uma linha "Meu dinheiro". Contas hoje vive em [22](22-mais-e-configuracoes.md) §4/§6 ("Contas com saldo", [ADR 0051](../adr/0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md)); Cofrinhos só no [ADR 0060](../adr/0060-cofrinhos-como-subcontas-reais.md) e no plano S8. Esta spec dá a ambos uma tela própria.
Dependências: [inicio](inicio.md) (card de saldo), [12](12-cartao-e-faturas.md), [13](13-detalhe-fatura.md), [28](28-rendas.md), [39](39-quem-me-deve.md), [10](10-compromissos-futuros.md), [22](22-mais-e-configuracoes.md), [ADR 0085](../adr/0085-remodelagem-de-cores-e-camadas.md) (cores), [ADR 0086](../adr/0086-remodelagem-da-tela-orcamento.md) §9 (o ícone `wallet` é da aba Orçamento).

## 1. Objetivo e contexto

Hoje o dinheiro está espalhado: o saldo está no Início; contas, cofrinhos, cartão e rendas estão em quatro linhas de Mais. Quem pergunta "quanto tenho e quanto vou ter?" precisa abrir várias telas. O hub **Meu dinheiro** responde a duas perguntas em um só lugar: **quanto tenho agora** (saldo atual) e **quanto devo ter no fim do mês** (saldo previsto), e leva a cada conta, cartão, renda, valor a receber e compromisso.

O hub não cria regra financeira própria: usa o saldo definido no [ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md). Não é integração bancária; o saldo é o que o Accord calcula com o que foi registrado.

## 2. Entradas, saídas e navegação

- **Entradas:** toque no card de saldo do Início; linha "Meu dinheiro" em Mais, no grupo "Orçamento e contas". A barra inferior não muda ([ADR 0071](../adr/0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)). Os links "Do mês" do Orçamento (Rendas, Cartão e faturas) e os "Primeiros passos" (Contas, Cartão) continuam abrindo seus destinos direto.
- **Saídas:** Contas (§5), detalhe da conta (§5.2), Cofrinhos (§6), detalhe do cofrinho (§6.2), [12](12-cartao-e-faturas.md), [13](13-detalhe-fatura.md), [28](28-rendas.md), [39](39-quem-me-deve.md), [10](10-compromissos-futuros.md) e a atribuição de lançamentos sem conta.
- **Voltar:** devolve o foco ao item que abriu o destino e preserva o mês escolhido (guardado no estado salvo da tela). O mês do hub não altera o mês do Orçamento.
- **Ícone da linha em Mais:** não usar `wallet`. Candidatos `vault` ou `coins`, **a confirmar no AAR Lucide 2.2.1** (não conferido).

## 3. Hub — layout textual

### Layout escolhido (base B, com painel de saldo e Cartões da A)

```text
‹ Meu dinheiro                                  (olho)
‹        Dezembro 2026 ▾        ›
┌ financeSurface ───────────────────────────────────┐
│ Saldo previsto no fim de dezembro                  │
│ R$ 5.950,00                                  herói │
│ Saldo atual            R$ 1.450,00                 │
│ dos quais R$ 500,00 em cofrinhos                   │
│ inclui R$ 80,00 atrasados            (se houver)   │
└────────────────────────────────────────────────────┘
⚠ 3 lançamentos sem conta                [Atribuir]  MessageBanner com ação
Contas                                    [+ Conta]
 [logo] Itaú · Conta A  Minha padrão    Atual R$ 1.000,00
        Cofrinhos: 1 · R$ 500,00
 [carteira] Carteira                    Atual − R$ 50,00
 Total das contas                       R$ 1.450,00
Cartões                                   [+ Cartão]
┌ Itaú Visa                                          ┐
│ Fatura de dez · Aberta · vence 15/12  R$ 1.200,00   │
│ ▓▓▓▓░░░░ R$ 3.200 de R$ 8.000                      │
│ Limite livre estimado R$ 4.800,00                  │
└─────────────────────────────────────────────────────┘
Resumo de dezembro                        previsto × realizado
 Rendas        recebido de previsto   ▓░░░░░░  + R$ 0,00 de + R$ 6.000,00
 Compromissos  pago / conhecido       ▓▓░░░░░  − R$ 0,00 de − R$ 1.500,00
 Quem me deve  recebido / a receber   ▓░░░░░░  + R$ 0,00 de + R$ 500,00
```

Os valores do wireframe são ilustrativos e não fecham entre si; os números de aceite estão na §9.

**Alternativas não escolhidas.** (A) painel de saldo e contas em cards grandes, com fileira de ações rápidas (Transferir · Guardar · Nova conta · Novo cartão) e "Este mês" em três blocos; (B) "Saldo previsto" e "Saldo atual" lado a lado e cartões em linhas, com "Limite livre estimado" só em texto. **Confirmado no aceite de 13/11/2026:** a fileira de ações rápidas da A **não entra** no hub; Transferir e Guardar ficam em Contas e nos cofrinhos, e "Nova conta"/"Novo cartão" nos botões "+" das seções. Se o usuário quiser a fileira, ela volta com rótulo, em grade 2×2 com fonte ≥ 130%.

### Blocos (comuns)

- **Cabeçalho:** `DetailHeader` com título "Meu dinheiro" e, no slot `action`, o botão **olho** (ocultar/mostrar valores).
- **Seletor de mês:** mesmo componente do Orçamento; define o "Saldo previsto no fim de {mês}" e o "Resumo de {mês}". Se o cálculo pesar, limitar a navegação a cerca de 12 meses à frente (a medir).
- **Topo de saldo:** Saldo previsto no fim do mês navegado (principal, em `amountHero`), Saldo atual (hoje) e "dos quais R$ x em cofrinhos". "inclui R$ x atrasados" só aparece quando houver. Ajuda curta: "Saldo previsto = o que tenho hoje, mais o que ainda deve entrar e sair até o fim do mês." Regras exatas no [ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md).
- **Contas:** uma linha ou card por conta, com saldo e cofrinhos; a Carteira entra igual. Abre o detalhe da conta (§5.2).
- **Cartões:** um card por cartão, com a fatura atual (mês, situação, vencimento, valor), barra de limite com texto e o **limite livre estimado** (ver [12](12-cartao-e-faturas.md), alvo).
- **Resumo de {mês}:** três linhas previsto × realizado com barra fina (Rendas: recebido de previsto, verde; Quem me deve: recebido e a receber, verde; Compromissos: pago de conhecido, como despesa, vermelho); cada uma abre sua tela. A barra tem o valor em texto.
- **Aviso "N lançamentos sem conta":** `MessageBanner` com ação "Atribuir"; esses realizados ficam fora do saldo atual até terem conta.

### Ações do hub

"+ Conta" e "+ Cartão" no cabeçalho de cada seção; Transferir e Guardar ficam em Contas e nos cofrinhos (sem fileira de ações rápidas no hub, confirmado no aceite de 13/11/2026).

## 4. Hub — estados

Os blocos carregam e falham **de forma independente** (Loading, Ready, Failed por bloco): um erro em Cartões não esconde Contas.

1. **Carregando:** esqueleto (`SkeletonBlock`/`SkeletonLines`) por bloco, com altura reservada; valores em "—", nunca zero.
2. **Vazio:** sem contas nem cartões, mostra só a Carteira e os botões "+ Conta" e "+ Cartão" ("Comece pela conta onde você recebe seu dinheiro"). Sem cartão, a seção Cartões vira o convite "Novo cartão".
3. **Normal:** conforme a §3.
4. **Erro:** por bloco, `MessageBanner` com ação "Tentar de novo"; mantém o último resultado do bloco quando houver.
5. **Atenção/parcial:**
   - "N lançamentos sem conta": banner com ação.
   - "inclui R$ x atrasados": pendentes vencidos, com o limite do [ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md).
   - **Conta sem saldo inicial conta como R$ 0:** a linha diz "Saldo inicial não informado (considerado R$ 0)". A Carteira sem saldo inicial pode ficar negativa pelo dinheiro já gasto (§9).
   - **Cartão acima do limite:** sem valor negativo, "Acima do limite em R$ x" com ícone e palavra; só alerta ([ADR 0031](../adr/0031-limite-de-credito-com-alerta.md)).
6. **Valores ocultos:** com o olho ativo, todo valor vira "••••", sem mudar o layout; preferência **por aparelho**, não sincroniza; o TalkBack lê "valor oculto"; o olho anuncia "Mostrar valores"/"Ocultar valores". Vale também no Início.
7. **Offline:** o hub funciona com dados locais; a situação de sincronização continua só em Mais (ADR 0071).

## 5. Contas

### 5.1 Lista

**Alvo (variante A, escolhida em 12/11/2026):** um card por conta com o saldo **dentro**: "Saldo da conta", "Nos cofrinhos", "Total". Sem saldo inicial: "Saldo inicial não informado (considerado R$ 0)" e o botão "Informar saldo inicial", **só nesse caso**. Fileira de ícones com rótulo: Transferir · Guardar · Cofrinhos · ⋮. A Carteira tem card igual, sem Editar nem Definir padrão. "Adicionar conta" no rodapé.

**Alternativa não escolhida (B):** linhas compactas com o saldo à direita, detalhe expandido inline e "Transferir entre contas" no topo.

**Menu ⋮** (descrição TalkBack com o nome do item, por exemplo "Mais ações de Conta A"): Editar conta · Definir como minha padrão · Corrigir saldo inicial (só se já informado) · Ajustar saldo.

### 5.2 Detalhe da conta e histórico

Rota própria por conta. Herói com o saldo; abaixo, "Nos cofrinhos" e total; ações Transferir, Guardar, Cofrinhos e ⋮. **Histórico** da conta (hoje `AccountBalanceView.events` existe e não é exibido): lista cronológica com data, descrição, valor com sinal e ícone de direção. Transferência e guardar/resgatar são neutros (sem verde nem vermelho); receita verde e despesa vermelha seguem o ADR 0085. Pendentes aparecem à parte ("Previstos") e não entram no saldo atual.

### 5.3 Diálogos

`accordDialogColor()`; datas por `DatePickerSheet`/`DateButton` (não "AAAA-MM-DD"); o botão confirma com o verbo da ação: "Salvar saldo inicial", "Transferir" ou "Salvar ajuste" (hoje diz "Salvar conta"); destino da transferência por rádio. Transferência e ajuste explicam que não alteram o orçamento e oferecem Desfazer (ADR 0051). Rascunho preservado; erros sem fechar o diálogo.

### 5.4 Estados de Contas

Carregando: esqueleto. Vazio: só a Carteira e "Adicionar conta". Erro: banner com "Tentar de novo". Sem saldo inicial: ver §5.1. Saldo negativo: sinal "−" e a palavra "negativo" para leitor de tela, nunca só cor. Valores ocultos: como no hub.

## 6. Cofrinhos

### 6.1 Lista

Resumo "Nos cofrinhos R$ x" no topo. Grupos por conta, com cabeçalho "{conta} · Nos cofrinhos R$ x" (sem repetir o `BalanceSummary`). Cada cofrinho em card com barra de alvo e texto "R$ 800 de R$ 1.000 (80%)", e prazo por extenso ("até 15 de março de 2027"). "Arquivados (n)" recolhido por padrão. "Criar cofrinho" é a ação principal. Vindo de uma conta, o filtro dessa conta fica visível e removível. Sem conta, `savings_needs_account` passa a dizer "Crie uma conta em Contas" (hoje cita "Gerenciar contas"; texto a corrigir).

### 6.2 Detalhe (rota própria `savings/{id}`)

Herói com o saldo; "Conta vinculada" (link), alvo e prazo. **Guardar** (principal) e **Resgatar** (secundário) lado a lado. ⋮: Editar · Arquivar (desativado com **motivo visível** quando houver saldo: "Resgate o saldo antes de arquivar"). Histórico Guardado/Resgatado em **cor neutra** com ícone de direção: são transferências entre conta e cofrinho, não receita nem despesa.

### 6.3 Estados de Cofrinhos

Carregando: esqueleto. Vazio: "Você ainda não tem cofrinhos" + "Criar cofrinho". Erro: banner com ação. Sem conta: orientação da §6.1. Arquivado: somente leitura, com histórico. Valores ocultos: como no hub.

## 7. Regras que esta tela reutiliza

- Saldo atual, previsto, cofrinhos, atrasados, cartão pela fatura e estorno de Pix/boleto: [ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md). Resumo: o saldo atual é o que as contas têm **hoje** (saldo inicial + eventos realizados até hoje, mais cofrinhos); o previsto soma o que ainda deve entrar e sair até o fim do mês navegado, acumulando mês a mês.
- Conta sem saldo inicial = R$ 0, com todos os lançamentos realizados contando. **Contraria** a regra anterior "nunca zero" ([ADR 0051](../adr/0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md); [ADR 0060](../adr/0060-cofrinhos-como-subcontas-reais.md)), por decisão do usuário em 12/11/2026.
- Compromissos do mês aparecem como **despesa**: vermelho, sinal "−", ícone e rótulo.
- Receita verde e despesa vermelha sempre com sinal, ícone e rótulo ([ADR 0085](../adr/0085-remodelagem-de-cores-e-camadas.md)); valores tabulares à direita.
- Despesa do mês (orçamento) conta a **parcela**; o saldo conta a **fatura** (caixa). O hub explica a diferença em texto de ajuda.
- Linguagem sem julgamento; distinguir comprometido/pago, renda prevista/recebida, conferido/sincronizado e intenção.

## 8. Acessibilidade

- Alvos de 48 × 48 dp, inclusive olho, ⋮, ações da fileira, linhas de conta e cartão.
- Cada linha ou card de conta é um só elemento focável com descrição completa ("Itaú, Conta A, minha padrão, saldo 1.000 reais, 1 cofrinho com 500 reais"); botões dentro do card ficam **irmãos** da área de texto, não aninhados.
- Nunca só cor: negativo com "−" e palavra; acima do limite com ícone e "Acima do limite"; receita e despesa com sinal, ícone e rótulo.
- Fonte grande: a partir de 130% a fileira de ações vira grade 2×2; nenhum valor é cortado.
- Ordem de foco: cabeçalho, olho, mês, saldo, avisos, Contas, Cartões, Resumo. Ao voltar de um destino, o foco retorna ao item de origem.
- Olho: "valor oculto" lido no lugar do valor; estado do botão anunciado.
- Barras de limite e de alvo trazem o valor em texto.

## 9. Critérios de aceite

1. **Saldo atual:** Conta A com saldo inicial R$ 1.000 e corte em 01/11; Carteira sem saldo inicial com R$ 50 de dinheiro gasto; cofrinho com R$ 500. Atual = 1.000 − 50 + 500 = **R$ 1.450**, com "dos quais R$ 500 em cofrinhos".
2. **Previsto (dezembro):** renda prevista de R$ 6.000 em 05/12, fatura de dezembro conhecida de R$ 1.200 (vence 10/12) e boleto de R$ 300 em 20/12 → previsto = 1.450 + 6.000 − 1.200 − 300 = **R$ 5.950**. O previsto de janeiro acumula os pendentes de janeiro sobre R$ 5.950.
3. **Neutros:** transferir R$ 200 da Conta A para a Carteira não muda o saldo atual; guardar R$ 200 no cofrinho também não.
4. **Fatura:** com total conferido R$ 1.250 e conhecido R$ 1.200, vale R$ 1.250. Pagamento divergente de R$ 500 numa fatura de R$ 1.250 deixa pendente R$ 750.
5. **Sem saldo inicial:** a conta entra com R$ 0 e a tela diz "considerado R$ 0".
6. **Atrasados:** pendente vencido entra no previsto só desde o corte da conta (sem conta ou sem corte, desde o dia 1 do mês atual) e aparece em "inclui R$ x atrasados".
7. **Sem conta:** realizado sem conta fica fora do atual e gera o aviso "N lançamentos sem conta" com ação.
8. **Falha por bloco:** erro em Cartões mantém Contas e o topo visíveis, com "Tentar de novo" no bloco.
9. **Ocultar:** com o olho ativo nenhum valor aparece, no hub e no Início, e o TalkBack lê "valor oculto"; a preferência persiste no aparelho e não afeta outro.
10. **Compromissos como despesa:** a linha do resumo mostra "− R$ x" em vermelho, com ícone e rótulo.
11. **Navegação:** o card do Início e a linha de Mais abrem o hub; as quatro linhas antigas de Mais deixam de existir; Primeiros passos e links do Orçamento continuam diretos; nenhuma opção some (§10).
12. **Acessibilidade:** alvos de 48 dp, descrição completa por conta, foco devolvido ao voltar, fonte ≥ 130% sem corte.

## 10. Mapa de destino (nenhuma opção some)

| Hoje | No alvo |
|---|---|
| Mais: Rendas, Cartões e faturas, Contas, Cofrinhos | Linha "Meu dinheiro" → hub → cada seção |
| Contas: Editar / Definir padrão | ⋮ da conta |
| Contas: Informar saldo inicial | Botão só se desconhecido; senão "Corrigir saldo inicial" no ⋮ |
| Contas: Transferir | Ícone na conta |
| Contas: Ajustar saldo | ⋮ |
| Contas: Cofrinhos da conta | Ícone na conta |
| Contas: Adicionar conta | Rodapé e "+ Conta" do hub |
| Cofrinhos: Criar | Ação principal da lista |
| Cofrinhos: Guardar / Resgatar | Detalhe |
| Cofrinhos: Editar / Arquivar | ⋮ do detalhe |
| Cofrinhos arquivados | Seção recolhida |
| Cofrinhos: histórico | Detalhe |
| Compromissos, Quem me deve | Continuam em Planejar, com resumo no hub |
| Orçamento "Do mês", Primeiros passos | Inalterados |
| Mais → "Datas e receitas" | "Data nos lançamentos" (opção de receitas previstas removida; ver [22](22-mais-e-configuracoes.md) e ADR 0091) |

Destinos de Cartão, Fatura, Rendas, Quem me deve e Compromissos: seção "Alvo — remodelagem" de cada spec.

## 11. Divergências que esta proposta resolve

- Contas só tinha texto em [22](22-mais-e-configuracoes.md); Cofrinhos só no ADR 0060 e no plano S8: ganham spec própria.
- O histórico por conta existe no dado (`AccountBalanceView.events`) e não aparece na tela.
- O botão de confirmar de Contas diz "Salvar conta" para saldo inicial, transferência e ajuste.
- `savings_needs_account` aponta "Gerenciar contas".
- Cofrinhos repetem o resumo de saldo em cada grupo; guardar e resgatar ficam na lista, longe do histórico.
- Quatro linhas de Mais para um mesmo assunto, sem visão única de saldo atual e previsto.
- O "Saldo total" do Início depende do critério de data e de uma opção de receitas previstas; o alvo o troca por previsto e atual (ver [inicio](inicio.md)).

## 12. Não feito

Sem código, build, testes, medição ou validação em emulador, Galaxy S20 FE/S23 FE e TalkBack. Ícone do hub e ícones candidatos não conferidos no AAR Lucide 2.2.1. Custo de `occurrencesIn` com dados reais não medido. Não se verificou se a importação de extrato cria `InvoicePayment` (pesa nos atrasados de fatura). As variantes foram escolhidas em 12/11/2026, mas a fileira de ações rápidas fora do hub está por confirmar no aceite. Riscos: previsto muito negativo para quem não marca pagamentos (mitigado pelo limite dos atrasados) e Carteira negativa sem saldo inicial. Fatias de implementação, testes que mudam e emendas aos ADRs 0051, 0060, 0071 e 0078 ficam nos ADRs 0091/0092; nenhuma bead de implementação foi criada.
