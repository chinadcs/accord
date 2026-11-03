---
status: accepted
recorded_at: 2026-11-02
decision_date: 2026-11-02
---

# ADR 0036 — Interpretador por regras no P0 e SLM como candidato opcional adiado

## Contexto

O [ADR 0003](0003-slm-propoe-app-valida-calcula.md) separa quem propõe de quem valida e calcula, e as fontes do projeto
tratavam o SLM no aparelho como parte do P0 ([ADR 0004](0004-distribuicao-separada-do-modelo.md), [ADR 0028](0028-distribuicao-verificada-e-ciclo-de-vida-do-modelo.md)).
A Etapa 0 evoluiu até um pipeline em que preparação e validação locais decidem ação, valores, datas, contas e segurança, e o
modelo só escolhe, entre opções literais preparadas pelo app, qual trecho da frase é a descrição ([ADR 0033](0033-pipeline-reduzido-de-frases-livres.md), proposto).

A [evidência de 02/11/2026](../poc/relatorio-decisao-slm-2026-11-02.md), toda em emulador e sobre frases sintéticas do mesmo autor das regras:

- Frases conhecidas: Qwen3.5-2B Q5_K_M acerta cinco descrições a mais que o controle sem modelo (592 × 587), sem perdas, todas no
  padrão “loja ou pagador antes, item curto depois”.
- Frases novas (105 e 124): empate (100 × 100 e 115 × 115); o modelo foi consultado em 20 casos e escolheu o mesmo que o controle em todos.
- Só cerca de 9% das mensagens chegam ao modelo. Quando ele decidia mais coisas (ação, alvo), ficou pior que as regras.
- Custo medido no emulador: 1,42 GB de arquivo, cerca de 1,8 GB de RAM de pico, 4,8 a 9,6 s por consulta. Nada foi medido nos Galaxy.
- As falhas de segurança encontradas estavam nas regras locais, não no modelo.

## Decisão

Decisão do usuário em 02/11/2026 (“Usar a B”, a partir do relatório de decisão):

1. O Chat do P0 usa um **interpretador local por regras** (preparação e validador já exercitados na bancada), atrás da porta
   `Interpreter`, sem SLM e sem download de modelo. Continua valendo o ADR 0003: o app valida, calcula e aplica; sem SQL livre,
   sem confiança só na autoavaliação e sem IA remota automática. Dados e interpretação permanecem no aparelho.
2. **O SLM deixa de ser requisito do P0**. Passa a candidato opcional, para uma fase posterior. A porta `ModelEngine`, o gerenciador
   de modelo, os ADRs 0004 e 0028 e os oito candidatos de `poc/assets/models.json` ficam preservados, sem remoção de código.
   Qwen3.5-2B Q5_K_M continua referência experimental; nenhum modelo, motor, quantização ou hospedagem é escolhido.
3. Reabrir o SLM exige nova decisão do usuário e, no mínimo, evidência de ganho seguro sobre as regras em **frases de pessoas
   reais** e medição nos Galaxy S20 FE e S23 FE (RAM, bateria, latência, interrupção), além dos critérios já fixados em `accord-nsu.21`
   (zero ação financeira divergente aceita e zero efeito indevido). `accord-q12` fica adiada até lá.
4. Antes de colocar as regras em produção, os defeitos de segurança registrados (`accord-nsu.21.9`, `accord-nsu.21.10`) precisam ser
   corrigidos. O código experimental de `core.chat` não migra por herança: a promoção ao produto é uma tarefa própria, com testes.

Não altera o contrato do [ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md), os invariantes financeiros, a
meta de 5 s do [ADR 0035](0035-meta-de-latencia-desejada-sem-corte-eliminatorio.md) (hoje irrelevante sem inferência) nem os ADRs
proposto 0030, 0033 e 0034, que seguem como registro da pesquisa. O ADR 0003 continua aceito; a execução do SLM que ele descreve
passa a ser opcional por este registro.

## Consequências

Benefícios: Chat funcional sem baixar 1,4 GB nem reservar ~1,8 GB de RAM; resposta imediata; sem risco de encerramento do processo por
memória; esforço concentrado nas regras, onde estão as falhas medidas; o registro manual não depende de modelo (como já definia o ADR 0004).

Custos e limites: perde-se o ganho medido de cerca de 2% das descrições (5 em 234) e a ideia de “SLM no aparelho” no P0. As regras
reconhecem menos paráfrases que um modelo poderia, então haverá mais recusas e perguntas recuperáveis. A avaliação foi feita com frases
sintéticas do mesmo autor das regras e só em emulador; frases reais podem mudar o quadro, e a cobertura das regras (listas de
estabelecimentos, verbos, substantivos de campo) precisa crescer com evidência real.

Implementado em 02/11/2026 (`accord-7j0`): `RuleInterpreter` (hoje perfil 18, validador 21) é o interpretador do Chat quando não há motor
injetado nem piloto; o aviso de modelo do Chat e o passo “modelo no aparelho” ficam ocultos, e a tela 25 não é alcançável no P0.
Segue em aberto: a revisão dos textos de recusa em pt-BR e a validação com frases reais e nos Galaxy (`accord-a7t`).

## Fontes e evidências

- [Relatório de decisão](../poc/relatorio-decisao-slm-2026-11-02.md) e seus resultados:
  [v1](../poc/avaliacao-independente-financeira-v1-resultados-2026-11-02.md), [v2](../poc/avaliacao-independente-financeira-v2-resultados-2026-11-02.md),
  [v3](../poc/contrato-financeiro-v3-resultados-2026-11-02.md), [multicampo](../poc/correcao-multicampo-resultados-2026-11-02.md).
- [Cobertura nas 694 frases escritas pelo usuário](../poc/cobertura-natural-2026-11-02.md): 619 corretas (89,2%), zero efeito indevido; o corpus já
  influenciou as regras, então não é avaliação independente. Frases de uma segunda pessoa e frases novas: [kit de coleta](../poc/coleta-frases-reais.md).
- Escolha do usuário em 02/11/2026 (opção B do relatório). Beads `accord-nsu.21`, `.21.9`, `.21.10`, `accord-q12`.
- Estado de implementação: regras e validador existem na bancada (`core.chat`, perfil 13/validador 16) e em testes; o produto usa o
  interpretador fake. Nada foi medido em aparelho físico; esta decisão não prova qualidade das regras em frases reais.
