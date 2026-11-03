---
status: accepted
recorded_at: 2026-11-02
decision_date: 2026-11-03
---

# ADR 0039 — Edição, movimentação de lançamentos e remoção de categorias

## Contexto

Hoje a tela de categorias só cria, renomeia, reordena e move subcategoria; o texto da tela diz
"não há exclusão" e a [spec 23](../specs/design/23-categorias-e-subcategorias.md) manda não
adicionar exclusão destrutiva nem migração financeira automática **como funcionalidade
presumida**. Em 02/11/2026 o usuário pediu: tela de edição de categoria com ícone e cor (a
subcategoria herda a cor do pai, sem escolher), remover categoria e **mover os lançamentos** de
uma categoria para outra, inclusive sem removê-la. Isso mexe em histórico financeiro, em
limites e na sincronização entre dois aparelhos que podem editar offline
([ADR 0018](0018-conflitos-com-escolha-explicita.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md)).

A cor hoje é derivada no cliente da categoria principal (sementes com cor fixa; as demais por
hash do identificador), sem campo persistido. Escolher a cor exige persisti-la e sincronizá-la.

## Proposta

1. **Cor e ícone são campos da categoria principal.** A cor passa a ser um campo sincronizado;
   sem valor, vale a cor derivada atual. Subcategoria não tem cor própria e usa a do pai. Editar
   ícone, cor ou nome é uma edição do agregado da categoria, com versão esperada.
2. **Mover lançamentos é uma única operação atômica**: de uma categoria de origem para outra de
   destino, com opção de remover a origem. Escopo escolhido na hora: todos os lançamentos ou só do
   mês atual em diante. Compra parcelada entra inteira, como já vale para a mudança de categoria
   ([ADR 0010](0010-identidade-da-compra-parcelada.md)). A prévia mostra quantidade e meses
   afetados antes de confirmar. Reescreve a classificação dos lançamentos, preservando os campos
   originais importados e as identidades.
3. **Limites nunca se transferem em silêncio.** Se a origem tem limite ou divisão, a pessoa
   decide por mês, como no `MoveSubcategory` ([ADR 0012](0012-limites-com-divisao-e-vigencia.md)).
4. **Remover marca a categoria como removida, sem apagar a linha.** O identificador permanece
   para que o outro aparelho e operações antigas continuem resolvendo. Categoria removida sai dos
   seletores e das listas novas; a remoção exige antes mover os lançamentos existentes.
5. **Lançamento que chega de aparelho offline numa categoria removida vai para "a classificar"
   em Revisar** (decisão do usuário em 02/11/2026). O serviço recusa a categoria removida sem
   descartar o lançamento; o app o mantém salvo sem categoria e a escolha fica com a pessoa,
   conforme "dúvidas recuperáveis". Não há redirecionamento automático para o destino da remoção.
6. **Conflitos seguem o ADR 0018, sem mesclagem.** Dois aparelhos movendo a mesma categoria para
   destinos diferentes geram **um** conflito da operação (versão do outro aparelho × a escolhida
   aqui), não um por lançamento. Lançamento editado em outro aparelho que esta operação moveu gera
   o conflito normal de duas versões. Edições concorrentes da mesma categoria também conflitam,
   mesmo em campos independentes.
7. **Honestidade sobre o que se sabe.** A confirmação diz que vale para os lançamentos que este
   aparelho conhece; o que o outro celular registrou offline cai nas regras acima, e o app não
   afirma conhecê-lo.

8. **Tipo de categoria: despesa ou receita.** Em 02/11/2026 o usuário afirmou que "Categoria de
   Renda é uma categoria para receitas" e escolheu criá-la como **nova categoria de receitas**
   (hoje a renda não tem categoria e todas as sementes são de despesa). Proposta: cada categoria
   principal tem um tipo; a semente **Renda** é do tipo receita e recebe as rendas; subcategorias
   herdam o tipo do pai. Mover lançamentos só entre categorias do **mesmo tipo**; Renda não
   aparece nos seletores de despesa nem como destino de despesa; o orçamento não a trata como
   gasto nem lhe aplica limite de despesa. Renda prevista continua distinta de recebida e fora do
   saldo confirmado. A categoria receita também pode ser editada (ícone e cor) e removida após mover
   as rendas. Como altera o modelo de renda (plano e S1 dizem que renda não tem categoria),
   depende do aceite e atualiza essas fontes.

   **Nota de 03/11/2026, ao registrar o aceite:** o [ADR 0038](0038-categorias-de-renda.md), aceito depois deste texto, resolveu a categoria de renda de outro modo: um agregado
   próprio (`IncomeCategory`), lista plana, sem tipo na árvore de despesas. O desenho acima (tipo de categoria na árvore, semente Renda) **não é o que existe nem o que será
   construído**; vale o 0038. O aceite do usuário ("Aceito os ADRS 39 e 40") cobre os pontos 1 a 7. Se ele quiser mover ou remover categorias de renda, é uma extensão do 0038 a decidir
   (a tarefa `accord-udwd.7` fica sob revisão).

