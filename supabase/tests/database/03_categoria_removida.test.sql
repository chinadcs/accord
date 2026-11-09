-- Categoria removida (ADR 0039): o serviço nunca guarda lançamento vivo numa categoria removida.
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

create schema tests;
grant usage on schema tests to authenticated;
create function tests.as_user(p_id uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_id, 'role', 'authenticated')::text, true);
  set local role authenticated;
end;
$$;
grant execute on function tests.as_user(uuid) to authenticated;

insert into auth.users (id, email, aud, role)
values ('00000000-0000-0000-0000-000000000001', 'u1@teste.exemplo', 'authenticated', 'authenticated');

select tests.as_user('00000000-0000-0000-0000-000000000001');
select public.create_space('00000000-0000-0000-0000-0000000000e1', 'Casa');

-- Duas categorias e dois lançamentos (um movimento direto e uma recorrência com trecho e exceção).
select public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Category","id":"cat-a","baseVersion":null,"state":"{\"parentId\":null,\"removed\":false}","tombstone":false},
                {"type":"Category","id":"cat-b","baseVersion":null,"state":"{\"parentId\":null,\"removed\":false}","tombstone":false},
                {"type":"Movement","id":"mov-1","baseVersion":null,"state":"{\"classificationId\":\"cat-a\",\"voided\":false}","tombstone":false},
                {"type":"Recurrence","id":"rec-1","baseVersion":null,"state":"{\"segments\":[{\"fromNumber\":1,\"classificationId\":\"cat-a\"}],\"overrides\":[],\"voided\":false}","tombstone":false}]}]');

-- 1. Remover cat-a sem tirar os lançamentos é recusado (movimento e recorrência guardados apontam para ela).
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"r-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":2,
     "changes":[{"type":"Category","id":"cat-a","baseVersion":1,"state":"{\"parentId\":null,\"removed\":true}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'CategoryInUse', 'remover com lançamento vivo apontando para a categoria é recusado');
select is((select (state::jsonb ->> 'removed') from public.space_entities where id = 'cat-a'), 'false', 'a categoria continua ativa depois da recusa');

-- 2. Remover e tirar só o movimento: a recorrência ainda aponta para cat-a.
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"r-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":3,
     "changes":[{"type":"Category","id":"cat-a","baseVersion":1,"state":"{\"parentId\":null,\"removed\":true}","tombstone":false},
                {"type":"Movement","id":"mov-1","baseVersion":1,"state":"{\"classificationId\":\"cat-b\",\"voided\":false}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'CategoryInUse', 'a recorrência com trecho na categoria também impede a remoção');
select is((select (state::jsonb ->> 'classificationId') from public.space_entities where id = 'mov-1'), 'cat-a', 'a recusa não deixou o movimento pela metade');

-- 3. Mover tudo e remover na mesma operação é aplicado.
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"r-3","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":4,
     "changes":[{"type":"Category","id":"cat-a","baseVersion":1,"state":"{\"parentId\":null,\"removed\":true}","tombstone":false},
                {"type":"Movement","id":"mov-1","baseVersion":1,"state":"{\"classificationId\":\"cat-b\",\"voided\":false}","tombstone":false},
                {"type":"Recurrence","id":"rec-1","baseVersion":1,"state":"{\"segments\":[{\"fromNumber\":1,\"classificationId\":\"cat-b\"}],\"overrides\":[],\"voided\":false}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'mover tudo e remover na mesma operação é aplicado');

-- 4. Lançamento novo na categoria removida é recusado, sem descartar o resto do estado.
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"late-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":5,
     "changes":[{"type":"Movement","id":"mov-late","baseVersion":null,"state":"{\"classificationId\":\"cat-a\",\"voided\":false}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'CategoryRemoved', 'lançamento tardio numa categoria removida é recusado');
select is((select count(*)::int from public.space_entities where id = 'mov-late'), 0, 'a recusa não grava o lançamento tardio');

-- 5. Lançamento excluído (tombstone) pode citar a categoria removida; sem categoria também é aceito.
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"late-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":6,
     "changes":[{"type":"Movement","id":"mov-del","baseVersion":null,"state":"{\"classificationId\":\"cat-a\",\"voided\":true}","tombstone":true}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'registro excluído pode citar a categoria removida');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"late-3","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":7,
     "changes":[{"type":"Movement","id":"mov-late","baseVersion":null,"state":"{\"classificationId\":null,\"voided\":false}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'o lançamento reenviado sem categoria ("a classificar") é aceito');

select * from finish();
rollback;
