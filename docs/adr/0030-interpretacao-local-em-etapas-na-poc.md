---
status: proposed
recorded_at: 2026-10-25
decision_date: null
---

# ADR 0030 — Interpretação local em etapas e ligação de trechos na PoC

## Contexto

O [ADR 0003](0003-slm-propoe-app-valida-calcula.md) mantém o aplicativo como autoridade de
validação e cálculo. O [ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md)
define a proposta de ações por trechos literais e referências. Nas iterações autorizadas em
25/10/2026 (`accord-jhk`), os modelos pequenos confundiram ação, referências, campos presentes
e categoria quando uma chamada precisava resolver tudo de uma vez.

A [pesquisa](../poc/tecnicas-extracao-slm.md) oferece hipóteses de decomposição e rótulos
semânticos, sem comprovar ganho nos candidatos. As [runs versionadas](../poc/iteracoes-pc.md)
preservam tentativas que falharam; não se deve inferir adoção a partir do código da bancada.

## Decisão proposta

Avaliar na bancada PC uma variante opt-in `--routed`: classificar a ação; ligar campos aos
trechos literais disponíveis; classificar a finalidade em chamada separada quando necessária;
montar a proposta no contrato existente e submetê-la ao mesmo `ChatValidator`. Números,
parcelas, datas e meios seguem os leitores locais existentes, sem cálculo financeiro pelo SLM.
Referências de categoria podem derivar dos nomes/pais apresentados, com lookup exato para os
mesmos IDs de domínio, sem persistir ou sincronizar aliases.

A aceitação no produto **continua pendente**, a cargo do usuário após evidência de qualidade,
recuperação e custo nos dois celulares. Esta proposta não substitui os ADRs 0003/0027, escolhe
modelo ou motor, ou altera o contrato público de ações. O protocolo interno de classificação
do experimento pode ser mais simples que a proposta financeira final.

## Categoria do modelo fora do vocabulário é sugestão (26/10/2026)

Medição no PC (`run-063`/`run-064`, 210 despesas com vocabulário que o app não conhece): quando o modelo escolhe a categoria
entre todas, o melhor candidato acerta 41% e **erra 52%**; o perfil que desiste fica "a classificar" e erra 6%.
Por isso, **a pedido do usuário**, o perfil 19 trata essa escolha apenas como **sugestão a confirmar**:

- a proposta sai como `clarify` (campo categoria, uma opção) com o lançamento **sem categoria**, no contrato estrito já
  existente do [ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md), sem campo novo;
- o lançamento é salvo "a classificar" (fora de categoria e limites) e a pergunta de categoria fica aberta, com a opção
  sugerida primeiro e destacada; as dicas já viviam na mensagem do histórico local (`categoryHints`), sem tabela nova,
  sem migração e sem sincronizar a sugestão;
- a categoria só passa a valer quando o casal toca em "Sugerida" no Chat, ou em "Aceitar" na raiz Revisar (seção
  "Categorias a esclarecer"), ou escolhe outra; recusar não grava nada;
- vocabulário conhecido do app continua aplicando a categoria direto (perfis 15 a 18), sem passar pela sugestão.

Fica pendente: a fila vale só para o aparelho que recebeu a mensagem (o Chat não sincroniza); o outro aparelho enxerga o
lançamento "a classificar" sem a sugestão. A decisão não torna o perfil 19 o adaptador do produto: o motor continua não
integrado ao Chat (`accord-q12`).

## Comparação de classificação sem SLM (26/10/2026)

Por pedido do usuário, `accord-nsu.10` implementa uma bancada de categorias independente do motor:
vocabulário atual, ampliação por expressões de treino, regressão logística sobre n-gramas e combinação
em cascata. O treino usa Python/scikit-learn fixado; a JVM executa os pesos exportados sem Python,
GGUF ou servidor. Há partições distintas para treino, seleção, calibração, corte de abstenção e teste,
com grupos de entidades/expressões indivisíveis. A referência de 95% de precisão é experimental.

