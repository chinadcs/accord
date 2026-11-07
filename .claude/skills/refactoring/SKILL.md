---
name: refactoring
description: Refatorar código do Accord preservando comportamento financeiro, identidade e trabalho pendente. Usar
  para duplicação ou estrutura difícil, sem misturar mudança de regra ou impor padrões antes de haver código.
---

# refactoring — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Preservar comportamento verificável

Na base documental não há código de produto para refatorar. Quando existir, identificar primeiro
a dificuldade concreta e o comportamento que deve permanecer, usando specs/aceite e exemplos
independentes. Não extrair abstração só por semelhança visual ou para caber num padrão.

Regras duplicadas entre chat/manual/importação merecem atenção, mas validação de autorização no
serviço não deve ser removida porque o cliente também valida. Provas independentes não devem
importar o próprio cálculo para produzir seu resultado esperado.

Preservar identidade, centavos, datas, faturas, originais, pagamentos, alcance das correções e
fila. Mudar escopo de parcelas/recorrências, resolução de conflito ou mês de orçamento é mudança
de comportamento e precisa ser descrita como tal. Não esconder migração de dados em limpeza.

Fazer a menor transformação que resolve a dificuldade e executar verificações existentes.
Reportar mudança estrutural, evidência de equivalência e limitações; não presumir just, testes
ou mutation tooling instalados. Atualizar mapa/comandos somente quando sua forma real mudar.
