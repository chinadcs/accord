---
status: proposed
recorded_at: 2026-11-08
decision_date: null
---

# ADR 0055 — Biblioteca de gráficos: Charty

## Contexto

O S6 ([S6](../specs/S6-p1-analise-revisao.md), linhas 393–399) propõe gráficos com primitivas do Compose,
sem biblioteca. Em 08/11/2026 o usuário escolheu a **Charty** para três gráficos: gasto por categoria
(Orçamento), barras mês a mês (Comparações) e previsto × realizado (Previsões). As specs continuam exigindo
valor exato, alternativa textual (lista), contraste 3:1 e sem animação com movimento reduzido.

## Decisão (proposta)

Adotar a Charty (`com.himanshoe:charty`) para esses três gráficos, **condicionada à avaliação**: licença,
versão estável fixada exatamente ([ADR 0021](0021-recursos-visuais-locais-e-versoes-fixadas.md)),
compatibilidade com o Compose BOM 2026.09.00 e minSdk 29, peso no APK, manutenção ativa e funcionamento sem
rede e sem JS. Se a biblioteca não der controle de cores, rótulos e animação, os gráficos ficam atrás de um
wrapper próprio que garante os requisitos das specs. `BudgetBar` e as barras de `ComparisonScreen` ficam
como estão. Se a Charty não passar, volta ao usuário antes de trocar de biblioteca.

## Consequências

Substitui a proposta de "sem biblioteca" do S6 quando aceito; o trecho do S6 será atualizado junto. Nova
dependência a manter e a verificar a cada atualização. A biblioteca não cumpre sozinha acessibilidade nem
contraste: a verificação é do wrapper e dos testes.

## Fontes e evidências

- [Plano](../plano-melhorias-2026-11.md), etapa 6; Beads `accord-tk5f`.
- Verificação preliminar (busca pública, 08/11/2026, **não conclusiva**): Apache-2.0, repositório
  `hi-manshu/Charty`, publicada no Maven Central com versão 3.2.0 como a mais recente encontrada, com suporte
  a Kotlin Multiplatform. Data de lançamento, tamanho, compatibilidade e manutenção **ainda não verificados**.
- Decisão do usuário em 08/11/2026; **aceite pendente da avaliação**. Nada adicionado ao build.
