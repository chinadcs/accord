---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0056 — Pistas conservadoras e calibração posterior

## Contexto

As pistas de revisão de S7 (telas 37/38) dependiam de critérios a decidir com dados do piloto.
Em 08/11/2026, na conversa de `accord-9y6.12`, o usuário aprovou regras conservadoras iniciais
e depois aceitou separar a calibração, permitindo implementar e testar antes de avaliar o
histórico real. Não houve calibração nem medição de qualidade nesta decisão.

## Decisão

- Cobranças parecidas exigem mesmo valor exato, mesma descrição ou estabelecimento e mesma
  data original da compra. Não ampliar a janela para dias próximos.
- Frequência é agrupada por subcategoria e usa a data original da compra; uma compra
  parcelada conta uma única vez. Comparar os últimos 28 dias completos com os 28 imediatamente
  anteriores, excluindo o dia em andamento. Exigir simultaneamente anterior >=3 compras,
  atual >=6 e atual >=2 × anterior: 3→6 e 4→8 geram pista; 4→6, 2→6 e 0→6 não geram.
- Histórico insuficiente ou cobertura inadequada impede concluir aumento. Os números são
  parâmetros provisórios aprovados, não resultados de calibração.
- A avaliação de uma pessoa vale para todo o espaço, mostrando quem avaliou. Outros aparelhos
  refletem essa avaliação após sincronizar; offline, podem ainda não tê-la recebido.
- Uma pista avaliada só reabre por mudança de evidência relacionada ao motivo (valor, data,
  estabelecimento ou compras consideradas), se o sinal ainda satisfizer a detecção. Alterações
  sem relação não reabrem. Preservar a avaliação anterior; evidência insuficiente é limitação.
- Encerrar a decisão inicial em `accord-9y6.12`, liberando C1–C4. Calibrar depois em
  `accord-9y6.17`, com histórico autorizado do piloto, quantidades de pistas e dispensas como
  legítimas. Mudanças nos limiares exigem nova decisão do usuário.

## Consequências

Os critérios permitem implementar exemplos e limites verificáveis sem inventar evidência de
qualidade. A regra de frequência deixa de detectar aumentos a partir de bases menores que
três compras e pode perder sinais úteis; isso faz parte da escolha conservadora inicial.
Ainda pode haver pistas legítimas dispensadas. A calibração posterior verifica o ruído real.

A dispensa compartilhada e a reabertura exigem relacionar avaliações a evidências relevantes,
sem dispensar automaticamente uma evidência alterada só porque sua pista já foi avaliada.
O contrato concreto de persistência/sincronização continua na implementação de S7.C2/C3;
as propostas de chave e registros append-only na spec não foram implementadas por este ADR.

Pistas não alteram dinheiro, não excluem/estornam compras nem geram notificações. Preservar as
exclusões de parcelas, recorrências esperadas, pagamentos de fatura, estornos vinculados e
pendências de importação descritas em S7. Dados reais permanecem locais; o relato da calibração
usa agregados e limitações, sem conteúdo bancário ou conversas no repositório/Beads.

## Fontes e evidências

### Contrato de leitura e detector (C1/C2)

`clueSource(from, untilExclusive)` expõe evidências pela mesma construção da atividade,
com revisão, raiz da compra, vínculo do crédito, subcategoria histórica e pendência importada.
`clueImportOrigins` lê só extraídos persistidos dos lotes confirmados. São consultas sem escrita.
O detector puro em `core.clues` normaliza descrição apenas por espaços e caixa, sem aproximação
semântica. Cobranças parecidas usam conjunto ordenado de IDs; frequência usa subcategoria e
intervalos. As chaves são SHA-256 de campos com comprimento prefixado. Uma impressão separada
inclui IDs, data, valor e descrição (e subcategoria na frequência), sem revisão ou autor:
edições sem relação não reabrem. Não são criadas despesas, pendências ou notificações.

Para frequência, a cobertura inicial exige intervalos declarados conferidos, contínuos nos
56 dias, para cada canal importado conhecido. Registros manuais/origem desconhecida e dúvidas
de importação impedem atestar cobertura adequada. Isso é conservador e não comprova que canais
ausentes foram importados. Similaridade não exige histórico completo; limitações ficam visíveis.
### Avaliação persistida e sincronizada (C3)

`DismissClue(key, fingerprint, reference)` recalcula o sinal dentro da transação do comando.
Se chave/impressão não coincidem, recusa e pede atualizar; se coincidem, acrescenta uma
`ClueEvaluation` com UUID próprio, autor, horário e instantâneo das evidências (IDs, tipo,
descrição, centavos, data original, categoria e revisão). Não copia campos bancários originais,
arquivos ou Chat para a avaliação. Mesma identidade de comando repete o recibo, sem nova linha.
Dois aparelhos podem avaliar offline e gerar dois registros de histórico, sem conflito.

Room 20 acrescenta `clue_evaluation` com migração automática 19→20. O agregado sincroniza
pelo protocolo 2 existente; o serviço e o fake recusam editar, remover ou recriar o mesmo ID
com outra operação (`ClueEvaluationImmutable`). A repetição idempotente é conferida antes
dessa recusa. `space_entities` e o RLS existentes limitam leituras/escritas aos membros;
não há tabela pública de conversas nem efeito em valores, meses ou avisos. A migração
`20261108150000` foi aplicada apenas no Supabase local, sem autorização de implantação remota.

- Aceites explícitos do usuário na conversa de `accord-9y6.12`, em 08/11/2026; aceite final:
  “Vamos seguir nessa regra conservadora inicialmente”.
- [S7](../specs/S7-p2-intencoes-simulacao-pistas.md), telas
  [37](../specs/design/37-pistas-de-revisao.md) e [38](../specs/design/38-detalhe-pista.md).
- [ADR 0048](0048-intencoes-de-compra-conversao-e-simulacao.md): intenções e simulador;
  pistas ficaram fora daquele ADR. [ADR 0024](0024-protocolo-por-agregados-e-cursor.md):
  protocolo existente de sincronização, sem novo contrato concreto aprovado aqui.
- C1–C4 (`accord-9y6.13` a `.16`) implementados e exercitados: 706 testes core, 659 de
  persistência, 138 remote (incluindo Supabase local obrigatório), 165 app e 134 pgTAP passam.
  Há 11 testes instrumentados distintos (9 de UI, repetidos também com fonte real 200%, e 2
  com Room/ViewModel/Compose). A UI P2 compara origens temporariamente, sem criar pendência;
  correção usa o lançamento atual. Receber só `ClueEvaluation` também não dispara o callback
  de avaliação de avisos. [Evidências](../pistas-revisao-2026-11-08.md).
- Calibração (`accord-9y6.17`), Galaxy/TalkBack (`accord-9y6.18`) e migração hospedada
  (`accord-9y6.19`, exige autorização explícita) pendentes. Nenhuma implantação remota aqui.
