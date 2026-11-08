-- Permissões do espaço do casal (S2 "Permissões contra Supabase local"): contas U1 e U2 no
-- espaço E1, U3 no espaço E2, U4 e U5 sem espaço, e chamadas anônimas. Valores fictícios.
begin;
create extension if not exists pgtap with schema extensions;
select plan(58);

create schema tests;
grant usage on schema tests to anon, authenticated;
create table tests.ctx (k text primary key, v jsonb);
grant all on tests.ctx to anon, authenticated;

create function tests.as_user(p_id uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_id, 'role', 'authenticated')::text, true);
  set local role authenticated;
end;
$$;
create function tests.as_anon() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '', true);
  set local role anon;
end;
$$;
create function tests.back() returns void language plpgsql as $$
begin
  reset role;
  perform set_config('request.jwt.claims', '', true);
end;
$$;
grant execute on all functions in schema tests to anon, authenticated;

insert into auth.users (id, email, aud, role) values
  ('00000000-0000-0000-0000-0000000000a1', 'u1@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-0000000000a2', 'u2@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-0000000000a3', 'u3@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-0000000000a4', 'u4@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-0000000000a5', 'u5@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-0000000000a6', 'u6@teste.exemplo', 'authenticated', 'authenticated');

-- ---- espaços e convite pelas funções ----
select tests.as_user('00000000-0000-0000-0000-0000000000a1');
select public.create_space('00000000-0000-0000-0000-0000000000e1', 'Casa');
insert into tests.ctx select 'invite1', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
select tests.back();

select tests.as_user('00000000-0000-0000-0000-0000000000a3');
select public.create_space('00000000-0000-0000-0000-0000000000e2', 'Outra casa');
select tests.back();

select matches((select v ->> 'code' from tests.ctx where k = 'invite1'), '^[2-9A-HJKMNP-Z]{8}$',
  'código com 8 caracteres alfanuméricos em caixa alta, sem 0/O e 1/I/L');
select is(
  (select (v ->> 'validUntilMillis')::bigint - (v ->> 'generatedAtMillis')::bigint from tests.ctx where k = 'invite1'),
  3 * 24 * 60 * 60 * 1000::bigint, 'validade de 3 dias a partir da geração');
select is((select count(*)::int from public.space_invites where space_id = '00000000-0000-0000-0000-0000000000e1'), 1, 'só o resumo do convite é guardado');
select ok(not exists (select 1 from public.space_invites where code_digest = convert_to((select v ->> 'code' from tests.ctx where k = 'invite1'), 'UTF8')),
  'o resumo não é o código em texto');

-- ---- anônimo: nada lê, nada escreve, nada executa ----
select tests.as_anon();
select throws_ok('select * from public.spaces', '42501', null, 'anon não lê espaços');
select throws_ok('select * from public.space_members', '42501', null, 'anon não lê associações');
select throws_ok('select * from public.space_entities', '42501', null, 'anon não lê entidades');
select throws_ok('select * from public.space_invites', '42501', null, 'anon não lê convites');
select throws_ok('select * from public.applied_operations', '42501', null, 'anon não lê operações aplicadas');
select throws_ok('select * from public.invite_attempts', '42501', null, 'anon não lê tentativas');
select throws_ok($$insert into public.spaces (id, name, created_by) values (gen_random_uuid(), 'x', '00000000-0000-0000-0000-0000000000a1')$$,
  '42501', null, 'anon não escreve espaços');
select throws_ok($$select public.create_space(gen_random_uuid(), 'x')$$, '42501', null, 'anon não executa create_space');
select throws_ok($$select public.generate_invite('00000000-0000-0000-0000-0000000000e1')$$, '42501', null, 'anon não gera convite');
select throws_ok($$select public.join_with_code('ABCDEFGH')$$, '42501', null, 'anon não entra com código');
select throws_ok($$select public.push_operations('00000000-0000-0000-0000-0000000000e1', '[]')$$, '42501', null, 'anon não envia');
select throws_ok($$select public.pull_changes('00000000-0000-0000-0000-0000000000e1', 0, 10)$$, '42501', null, 'anon não recebe');
select throws_ok($$select public.list_participants('00000000-0000-0000-0000-0000000000e1')$$, '42501', null, 'anon não lista participantes');
select throws_ok($$select public.current_space()$$, '42501', null, 'anon não consulta o próprio espaço');
select tests.back();

-- ---- U3 (espaço E2) não vê nem altera o espaço E1, mesmo conhecendo os identificadores ----
select tests.as_user('00000000-0000-0000-0000-0000000000a1');
select public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-e1-1","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":2,"installationId":"i1","localTimeMillis":1,
     "changes":[{"type":"Movement","id":"mov-1","baseVersion":null,"state":"{\"valor\":6800}","tombstone":false}]}]');
