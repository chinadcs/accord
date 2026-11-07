-- Accord: ícone escolhido por lançamento ou por nome (ADR 0047).
--
-- Só amplia a lista de tipos aceitos em envio/recebimento com `IconAssociation`; autorização, versão, conflito, idempotência e atomicidade
-- continuam as de S2. Nenhuma tabela nova: o estado fica em space_entities, sob o mesmo RLS de leitura dos membros. O serviço não valida
-- a chave do ícone (o catálogo é do aplicativo); aparelhos ignoram chave que não conhecem.
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation'];
$$;
