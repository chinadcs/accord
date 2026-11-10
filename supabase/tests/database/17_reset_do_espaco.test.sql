begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
insert into auth.users(id,email,aud,role)
select ('00000000-0000-0000-0000-000000000c0'||n)::uuid,'reset-'||n||'@teste.exemplo','authenticated','authenticated' from generate_series(1,4) n;
insert into public.spaces(id,name,created_by) values
 ('00000000-0000-0000-0000-000000000b01','Casa para zerar','00000000-0000-0000-0000-000000000c01'),
 ('00000000-0000-0000-0000-000000000b02','Casa intacta','00000000-0000-0000-0000-000000000c03');
insert into public.space_members(space_id,account_id,nickname,origin) values
 ('00000000-0000-0000-0000-000000000b01','00000000-0000-0000-0000-000000000c01','Pessoa 1','creation'),
 ('00000000-0000-0000-0000-000000000b01','00000000-0000-0000-0000-000000000c02','Pessoa 2','invite'),
 ('00000000-0000-0000-0000-000000000b02','00000000-0000-0000-0000-000000000c03','Pessoa 3','creation');
insert into public.space_entities(type,id,space_id,version,state,author_id,change_cursor) values
 ('Movement','reset-a','00000000-0000-0000-0000-000000000b01',1,'{"id":"reset-a","amountCents":100}','00000000-0000-0000-0000-000000000c01',1),
 ('Movement','reset-b','00000000-0000-0000-0000-000000000b01',1,'{"id":"reset-b","amountCents":200}','00000000-0000-0000-0000-000000000c02',2),
 ('Movement','intacta-a','00000000-0000-0000-0000-000000000b02',1,'{"id":"intacta-a","amountCents":300}','00000000-0000-0000-0000-000000000c03',1);

select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c01','request_space_reset',
 '00000000-0000-0000-0000-000000000a01','00000000-0000-0000-0000-000000000b01')->>'status','AwaitingApprovals','um membro sozinho não zera o espaço de duas pessoas');
select is((select count(*)::int from public.space_entities where space_id='00000000-0000-0000-0000-000000000b01'),2,'pedido não apaga nada');
select is((select kind from public.privacy_space_deletion where id='00000000-0000-0000-0000-000000000a01'),'Reset','pedido registra o tipo reset');
select throws_ok($$select public.privacy_space_action('00000000-0000-0000-0000-000000000c01','request_space_deletion','00000000-0000-0000-0000-000000000a02','00000000-0000-0000-0000-000000000b01')$$,
 '42501','privacy_request_exists','excluir não corre junto com um reset pendente');
select throws_ok($$select public.privacy_space_action('00000000-0000-0000-0000-000000000c04','approve_space_deletion','00000000-0000-0000-0000-000000000a01')$$,
 '42501','privacy_not_member','não membro não aprova o reset');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c02','cancel_space_deletion',
 '00000000-0000-0000-0000-000000000a01')->>'status','Cancelled','qualquer membro pode recusar o reset');
select is((select reset_epoch from public.spaces where id='00000000-0000-0000-0000-000000000b01'),0::bigint,'recusar não muda a época');

select public.privacy_space_action('00000000-0000-0000-0000-000000000c01','request_space_reset',
 '00000000-0000-0000-0000-000000000a03','00000000-0000-0000-0000-000000000b01');
insert into public.space_members(space_id,account_id,nickname,origin) values
 ('00000000-0000-0000-0000-000000000b01','00000000-0000-0000-0000-000000000c04','Pessoa 4','invite');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c02','approve_space_deletion',
 '00000000-0000-0000-0000-000000000a03')->>'status','Invalidated','mudança de membros invalida o reset pendente');
select is((select count(*)::int from public.space_entities where space_id='00000000-0000-0000-0000-000000000b01'),2,'pedido invalidado não apaga nada');

select public.privacy_space_action('00000000-0000-0000-0000-000000000c01','request_space_reset',
 '00000000-0000-0000-0000-000000000a04','00000000-0000-0000-0000-000000000b01');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c02','approve_space_deletion',
 '00000000-0000-0000-0000-000000000a04')->>'status','AwaitingApprovals','falta aprovar um membro');
select is((select count(*)::int from public.space_entities where space_id='00000000-0000-0000-0000-000000000b01'),2,'sem unanimidade nada é apagado');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c04','approve_space_deletion',
 '00000000-0000-0000-0000-000000000a04')->>'status','Completed','todos aprovaram: o reset conclui');
select is((select count(*)::int from public.space_entities where space_id='00000000-0000-0000-0000-000000000b01'),0,'dados financeiros do espaço zerados');
select is((select count(*)::int from public.space_entities where space_id='00000000-0000-0000-0000-000000000b02'),1,'outro espaço intacto');
select is((select count(*)::int from public.space_members where space_id='00000000-0000-0000-0000-000000000b01'),3,'membros preservados');
select is((select reset_epoch from public.spaces where id='00000000-0000-0000-0000-000000000b01'),1::bigint,'época sobe uma vez');
select is(public.privacy_space_action('00000000-0000-0000-0000-000000000c01','approve_space_deletion',
 '00000000-0000-0000-0000-000000000a04')->>'status','Completed','repetir a aprovação não zera de novo');
select is((select reset_epoch from public.spaces where id='00000000-0000-0000-0000-000000000b01'),1::bigint,'época não sobe na repetição');

select is(public.privacy_status('00000000-0000-0000-0000-000000000c02')->'spaces'->0->>'resetEpoch','1','status informa a época do espaço');
select is(jsonb_array_length(public.privacy_status('00000000-0000-0000-0000-000000000c02')->'requests'),1,'status lista só o último reset concluído, não o histórico');
select is(public.privacy_status('00000000-0000-0000-0000-000000000c02')->'requests'->0->>'kind','Reset','status informa o tipo do pedido');
select * from finish();
rollback;
