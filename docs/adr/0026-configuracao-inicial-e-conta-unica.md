---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0026 — Vigência inicial de limites e padrão de conta única

## Contexto

O usuário pediu simplificar os primeiros passos: limites opcionais com vigência daqui para
frente e conta única automaticamente usada como padrão. A escolha de vigência para uma edição
posterior continua necessária para preservar meses independentes, conforme [ADR 0012](0012-limites-com-divisao-e-vigencia.md).

## Decisão

Na configuração pelos primeiros passos, limites se aplicam ao mês atual e seguintes. A tela
explicita essa vigência, sem seletor de mês ou alternativa pontual. Deixar tudo sem definir e
continuar é permitido. Fora dos primeiros passos, permanece a escolha explícita de alcance.

Com uma única conta e nenhuma preferência individual salva, ela é o padrão efetivo para
preenchimentos de Pix, débito, boleto e renda. A consulta de contas e a validação dos registros
usam a mesma regra. Com várias contas, vale a preferência individual; sem ela, pedir escolha.

## Consequências

Esta entrega deriva o padrão único da lista local, sem criar preferências sincronizáveis na
leitura. Ao passar a várias contas, uma pessoa sem preferência salva precisa escolher seu
padrão; uma escolha explícita existente permanece. Registros anteriores mantêm seus vínculos.
Essa regra também funciona para conta recebida por sincronização, sem conhecer dados ainda
offline do outro aparelho. A vigência inicial usa o mesmo comando financeiro já existente.

## Fontes e evidências

- Pedido explícito do usuário em 24/10/2026, tarefa Beads `accord-bte`.
- [Plano §14](../specs/plans/plano-desenvolvimento.md), telas [03](../design/03-primeiros-passos.md),
  [22](../design/22-mais-e-configuracoes.md) e [24](../design/24-limites-do-orcamento.md).
- Implementação em `LimitsViewModel` e `RoomFinanceSpace`; evidências de execução ficam no Beads.
