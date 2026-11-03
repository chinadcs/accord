-- Accord S4: o lote de importação confirmado passa a ser um agregado sincronizável (ADR 0040).
--
-- Só amplia a lista de tipos aceitos em envio/recebimento; autorização, versão e idempotência continuam as das funções de S2.
-- Nenhuma tabela nova: o estado (metadados do arquivo, movimentos extraídos com os campos originais, resolução de cada um e
-- pares distintos) fica em space_entities, sob o mesmo RLS. O arquivo bancário nunca é enviado, e a revisão em andamento
-- não gera operação: só a confirmação chega aqui.
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch'];
$$;
