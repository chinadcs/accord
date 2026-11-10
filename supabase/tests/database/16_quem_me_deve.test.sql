-- Valores e identidades fictícios. "Quem me deve" (ADR 0083): tipos aceitos no protocolo e paridade do saldo próprio com o empréstimo.
begin;
create extension if not exists pgtap with schema extensions;
select plan(5);
create schema tests;
create function tests.change(t text,i text,s jsonb) returns jsonb language sql as $$
 select jsonb_build_object('type',t,'id',i,'state',s::text,'tombstone',false);
$$;
create table tests.ctx (changes jsonb);
insert into tests.ctx values (jsonb_build_array(
 tests.change('Account','a','{"id":"a","initialBalanceCents":100000,"balanceCutoffEpochDay":10000,"isWallet":false}'),
 tests.change('Debtor','d','{"id":"d","name":"Teste","nameKey":"teste","authorId":"00000000-0000-0000-0000-000000000001","voided":false}'),
 tests.change('Receivable','r','{"id":"r","debtorId":"d","totalCents":30000,"accountId":"a","lentFromAccountId":"a","lentEpochDay":10001,"countsInBudget":false,"voided":false,"authorId":"00000000-0000-0000-0000-000000000001"}'),
 tests.change('Receivable','counted','{"id":"counted","debtorId":"d","totalCents":77777,"accountId":"a","lentFromAccountId":null,"lentEpochDay":null,"countsInBudget":true,"voided":false}'),
 tests.change('Receivable','other-account','{"id":"other-account","debtorId":"d","totalCents":55555,"accountId":"b","lentFromAccountId":"b","lentEpochDay":10001,"countsInBudget":false,"voided":false}'),
 tests.change('Receivable','voided','{"id":"voided","debtorId":"d","totalCents":88888,"accountId":"a","lentFromAccountId":"a","lentEpochDay":10001,"countsInBudget":false,"voided":true}'),
 tests.change('Income','receipt','{"accountId":"a","receivedEpochDay":10001,"expectedCents":10000,"receivedCents":10000,"receivableId":"r","voided":false}'),
 tests.change('Income','expected','{"accountId":"a","receivedEpochDay":null,"expectedCents":20000,"receivableId":"r","voided":false}')
));
select ok('Debtor'=any(accord_private.shared_types()) and 'Receivable'=any(accord_private.shared_types()),'tipos novos estão permitidos no protocolo');
select is(
 (select balance from accord_private.savings_account_days('00000000-0000-0000-0000-0000000000e1',(select changes from tests.ctx),'a',10001) order by event_day desc limit 1),
 80000::numeric,
 'saldo próprio desconta o empréstimo da conta (100.000 − 30.000) e soma só o recebimento (+10.000), sem o previsto, o contado, o cancelado nem o de outra conta'
);
select is(
 (select balance from accord_private.savings_account_days('00000000-0000-0000-0000-0000000000e1',(select changes from tests.ctx),'a',10001) order by event_day desc limit 1),
 100000::numeric - 30000 + 10000,
 'conta fecha com saída e entrada do mesmo dia'
);
select is(
 accord_private.privacy_scrub_author('{"authorId":"00000000-0000-0000-0000-000000000001","debtorId":"d"}','00000000-0000-0000-0000-000000000001'),
 '{"authorId":"deleted","debtorId":"d"}'::jsonb,
 'exclusão da conta retira a autoria do devedor e do valor a receber'
);
select ok(not has_function_privilege('authenticated','accord_private.savings_account_days(uuid,jsonb,text,bigint)','execute'),'cliente não lê a projeção privada');
select * from finish();
rollback;
