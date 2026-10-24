---
name: tdd
description: Definir e implementar testes de comportamento do Accord usando critérios do plano e telas, exemplos
  monetários independentes e cenários de recuperação. Usar em funcionalidade ou bug, sem exigir testes artificiais
  de documentação.
---

# tdd — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Da regra ao teste

Ler o aceite da tela e identificar resultado observável. Escrever um caso que falha pelo
comportamento ausente/incorreto, implementar o necessário e refatorar sem mudar a regra.
Não há framework escolhido na base documental; selecionar tier/ferramenta na implementação.

Regras puras de dinheiro/datas usam exemplos independentes: seis parcelas de R$ 200 não somam
outra compra de R$ 1.200; boleto pago em novembro sai da previsão de outubro; divisão de limite
não cria orçamento; crédito parcial abate só o confirmado. Não calcular esperado pela função testada.

Integração local prova persistência/fila sob interrupção; serviço prova autorização e versão
com usuários reais de teste sem credencial administrativa no caminho medido. Sync cobre resposta
perdida após aplicação e conflito alterado durante revisão. Importação cobre reenvio e compras
legítimas iguais. Alertas usam relógio controlado e ambos os aparelhos.

Fake isola inferência/rede sem calcular regras pelo SLM; teste de dispositivo prova ciclo de
vida, offline e acessibilidade. Benchmark mede modelo nos dois celulares e não é substituído por
fake/build verde. Negativas de acesso exigem caso permitido; guards relevantes precisam ser
observados recusando o caso inválido. Teste proporcional: não criar suíte que espelha wording
ou estrutura de uma edição documental reversível.
