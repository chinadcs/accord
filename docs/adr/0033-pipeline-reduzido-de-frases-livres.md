---
status: proposed
recorded_at: 2026-10-26
decision_date: null
---

# ADR 0033 — Pipeline reduzido de frases livres com categoria separada

## Contexto

O piloto do [ADR 0032](0032-piloto-local-de-categorias-com-confirmacao.md) exercitou classificação
assistida e UI com quatro frases fake. Ele não entende frases novas. O experimento do
[ADR 0030](0030-interpretacao-local-em-etapas-na-poc.md) mistura intenção, ligação de campos,
regras lexicais e categoria; seus resultados não qualificam automaticamente um extrator reduzido.
O usuário pediu desenhar esse pipeline e o aceite antes de escolher modelo/motor.

## Decisão proposta

Atrás da porta `Interpreter` e do contrato do [ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md),
reduzir o papel do SLM a identificar intenção e ligar campos a trechos literais/refs válidas.
O app valida e normaliza valores, datas e referências; S1 calcula e aplica com idempotência.
Categoria implícita fica exclusivamente na porta separada `CategorySuggester`, assíncrona após
salvar a classificar, com confirmação por toque. Categoria explicitamente escolhida é preservada.
O fake continua disponível e identificado, sem fallback silencioso a ele durante falha real.

Comparar na bancada uma intenção mais uma ligação, sem chamada SLM para categoria, mantendo
respostas a perguntas, correções e abstenção. Medir extração, validação/efeito e sugestão
separadamente, além do tempo conjunto. Começar a triagem no S20 FE; a dispensa do S23 FE para
o classificador não se estende ao novo fluxo. O [desenho e aceite](../poc/pipeline-frases-livres.md)
define contratos, cenários, medidas e a passagem para `accord-q12`/`accord-8q5`/sete dias.

Após as avaliações negativas, a variante experimental v6 mantém decisões de escopo e campos
financeiros já ancorados no caminho local e limita a chamada SLM à seleção literal de descrição.
Abstenção mantém a descrição local; formato/ID inválido recusa e falha do motor é propagada.
Essa evolução da proposta ainda exige ganho seguro sobre controle forte antes da adoção;
a comparação antiga de intenção/ligação permanece como evidência histórica.

A revisão v7 restringe as opções a frases completas delimitadas por evidência local, retira
contexto periférico explícito e reconfere truncamentos no validador v9, incluindo saída forjada.
Com uma finalidade única dispensa o seletor; ambiguidade entre frases completas ainda pode
exigir SLM. Essa proposta não garante análise semântica universal nem comprova ganho do modelo.

O usuário autorizou a execução do plano em 26/10/2026, com integração condicionada à
qualificação nova e aos resultados físicos. Essas condições ainda precisam ser satisfeitas;
esta proposta não escolhe modelo, motor,
quantização ou hospedagem, não aprova todo o ADR 0030 e não substitui os ADRs 0027/0032.

## Consequências

A extração pode ser comparada sem confundir erro de categoria com erro financeiro. O
classificador congelado e o fake continuam reutilizáveis. A confirmação de categoria conserva
o registro claro com Editar/Desfazer e evita uma confirmação financeira redundante.

Duas chamadas podem aumentar o custo de prefixo; a triagem deve medir esse custo. Gramática
e ancoragem não provam interpretação correta, e regras experimentais não migram por herança.
Perguntas digitadas, contas desconhecidas, contexto alterado, cancelamento e recursos nativos
em uso precisam de verificação antes da adoção. A recomendação de finalista não é aprovação
de qualidade do produto nem medição no segundo aparelho.

## Fontes e evidências

- Autorização do usuário em 26/10/2026: desenhar pipeline e critérios antes de escolher modelo;
  manter fake até autorização de integração. Beads `accord-q12.2`.
- [S3](../specs/plans/S3-chat-slm-modelo.md), [Chat](../design/04-chat.md),
  [piloto](../poc/piloto-classificacao-assistida.md) e [Etapa 0](../poc/etapa-0.md).
- Portas existentes em `core.chat`/`core.model`, coordenador e adaptador debug do fake inspecionados
  nesta entrega. Nenhum código de motor alterado e nenhuma nova rodada em aparelho executada.
