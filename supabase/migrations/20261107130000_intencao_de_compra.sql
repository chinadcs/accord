-- Accord: intenção de compra do casal (S7, ADR 0048).
--
-- Só amplia a lista de tipos aceitos em envio/recebimento com `Intention`; autorização, versão, conflito, idempotência e atomicidade
-- continuam as de S2. Nenhuma tabela nova: o estado fica em space_entities, sob o mesmo RLS de leitura dos membros. Edição concorrente da mesma
-- intenção vira conflito de versão (as duas versões inteiras, sem mesclar). O vínculo de conversão (intenção de origem do compromisso) vem em
-- migração própria.
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation,Intention

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation','Intention'];
$$;
