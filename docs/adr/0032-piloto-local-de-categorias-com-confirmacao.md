---
status: accepted
recorded_at: 2026-10-26
decision_date: 2026-10-26
---

# ADR 0032 — Piloto local de categorias com confirmação e interpretador fake

## Contexto

O classificador de n-gramas e a guarda evidence-v1 foram medidos no S20 FE. O usuário aceitou
essa validação também para o S23 FE, dispensando a repetição da bancada. A qualidade ainda
exige abstenção e confirmação: os 95% foram alcançados apenas em regressão conhecida.
O Chat já salva despesas sem categoria e oferece classificação posterior, conforme o
[ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md). O motor real continua aberto.

Em 26/10, o usuário autorizou prosseguir com o [plano de piloto](../poc/piloto-classificacao-assistida.md)
e determinou usar interpretador fake por enquanto. Isso permite exercitar a integração local;
não permite concluir a escolha de SLM/motor nem iniciar a avaliação de linguagem natural.

## Decisão

Integrar uma porta `CategorySuggester` sem autoridade financeira ao coordenador do Chat. Depois
de salvar a despesa válida **a classificar**, executar a sugestão fora da UI. Somente o toque
na pergunta existente aplica categoria ao mesmo lançamento. Resultado antigo é descartado
se pergunta, mensagem, revisão do lançamento, catálogo ou escopo da sessão mudarem.
`SpaceRuntime` leva a identidade imutável do banco aberto (conta/espaço); a configuração do
classificador usa essa identidade, sem depender da publicação posterior do estado de acesso da UI.

O adaptador experimental existe apenas em debug, desligado por padrão. Empacota o modelo070
com SHA-256 fixado, corte 0,962 e evidence-v1. Mapeia chaves seed para os IDs reais do espaço;
qualquer mudança de IDs, nomes, pais ou composição do catálogo desativa sugestões. Não usa
contas/pessoas da bancada nem remapeia por semelhança. Release não inclui pesos nem adaptador.
Essa distribuição debug é restrita ao experimento e não substitui o download do SLM definido no
[ADR 0028](0028-distribuicao-verificada-e-ciclo-de-vida-do-modelo.md).

O fake reconhece quatro frases sintéticas, mostradas na UI, passando pelo validador e pela
fachada reais. A UI identifica o fake e informa que os exemplos registram despesas no espaço
aberto. Texto desconhecido preserva o caminho manual. Não há login fictício ou autorização
financeira especial no build de piloto.

Sugestões e conversa permanecem no histórico local. Diagnósticos mínimos ficam em
`noBackupFilesDir`, fora da outbox: identificador opaco da pergunta/solicitação, instante, hash,
política, evento, motivo categórico e tempos. Não contêm texto, valor, categoria, banco ou
credenciais. Escrita em fila limitada fora da UI, com rotação de dois arquivos de até 5.000
eventos; falha de diagnóstico não interrompe finanças. Não há exportação ou envio automático.

## Consequências

O piloto exercita sugestões, confirmação, abstenção, retomada e descarte de resultados antigos
sem depender do SLM. O ranking secundário aparece como opção; só a primeira sugestão qualificada
recebe destaque. `Ver todas` mantém escolha manual, com caminhos completos para filhas.
Não há alteração de esquema Room, treinamento online ou aplicação automática de categoria.

O fake limita a experiência às frases de demonstração. Sete dias de uso natural, feedback por
joinha e acompanhamento de correções posteriores ainda dependem de entregas separadas. A
instrumentação atual registra avaliação/publicação/descarte e escolha na pergunta; é diagnóstica,
com perda possível se a fila saturar, e não comprova precisão nem generalização. Desligar a flag
e reiniciar volta ao interpretador configurado e à escolha manual, preservando banco e histórico.

## Atualização de 02/11/2026 — modo com regras (`accord-sgz`)

Após o [ADR 0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md), o build debug aceita
`-Paccord.categoryPilot=rules`: o piloto fornece só o sugeridor de categorias e o Chat usa o
`RuleInterpreter`, sem o fake. `-Paccord.categoryPilot=true` mantém o comportamento original (fake de
quatro frases). Nada muda no release (sem pesos nem adaptador) nem nas garantias acima; a decisão de
levar o sugeridor ao release continua aberta e exige aceite do usuário. Verificado apenas por compilação
e instalação no S20 FE; o top 3 em uso com frases livres não foi medido.

