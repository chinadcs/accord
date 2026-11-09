-- Valores e autores fictícios. Paridade do conjunto de eventos e limites dos saldos no serviço.
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);
create schema tests;
create function tests.change(t text,i text,s jsonb) returns jsonb language sql as $$
 select jsonb_build_object('type',t,'id',i,'state',s::text,'tombstone',false);
$$;
create table tests.ctx (changes jsonb);
insert into tests.ctx values (jsonb_build_array(
 tests.change('Account','a','{"id":"a","initialBalanceCents":100000,"balanceCutoffEpochDay":10000,"isWallet":false}'),
 tests.change('SavingsPocket','p','{"id":"p","accountId":"a","name":"Reserva","initialCents":10000,"cutoffEpochDay":10000,"goalCents":50000,"deadlineEpochDay":null,"archived":false,"createdBy":"00000000-0000-0000-0000-000000000001"}'),
 tests.change('Movement','m','{"accountId":"a","epochDay":10001,"amountCents":1000,"voided":false}'),
 tests.change('Movement','voided','{"accountId":"a","epochDay":10001,"amountCents":55555,"voided":true}'),
 tests.change('Movement','cash','{"accountId":null,"kind":"Cash","epochDay":10001,"amountCents":99999,"voided":false}'),
 tests.change('Movement','before-cut','{"accountId":"a","epochDay":9999,"amountCents":90000,"voided":false}'),
 tests.change('Income','received','{"accountId":"a","receivedEpochDay":10001,"expectedCents":99999,"receivedCents":25000,"voided":false}'),
 tests.change('Income','expected','{"accountId":"a","receivedEpochDay":null,"expectedCents":999999,"voided":false}'),
 tests.change('Boleto','b','{"accountId":"a","paidEpochDay":10001,"expectedCents":9999,"paidCents":2000,"voided":false}'),
 tests.change('InvoicePayment','ip','{"accountId":"a","epochDay":10001,"amountCents":3000,"voided":false}'),
 tests.change('AccountMove','ti','{"toAccountId":"a","epochDay":10001,"amountCents":7000,"voided":false}'),
 tests.change('AccountMove','to','{"fromAccountId":"a","epochDay":10001,"amountCents":11000,"voided":false}'),
 tests.change('AccountMove','adj','{"toAccountId":"a","epochDay":10001,"amountCents":500,"voided":false}'),
 tests.change('Recurrence','expense','{"form":"OffCard","kind":"Expense","voided":false,"settlements":[{"accountId":"a","epochDay":10001,"cents":4000}]}'),
 tests.change('Recurrence','income','{"form":"OffCard","kind":"Income","voided":false,"settlements":[{"accountId":"a","epochDay":10001,"cents":5000}]}'),
 tests.change('Recurrence','card','{"form":"Card","kind":"Expense","voided":false,"settlements":[{"accountId":"a","epochDay":10001,"cents":99999}]}'),
 tests.change('SavingsMove','deposit','{"id":"deposit","pocketId":"p","accountId":"a","epochDay":10001,"amountCents":20000,"direction":"Deposit","authorId":"00000000-0000-0000-0000-000000000001","voided":false}'),
 tests.change('SavingsMove','withdraw','{"id":"withdraw","pocketId":"p","accountId":"a","epochDay":10001,"amountCents":6000,"direction":"Withdraw","authorId":"00000000-0000-0000-0000-000000000001","voided":false}')
));
select is((select balance from accord_private.savings_account_days('00000000-0000-0000-0000-0000000000e1',(select changes from tests.ctx),'a',10001) order by event_day desc limit 1),102500::numeric,'saldo próprio combina apenas eventos efetivos e exclui corte anterior');
select is((select balance from accord_private.savings_pocket_days('00000000-0000-0000-0000-0000000000e1',(select changes from tests.ctx),'p') order by event_day desc limit 1),24000::numeric,'reserva tem inicial mais guardar menos resgatar');
select is(accord_private.validate_savings('00000000-0000-0000-0000-0000000000e1',(select changes from tests.ctx),'00000000-0000-0000-0000-000000000001'),null::text,'conjunto permitido passa pela validação canônica');
select ok(not has_function_privilege('authenticated','accord_private.savings_states(uuid,jsonb)','execute'),'cliente não lê projeção privada');
select ok(not has_function_privilege('authenticated','accord_private.validate_savings(uuid,jsonb,uuid)','execute'),'cliente não chama validação com pessoa escolhida');
select ok('SavingsPocket'=any(accord_private.shared_types()) and 'SavingsMove'=any(accord_private.shared_types()),'tipos novos estão permitidos no protocolo');
select is(accord_private.validate_savings('00000000-0000-0000-0000-0000000000e1',(select changes||jsonb_build_array(tests.change('Movement','bad','{"accountId":"p"}')) from tests.ctx),'00000000-0000-0000-0000-000000000001'),'Resgate o cofrinho antes de pagar.','reserva não é conta de pagamento');
select isnt(accord_private.validate_savings('00000000-0000-0000-0000-0000000000e1',(select jsonb_set(changes,'{1,state}',to_jsonb('{"id":"p","accountId":"a","name":"Reserva","initialCents":0,"cutoffEpochDay":10000,"goalCents":null,"deadlineEpochDay":null,"archived":false,"createdBy":"00000000-0000-0000-0000-000000000001"}'::text))||jsonb_build_array(tests.change('SavingsMove','early','{"id":"early","pocketId":"p","accountId":"a","epochDay":10000,"amountCents":1,"direction":"Withdraw","authorId":"00000000-0000-0000-0000-000000000001","voided":false}')) from tests.ctx),'00000000-0000-0000-0000-000000000001'),null::text,'resgate retroativo não depende de depósito futuro');
select is(accord_private.privacy_scrub_author('{"createdBy":"00000000-0000-0000-0000-000000000001","authorId":"00000000-0000-0000-0000-000000000001"}','00000000-0000-0000-0000-000000000001'),' {"createdBy":"deleted","authorId":"deleted"}'::jsonb,'exclusão da conta retira também autoria de criação do cofrinho');
select * from finish();
rollback;