- Continuação autorizada por “Prossiga” em 26/10/2026 (`accord-nsu.14`): perfil reduzido e bateria
  implementados apenas na bancada, com [triagem no S20 FE](../poc/triagem-frases-livres-s20fe-2026-10-26.md).
  Esse experimento não aceita a integração ao produto nem escolhe modelo/motor. O status permanece proposto.
- Continuação “Faça isso” (`accord-nsu.15`): [comparação controlada PC/S20](../poc/comparacao-controlada-pc-s20fe-2026-10-26.md),
  com v1 congelada, v2 híbrida de prefixo comum e controle de chamada única. V2 Qwen S20:
  51/60+24/30 na nota financeira, 49/60+20/30 em todos os campos, zero ações erradas aceitas.
  A v3 restringe a intenção por regras locais; o controle sem SLM consegue 90/90 financeiros,
  porém 0/28 descrições de novo registro. Essa regressão não sustenta escolher pesos pela nota
  financeira isolada. `accord-nsu.16` exige nova qualificação de extração completa e contribuição
  do modelo antes da recomendação. Nenhuma adoção nem mudança no aceite deste ADR.
- Implementação do plano autorizada em 26/10/2026 (`accord-nsu.16`): [120 frases novas e controles](../poc/qualificacao-frases-novas-2026-10-26.md),
  corpus e runtime congelados antes da execução. Critério: zero ação financeira errada/efeito
  indevido e ganho de extração completa sobre o melhor controle sem SLM; nenhum mínimo percentual
  novo. Retenção local de 30 dias definida para futuros diagnósticos/votos de `accord-8q5`.
  A execução da avaliação e sua infraestrutura não adotam o perfil no Chat; a escolha continua
  condicionada aos resultados. Não houve integração nem início dos sete dias por essa autorização.
- Resultado: 14 execuções PC completas, 1.680 avaliações, todas as notas revalidadas. Qwen 2B Q5
  em chamada única é o melhor com 69/120 completos contra 67/120 do controle forte, porém quatro
  erros aceitos; nenhum perfil qualifica. As correções estão em `accord-nsu.17`/`.18`. A rodada
  física não foi recuperada porque o usuário informou que o S20 FE está indisponível na sessão.
  O resultado rejeita a adoção desta configuração, sem decidir modelo/motor nem alterar o status
  da proposta estrutural.
- Continuação autorizada em 26/10/2026: [correções de preparação v4/validador v5](../poc/correcoes-preparacao-v4-2026-10-26.md),
  `accord-nsu.17`/`.18`, verificadas por regressão determinística sem repetir modelos. Preservados
  corpus v1, replay com validador v4 e snapshots. O usuário definiu Qwen3.5-2B Q5_K_M como referência
  experimental, mantendo os oito candidatos; não adotou pesos. `accord-nsu.21` exige qualificação
  nova com segurança e ganho completo sobre controle forte antes da integração `accord-q12`.
  Correção em corpus conhecido não prova generalização nem aceita este ADR.
- Continuação “Prossiga com isso”: [qualificação v2](../poc/qualificacao-v2-resultados-2026-10-26.md),
  corpus novo e v4/v5 congelados antes de inferir, 480 avaliações/187 hashes conferidos.
  Controle forte 98/120 completos; Qwen Q5 duas chamadas 84, chamada única 96, ambos repetindo
  dois efeitos errados. Não há ganho seguro nem autorização derivada para integrar. `.21`
  permanece aberto, dependente de `.22` (pergunta/alvo e entrada adversa) e `.23` (18 ações
  esperadas excluídas e contribuição do SLM). Oito candidatos mantidos; nenhuma rodada física.
  Esta evidência rejeita a configuração experimental, sem aceitar/substituir o ADR estrutural.
- Continuação “Prossiga” em 27/10/2026: [escopo v5 e qualificação v3](../poc/escopo-v5-e-qualificacao-v3-2026-10-27.md).
  `.22`/`.23` separam pergunta/recibo/mensagem nova e reconferem vínculo no validador. Mensagens
  novas recebem escolhas semânticas mais amplas; respostas/correções ficam no escopo ancorado.
  A variante experimental ainda pede seleção SLM após resolver o escopo localmente. Nos 96
  casos novos congelados, controle forte 93 completos, Qwen Q5 shared30/single24, zero efeito
  indevido em todos e nenhum ganho pareado do modelo. Não qualifica. Validador v7 acrescenta
  limite antes da análise e evidência de renda; revalida separadamente as mesmas 384 decisões.
  `.24` conserva a revisão do papel do SLM como bloqueio da qualificação/integração; `.25`
  guarda a falsa instituição em “banco de jardim”. Nenhuma adoção, medição física ou aceite
  deste ADR decorre dessas correções. O contrato do ADR 0027 e os dados financeiros não mudam.
