# Decisões arquiteturais do Accord

ADRs registram decisões técnicas e de produto que moldam estrutura, dados, contratos ou
atributos de qualidade. Este diretório preserva o **porquê**; o plano e as specs continuam
definindo comportamento e aceite. [Arquitetura](../../ARCHITECTURE.md) resume o desenho atual.

## Quando escrever

Registrar uma decisão quando houver impacto relevante em estrutura, interfaces, persistência,
sincronização, autorização, privacidade, operação ou regras financeiras estruturais. Custo de
reversão, alternativas com consequências diferentes e motivos que o código não explica são
sinais fortes. Recuperar também escolhas anteriores ainda relevantes, usando fontes existentes.

Exemplos do Accord: Room; limite compartilhado com vigência mensal; SLM sem autoridade para
calcular dinheiro; escolha explícita de conflitos. Correções pontuais, ajustes de texto/layout,
bump rotineiro e detalhamento de aceite permanecem nas specs/Beads, salvo nova escolha estrutural.
Não criar um ADR por tela, tarefa ou biblioteca automaticamente.

Para mudança grande ainda indefinida, discutir o desenho na spec/Beads; criar ADR proposto
quando houver uma escolha concreta para avaliar. Uma pergunta aberta sem proposta permanece
no Beads. O registro deve ajudar uma pessoa futura a entender uma decisão, não funcionar como
tracker, guia de implementação ou cópia das specs.

## Como registrar e revisar

1. Ler os ADRs relacionados e as fontes aplicáveis: plano §§14/16, spec de desenvolvimento,
   telas, Beads e código. Distinguir requisito confirmado, proposta e evidência de execução.
2. Usar o [template](template.md). Escolher o próximo número após o maior existente, sem
   reutilizar números, e um nome `NNNN-titulo-em-kebab-case.md`. Conferir colisões antes de salvar.
3. Explicar contexto, decisão central e consequências, incluindo custos e limitações. Usar
   alternativas históricas somente quando documentadas. Comparações feitas agora devem ser
   identificadas como análise atual; motivação histórica ausente é uma lacuna, não uma licença
   para inventá-la. Um registro curto costuma bastar; separar escolhas com ciclos independentes.
4. Revisar escopo, fontes e status antes de aceitar. Conferir as fronteiras entre confirmado e
   proposto e se o texto preserva identidade, dinheiro e autorização. O responsável pela entrega
   pode fazer essa revisão; outro agente depende da autorização de delegação vigente.
5. Atualizar o índice abaixo, referências afetadas e a tarefa Beads. No encerramento, relatar os
   ADRs criados/substituídos e as decisões que continuam pendentes.

O frontmatter usa `status`, `recorded_at` e `decision_date` (datas ISO `AAAA-MM-DD` ou `null`
quando a decisão ainda não ocorreu ou sua data original não é conhecida). A data do registro
retrospectivo não é a data da decisão. Evidência de implementação/teste fica no corpo, separada
do status. Não atribuir ao autor da documentação a aprovação de uma decisão de produto.

### Estados e autoridade

| Estado | Significado |
| --- | --- |
| `proposed` | Escolha concreta aguardando a decisão de quem tem autoridade |
| `accepted` | Escolha aprovada, com fonte ou autorização vigente identificada |
| `rejected` | Proposta explicitamente rejeitada, com motivo e fonte |
| `superseded` | Decisão substituída por outro ADR identificado |
| `deprecated` | Decisão que deixou de se aplicar, com motivo e fonte |

Decisões já aprovadas podem ser recuperadas diretamente como `accepted`, após conferir a fonte.
Agentes registram escolhas técnicas dentro da autorização vigente; decisões reservadas ao usuário
aguardam sua resposta. Implementação existente, tarefa fechada e ausência de objeção não aprovam
uma proposta. Aceitar este fluxo não aceita automaticamente os ADRs 0022–0025. A revisão de
aceite dessas quatro propostas está na tarefa Beads `accord-awb`.

Propostas podem evoluir antes do aceite. Para mudar uma decisão aceita, criar outro ADR e ligar
os dois; no antigo, atualizar status e referência ao substituto, preservando decisão e motivos.
Correções editoriais ou de links não mudam a decisão. Não usar `rejected` para uma alternativa
hipotética que nunca foi proposta. Registros substituídos continuam no índice e seus números
não voltam a ser usados.

ADRs não substituem decisões posteriores aprovadas no plano/specs nem ganham precedência só
pela data. Se uma nova decisão mudar requisito, atualizar as fontes afetadas sob a mesma
autorização. Divergência sem solução vira pendência no Beads; não escolher uma versão em silêncio.

### Continuidade e divisão de responsabilidades

[AGENTS.md](../../AGENTS.md) aciona a consulta em mudanças estruturais. A
[skill adr-writing](../../.claude/skills/adr-writing/SKILL.md) orienta o trabalho e o
[adr-writer](../../.claude/agents/adr-writer.md) redige/revisa quando acionado. Architect fornece
o desenho; revisores conferem aderência; o curador verifica o registro no encerramento.
O catálogo não dispara subagentes nem amplia permissões.