Alternativa analisada agora (não histórica): registrar na origem um redirecionamento "vale
como destino", em vez de reescrever lançamentos. Conflita menos, mas não atende "mover sem
remover" e muda o cálculo das projeções ([ADR 0025](0025-projecoes-financeiras-derivadas.md)).
Por isso não é a proposta.

## Consequências

Atende o pedido sem apagar histórico nem criar combinações nunca escolhidas. Custos: nova
migração de esquema (Room e Supabase), novos comandos e uma resposta de serviço para categoria
removida; operação grande pode ter payload alto (limite de tamanho não verificado) e precisa de
teste de reenvio após resposta perdida; mover meses anteriores altera orçamentos passados, por
isso o escopo é explícito. Editar nome e cor da mesma categoria nos dois aparelhos conflita por
regra, ainda que os campos sejam independentes. Reverte, para este caso, a orientação "sem
exclusão" da spec 23 e do texto da tela, que devem ser atualizados com a implementação (aceito em 03/11/2026).

## Fontes e evidências

- Pedido e escolha do ponto 5 pelo usuário em 02/11/2026. Aceite do ADR pelo usuário em 03/11/2026 ("Aceito os ADRS 39 e 40"); o ponto 8 foi superado pelo ADR 0038 (nota acima).
- [Spec 23](../specs/design/23-categorias-e-subcategorias.md), [S1](../specs/S1-base-motor-financeiro.md)
  ("sem exclusão destrutiva"), [ADR 0010](0010-identidade-da-compra-parcelada.md),
  [0012](0012-limites-com-divisao-e-vigencia.md), [0017](0017-banco-fila-atomicos-e-idempotencia.md),
  [0018](0018-conflitos-com-escolha-explicita.md), [0024](0024-protocolo-por-agregados-e-cursor.md).
- Beads: épico `accord-udwd` e filhas `.1` a `.6`. Nada implementado até o aceite; a cor derivada atual está
  em `app/src/main/kotlin/app/accord/ui/CategoryColors.kt` e não é persistida.
- Implementação (03/11/2026): `core.space` (`EditCategory`, `MoveCategoryEntries`, `RemoveCategory`, `CategoryPalette`), `persistence` (`CategoryPlanner`, colunas `colorIndex` e `removed`, esquema 8, reparo em `SyncStore`), `supabase/migrations/20261103130000_categoria_removida.sql` (recusas `CategoryInUse` e `CategoryRemoved`), app (`CategoryEditScreen`, `CategoryMoveScreen`).
  Decisões de implementação não previstas acima: a cor é um índice de uma paleta fixa de 12 (não cor livre); a remoção exige mover todos os lançamentos; limites nunca são transferidos, e remover categoria com limite definido exige confirmação para descartá-lo (a escolha mês a mês do `MoveSubcategory` não foi replicada); um lançamento excluído que aponta para a categoria removida volta a "a classificar" se for restaurado; o desfazer de uma correção cuja categoria anterior foi removida também deixa o lançamento "a classificar".
  Testes: `CategoryEditMoveRemoveTest` (16), `SyncEngineTest` (3 novos: lançamento tardio, remoção recusada e reenviada com os lançamentos novos, conflito único), contrato `SpaceServiceContract` (4 novos, no fake e no Supabase local), pgTAP `03_categoria_removida` (9), `CategoryEditFlowTest` (2, emulador). Não verificado: Galaxy S20 FE e S23 FE, conflito real entre os dois celulares, tamanho de envio de uma movimentação muito grande.
