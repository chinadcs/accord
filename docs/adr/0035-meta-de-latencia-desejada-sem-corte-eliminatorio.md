---
status: accepted
recorded_at: 2026-10-31
decision_date: 2026-10-31
---

# ADR 0035 — Meta de latência desejada, sem corte eliminatório

## Contexto

A meta de até cinco segundos foi usada como critério eliminatório em comparações experimentais.
A última regressão no emulador teve mediana de 9,57 s nas chamadas após a primeira, além de uma
perda de descrição. O usuário esclareceu em 31/10/2026 que os cinco segundos são uma meta desejada.

## Decisão

Manter **até 5 s como meta desejada**, do envio ao resultado visível de frase curta com o modelo
carregado. Ultrapassar esse tempo, isoladamente, **não elimina candidato nem bloqueia integração**
e não exige nova autorização só pelo excedente. A regra vale para as avaliações futuras e para
a interpretação atual dos requisitos de qualificação.

Continuar medindo carga, latência carregada, distribuição e casos acima de cinco segundos,
separadamente de download e sincronização. Preservar a mensagem, Cancelar e Registro manual;
a meta continua sem provocar cancelamento automático. A definição de frase curta permanece
até 60 caracteres, conforme a spec S3.

Segurança financeira, qualidade, ganho sobre controle forte, recuperação, estabilidade, teto
de download e evidência aplicável continuam com seus critérios existentes. A decisão não
adota modelo/motor nem comprova desempenho nos Galaxy ou integração do motor no Chat.

## Consequências

Latência passa a orientar diagnóstico e otimização, sem reprovação automática pelo limite de
cinco segundos. Isso permite avaliar a contribuição do modelo independentemente do objetivo
de tempo. A experiência de demora e a capacidade de cancelar/retomar continuam verificáveis.

Preservar os protocolos, dados, notas e selos históricos, inclusive os critérios de tempo que
eles aplicavam. Relatar esta decisão posterior separadamente, sem reescrever os resultados.
O perfil financeiro de 31/10 continua sem qualificação por apresentar perda pareada de descrição;
os 9,57 s observados deixam de ser um bloqueio eliminatório adicional.

## Fontes e aceite

- Aceito diretamente pelo usuário em 31/10/2026: “Pode considerar a meta de 5s como desejada e
  não de carater eliminatorio”. Beads `accord-nsu.21.4`.
- [Plano §14](../plano-desenvolvimento.md#14-decisões-posteriores-e-amostra-recebida--22102026),
  [S3](../specs/S3-chat-slm-modelo.md), [Chat](../specs/design/04-chat.md) e
  [Modelo no aparelho](../specs/design/25-modelo-no-aparelho.md).
- [ADR 0004](0004-distribuicao-separada-do-modelo.md) e
  [ADR 0028](0028-distribuicao-verificada-e-ciclo-de-vida-do-modelo.md) permanecem aceitos;
  esta decisão complementa a avaliação de latência, preservando distribuição e ciclo de vida.
- [Resultados financeiros de 31/10](../poc/contrato-financeiro-resultados-2026-10-31.md),
  conservados com os critérios da rodada. Nenhuma inferência ou medição nova nesta decisão.
