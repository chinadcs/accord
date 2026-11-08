-- Evento de aviso de possível excesso (ADR 0044): imutável, criado por chave natural e lido só pelos membros do espaço.
-- Valores fictícios. U1 e U2 são membros de E1; U3 não é membro.
begin;
create extension if not exists pgtap with schema extensions;
select plan(18);

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

-- 1. Um membro cria o evento.
select tests.as_user('00000000-0000-0000-0000-000000000001');
insert into tests.ctx select 'a', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"a-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":null,"createIfAbsent":true,"state":"{\"categoryId\":\"c1\",\"windowMonday\":\"2026-10-19\",\"totalCents\":130000}","tombstone":false}]}]');
select is((select v -> 'results' -> 0 ->> 'result' from tests.ctx where k = 'a'), 'applied', 'o membro cria o evento');
select is((select v -> 'results' -> 0 -> 'versions' -> 0 ->> 'version' from tests.ctx where k = 'a'), '1', 'a versão do evento é 1');
select is(jsonb_array_length((select v -> 'results' -> 0 -> 'absorbed' from tests.ctx where k = 'a')), 0, 'nada foi absorvido na criação');
reset role;

-- 2. O outro membro, com outra operação e outro conteúdo, recebe o canônico: sem escrita, sem versão nova, sem conflito.
select tests.as_user('00000000-0000-0000-0000-000000000002');
insert into tests.ctx select 'b', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"b-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i2","localTimeMillis":2,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":null,"createIfAbsent":true,"state":"{\"categoryId\":\"c1\",\"windowMonday\":\"2026-10-19\",\"totalCents\":135000}","tombstone":false}]}]');
select is((select v -> 'results' -> 0 ->> 'result' from tests.ctx where k = 'b'), 'applied', 'a segunda inserção da chave não vira conflito');
select is((select v -> 'results' -> 0 -> 'absorbed' -> 0 ->> 'id' from tests.ctx where k = 'b'), 'ev-1', 'a resposta traz o evento existente');
select is(((select v -> 'results' -> 0 -> 'absorbed' -> 0 ->> 'state' from tests.ctx where k = 'b')::jsonb) ->> 'totalCents', '130000', 'o canônico é o do primeiro reconhecido');
select is(jsonb_array_length((select v -> 'results' -> 0 -> 'versions' from tests.ctx where k = 'b')), 0, 'nenhuma versão nova foi escrita');
reset role;
select is((select count(*)::int from public.space_entities where type = 'AlertEvent' and space_id = '00000000-0000-0000-0000-0000000000e1'), 1, 'há um só registro para a chave');
select is((select version from public.space_entities where id = 'ev-1'), 1::bigint, 'a versão continua 1');
select is((select author_id from public.space_entities where id = 'ev-1'), '00000000-0000-0000-0000-000000000001'::uuid, 'o autor continua o do primeiro');

-- 3. Reenvio da mesma operação: o mesmo resultado, sem segunda inserção.
select tests.as_user('00000000-0000-0000-0000-000000000002');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"b-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i2","localTimeMillis":2,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":null,"createIfAbsent":true,"state":"{\"categoryId\":\"c1\",\"windowMonday\":\"2026-10-19\",\"totalCents\":135000}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'repeated',
  'true', 'o reenvio devolve o resultado já registrado');
reset role;

-- 4. Imutável: alterar, remover ou recriar sem createIfAbsent é recusado.
select tests.as_user('00000000-0000-0000-0000-000000000001');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i1","localTimeMillis":3,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":1,"state":"{\"totalCents\":1}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'AlertEventImmutable', 'alterar o evento é recusado');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i1","localTimeMillis":4,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":1,"state":"{}","tombstone":true}]}]')) -> 'results' -> 0 ->> 'reason',
  'AlertEventImmutable', 'remover o evento é recusado');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-3","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i1","localTimeMillis":5,
     "changes":[{"type":"AlertEvent","id":"ev-1","baseVersion":null,"state":"{\"totalCents\":1}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'AlertEventImmutable', 'recriar sem createIfAbsent também é recusado');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-4","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i1","localTimeMillis":6,
     "changes":[{"type":"Movement","id":"mov-1","baseVersion":null,"createIfAbsent":true,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'InvalidEnvelope', 'createIfAbsent só vale para o evento de aviso');
reset role;
select is((select (state::jsonb) ->> 'totalCents' from public.space_entities where id = 'ev-1'), '130000', 'as recusas não alteraram o evento');

-- 5. Quem não é membro não lê nem insere.
select tests.as_user('00000000-0000-0000-0000-000000000003');
select is((select count(*)::int from public.space_entities where type = 'AlertEvent'), 0, 'não membro não lê o evento');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"d-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":1,"installationId":"i3","localTimeMillis":7,
     "changes":[{"type":"AlertEvent","id":"ev-2","baseVersion":null,"createIfAbsent":true,"state":"{}","tombstone":false}]}]')) ->> 'error',
  'not_member', 'não membro não insere');
reset role;

select * from finish();
rollback;
