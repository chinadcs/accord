---
name: mutation-testing
description: Avaliar se testes das regras financeiras e de sincronização do Accord detectam erros deliberados. Usar
  em análise de sobreviventes ou validação de guards, sem presumir ferramenta de mutação já instalada.
---

# mutation-testing — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Escolher mutações pelo comportamento

Não há framework/gate de mutação configurado. Não instalar ferramenta nem exigir execução em
toda mudança de documentação. Usar quando há uma regra/teste específico cuja força precisa ser
avaliada, com escopo e custo proporcionais.

Mutações úteis: somar compra e parcelas, não retirar previsão do boleto pago, deslocar estorno
para mês original, contar distribuição de subcategoria duas vezes, aceitar versão stale,
reaplicar operação reconhecida ou liberar outro alerta na mesma semana.

Exigir que testes falhem pelo resultado incorreto, não por arquivo/string/estrutura alterada.
Para sobrevivente, reproduzir cenário e avaliar equivalência ou lacuna real; não adicionar
assertiva que apenas espelha o código. Usar dados sintéticos e relógio controlado nas janelas
America/Sao_Paulo. Relatar comando real, mutação, teste e evidência, sem declarar um guard provado
por suíte que não chegou a executar o caminho.