Essa autorização cobre o experimento, **não a adoção no produto**. Não muda proposta financeira,
validador, banco, fila, privacidade do Chat ou decisões de modelo/motor. O formato local de intercâmbio
fica nas bancadas `:pocpc`/`:poc` e no núcleo compartilhado `:categorybench`, fora das APIs do aplicativo.
Na bancada original, `:app` não dependia desse módulo; o piloto debug posterior está no ADR 0032.
Corpus e intermediários com descrições/gabaritos ficam
fora do repositório; snapshots locais preservam código, pesos e métricas sem frases completas.
O [método e resultado](../poc/classificador-ngramas-jvm.md) registram a execução e seus limites.

## Consequências

Em 26/10/2026, o [ADR 0033](0033-pipeline-reduzido-de-frases-livres.md) propõe um desenho de
produto reduzido: intenção/trechos pelo SLM e categoria no classificador separado, com aceite
próprio antes da escolha do motor. Essa preparação não aceita os perfis experimentais deste
registro nem altera o piloto fake aceito no ADR 0032.

Em `accord-nsu.12`, por autorização do usuário, a auditoria manteve o gabarito congelado e isolou
três casos discutíveis. A guarda experimental `evidence-v1` conserva relações do texto original
para vetar sugestões inconsistentes e impede sobreposição lexical de outra família. Não altera
pesos/cortes ou grava categoria automaticamente. Seu ganho foi medido como **regressão conhecida**,
sem afirmar nova generalização. O núcleo foi extraído para `:categorybench` para executar o mesmo
código JVM/Android, com serialização gerada no módulo compartilhado. Três processos novos no S20 FE
confirmaram paridade de 2.880 decisões. Inicialmente o S23 FE foi reservado para outra sessão;
após os resultados, em 26/10/2026, o usuário aceitou a validação do classificador/guarda no S20 FE
como suficiente também para o S23 FE e dispensou a repetição (`accord-nsu.13`). Esse aceite encerra
a exigência desta bancada; a evidência e os números permanecem medidos somente no S20 FE. A
[evidência](../poc/auditoria-categorias-e-s20fe.md) e o [plano de piloto](../poc/piloto-classificacao-assistida.md)
preservam limites de memória/latência e confirmação explícita. Naquele registro, o piloto ainda não
estava implementado; a autorização e implementação posterior com fake estão no ADR 0032.

Na continuação autorizada em 26/10 (`accord-nsu.11`), corpora antigos passam a desenvolvimento,
com avaliação escrita por outro agente sem acesso ao treino/resultados, após autorização explícita
do usuário. A bancada compara uma guarda lexical por família/filhas, seleção de categorias lexicais
apenas na etapa de seleção e classificador recalibrado. O intercâmbio local schema 2 preserva leitura
do schema 1; não altera APIs financeiras. Fonte/runtime, pesos e cortes congelam antes de receber
o teste. O [resultado pareado](../poc/classificador-ngramas-avaliacao-independente.md) melhora cobertura
e precisão da combinação, mas não atinge 95%; não há adoção no produto ou comprovação nos celulares.

A separação reduz a quantidade de decisões por chamada e permite auditar os erros por etapa.
A ligação local evita inventar campos, mas só é segura quando há um candidato inequívoco;
ambiguidades permanecem recusadas/perguntadas pelo fluxo do aplicativo, sem escolher por
posição, valor maior, confiança declarada ou outra heurística financeira silenciosa.

Chamadas e prefixos adicionais podem aumentar tempo e RAM. A medida relevante passa a ser a
do pipeline completo, com quantidade de chamadas e perfil registrados. Resultados do PC não
comprovam a meta de 5 s nos aparelhos. O protótipo atual não cobre toda a conversação: perguntas
abertas, descrições complexas e correções fora do único recibo exigem desenho/testes antes de
integração com SLM real. A validação original permanece; o piloto debug com fake e sugestões,
autorizado posteriormente, segue o alcance limitado do ADR 0032.

