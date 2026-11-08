-- Accord: transferências e ajustes de saldo (ADR 0051). Mesmo RLS e protocolo financeiro.
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation,Intention,AccountMove

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation','Intention','AccountMove'];
$$;
