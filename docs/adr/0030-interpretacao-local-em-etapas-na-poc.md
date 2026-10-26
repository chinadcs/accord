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

## Consequências

A separação reduz a quantidade de decisões por chamada e permite auditar os erros por etapa.
A ligação local evita inventar campos, mas só é segura quando há um candidato inequívoco;
ambiguidades permanecem recusadas/perguntadas pelo fluxo do aplicativo, sem escolher por
posição, valor maior, confiança declarada ou outra heurística financeira silenciosa.

Chamadas e prefixos adicionais podem aumentar tempo e RAM. A medida relevante passa a ser a
do pipeline completo, com quantidade de chamadas e perfil registrados. Resultados do PC não
comprovam a meta de 5 s nos aparelhos. O protótipo atual não cobre toda a conversação: perguntas
abertas, descrições complexas e correções fora do único recibo exigem desenho/testes antes de
qualquer integração. O aplicativo continua com inferência/validação original e sem opção nova
de produto para a pessoa selecionar.

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
