---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0055 — Biblioteca de gráficos: Charty

## Contexto

O S6 ([S6](../specs/plans/S6-p1-analise-revisao.md), linhas 393–399) propõe gráficos com primitivas do Compose,
sem biblioteca. Em 08/11/2026 o usuário escolheu a **Charty** para três gráficos: gasto por categoria
(Orçamento), barras mês a mês (Comparações) e previsto × realizado (Previsões). As specs continuam exigindo
valor exato, alternativa textual (lista), contraste 3:1 e sem animação com movimento reduzido.

## Decisão

Adotar a Charty (`com.himanshoe:charty`) para esses três gráficos, **condicionada à avaliação**: licença,
versão estável fixada exatamente ([ADR 0021](0021-recursos-visuais-locais-e-versoes-fixadas.md)),
compatibilidade com o Compose BOM 2026.09.00 e minSdk 29, peso no APK, manutenção ativa e funcionamento sem
rede e sem JS. Se a biblioteca não der controle de cores, rótulos e animação, os gráficos ficam atrás de um
wrapper próprio que garante os requisitos das specs. `BudgetBar` e as barras de `ComparisonScreen` ficam
como estão. Se a Charty não passar, volta ao usuário antes de trocar de biblioteca.

## Consequências

Substitui a proposta de "sem biblioteca" do S6 (trecho já atualizado). Nova
dependência a manter e a verificar a cada atualização. A biblioteca não cumpre sozinha acessibilidade nem
contraste: a verificação é do wrapper e dos testes.

## Fontes e evidências

- [Plano](../specs/plans/plano-melhorias-2026-11.md), etapa 6; Beads `accord-tk5f`.
- Verificação preliminar (busca pública, 08/11/2026, **não conclusiva**): Apache-2.0, repositório
  `hi-manshu/Charty`, publicada no Maven Central com versão 3.2.0 como a mais recente encontrada, com suporte
  a Kotlin Multiplatform. Data de lançamento, tamanho, compatibilidade e manutenção **ainda não verificados**.
- Decisão do usuário em 08/11/2026; aceito em 08/11/2026 ("Aceito todos os ADRs"). A adoção no build continua **condicionada à avaliação** da Decisão. Nada adicionado ao build.

## Avaliação e implementação — 08/11/2026

A condição documental foi verificada pelo agente `dependency-bumper` em `accord-tk5f`;
o build e a execução no emulador confirmam a integração. Adotada **3.2.0**, fixada exatamente
em `gradle/libs.versions.toml`, sem alterar o BOM, Kotlin ou minSdk do Accord.

- [Metadados Maven](https://repo.maven.apache.org/maven2/com/himanshoe/charty/maven-metadata.xml)
  e [release v3.2.0](https://github.com/hi-manshu/charty/releases/tag/v3.2.0): última estável
  verificada, publicada em 31/08/2026. Releases 3.0–3.2 em agosto e último commit da versão nessa
  data indicam manutenção recente; não comprovam continuidade futura.
- [POM](https://repo.maven.apache.org/maven2/com/himanshoe/charty/3.2.0/charty-3.2.0.pom) e
  [README da tag](https://github.com/hi-manshu/charty/blob/v3.2.0/README.md#license): Apache-2.0,
  Copyright 2025 The Charty Authors. Não há LICENSE/NOTICE próprio na tag nem no AAR;
  o APK inclui atribuição, fontes da declaração e o texto padrão Apache-2.0 em `assets/licenses/`.
- [AAR Android](https://repo.maven.apache.org/maven2/com/himanshoe/charty-android/3.2.0/charty-android-3.2.0.aar):
  1.841.819 bytes, SHA-256 `b4f9e349ebd414eabcd13432e0f6e164ce8f82d5b562b91b6844a0a544a5a249`;
  `classes.jar` de 1.943.296 bytes, sem assets. Isso não é o impacto final no APK nem RAM.
- minSdk 24 na biblioteca, compatível com 29 no app. Kotlin 2.4.0 na publicação, 2.4.21
  no Accord. Compose Multiplatform 1.11.1 é dependência transitiva; o grafo Android resolve
  `androidx.compose.ui:ui:1.12.1` pelo BOM 2026.09.00. Build/testes exercitam essa combinação;
  não foi necessário atualizar dependências existentes.
- [Fontes de BarChart](https://github.com/hi-manshu/charty/blob/v3.2.0/charty/src/commonMain/kotlin/com/himanshoe/charty/bar/BarChart.kt):
  Canvas Compose, sem WebView, JS ou rede para renderizar. Não se fez auditoria geral de
  vulnerabilidades nem medição de RAM nos Galaxy.

O wrapper `MoneyChart` usa `Animation.Disabled` e `animateValueChanges=false` em todos os
modos, inclusive movimento reduzido; não depende de tooltip. Dinheiro exato permanece em
`Money`: Float serve só à geometria. A lista numerada e sempre presente é a alternativa
acessível; suprime a semântica automática da biblioteca, em inglês e baseada em Float.
Dados ausentes não viram zero; negativos mantêm sinal e linha zero. Rótulos ficam fora do
Canvas para evitar corte em extremos e contraste insuficiente sobre as barras. Usa
`dataNeutral`, com fallback para `onSurface` se o contraste sobre `surface` ficar abaixo de
3:1; a série prevista usa `onSurface`, ordem e rótulo. Nenhuma nova cor arbitrária.

Nas Previsões, o rótulo é **Conhecidos × previsão**: conhecidos incluem compromissos futuros,
não equivalem a pago. O total previsto inclui os conhecidos e não deve ser somado a eles.
Previsão indisponível e mês encerrado não recebem barra fictícia. Não muda a fórmula, a
persistência nem as regras financeiras. Os gráficos usam o período dos dados exibidos,
inclusive a última visão preservada após falha.

Evidências de build, pacote e testes: [relatório da etapa 6](../graficos-charty-etapa-6.md).
Validação física, TalkBack, orientação e estabilidade/memória nos dois Galaxy: `accord-g3xc`.
