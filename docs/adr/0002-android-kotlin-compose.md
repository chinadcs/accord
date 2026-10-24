---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0002 — Android nativo com Kotlin e Jetpack Compose

## Contexto

O piloto atende duas pessoas em Galaxy S20 FE e S23 FE, com uso local e inferência no aparelho.
A plataforma precisa preservar acessibilidade, temas e recuperação dos fluxos. As fontes
registram a escolha do usuário, sem uma comparação histórica detalhada com outras plataformas.

## Decisão

Desenvolver o aplicativo Android nativo em Kotlin e Jetpack Compose, validando nos dois
aparelhos; o S20 FE é a referência mínima. Skills ou exemplos de outros projetos não alteram
essa plataforma. Este aceite não escolhe motor/modelo de IA.

## Consequências

UI, ciclo de vida e testes de aparelho seguem o ambiente Android. As specs guiam estados e
acessibilidade; build ou emulador não comprovam desempenho e qualidade nos celulares reais.
Uma futura mudança de plataforma exige nova decisão.

## Fontes e evidências

- [Plano §§11/14](../plano-desenvolvimento.md) e [S1](../specs/S1-base-motor-financeiro.md).
- Há [módulo Android](../../app/build.gradle.kts); S1 está fechado em `accord-9tl`, com validações
  restantes em `accord-abv`. Não foram executados testes de produto nesta documentação.
- Registro retrospectivo; a data exata da aprovação original não foi recuperada.
