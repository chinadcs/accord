-- Accord: "Quem me deve" (ADR 0083): devedor e valor a receber do casal.
--
-- Amplia a lista de tipos aceitos com `Debtor` e `Receivable` e mantém a paridade do saldo próprio da conta usado na validação dos
-- cofrinhos: o dinheiro emprestado que só reduz o saldo (`lentFromAccountId`/`lentEpochDay`) também sai dele, como em BalanceReader.
-- Nenhuma tabela nova: o estado fica em space_entities sob o mesmo RLS. Autorização, versão, conflito, idempotência e atomicidade
-- continuam as de S2; o vínculo `receivableId` em renda e movimento é campo aditivo do estado. A soma dos recebimentos contra o total
-- vale no aparelho (cada renda é uma entidade própria); o serviço não a recalcula.
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation,Intention,AccountMove,ClueEvaluation,SavingsPocket,SavingsMove,Debtor,Receivable

create or replace function accord_private.shared_types() returns text[] language sql immutable set search_path = '' as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation','Intention',
               'AccountMove','ClueEvaluation','SavingsPocket','SavingsMove','Debtor','Receivable'];
$$;

-- Espelha BalanceReader (inclui o empréstimo de "Quem me deve"): somente recebimentos/saídas efetivos, sem rendas previstas nem compras no cartão.
create or replace function accord_private.savings_account_days(p_space uuid,p_changes jsonb,p_id text,p_from bigint)
returns table(event_day bigint,balance numeric) language sql stable set search_path = '' as $$
with states as materialized(select * from accord_private.savings_states(p_space,p_changes)),
a as(select data account_data from states where type='Account' and id=p_id),
live as(select * from states where not coalesce((data->>'voided')::boolean,false)),
events as(
 select (data->>'epochDay')::bigint event_day,-(data->>'amountCents')::numeric cents from live,a
 where type='Movement' and (data->>'accountId'=p_id or (data->>'accountId' is null and data->>'kind'='Cash' and coalesce((a.account_data->>'isWallet')::boolean,false)))
 union all select (data->>'receivedEpochDay')::bigint,coalesce(data->>'receivedCents',data->>'expectedCents')::numeric from live
 where type='Income' and data->>'accountId'=p_id and data->>'receivedEpochDay' is not null
 union all select (data->>'paidEpochDay')::bigint,-coalesce(data->>'paidCents',data->>'expectedCents')::numeric from live
 where type='Boleto' and data->>'accountId'=p_id and data->>'paidEpochDay' is not null
 union all select (data->>'epochDay')::bigint,-(data->>'amountCents')::numeric from live where type='InvoicePayment' and data->>'accountId'=p_id
 union all select (data->>'epochDay')::bigint,(data->>'amountCents')::numeric from live where type='AccountMove' and data->>'toAccountId'=p_id
 union all select (data->>'epochDay')::bigint,-(data->>'amountCents')::numeric from live where type='AccountMove' and data->>'fromAccountId'=p_id
 -- ADR 0083: dinheiro emprestado que só reduz o saldo (com "contar como despesa" a saída é um Movement, já somado acima).
 union all select (data->>'lentEpochDay')::bigint,-(data->>'totalCents')::numeric from live
 where type='Receivable' and data->>'lentFromAccountId'=p_id and data->>'lentEpochDay' is not null
 union all select (data->>'epochDay')::bigint,case when data->>'direction'='Deposit' then -(data->>'amountCents')::numeric else (data->>'amountCents')::numeric end from live where type='SavingsMove' and data->>'accountId'=p_id
 union all select (s->>'epochDay')::bigint,case when data->>'kind'='Income' then (s->>'cents')::numeric else -(s->>'cents')::numeric end
 from live cross join lateral jsonb_array_elements(coalesce(data->'settlements','[]'::jsonb)) s
 where type='Recurrence' and data->>'form'='OffCard' and s->>'accountId'=p_id
 union all select p_from,0::numeric
),daily as(select event_day,sum(cents) cents from events,a where event_day >= (a.account_data->>'balanceCutoffEpochDay')::bigint group by event_day),
running as(select event_day,(a.account_data->>'initialBalanceCents')::numeric+sum(cents) over(order by event_day) balance from daily,a)
select * from running where event_day>=p_from;
$$;
