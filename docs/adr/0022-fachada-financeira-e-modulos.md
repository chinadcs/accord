---
status: proposed
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0022 — Fachada financeira como entrada e núcleo independente de Android

## Contexto

Formulário, Chat e importação devem executar as mesmas regras. S1 propôs um ponto de entrada
comum para comandos/consultas e separação suficiente para testes sem criar um módulo por função.
O código já segue esse desenho, mas a memória de implementação mantém escolhas como propostas.

## Proposta

Usar `FinanceSpace` como fachada financeira; revalidar invariantes em cada comando e impedir
escrita direta pela UI, SLM ou importador. Manter motor/contratos em `:core` Kotlin JVM,
implementação Room em `:persistence`, UI em `:app` e integração/fake remoto em `:remote`.
Ligar dependências por construtores, sem tornar framework de DI obrigatório.

## Consequências

A regra fica reutilizável e testável sem Android/rede/inferência. A fachada concentra aplicação
e pode crescer; `accord-abv` já acompanha a divisão de `RoomFinanceSpace`. O núcleo independente
não garante por si só que todas as origens respeitem a entrada comum.

## Fontes e evidências

- [S1 §§1–2](../specs/S1-base-motor-financeiro.md), Beads `accord-9tl.1`, `.6`, `.7`;
  memória `s1-fachada-decisoes-de-implementacao`.
- [Módulos reais](../../settings.gradle.kts) e
  [contrato](../../core/src/main/kotlin/app/accord/core/space/Contract.kt).
- Implementação existente; aceite do desenho concreto ainda não recuperado. Registrar a decisão
  do usuário sobre a proposta antes de mudar o estado; adoção de Room segue aceita no ADR 0016.
- Acompanhamento do aceite: Beads `accord-awb`.
