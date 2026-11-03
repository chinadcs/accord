---
status: accepted
recorded_at: 2026-11-03
decision_date: 2026-11-03
---

# ADR 0040 — Lote de importação confirmado como agregado sincronizado e confirmação atômica por comando composto

## Contexto

O [ADR 0019](0019-importacao-local-com-rastreabilidade.md) manda importar localmente, com prévia, guardar extraídos, campos originais, metadados e decisões, e não duplicar ao reimportar,
mas não diz **onde** esse trabalho mora, **o que sincroniza** nem **como** a confirmação aplica vários efeitos sem deixar o espaço pela metade. A [spec S4](../specs/S4-importacao-retomada-pendencias.md)
(seções "Extensão da fachada", "Confirmação, atomicidade e idempotência", "Sincronização") deixava essas escolhas como proposta. A tarefa `accord-c9m.3` precisou fixá-las para implementar.

## Decisão

Desenho implementado e testado localmente; aceito pelo usuário em 03/11/2026 ("Aceito os ADRS 39 e 40").

1. **Em revisão é trabalho local.** Lote, movimentos extraídos (com os campos originais exatos do arquivo) e decisões ficam em tabelas Room (`import_batch`, `import_movement`, `import_decision`), não geram
   operação e não sincronizam. Descartar apaga tudo. O arquivo nunca é guardado. A prévia não é gravada: é recalculada do extraído, das decisões e do estado local atual, então reflete o que chegou por sincronização.
2. **Só o lote confirmado sincroniza**, como o agregado `ImportBatch` ([ADR 0024](0024-protocolo-por-agregados-e-cursor.md)): metadados do arquivo, cada movimento com a chave de origem, a resolução
   (criado, vinculado, parcela, fatura quitada, boleto pago, renda, já conhecido ou pendência com seus motivos) e o alvo a que ficou ligado, mais os pares distintos. É o **registro de origem**: o outro
   aparelho reconhece as mesmas linhas ao reimportar. O serviço só ganha o tipo na lista de tipos aceitos (migração `20261103120000_lote_de_importacao.sql`); estado, versão e autorização seguem as funções de S2.
3. **Confirmar é um comando da fachada**, `ConfirmImportBatch`, na mesma fila de operações dos demais. Dentro da transação da própria operação ele **reaplica a reconciliação** e executa, pelo mesmo
   caminho e validador de Chat e formulários, os comandos de S1 que cada movimento pede (`RegisterDirectPayment`, `RegisterCardPurchase`, `CorrectInvoiceAssignment`, `PayInvoice`, `PayBoleto`,
   `RegisterIncome`), com origem `Import`. Qualquer recusa desfaz a transação inteira e devolve o motivo. Os vínculos seguros só gravam o registro de origem; o lançamento mantém identidade, descrição e categoria.
4. **Idempotência.** O ID do comando é a identidade da confirmação (repetir devolve o resultado gravado). Os IDs das operações dos passos derivam de lote, chave do movimento e posição, então repetir após
   encerramento do processo gera os mesmos IDs.
5. **Revalidação.** A pessoa confirma o que viu: um *token* (hash da revisão do lote, da prévia, do que já é conhecido, dos lançamentos candidatos e do contexto de cobrança) acompanha o comando. Se algo
   relevante mudou, nada é aplicado e a nova revisão volta. Isso cobre decisões novas, dados recebidos por sincronização e edição dos alvos.
6. **Dúvidas não viram efeito.** O que continua dúvida fica no lote como `Pending`, com os motivos; a fila única de pendências (S4.5) parte daí.
7. **Resolver uma dúvida guardada** (S4.5) é outro comando da fachada, `ResolveImportItem`, sobre um movimento `Pending` de um lote confirmado. As escolhas da pessoa
   (categoria, vincular, manter separado, "é novo", "é renda de tal categoria", "é repetição") são decisões **locais** sobre o lote confirmado, que não mexem na sua revisão sincronizada. O
   movimento é relido pela mesma reconciliação com essas decisões; só se ele passa a ser claro os mesmos comandos de S1 rodam, numa transação, e a resolução do movimento entra no estado
   sincronizado do lote (nova revisão, uma operação). "É repetição" cria a resolução `Dismissed`: nada é criado e a chave de origem fica lembrada, para a reimportação não perguntar de novo.
   Chave já efetivada passa a valer sobre as dúvidas de FITID e de repetição (regra 1 da spec).
8. **Complemento de 03/11/2026 (`accord-c9m.9`), dentro desta decisão.** A revisão aceita também "revisar depois" (o movimento fica fora da confirmação e vira pendência, com o original)
   e "é repetição de outro movimento deste arquivo" (nada é criado, a chave de origem fica lembrada e a ligação com o original é guardada; os dois originais ficam). Antes de mostrar a
   prévia, cada passo é planejado pelo mesmo validador, sem gravar: o que ele recusaria agora vira dúvida "recusado agora", guardada e tentada de novo a cada leitura, em vez de impedir o lote
   inteiro. Ao confirmar, as escolhas feitas para um movimento que continua aberto (inclusive a categoria padrão do lote) ficam com ele.
9. **Escolhas pequenas, ajustáveis:** a decisão por movimento é ancorada na ordem do extraído (a chave depende da origem escolhida); lançamento novo numa conta é Pix se a linha nomeia Pix, senão débito
   (o documento não diz mais que isso); o histórico de categorias por descrição só sugere, nunca aplica.

## Consequências

- Reimportar não duplica nos dois aparelhos depois que o lote confirmado chega; antes disso, uma importação repetida em dois aparelhos offline ainda pode duplicar até a escolha explícita (risco da spec).
- Um passo recusado pelo validador (por exemplo, fatura já quitada) trava a confirmação inteira até a pessoa decidir diferente; não há confirmação parcial.
- O estado do lote cresce com o número de movimentos (campos originais incluídos) e viaja inteiro; um lote de centenas de linhas é aceitável, de milhares exigiria rever.
- Desfazer um passo avulso pelo histórico de operações deixa o registro de origem apontando para um alvo excluído, que a reimportação trata como "removido anteriormente".
- Dois aparelhos que resolvem a mesma dúvida offline geram dois estados do mesmo lote: o conflito é o de S2 (escolha explícita), não uma mescla.
- A fila de pendências deriva das dúvidas do lote, dos lançamentos e faturas (S1), dos conflitos e do Chat; só a resolução das dúvidas de importação é persistida por esta decisão.
- A forma de continuar no outro aparelho uma revisão não confirmada, a retenção de lotes descartados e a unicidade da chave de origem no serviço seguem como perguntas abertas da spec.

## Fontes e evidências

- [S4](../specs/S4-importacao-retomada-pendencias.md), [ADR 0019](0019-importacao-local-com-rastreabilidade.md), [ADR 0022](0022-fachada-financeira-e-modulos.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md),
  [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md), [ADR 0038](0038-categorias-de-renda.md). Beads `accord-c9m.3`.
- Aceite: usuário, 03/11/2026. O aceite não prova serviço hospedado nem validação nos dois celulares.
- Implementação: `core.importing.ImportSession`/`ImportSteps`, `persistence.RoomImportSession`/`ImportEvaluator`/`ImportPlanner`, esquema Room 6, migração Supabase local. Testes JVM do núcleo e da
  persistência (inclui falha no meio da confirmação, reenvio, vínculo, par distinto, recebimento por outro aparelho) e pgTAP local do novo tipo. Nada disso comprova serviço hospedado, telas, leitor PDF
  nem compatibilidade com arquivos reais de banco além dos exemplos OFX do usuário já usados em S4.1/S4.2.