## Atualização de 03/11/2026 — importação (`accord-uxmg`)

A pedido do usuário, o mesmo sugeridor roda na importação: ao preparar um lote novo, a primeira sugestão
de cada despesa é guardada como decisão de categoria do lote (o histórico do casal tem precedência;
abstenção deixa o movimento como estava). Isso abre uma exceção à regra "só o toque aplica a
categoria", restrita à importação: o lote segue editável e nada vira lançamento antes da confirmação.
Vale só onde o piloto está ligado; o release não tem pesos. A qualidade do modelo em descrições
bancárias (validado em frases do Chat) **não foi medida**.

## Atualização de 09/11/2026 — sugestor no release (`accord-7qa8`)

O usuário aprovou levar o sugestor (o que devolve até três categorias) ao release: “Se for ele, eu aprovo
levar ele ao release”. Isso **substitui** "Release não inclui pesos nem adaptador" e a exceção só-debug acima
para o sugestor; o fake do Chat continua exclusivo do build debug (`-Paccord.categoryPilot=true`).

- `:categorybench` vira dependência `implementation` do `:app`; `CategorySuggesterPilot` (main) fornece o
  sugestor e os diagnósticos; release e debug o ligam por padrão, com o Chat por regras (ADR 0036).
- O modelo070 (7.887.106 bytes, SHA-256 `5d40a47a…fa016`) passa a ficar versionado em
  `app/src/main/assets/category-suggester/model.json`, conferido no build (`validateCategorySuggesterArtifact`)
  e na carga; o original em `poc/results/` é ignorado pelo git e deixa de ser requisito de build.
- Mantidas as garantias: só o toque aplica categoria, abstenção, descarte de resultado antigo, sem rede nem
  sincronização. O catálogo ainda precisa ser idêntico às sementes: depois de editar, mover ou remover
  categorias (ADR 0039), as sugestões ficam desativadas (`CatalogIncompatible`) e a escolha é manual. Cobrir
  catálogos personalizados é decisão futura.
- Não medido: top 3 com frases livres, descrições bancárias da importação e desempenho no S23 FE (aceite do
  S20 FE estendido pelo usuário em 26/10). Verificado por compilação, testes JVM de `:categorybench` e
  `:app:testDebugUnitTest` e, no emulador `Accord_Rapido_API_33`, `CategoryPilotOffFlowTest` (2/2, sugestor
  ligado por padrão) e `CategoryPilotFlowTest` com `-Paccord.categoryPilot=true` (3 de 5; um pulado por exigir
  fonte 2.0; `abstentionOffersAllCategoriesAndUnknownPhraseKeepsManualPath` falha igual em `HEAD` sem esta
  mudança, ver `accord-p2qz`). Release assinado não gerado; nada medido nos aparelhos físicos.

## Fontes e evidências

- Pedido explícito do usuário em 26/10/2026: “Vamos de interpretador fake por enquanto”.
- [Auditoria e medição S20 FE](../poc/auditoria-categorias-e-s20fe.md), tarefas `accord-nsu.12/.13`.
- [Plano e operação do piloto](../poc/piloto-classificacao-assistida.md), tarefas `accord-f5z` e `accord-6ec`.
- [Validação física da UI no S20 FE](../poc/ui-classificacao-assistida-s20fe.md), `accord-6ak.2`:
  testes e capturas em instalação isolada; TalkBack manual permanece pendente. Não mede o SLM.
- [S3](../specs/plans/S3-chat-slm-modelo.md), [Chat](../design/04-chat.md) e [Revisar](../design/18-pendencias.md).
- `accord-q12` continua aberto para o motor real; `accord-8q5` mantém o feedback completo.
- Este aceite se limita ao piloto com fake. O [ADR 0030](0030-interpretacao-local-em-etapas-na-poc.md)
  permanece proposto para o pipeline em etapas e não foi aceito integralmente por esta decisão.