Beads continua sendo o tracker: tarefas e pendências ficam lá; memórias duráveis usam resumo e
link ao ADR, sem duplicar a justificativa integral. O índice abaixo é um catálogo de decisões,
não uma lista de tarefas. A orientação também vale para sessões sem subagentes.

## Referências e critério adotado

Consultadas em **24/10/2026**, durante o planejamento aprovado pelo usuário:

- [Michael Nygard — Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):
  registro pequeno, decisão central, contexto, consequências e preservação de substituídos.
- [ADR GitHub organization](https://adr.github.io/): decisão justificada e impacto arquitetural;
  o conceito também pode abranger decisões de design e produto.
- [AWS — FAQ about architectural decision records](https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/faq.html):
  estrutura, interfaces e qualidade como critérios; revisão antes do aceite.
- [Spotify — When Should I Write an Architecture Decision Record](https://engineering.atspotify.com/2020/04/when-should-i-write-an-architecture-decision-record):
  registrar escolhas significativas durante o trabalho e recuperar decisões anteriores.
- [Microsoft — Maintain an architecture decision record](https://learn.microsoft.com/en-us/azure/well-architected/architect-role/architecture-decision-record):
  escolhas difíceis de reverter, justificativa e histórico preservado.

O filtro acima é a adaptação dessas referências ao Accord, aprovada no plano desta entrega.
Não exige pesquisar novamente a metodologia em cada ADR. Rever fontes quando o método mudar;
verificar documentação técnica atual quando uma nova escolha depender de fatos sujeitos a mudança.

## Índice

Carga inicial retrospectiva: fontes em plano, specs, memórias e tarefas Beads; implementação
observada em 24/10/2026. Os estados abaixo são de decisão, não de conclusão do produto.

| ADR | Decisão | Estado |
| --- | --- | --- |
| [0001](0001-adotar-adrs.md) | Adotar ADRs para decisões estruturais | accepted |
| [0002](0002-android-kotlin-compose.md) | Android nativo com Kotlin e Compose | accepted |
| [0003](0003-slm-propoe-app-valida-calcula.md) | SLM propõe; aplicativo valida e calcula | accepted |
| [0004](0004-distribuicao-separada-do-modelo.md) | Modelo separado e registro manual disponível | accepted |
| [0005](0005-chat-local-e-transferencia-manual.md) | Chat local e transferência manual | accepted |
| [0006](0006-pendencias-hibridas-do-chat.md) | Pendências híbridas do Chat | accepted |
| [0007](0007-mes-do-orcamento-por-evento-financeiro.md) | Mês por evento financeiro | accepted |
| [0008](0008-estorno-e-cancelamento-distintos.md) | Estorno e cancelamento distintos da compra | accepted |
| [0009](0009-quitacao-integral-conferida.md) | Quitação integral da fatura conferida | accepted |
| [0010](0010-identidade-da-compra-parcelada.md) | Identidade e alcance da compra parcelada | accepted |
| [0011](0011-alcance-de-recorrencias.md) | Alcance e exceções de recorrências | accepted |
| [0012](0012-limites-com-divisao-e-vigencia.md) | Limites com divisão e vigência | accepted |
| [0013](0013-supabase-free-com-operacao-local.md) | Supabase Free com operação local | accepted |
| [0014](0014-espaco-igualitario-e-convite.md) | Espaço igualitário e convite no serviço | accepted |
| [0015](0015-piloto-sem-emails.md) | Piloto sem envio de e-mails | accepted |
| [0016](0016-persistencia-room.md) | Room e SQLite incluído | accepted |
| [0017](0017-banco-fila-atomicos-e-idempotencia.md) | Banco/fila atômicos e idempotência | accepted |
| [0018](0018-conflitos-com-escolha-explicita.md) | Conflitos com escolha explícita | accepted |
| [0019](0019-importacao-local-com-rastreabilidade.md) | Importação local com rastreabilidade | accepted |
| [0020](0020-alertas-semanais-offline-primeiro.md) | Alertas semanais, offline primeiro | accepted |
| [0021](0021-recursos-visuais-locais-e-versoes-fixadas.md) | Recursos visuais locais e versões fixadas | accepted |
| [0022](0022-fachada-financeira-e-modulos.md) | Fachada financeira e separação de módulos | proposed |
| [0023](0023-servico-rpc-rls-e-adaptador-http.md) | Serviço RPC/RLS e adaptador HTTP | proposed |
| [0024](0024-protocolo-por-agregados-e-cursor.md) | Protocolo por agregados e cursor | proposed |
| [0025](0025-projecoes-financeiras-derivadas.md) | Projeções financeiras derivadas | proposed |
| [0026](0026-configuracao-inicial-e-conta-unica.md) | Vigência inicial de limites e padrão de conta única | accepted |

Questões ainda sem decisão, como modelo/motor, leitores bancários, acesso local, retenção de
idempotência e validação financeira no serviço, permanecem nos Beads/specs S2–S4. Suas tarefas
existentes são a referência de pendências; não criar ADRs vazios para reservar números.
