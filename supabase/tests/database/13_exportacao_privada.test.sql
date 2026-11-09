begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, email, aud, role) values
  ('00000000-0000-0000-0000-000000000da1', 'export-a@teste.exemplo', 'authenticated', 'authenticated'),
  ('00000000-0000-0000-0000-000000000da2', 'export-b@teste.exemplo', 'authenticated', 'authenticated');
insert into public.spaces(id, name, created_by) values
  ('00000000-0000-0000-0000-000000000de1', 'Casa teste', '00000000-0000-0000-0000-000000000da1');
insert into public.space_members(space_id, account_id, nickname, origin) values
  ('00000000-0000-0000-0000-000000000de1', '00000000-0000-0000-0000-000000000da1', 'Pessoa teste', 'creation');

select ok(not has_function_privilege('anon', 'public.privacy_export(uuid,text,uuid)', 'EXECUTE'), 'anônimo não exporta');
select ok(not has_function_privilege('authenticated', 'public.privacy_export(uuid,text,uuid)', 'EXECUTE'), 'cliente não escolhe titular diretamente');
select ok(has_function_privilege('service_role', 'public.privacy_export(uuid,text,uuid)', 'EXECUTE'), 'endpoint confiável pode exportar');
select is(public.privacy_export('00000000-0000-0000-0000-000000000da1', 'Personal')->'profile'->>'email',
  'export-a@teste.exemplo', 'perfil só da pessoa solicitante');
select is(public.privacy_export('00000000-0000-0000-0000-000000000da1', 'Financial',
  '00000000-0000-0000-0000-000000000de1')->'space'->>'name', 'Casa teste', 'membro exporta seu espaço');
select throws_ok($$select public.privacy_export('00000000-0000-0000-0000-000000000da2', 'Financial',
  '00000000-0000-0000-0000-000000000de1')$$, '42501', 'privacy_not_member', 'outro titular não exporta espaço alheio');
select throws_ok($$select public.privacy_export('00000000-0000-0000-0000-000000000da1', 'Other')$$,
  '22023', 'privacy_invalid_scope', 'escopo fechado');

-- Simula a tabela do ADR 0054 sem implantar a coleta; toda fixture é revertida.
create table public.chat_collection_event(account_id uuid, body text);
insert into public.chat_collection_event values
  ('00000000-0000-0000-0000-000000000da1', 'evento fictício A'),
  ('00000000-0000-0000-0000-000000000da2', 'evento fictício B');
select is(jsonb_array_length(public.privacy_export('00000000-0000-0000-0000-000000000da1', 'Personal')->'collectedChat'),
  1, 'coleta retorna só registros próprios');
select is(public.privacy_export('00000000-0000-0000-0000-000000000da1', 'Personal')->'collectedChat'->0->>'body',
  'evento fictício A', 'não mistura conteúdo de outro membro');
select * from finish();
rollback;
