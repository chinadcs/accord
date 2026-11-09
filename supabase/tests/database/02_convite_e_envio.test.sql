-- Convite (prazo, substituição, normalização, tentativas) e envio/recebimento no banco.
begin;
create extension if not exists pgtap with schema extensions;
select plan(24);

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
from generate_series(1, 9) n;

-- ---- prazo de 3 dias, contado pelo relógio do banco ----
select tests.as_user('00000000-0000-0000-0000-000000000001');
select public.create_space('00000000-0000-0000-0000-0000000000e1', 'Casa');
insert into tests.ctx select 'c1', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
reset role;

-- Um minuto antes do fim: aceito.
update public.space_invites set valid_until = now() + interval '1 minute';
select tests.as_user('00000000-0000-0000-0000-000000000002');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'c1'))) ->> 'spaceId',
  '00000000-0000-0000-0000-0000000000e1', 'um minuto antes do fim o código é aceito');
reset role;

-- No instante do fim (limite exclusivo): recusado.
select tests.as_user('00000000-0000-0000-0000-000000000003');
select public.create_space('00000000-0000-0000-0000-0000000000e3', 'Outra');
insert into tests.ctx select 'c3', public.generate_invite('00000000-0000-0000-0000-0000000000e3');
reset role;
update public.space_invites set valid_until = now() where space_id = '00000000-0000-0000-0000-0000000000e3';
select tests.as_user('00000000-0000-0000-0000-000000000004');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'c3'))) ->> 'error', 'invalid_invite',
  'no instante do fim o código expira (limite exclusivo)');
reset role;

select tests.as_user('00000000-0000-0000-0000-000000000003');
select is((public.active_invite('00000000-0000-0000-0000-0000000000e3')) -> 'invite' ->> 'expired', 'true', 'o convite vencido aparece como expirado');
select is(((public.active_invite('00000000-0000-0000-0000-0000000000e3')) -> 'invite') ? 'code', false, 'o código nunca volta depois de mostrado');

-- ---- gerar outro código invalida o anterior; só um ativo ----
insert into tests.ctx select 'c3b', public.generate_invite('00000000-0000-0000-0000-0000000000e3');
reset role;
select is((select count(*)::int from public.space_invites where space_id = '00000000-0000-0000-0000-0000000000e3' and state = 'active'), 1, 'no máximo um convite ativo');
select is((select count(*)::int from public.space_invites where space_id = '00000000-0000-0000-0000-0000000000e3' and state = 'replaced'), 1, 'o anterior fica substituído');
select tests.as_user('00000000-0000-0000-0000-000000000005');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'c3'))) ->> 'error', 'invalid_invite', 'o código anterior não serve mais');
select is((public.join_with_code(' ' || lower(substr((select v ->> 'code' from tests.ctx where k = 'c3b'), 1, 4)) || ' ' || lower(substr((select v ->> 'code' from tests.ctx where k = 'c3b'), 5)) || ' ')) ->> 'spaceId',
  '00000000-0000-0000-0000-0000000000e3', 'minúsculas e espaços são normalizados');
reset role;

-- ---- tentativas falhas: 5 em 10 minutos ----
select tests.as_user('00000000-0000-0000-0000-000000000006');
select public.join_with_code('ZZZZZZZ1');
select public.join_with_code('ZZZZZZZ2');
select public.join_with_code('ZZZZZZZ3');
select public.join_with_code('ZZZZZZZ4');
select is((public.join_with_code('ZZZZZZZ5')) ->> 'error', 'invalid_invite', 'a quinta tentativa falha como inválida');
select is((public.join_with_code('ZZZZZZZ6')) ->> 'error', 'rate_limited', 'a sexta é bloqueada');
reset role;
update public.invite_attempts set at = at - interval '11 minutes';
select tests.as_user('00000000-0000-0000-0000-000000000006');
select is((public.join_with_code('ZZZZZZZ7')) ->> 'error', 'invalid_invite', 'depois da janela volta a avaliar');
reset role;

-- ---- envio: idempotência, conflito, divergência, atomicidade ----
select tests.as_user('00000000-0000-0000-0000-000000000001');
insert into tests.ctx select 'p1', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Movement","id":"l1","baseVersion":null,"state":"{\"valor\":6800}","tombstone":false}]}]');
insert into tests.ctx select 'p1b', public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":99,
     "changes":[{"type":"Movement","id":"l1","baseVersion":null,"state":"{\"valor\":6800}","tombstone":false}]}]');
select is((select v -> 'results' -> 0 ->> 'repeated' from tests.ctx where k = 'p1'), 'false', 'primeira aplicação');
select is((select v -> 'results' -> 0 ->> 'repeated' from tests.ctx where k = 'p1b'), 'true', 'reenvio devolve a mesma operação como repetida');
select is((select v -> 'results' -> 0 -> 'versions' from tests.ctx where k = 'p1b'), (select v -> 'results' -> 0 -> 'versions' from tests.ctx where k = 'p1'), 'com as mesmas versões');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Movement","id":"l1","baseVersion":null,"state":"{\"valor\":8000}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'DivergentOperation', 'mesmo ID com conteúdo alterado é recusado');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Movement","id":"l2","baseVersion":null,"state":"{}","tombstone":false},
                {"type":"Movement","id":"l1","baseVersion":null,"state":"{\"valor\":1}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'conflict', 'criação sobre identidade existente colide');
select is((select count(*)::int from public.space_entities where id = 'l2'), 0, 'a alteração irmã não ficou aplicada');
select is((public.pull_changes('00000000-0000-0000-0000-0000000000e1', 0, 100)) -> 'changes' -> 0 ->> 'state', '{"valor":6800}', 'o estado volta byte a byte');
select is((public.pull_changes('00000000-0000-0000-0000-0000000000e1', 0, 100)) ->> 'hasMore', 'false', 'sem mais páginas');

-- ---- categoria de renda (ADR 0038): tipo aceito; tipo desconhecido continua recusado ----
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-ic","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"IncomeCategory","id":"ic1","baseVersion":null,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'categoria de renda é um tipo sincronizável');
-- ---- lote de importação confirmado (ADR 0040): tipo aceito ----
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-ib","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"ImportBatch","id":"ib1","baseVersion":null,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'lote de importação confirmado é um tipo sincronizável');
-- ---- ícone escolhido por lançamento ou nome (ADR 0047): tipo aceito ----
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-ia","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"IconAssociation","id":"ia1","baseVersion":null,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'ícone escolhido é um tipo sincronizável');
-- ---- intenção de compra (ADR 0048): tipo aceito ----
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-in","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Intention","id":"in1","baseVersion":null,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'applied', 'intenção de compra é um tipo sincronizável');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-xx","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":3,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Inexistente","id":"x1","baseVersion":null,"state":"{}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result',
  'rejected', 'tipo desconhecido continua recusado');

select * from finish();
rollback;