- Continuação “prossiga” em 27/10/2026 (`accord-nsu.24`/`.25`): [descrição v6 e origem v8](../poc/descricao-v6-e-origem-v8-2026-10-27.md).
  Diagnóstico isolado em quatro casos conhecidos encontrou dependência das instruções para
  decisões redundantes, sem recuperação da extração completa ou evidência de solução pelo cache.
  A variante v6 remove essas chamadas e restringe o modelo a um identificador de descrição;
  o validador v8 corrige a falsa instituição em “banco de jardim”. Nove testes novos e os
  336 casos anteriores verificam o código; replays históricos permanecem intactos. Não houve
  inferência v6 nem nova qualificação. `.24`/`.21` continuam abertas e a integração bloqueada
  até demonstrar segurança e ganho. Este ADR permanece proposto, sem adoção de modelo/motor.
- Continuação “prossiga então” em 27/10/2026: [qualificação v4](../poc/qualificacao-v4-resultados-2026-10-27.md),
  perfil v6/validador8 e 64 frases novas congeladas antes da inferência. Controle forte 58/64
  completos, Qwen Q5 52/64; zero ganhos/seis perdas de descrição, zero erro financeiro aceito
  e efeito indevido. Todos os campos financeiros e os 40 caminhos locais permaneceram iguais.
  128 notas e 200 hashes conferidos. A avaliação rejeita esta configuração sem aceitar o ADR
  ou adotar automaticamente o controle. `.24`/`.21` continuam abertas, com revisão da descrição
  em `.27` e cobertura de resposta de data em `.26`; sem integração ou nova medição física.
- Continuação “prossiga para as próximas correções”: [descrição v7/data v9](../poc/correcoes-descricao-v7-data-v9-2026-10-27.md),
  `.26`/`.27`. Data ligada à pergunta atual; candidatos completos e guarda independente contra
  trecho periférico/truncado, com preservação de nomes dentro do próprio item. Onze testes
  novos, 879 JVM/61 Python verdes. Regressão de 400 casos: 397 completos, zero efeito indevido;
  esperado antigo preservado em todos. Validação suplementar recusa as 16 descrições erradas
  da v4 sem alterar suas 110 saídas antes completas, mantendo notas primárias/replays históricos.
  Sem inferência ou adoção: `.24`/`.21` permanecem abertas e este ADR continua proposto.
- Continuação “Continue com a próxima”: [qualificação v5](../poc/qualificacao-v5-resultados-2026-10-27.md),
  64 frases novas/v7/v9 congeladas antes de inferir. QwenQ5 59/64 completos contra controle58,
  um ganho e nenhuma perda, zero erro financeiro/efeito indevido; cinco descrições ainda erradas.
  128 notas/207 hashes conferidos. Cumpre o critério PC e permite triagem, sem aceitar este ADR
  ou escolher modelo/motor. `.24` conclui a etapa sintética; `.21` depende da bancada física `.28`,
  preparada com controle sem pesos e versões explícitas. Controle no emulador tem paridade PC;
  nenhum Galaxy medido, integração, feedback ou início dos sete dias nesta entrega.
- Instrução posterior “Validar somente no emulador”: [validação Android/CPU](../poc/validacao-v7-emulador-2026-10-27.md),
  tarefa `.28`. Qwen e controle58/64 completos, nenhum ganho/perda, zero erro financeiro
  aceito/efeito indevido. O único ganho PC desaparece no emulador; três repetições estáveis
  não isolam a causa da diferença entre backends. Chamadas curtas após a primeira com
  mediana7,65s; cancelamento e retomada exercitados. `.28` conclui o escopo solicitado,
  `.21` permanece aberta com revisão de utilidade/custo em `.29`. Sem adoção de modelo/motor,
  integração, medição Galaxy ou aceite deste ADR, que permanece proposto.