select tests.back();

select tests.as_user('00000000-0000-0000-0000-0000000000a3');
select is((select count(*)::int from public.spaces where id = '00000000-0000-0000-0000-0000000000e1'), 0, 'U3 não lê o espaço E1');
select is((select count(*)::int from public.space_members where space_id = '00000000-0000-0000-0000-0000000000e1'), 0, 'U3 não lê associações de E1');
select is((select count(*)::int from public.space_entities where space_id = '00000000-0000-0000-0000-0000000000e1'), 0, 'U3 não lê entidades de E1');
select is((select count(*)::int from public.space_entities where id = 'mov-1'), 0, 'U3 não lê a entidade de E1 nem por ID conhecido');
select throws_ok('select * from public.space_invites', '42501', null, 'ninguém lê convites diretamente');
select throws_ok('select * from public.applied_operations', '42501', null, 'ninguém lê operações aplicadas diretamente');
select is((public.pull_changes('00000000-0000-0000-0000-0000000000e1', 0, 10)) ->> 'error', 'not_member', 'U3 não recebe alterações de E1');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1', '[]')) ->> 'error', 'not_member', 'U3 não envia para E1');
select is((public.generate_invite('00000000-0000-0000-0000-0000000000e1')) ->> 'error', 'not_member', 'U3 não gera código para E1');
select is((public.list_participants('00000000-0000-0000-0000-0000000000e1')) ->> 'error', 'not_member', 'U3 não lista participantes de E1');
select is((public.create_space('00000000-0000-0000-0000-0000000000e1', 'Tomada')) ->> 'adoptedExisting', 'true',
  'quem já tem espaço recebe o próprio, e não toma o de outra pessoa');
select is(
  (public.push_operations('00000000-0000-0000-0000-0000000000e2',
    '[{"operationId":"op-e2-1","spaceId":"00000000-0000-0000-0000-0000000000e2","protocolVersion":2,"installationId":"i3","localTimeMillis":1,
       "changes":[{"type":"Movement","id":"mov-1","baseVersion":1,"state":"{\"valor\":1}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'reason',
  'ForeignSpace', 'U3 não altera a entidade de E1 por uma operação de E2');
select is(
  (public.push_operations('00000000-0000-0000-0000-0000000000e2',
    '[{"operationId":"op-e2-2","spaceId":"00000000-0000-0000-0000-0000000000e2","protocolVersion":2,"installationId":"i3","localTimeMillis":1,
       "changes":[{"type":"Movement","id":"mov-9","baseVersion":null,"state":"{}","tombstone":false,"references":[{"type":"Movement","id":"mov-1"}]}]}]')) -> 'results' -> 0 ->> 'reason',
  'ForeignSpace', 'U3 não cita a entidade de E1 numa operação de E2');

-- ---- escrita direta negada para quem é membro ----
select tests.back();
select tests.as_user('00000000-0000-0000-0000-0000000000a1');
select throws_ok($$insert into public.space_members (space_id, account_id, nickname, origin) values ('00000000-0000-0000-0000-0000000000e2', '00000000-0000-0000-0000-0000000000a1', 'x', 'invite')$$,
  '42501', null, 'U1 não insere a própria associação em E2');
select throws_ok($$insert into public.space_members (space_id, account_id, nickname, origin) values ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000a4', 'x', 'invite')$$,
  '42501', null, 'U4 não entra em E1 por inserção direta (nem U1 a coloca)');
select throws_ok($$update public.space_entities set state = '{}' where id = 'mov-1'$$, '42501', null, 'U1 não atualiza o estado direto, contornando a versão');
select throws_ok($$insert into public.space_entities (type, id, space_id, version, state, author_id, change_cursor) values ('Movement', 'mov-x', '00000000-0000-0000-0000-0000000000e1', 1, '{}', '00000000-0000-0000-0000-0000000000a1', 99)$$,
  '42501', null, 'U1 não insere entidade direto');
select throws_ok($$delete from public.space_entities where id = 'mov-1'$$, '42501', null, 'U1 não apaga entidade direto');
select throws_ok($$update public.spaces set change_cursor = 0$$, '42501', null, 'U1 não mexe no cursor');
select is((select count(*)::int from public.space_entities where id = 'mov-1'), 1, 'U1 vê a entidade de E1');

-- ---- convite: segunda pessoa, reuso, terceira pessoa ----
select tests.back();
select tests.as_user('00000000-0000-0000-0000-0000000000a2');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite1'))) ->> 'spaceId', '00000000-0000-0000-0000-0000000000e1', 'U2 entra em E1 com o código');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite1'))) ->> 'spaceId', '00000000-0000-0000-0000-0000000000e1', 'repetir devolve sucesso, sem segunda associação');
select tests.back();
select is((select count(*)::int from public.space_members where space_id = '00000000-0000-0000-0000-0000000000e1'), 2, 'E1 tem exatamente 2 associações');

