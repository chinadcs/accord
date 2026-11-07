---
name: reliability
description: Avaliar confiabilidade do ciclo registrar, acompanhar, importar, conferir e retomar no Accord, incluindo
  recuperação e evidências em aparelhos. Usar para incidentes ou critérios de operação, sem declarar SLA ou SLO
  já acordado.
---

# reliability — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## O que significa funcionar

O piloto é para o casal, sem garantia operacional comercial definida. Meta de até 5 s refere-se
somente à frase curta com modelo carregado; qualidade, memória e demais tempos continuam abertos.
Não transformar essa meta em SLA, timeout ou desempenho comprovado.

Priorizar integridade e recuperação: salvar offline sem perder trabalho; reenviar sem duplicar;
comparar conflito sem sobrescrever; retomar importação sem reaplicar; mostrar cobertura real;
manter manual durante falha do modelo. Provar que ambos os celulares conservam e compartilham
os mesmos movimentos autorizados após reconexão.

Para incidente, registrar gatilho, impacto, ponto de persistência/reconhecimento e recuperação
observada com dados sintéticos quando possível. Propor correção/teste que reproduza o caso, sem
adicionar infraestrutura especulativa. Supabase Free é escolha de custo, não promessa de uptime.
Piloto de 30 dias avalia regularidade, retomada, compreensão dos gastos e esforço de conferência;
metas quantitativas de uso serão definidas após uso real, conforme o plano §1.
