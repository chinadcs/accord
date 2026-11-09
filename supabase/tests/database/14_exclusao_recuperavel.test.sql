begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
insert into auth.users(id,email,aud,role)
select ('00000000-0000-0000-0000-000000000d0'||n)::uuid,'delete-'||n||'@teste.exemplo','authenticated','authenticated' from generate_series(1,6) n;
insert into public.spaces(id,name,created_by) values
 ('00000000-0000-0000-0000-000000000e01','Casa compartilhada','00000000-0000-0000-0000-000000000d01'),
 ('00000000-0000-0000-0000-000000000e02','Casa solo','00000000-0000-0000-0000-000000000d03'),
 ('00000000-0000-0000-0000-000000000e03','Casa preservada','00000000-0000-0000-0000-000000000d05');
insert into public.space_members(space_id,account_id,nickname,origin) values
 ('00000000-0000-0000-0000-000000000e01','00000000-0000-0000-0000-000000000d01','Pessoa 1','creation'),
 ('00000000-0000-0000-0000-000000000e01','00000000-0000-0000-0000-000000000d02','Pessoa 2','invite'),
 ('00000000-0000-0000-0000-000000000e02','00000000-0000-0000-0000-000000000d03','Pessoa 3','creation'),
 ('00000000-0000-0000-0000-000000000e03','00000000-0000-0000-0000-000000000d05','Pessoa 5','creation'),
 ('00000000-0000-0000-0000-000000000e03','00000000-0000-0000-0000-000000000d06','Pessoa 6','invite');
insert into public.space_entities(type,id,space_id,version,state,author_id,change_cursor) values
 ('Movement','teste-preservado','00000000-0000-0000-0000-000000000e03',1,
 '{"id":"teste-preservado","authorId":"00000000-0000-0000-0000-000000000d05","amountCents":15000}',
 '00000000-0000-0000-0000-000000000d05',1);

select ok(not has_function_privilege('authenticated','public.privacy_prepare_account(uuid,uuid,text)','EXECUTE'),'cliente não prepara por titular escolhido');
select ok(not has_function_privilege('anon','public.privacy_purge_account(uuid,text)','EXECUTE'),'anônimo não executa purge direto');
select ok(not has_function_privilege('authenticated','public.privacy_space_action(uuid,text,uuid,uuid)','EXECUTE'),'cliente não falsifica aprovação por RPC');

select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d01','request_space_deletion',
 '00000000-0000-0000-0000-000000000f01','00000000-0000-0000-0000-000000000e01')->>'status','AwaitingApprovals','primeira aprovação não exclui o espaço de duas pessoas');
select throws_ok($$select public.privacy_space_action('00000000-0000-0000-0000-000000000d04','approve_space_deletion','00000000-0000-0000-0000-000000000f01')$$,
 '42501','privacy_not_member','não membro não aprova');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d02','cancel_space_deletion',
 '00000000-0000-0000-0000-000000000f01')->>'status','Cancelled','qualquer membro pode recusar');
select is((select count(*)::int from public.spaces where id='00000000-0000-0000-0000-000000000e01'),1,'cancelar preserva espaço');

select public.privacy_space_action('00000000-0000-0000-0000-000000000d01','request_space_deletion',
 '00000000-0000-0000-0000-000000000f02','00000000-0000-0000-0000-000000000e01');
insert into public.space_members(space_id,account_id,nickname,origin) values
 ('00000000-0000-0000-0000-000000000e01','00000000-0000-0000-0000-000000000d04','Pessoa 4','invite');
select is((select status from public.privacy_space_deletion where id='00000000-0000-0000-0000-000000000f02'),'Invalidated','novo membro invalida aprovações');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d02','approve_space_deletion',
 '00000000-0000-0000-0000-000000000f02')->>'status','Invalidated','aprovar pedido antigo não apaga espaço');