-- O código é multiuso: a terceira pessoa entra com o mesmo código, enquanto houver vaga.
select tests.as_user('00000000-0000-0000-0000-0000000000a4');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite1'))) ->> 'spaceId', '00000000-0000-0000-0000-0000000000e1', 'U4 entra em E1 com o mesmo código (multiuso)');
select tests.back();
select is((select count(*)::int from public.space_members where space_id = '00000000-0000-0000-0000-0000000000e1'), 3, 'E1 tem 3 associações');

select tests.as_user('00000000-0000-0000-0000-0000000000a1');
insert into tests.ctx select 'invite2', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
select matches((select v ->> 'code' from tests.ctx where k = 'invite2'), '^[2-9A-HJKMNP-Z]{8}$', 'com 3 pessoas ainda se gera código');
select is((public.active_invite('00000000-0000-0000-0000-0000000000e1')) -> 'invite' ->> 'remainingSlots', '1', 'restam 1 vaga');
select is((public.revoke_invite('00000000-0000-0000-0000-0000000000e2')) ->> 'error', 'not_member', 'U1 não revoga o convite de E2');
select is((public.revoke_invite('00000000-0000-0000-0000-0000000000e1')) ->> 'revoked', 'true', 'U1 revoga o código ativo');
select tests.back();

select tests.as_user('00000000-0000-0000-0000-0000000000a5');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite1'))) ->> 'error', 'invalid_invite', 'o código anterior, substituído, não serve mais');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite2'))) ->> 'error', 'invalid_invite', 'o código revogado não serve');
select tests.back();

select tests.as_user('00000000-0000-0000-0000-0000000000a1');
insert into tests.ctx select 'invite3', public.generate_invite('00000000-0000-0000-0000-0000000000e1');
select tests.back();
select tests.as_user('00000000-0000-0000-0000-0000000000a5');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite3'))) ->> 'spaceId', '00000000-0000-0000-0000-0000000000e1', 'U5 entra com o novo código');
select tests.back();
select is((select count(*)::int from public.space_members where space_id = '00000000-0000-0000-0000-0000000000e1'), 4, 'E1 chega a 4 associações');

select tests.as_user('00000000-0000-0000-0000-0000000000a1');
select is((public.generate_invite('00000000-0000-0000-0000-0000000000e1')) ->> 'error', 'space_full', 'com 4 pessoas, gerar código é recusado');
select is((public.active_invite('00000000-0000-0000-0000-0000000000e1')) -> 'invite' ->> 'remainingSlots', '0', 'não restam vagas');
select tests.back();
select tests.as_user('00000000-0000-0000-0000-0000000000a6');
select is((public.join_with_code((select v ->> 'code' from tests.ctx where k = 'invite3'))) ->> 'error', 'space_full', 'a quinta pessoa é recusada: espaço lotado');
select tests.back();
select is((select count(*)::int from public.space_members where space_id = '00000000-0000-0000-0000-0000000000e1'), 4, 'E1 continua com 4 associações');

-- ---- U1 e U2 têm exatamente os mesmos direitos ----
select tests.as_user('00000000-0000-0000-0000-0000000000a2');
select is((select count(*)::int from public.space_entities where space_id = '00000000-0000-0000-0000-0000000000e1'), 1, 'U2 lê as entidades de E1');
select is((public.push_operations('00000000-0000-0000-0000-0000000000e1',
  '[{"operationId":"op-e1-2","spaceId":"00000000-0000-0000-0000-0000000000e1","protocolVersion":2,"installationId":"i2","localTimeMillis":1,
     "changes":[{"type":"Movement","id":"mov-1","baseVersion":1,"state":"{\"valor\":9000}","tombstone":false}]}]')) -> 'results' -> 0 ->> 'result', 'applied', 'U2 edita o que U1 criou');
select tests.back();

-- ---- Anônimo e terceiros não alteram as funções internas ----
select tests.as_user('00000000-0000-0000-0000-0000000000a1');
select throws_ok($$select accord_private.apply_operation('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000a1', '{}')$$,
  '42501', null, 'a função interna de aplicação não é executável por contas');
select tests.back();

select * from finish();
rollback;
