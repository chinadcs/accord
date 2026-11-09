-- Vínculo de conversão da intenção (ADR 0048): único por intenção, imutável e restrito ao espaço. Valores fictícios.
-- U1 e U2 são membros de E1; U3 é o único membro de E2 (outro espaço); U4 não é membro de nenhum.
begin;
create extension if not exists pgtap with schema extensions;
select plan(17);

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
from generate_series(1, 4) n;

select tests.as_user('00000000-0000-0000-0000-000000000001');
select public.create_space('00000000-0000-0000-0000-0000000000e1', 'Casa');
insert into tests.ctx select 'code', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000002');
select public.join_with_code((select v ->> 'code' from tests.ctx where k = 'code'));
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000003');
select public.create_space('00000000-0000-0000-0000-0000000000e2', 'Outra casa');
reset role;

-- Intenção no espaço E1 (U1) e outra no E2 (U3).
select tests.as_user('00000000-0000-0000-0000-000000000001');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"i-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Intention","id":"int-1","baseVersion":null,"state":"{\"name\":\"Televisão\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'a intenção é gravada no espaço');
reset role;
select tests.as_user('00000000-0000-0000-0000-000000000003');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e2',
  '[{"operationId":"i-2","spaceId":"00000000-0000-0000-0000-0000000000e2","protocolVersion":3,"installationId":"i3","localTimeMillis":1,
     "changes":[{"type":"Intention","id":"int-x","baseVersion":null,"state":"{\"name\":\"De outro espaço\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'o outro espaço tem a própria intenção');
reset role;

-- 1. A primeira conversão é aplicada.
select tests.as_user('00000000-0000-0000-0000-000000000001');
insert into tests.ctx select 'c1', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":2,
     "changes":[{"type":"CardPurchase","id":"compra-1","baseVersion":null,"state":"{\"description\":\"Televisão\",\"originIntentionId\":\"int-1\"}","tombstone":false}]}]');
select is((select v -> 'results' -> 0 ->> 'result' from tests.ctx where k = 'c1'), 'applied', 'a conversão é aplicada');
reset role;

-- 2. O outro membro converte a mesma intenção, de outro tipo e com outra identidade: conflito com o já reconhecido, nada gravado.
select tests.as_user('00000000-0000-0000-0000-000000000002');
insert into tests.ctx select 'c2', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":3,
     "changes":[{"type":"Boleto","id":"boleto-2","baseVersion":null,"state":"{\"description\":\"Televisão\",\"originIntentionId\":\"int-1\"}","tombstone":false}]}]');
select is((select v -> 'results' -> 0 ->> 'result' from tests.ctx where k = 'c2'), 'conflict', 'a segunda conversão volta como conflito');
select is((select v -> 'results' -> 0 -> 'current' -> 0 ->> 'id' from tests.ctx where k = 'c2'), 'compra-1', 'o conflito traz a identidade do já reconhecido');
select is((select v -> 'results' -> 0 -> 'current' -> 0 ->> 'version' from tests.ctx where k = 'c2'), '1', 'e a versão dele');
reset role;
select is((select count(*)::int from public.space_entities where id = 'boleto-2'), 0, 'o segundo compromisso não foi gravado');
select is((select count(*)::int from public.space_entities where state::jsonb ->> 'originIntentionId' = 'int-1'), 1, 'há um só compromisso para a intenção');

-- 3. O reenvio da primeira operação devolve o mesmo resultado, sem segundo compromisso.
select tests.as_user('00000000-0000-0000-0000-000000000001');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":2,
     "changes":[{"type":"CardPurchase","id":"compra-1","baseVersion":null,"state":"{\"description\":\"Televisão\",\"originIntentionId\":\"int-1\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'repeated',
  'true', 'o reenvio devolve o resultado já registrado');
reset role;

-- 4. Origem inexistente ou de outro espaço: recusada, nada gravado.
select tests.as_user('00000000-0000-0000-0000-000000000001');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-3","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":4,
     "changes":[{"type":"CardPurchase","id":"compra-3","baseVersion":null,"state":"{\"originIntentionId\":\"nao-existe\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'OriginNotFound', 'intenção inexistente é recusada');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"c-4","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":5,
     "changes":[{"type":"CardPurchase","id":"compra-4","baseVersion":null,"state":"{\"originIntentionId\":\"int-x\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'OriginNotFound', 'intenção de outro espaço é recusada');
reset role;
select is((select count(*)::int from public.space_entities where id in ('compra-3', 'compra-4')), 0, 'nada foi gravado nas recusas');

-- 5. Atualização sem o campo ou com outra origem não apaga nem troca o vínculo.
select tests.as_user('00000000-0000-0000-0000-000000000002');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"u-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":6,
     "changes":[{"type":"CardPurchase","id":"compra-1","baseVersion":1,"state":"{\"description\":\"Televisão 55\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'a edição sem o campo de origem é aplicada');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"u-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i2","localTimeMillis":7,
     "changes":[{"type":"CardPurchase","id":"compra-1","baseVersion":2,"state":"{\"description\":\"Televisão 55\",\"originIntentionId\":\"outra\"}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'a edição com outra origem também é aplicada');
reset role;
select is((select state::jsonb ->> 'originIntentionId' from public.space_entities where id = 'compra-1'), 'int-1', 'o vínculo continua o mesmo');
select is((select state::jsonb ->> 'description' from public.space_entities where id = 'compra-1'), 'Televisão 55', 'a edição foi aplicada');

-- 6. Quem não é membro não envia conversão nem lê o vínculo.
select tests.as_user('00000000-0000-0000-0000-000000000004');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"x-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i4","localTimeMillis":8,
     "changes":[{"type":"CardPurchase","id":"compra-9","baseVersion":null,"state":"{\"originIntentionId\":\"int-1\"}","tombstone":false}]}]')) ->> 'error',
  'not_member', 'quem não participa do espaço não envia conversão');
reset role;

select * from finish();
rollback;