Respostas brutas das etapas só são registradas nas frases fictícias; casos sensíveis não as
exportam. Nenhuma conversa ou inferência é enviada a um serviço externo. Erros de categoria,
ação e ligação continuam mensurados, sem tomar JSON válido como sinônimo de interpretação certa.

Na continuação autorizada em 25/10 (`accord-7nl`), a bancada também compara formatos de rótulo,
descrições compactas e seleção de candidatos por evidência. No perfil experimental 8, padrões
textuais restringem as intenções permitidas (sempre incluindo `sem_acao`); nomes e descrições
atuais das categorias recuperam opções por sobreposição lexical, preservando seus pais. Sem
correspondência, o catálogo inteiro continua disponível. Exemplos adicionais são derivados das
descrições desse catálogo, sem consultar os gabaritos. O SLM escolhe dentre as opções e a proposta
continua no contrato existente, validada pelo mesmo `ChatValidator`.

Esse perfil transfere parte da interpretação para filtros locais. Uma melhora de nota pertence
ao **pipeline híbrido**, e não demonstra melhora intrínseca dos pesos. Um filtro pode excluir a
intenção correta; uma recuperação pode perder a categoria; nomes de estabelecimentos continuam
sem comprovar produto. A opção de recusar não torna os filtros completos. Antes de adoção seriam
necessários cobertura de ambiguidades, categorias alteradas pelo casal, frases novas e medições
do custo nos dois celulares. Comparar novas variantes não aceita esta proposta nem muda regras
financeiras ou critérios de avaliação.

As variantes posteriores conservam a ausência de categoria quando não encontram evidência,
ligam a conta citada nas correções de pagamento e recusam indicações de instituição desconhecida
nos padrões cobertos. A PoC não transforma essa recusa em conversa de seleção de conta. O
vocabulário adicional é público/sintético e só se aplica às categorias iniciais com nome e pai
compatíveis com o seed; renomes e categorias novas usam o catálogo atual. Há exemplos locais
derivados desse vocabulário, sem frases privadas como demonstrações. Por isso, as medições
posteriores continuam avaliando um pipeline com regras locais, e não os pesos isolados.

## Fontes e evidências

Em 26/10/2026, o usuário autorizou o piloto de categorias com interpretador fake. O
[ADR 0032](0032-piloto-local-de-categorias-com-confirmacao.md) registra o alcance limitado,
implementação e privacidade. Essa autorização substitui o pré-requisito de motor real apenas
para o piloto debug; este ADR continua proposto para a interpretação em etapas com SLM.

- [S3 §2–5](../specs/S3-chat-slm-modelo.md), [plano §13–14](../plano-desenvolvimento.md).
- Pedido do usuário de 25/10/2026: executar/pesquisar/iterar os 11 candidatos até dois ou três
  atenderem os critérios; autoriza o experimento, não sua adoção definitiva. Beads `accord-jhk`.
- [Pesquisa EN/PT](../poc/tecnicas-extracao-slm.md), incluindo classificação antes da extração,
  rótulos semânticos e limites da avaliação adaptativa.
- [Protocolo e resultados](../poc/iteracoes-pc.md), implementação experimental em
  `pocpc/.../RoutedInterpreter.kt`; validação fake e runs reais no PC, ainda insuficientes para
  decidir a configuração do produto ou afirmar medição nos dois celulares.
- [Resultado de 25/10](../poc/resultados-pc-2026-10-25.md): três candidatos passam nos dois
  conjuntos com routed v2 e métrica v2 corrigida por autorização do usuário. As extensões
  v3/v4 não foram escolhidas para atribuir esses números, e o validador ganhou uma guarda
  contra correção numérica sem pedido de corrigir. Isso não constitui aceite desta proposta.
- Pedido posterior de continuar até um 0,6B, um 1,7B e um 2B passarem, incluindo frases reais
  após melhora; Beads `accord-7nl`. Avaliação privada separada, sem texto/saídas/gabaritos nos
  artefatos, e novas hipóteses na [pesquisa](../poc/tecnicas-extracao-slm.md).