select public.privacy_space_action('00000000-0000-0000-0000-000000000d01','request_space_deletion',
 '00000000-0000-0000-0000-000000000f03','00000000-0000-0000-0000-000000000e01');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d02','approve_space_deletion',
 '00000000-0000-0000-0000-000000000f03')->>'status','AwaitingApprovals','duas de três aprovações ainda não excluem');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d04','approve_space_deletion',
 '00000000-0000-0000-0000-000000000f03')->>'status','Completed','última aprovação exclui');
select is((select count(*)::int from public.spaces where id='00000000-0000-0000-0000-000000000e01'),0,'espaço removido');
select is((select count(*)::int from auth.users where id in ('00000000-0000-0000-0000-000000000d01','00000000-0000-0000-0000-000000000d02','00000000-0000-0000-0000-000000000d04')),3,'contas individuais preservadas');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000d02','approve_space_deletion',
 '00000000-0000-0000-0000-000000000f03')->>'status','Completed','resposta perdida repete sem nova exclusão');
select is((select count(*)::int from public.privacy_space_notice where space_id='00000000-0000-0000-0000-000000000e01'),3,'um aviso por membro, sem duplicar');

select public.privacy_prepare_account('00000000-0000-0000-0000-000000000d03','00000000-0000-0000-0000-000000000f04',repeat('a',64));
select set_config('request.jwt.claims','{"sub":"00000000-0000-0000-0000-000000000d03","role":"authenticated"}',true);
select ok(not accord_private.is_member('00000000-0000-0000-0000-000000000e02'),'sessão emitida antes do pedido já perdeu acesso');
select set_config('request.jwt.claims','',true);
select throws_ok($$select public.privacy_purge_account('00000000-0000-0000-0000-000000000f04',repeat('b',64))$$,
 '42501','privacy_invalid_proof','comprovante alheio não apaga nada');
select is(public.privacy_purge_account('00000000-0000-0000-0000-000000000f04',repeat('a',64))->>'status','AuthPending','SQL concluído ainda aguarda Auth');
select is((select count(*)::int from public.spaces where id='00000000-0000-0000-0000-000000000e02'),0,'último membro apaga também espaço');
select is(public.privacy_complete_account('00000000-0000-0000-0000-000000000f04',repeat('a',64))->>'status','AuthPending','não declara sucesso antes da exclusão Auth');
delete from auth.users where id='00000000-0000-0000-0000-000000000d03';
select is(public.privacy_complete_account('00000000-0000-0000-0000-000000000f04',repeat('a',64))->>'status','Completed','termina após remover Auth');
select is(public.privacy_purge_account('00000000-0000-0000-0000-000000000f04',repeat('a',64))->>'status','Completed','retomada funciona sem conta existente');
select ok((select subject_id is null from public.privacy_account_deletion where id='00000000-0000-0000-0000-000000000f04'),'recibo concluído não guarda titular');

select public.privacy_prepare_account('00000000-0000-0000-0000-000000000d05','00000000-0000-0000-0000-000000000f05',repeat('c',64));
select public.privacy_purge_account('00000000-0000-0000-0000-000000000f05',repeat('c',64));
select is((select count(*)::int from public.spaces where id='00000000-0000-0000-0000-000000000e03'),1,'conta excluída preserva espaço dos demais');
select is((select state::jsonb->>'authorId' from public.space_entities where id='teste-preservado'),'deleted','remove autoria pessoal no payload');
select is((select (state::jsonb->>'amountCents')::int from public.space_entities where id='teste-preservado'),15000,'preserva valor financeiro');
select is((select version::int from public.space_entities where id='teste-preservado'),2,'autoria alterada gera nova versão');
select ok((select author_id is null from public.space_entities where id='teste-preservado'),'remove vínculo com Auth');
delete from auth.users where id='00000000-0000-0000-0000-000000000d05';
select is(public.privacy_complete_account('00000000-0000-0000-0000-000000000f05',repeat('c',64))->>'status','Completed','excluir criador não bloqueia pelas referências antigas');
select * from finish();
rollback;
