---
status: proposed
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0076 — Compra parcelada em várias linhas do mesmo arquivo e estorno sem compra registrada

## Contexto

Em 09/11/2026 o usuário trouxe um CSV real de cartão (282 linhas, cartão Black, sem dados dele nas fixtures) e disse que as parcelas "ainda não deram certo" e que, num crédito
do cartão, "se não tiver compra pra estornar, crie uma compra e estorne ela mesma". Duas decisões do mesmo dia não cobriam esse arquivo:

- [ADR 0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md) tratava uma linha n/N por vez. No CSV a **mesma compra aparece em várias linhas** (1/3, 2/3 e 3/3,
  ou 11/12 e 12/12), todas com a **data da compra**. Cada linha virava uma dúvida "parcela de compra não registrada" (107 de 282), e cadastrar uma a uma criaria a compra
  várias vezes. Além disso, sem fatura declarada o 0073 tomava a fatura da data da linha como a fatura **daquela parcela**, o que só vale para a 1ª.
- [ADR 0074](0074-no-cartao-todo-valor-positivo-e-estorno.md) fazia todo crédito do cartão perguntar "qual compra foi estornada?", com a lista vazia e a dúvida sem saída
  quando não havia compra (consequência registrada lá).

## Decisão (proposta; o pedido é do usuário, o desenho é do agente)

1. **Série.** Linhas de cartão com n/N, sem compra registrada, mesma descrição, data e N, números diferentes e valores iguais a menos de N centavos formam uma **série** (uma
   compra). Duas compras iguais no mesmo dia viram duas séries. A linha de menor número (a primeira do arquivo, no empate) é a **líder**: ela cria a compra; as outras
   **entram nela** e cada uma é vinculada à sua parcela, na mesma confirmação, sem comando próprio.
2. **Total.** Série com as N parcelas no arquivo: o total é o que o arquivo declara (a soma), sem pergunta. Série parcial: o aplicativo **sugere** o total (o que aparece mais
   as parcelas que faltam, cada uma pelo menor valor visto) e a pessoa confirma, por compra (na linha) ou de uma vez para todas ("Cadastrar N compras parceladas pelo total
   sugerido"). A escolha fica na linha líder, seja qual linha a pessoa abra; as parcelas anteriores à primeira do arquivo seguem o 0073 (todas as N, nos meses antigos).
3. **Fatura.** A data da linha é a da compra: a 1ª parcela cai na fatura atribuída pela data (ADR 0043) e a parcela n, n − 1 meses depois. Com fatura declarada pelo documento
   vale o 0073 (a fatura declarada é a da linha líder).
4. **Arredondamento** (a cada parcela que o arquivo mostra, vale o valor da linha: [ADR 0081](0081-valor-exato-de-cada-parcela-da-compra-parcelada-importada.md)). O banco põe os centavos que sobram em geral na 1ª parcela; o aplicativo, na última (23/10/2026). Uma linha cabe na parcela do cronograma se diferir por
   **até N centavos** (N = parcelas). Vale ao vincular a uma compra registrada e ao validar o total. Diferença maior segue sendo "valor diferente" (dúvida).
5. **Refinanciamento da fatura** ("Parcela de refinanciamento"): continua dúvida, como decidido em 03/11/2026. Agrupa em série (uma decisão por compra, as demais linhas
   acompanham), mas **nunca** é aceito sozinho nem pelo botão em lote, mesmo com todas as parcelas no arquivo.
6. **Estorno sem compra.** No cartão, a pessoa pode escolher **"Não achei a compra: registrar e estornar"**: o aplicativo registra uma compra com a data e o valor do crédito, na
   categoria escolhida (a do CSV vale se houver), e estorna **todo** o valor dela, no mesmo passo e na mesma fatura (resultado nulo). Quando **nenhuma** compra do cartão nos 180
   dias anteriores (com saldo a estornar) nem débito anterior do mesmo arquivo de valor ao menos igual poderia ser a original, o aplicativo faz isso sozinho. Havendo candidato,
   a pergunta do 0074 continua (a escolha da compra é da pessoa).
7. Sem mudança de esquema, protocolo ou migração: a compra e o estorno são os comandos `RegisterCardPurchase` e `RegisterRefund` de sempre; as decisões novas são só trabalho
   local do lote.

## Consequências

- O arquivo do usuário passa de 110 dúvidas (107 de parcela e 3 de crédito) para 19: 16 do refinanciamento e 3 créditos com compra candidata no próprio arquivo.
- Estornar uma compra que o aplicativo não conhecia **cria uma despesa e um estorno** de mesmo valor: o total da fatura e da categoria não muda, mas a compra fica registrada.
  Se a original é de uma fatura anterior, a pessoa a escolhe (ou corrige a fatura) em vez de aceitar o automático.
- A tolerância de N centavos torna menos rígido o "valor igual ao cronograma" de 23/10/2026; a compra criada continua dividida com a diferença na última.
- Quem importa o mesmo arquivo de novo não duplica: cada linha tem a chave de origem de sempre.

## Fontes e evidências

- [Spec S4](../specs/plans/S4-importacao-retomada-pendencias.md) regras 9 e 12; [ADR 0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md); ADRs 0073 e 0074.
- Beads `accord-qid0`. Pedido do usuário em 09/11/2026; desenho e propostas (itens 1 a 6) **aguardam o aceite dele**.
- Testes JVM: `InstallmentSeriesTest` (14, core), `InstallmentSeriesImportTest` (5, persistência, arquivo sintético no formato do CSV); instrumentados no emulador:
  `InstallmentSeriesFlowTest` (2) e `ImportUiTest`. O CSV real foi lido só localmente, sem entrar no repositório. **Não validado nos celulares.**
