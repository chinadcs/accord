-- Evento de aviso de possível excesso (ADR 0044): imutável, criado por chave natural e lido só pelos membros do espaço.
-- Valores fictícios. U1 e U2 são membros de E1; U3 não é membro.
begin;
create extension if not exists pgtap with schema extensions;
select plan(8);

create schema tests;
grant usage on schema tests to authenticated;
create table tests.ctx (k text primary key, v jsonb);
grant all on tests.ctx to authenticated;
create function tests.as_user(p_id uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_id, 'role', 'authenticated')::text, true);
  set local role authenticated;
end;
$$;
grant execute on function tests.as_user(uuid) to authenticated;

insert into auth.users (id, email, aud, role)
select ('00000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid, 'u' || n || '@teste.exemplo', 'authenticated', 'authenticated'
from generate_series(1, 3) n;

select tests.as_user('00000000-0000-0000-0000-000000000001');
select public.create_space('00000000-0000-0000-0000-0000000000e1', 'Casa');
insert into tests.ctx select 'code', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000002');
select public.join_with_code((select v ->> 'code' from tests.ctx where k = 'code'));
reset role;


create function tests.clue_op(op text, eid text, base bigint default null, gone boolean default false) returns jsonb language sql as $$
 select jsonb_build_array(jsonb_build_object('operationId',op,'spaceId','00000000-0000-0000-0000-0000000000e1','protocolVersion',2,
 'installationId','i1','localTimeMillis',1,'changes',jsonb_build_array(jsonb_build_object('type','ClueEvaluation','id',eid,
 'baseVersion',base,'state','{"clueKey":"same","fingerprint":"v1"}','tombstone',gone))));
$$;
grant execute on function tests.clue_op(text,text,bigint,boolean) to authenticated;
select tests.as_user('00000000-0000-0000-0000-000000000001');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('a','ev-a')))->'results'->0->>'result', 'applied', 'membro cria avalia??o');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('a','ev-a')))->'results'->0->>'repeated', 'true', 'reenvio ap?s resposta perdida ? idempotente');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('edit','ev-a',1)))->'results'->0->>'reason', 'ClueEvaluationImmutable', 'altera??o recusada');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('delete','ev-a',1,true)))->'results'->0->>'reason', 'ClueEvaluationImmutable', 'exclus?o recusada');
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000002');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('b','ev-b')))->'results'->0->>'result', 'applied', 'outra avalia??o da mesma pista acrescenta hist?rico');
select is((select count(*)::int from public.space_entities where type='ClueEvaluation'), 2, 'outro membro l? ambas as avalia??es');
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000003');
select is((select count(*)::int from public.space_entities where type='ClueEvaluation'), 0, 'n?o membro n?o l? avalia??es');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', tests.clue_op('foreign','ev-c')))->>'error', 'not_member', 'n?o membro n?o grava avalia??o');
reset role;
select * from finish();
rollback;
