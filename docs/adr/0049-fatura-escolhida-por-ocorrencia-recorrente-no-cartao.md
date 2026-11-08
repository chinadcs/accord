---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0049 — Fatura escolhida para uma ocorrência de recorrência no cartão

## Contexto

O [ADR 0011](0011-alcance-de-recorrencias.md) define o alcance das edições de recorrência (só esta ocorrência ou esta e as próximas) e o contrato da série diz que, no
cartão, cada ocorrência recebe fatura como compra à vista. Essa fatura, porém, é **derivada**: o ciclo do cartão ([CardCycle](../../core/src/main/kotlin/app/accord/core/CardCycle.kt))
atribui o mês de vencimento pela data da ocorrência e marca "a conferir" quando a data cai na janela de véspera, dia e dia seguinte ao fechamento. Nada fica gravado por ocorrência.
Consequência: não havia comando para **confirmar** nem **corrigir** a fatura de uma ocorrência, e o selo só sumia se a data saísse da janela. Uma compra parcelada tem
`CorrectInvoiceAssignment` (1ª parcela), e a compra à vista tem a fatura informada no registro; a ocorrência recorrente ficou sem equivalente (`accord-6w8w`, achado de `accord-abv`).
Reconferido em 08/11/2026: a importação (S4: `accord-c9m.5`, `.11`, `.12`) resolve compras e parcelas importadas, não ocorrências de série.

Restrição do projeto: não estender alcance de edição por suposição (AGENTS.md, "Alcance de edição").

## Decisão

Alcance aceito pelo usuário em 08/11/2026 (opção "só esta ocorrência", em resposta à pergunta do `accord-6w8w`); o desenho técnico dos itens 2 a 7 foi aceito pelo usuário no mesmo dia ("aceito o 049").

1. **A pessoa pode confirmar ou corrigir a fatura de uma só ocorrência.** A escolha é uma **exceção da ocorrência** (a mesma família das exceções de campo do ADR 0011), nunca um campo da série e nunca vale
   para as próximas: as demais ocorrências continuam seguindo o ciclo, inclusive as que também caem na janela. "Esta e as próximas" para fatura **não** existe e só entraria por nova decisão do usuário.
2. **Estado.** `OccurrenceOverride` ganha `invoice` (mês de vencimento, opcional). Na Room, a coluna `invoiceMonth` (texto `AAAA-MM`, nula) em `recurrence_override` (migração automática 15→16).
   Vai no estado sincronizado da `Recurrence` junto das demais exceções; não há tipo de entidade novo. O serviço só valida tipo e referências do estado, que é opaco para este campo: **sem migração Supabase**.
   Estados antigos, sem o campo, decodificam como "segue o ciclo".
3. **Comando `CorrectOccurrenceInvoice(recurrenceId, occurrence, invoice, expectedRevision)`.** Só para despesa recorrente no cartão. Grava a exceção, incrementa a revisão da série e tira o selo "a conferir" da ocorrência
   (`AssignmentState.Confirmed`, o mesmo estado de uma fatura informada). Escolher a própria fatura estimada **confirma** sem mudar de mês; escolher outra **move** o valor de uma fatura para a outra (orçamento do mês,
   composição e total conhecido da fatura, previsão e pendências).
4. **Limite de um mês da estimada.** A fatura escolhida fica no máximo um mês antes ou depois da estimada pelo ciclo, que é o erro que a janela de fechamento realmente representa. O limite é do desenho, não do produto, e também
   mantém curta a janela de meses que a consulta de ocorrências precisa examinar. Fora dele, o comando recusa (`OutOfRange`).
5. **Fatura quitada.** Mudar de fatura é recusado se a fatura de origem ou a de destino já foi quitada, como na correção da fatura da 1ª parcela. Confirmar a fatura em que a ocorrência já está continua permitido,
   porque nada se move. Confirmar duas vezes a mesma fatura é recusado, sem gerar operação.
6. **Sem desfazer.** A operação não é desfazível no P0, como `EditRecurrence`: o desfazer de série restaura pagamento de ocorrência, não exceções. A correção é escolher outra fatura. Editar a série "esta e as próximas"
   preserva a fatura escolhida das ocorrências (a edição só troca os campos de série). Excluir a ocorrência a faz deixar de existir, e a fatura escolhida fica sem efeito.
7. **Interface.** No detalhe da ocorrência no cartão, ação "Conferir fatura" (ou "Corrigir fatura", se já escolhida) com as três faturas possíveis e **sem opção pré-selecionada**; a nota diz que vale só para esta ocorrência.
   Ocorrências "a conferir" **não** entram na fila de Revisar nesta entrega: continuam contadas nos totais provisórios do orçamento e da fatura, como antes.

## Consequências

Fecha a lacuna sem mudar o contrato de recorrência, o protocolo nem o serviço, e sem custo para quem não usa a ação (campo nulo). Duas pessoas que corrigem a mesma ocorrência offline geram conflito de edição da série, resolvido pela
escolha explícita do [ADR 0018](0018-conflitos-com-escolha-explicita.md), como qualquer edição de recorrência. O estado carrega a fatura escolhida para o outro aparelho (`EntityStatesRoundTripTest`).

Limites a rever: (a) o teto de um mês, se uma fatura precisar mover mais; (b) a regra de "esta e as próximas" para fatura, se for pedida; (c) incluir ocorrências "a conferir" na fila de Revisar, que exigiria um item de
pendência novo; (d) o desfazer, se o desfazer de série passar a restaurar exceções. A janela de consulta de ocorrências olha agora até três meses antes da data (antes, um), um custo pequeno por mês consultado.

## Fontes e evidências

- [ADR 0011](0011-alcance-de-recorrencias.md), [ADR 0018](0018-conflitos-com-escolha-explicita.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md), [ADR 0025](0025-projecoes-financeiras-derivadas.md); plano §7.2 e S1 §7.6.
- Beads `accord-6w8w` (origem `accord-abv`). Aceite do alcance e do desenho: usuário, 08/11/2026.
- Implementação e validação locais: `CorrectOccurrenceInvoice`, `RecurrencePlanner.planCorrectInvoice`, `RecurrenceTest` (8 casos novos), `EntityStatesRoundTripTest`, `LinkedActionsTest` e `LinkedFlowTest` no AVD.
  Sem validação nos Galaxy S20 FE e S23 FE e sem teste contra o Supabase hospedado. O aceite não prova validação em aparelho nem implantação.
