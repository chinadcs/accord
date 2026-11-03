-- Accord S1/S4: categorias de renda (ADR 0038) passam a ser um agregado sincronizável, como Category.
--
-- Só amplia a lista de tipos aceitos em envio/recebimento; autorização, versão e idempotência continuam
-- as das funções de S2. Nenhuma tabela nova: o estado fica em space_entities, sob o mesmo RLS.
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory'];
$$;
