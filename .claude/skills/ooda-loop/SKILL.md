---
name: ooda-loop
description: Coordenar trabalho autorizado do Accord pelo ciclo observar, orientar, decidir e agir, usando docs
  e Beads como estado verificável. Usar ao retomar ou organizar frentes; não autoriza delegação ou operações remotas.
---

# ooda-loop — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Ciclo ancorado em evidência

Observar árvore/diff, plano/spec afetada, bd ready/list/show e validações disponíveis. Orientar
separando decisão confirmada, proposta, trabalho implementado e medição real; consultar decisões
posteriores antes de reaproveitar orientação antiga.

Decidir o próximo avanço com critério de aceite concreto e dependências explícitas no Beads.
O catálogo de agentes ajuda a escolher a perspectiva, mas subagentes só são usados com
autorização da sessão/usuário. Não presumir multiagente para qualquer mudança.

Agir dentro do escopo, validar o resultado e reavaliar com a evidência nova. Se usar frentes
paralelas autorizadas, definir dono/arquivos e informar compartilhamento da árvore. Evitar
concorrência sobre banco/aparelho ou alterações sobrepostas.

Ao retomar, recuperar contexto com bd prime e issues, sem memória privada. Ao encerrar, fechar
somente concluídas, relatar validação/pendências e git status; não executar commit, pull, push
ou sync remoto sem pedido explícito. Uma iteração incompleta não é aceite cumprido.
