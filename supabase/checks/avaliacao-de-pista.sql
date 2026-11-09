-- Fumaça de implantação: dados fictícios, identidades aleatórias, nenhuma extensão.
-- Executar só com autorização para o ambiente alvo. A subtransação é sempre revertida.
-- Falha de uma assertiva aborta a transação; não remove dados para corrigir uma falha.
do $smoke$
declare
  u1 uuid := gen_random_uuid();
  u2 uuid := gen_random_uuid();
  u3 uuid := gen_random_uuid();
  s uuid := gen_random_uuid();
  eid text := gen_random_uuid()::text;
  opid text := gen_random_uuid()::text;
  op jsonb;
  changed jsonb;
  answer jsonb;
  code text;
begin
  perform set_config('statement_timeout', '20s', true);
  if not ('ClueEvaluation' = any(accord_private.shared_types())) then
    raise exception 'ClueEvaluation não está habilitada';
  end if;
  if not (select relrowsecurity from pg_class where oid = 'public.space_entities'::regclass) then
    raise exception 'RLS de space_entities não está habilitada';
  end if;
  if has_function_privilege('anon', 'public.push_operations(uuid,jsonb)', 'EXECUTE') then
    raise exception 'anon pode executar push_operations';
  end if;

  begin
  insert into auth.users (id, email, aud, role)
  select id, id::text || '@smoke.invalid', 'authenticated', 'authenticated'
  from unnest(array[u1,u2,u3]) as ids(id);

  perform set_config('request.jwt.claims', json_build_object('sub', u1, 'role', 'authenticated')::text, true);
  set local role authenticated;
  answer := public.create_space(s, 'Fumaça fictícia');
  if answer->>'spaceId' is distinct from s::text then
    raise exception 'Membro não criou espaço fictício';
  end if;
  code := public.generate_invite(s)->>'code';

  op := jsonb_build_array(jsonb_build_object(
    'operationId',opid, 'spaceId',s, 'protocolVersion',2,
    'installationId','smoke', 'localTimeMillis',1,
    'changes',jsonb_build_array(jsonb_build_object(
      'type','ClueEvaluation', 'id',eid, 'baseVersion',null,
      'state','{"clueKey":"smoke","fingerprint":"v1"}', 'tombstone',false))));
  answer := public.push_operations(s,op);
  if answer->'results'->0->>'result' is distinct from 'applied' then
    raise exception 'Membro não criou avaliação';
  end if;
  answer := public.push_operations(s,op);
  if answer->'results'->0->>'repeated' is distinct from 'true' then
    raise exception 'Reenvio não foi idempotente';
  end if;

  changed := jsonb_set(op, '{0,operationId}', to_jsonb(gen_random_uuid()::text));
  changed := jsonb_set(changed, '{0,changes,0,baseVersion}', '1');
  answer := public.push_operations(s,changed);
  if answer->'results'->0->>'reason' is distinct from 'ClueEvaluationImmutable' then
    raise exception 'Alteração de avaliação não foi recusada';
  end if;
  changed := jsonb_set(changed, '{0,operationId}', to_jsonb(gen_random_uuid()::text));
  changed := jsonb_set(changed, '{0,changes,0,tombstone}', 'true');
  answer := public.push_operations(s,changed);
  if answer->'results'->0->>'reason' is distinct from 'ClueEvaluationImmutable' then
    raise exception 'Exclusão de avaliação não foi recusada';
  end if;

  changed := jsonb_set(op, '{0,operationId}', to_jsonb(gen_random_uuid()::text));
  changed := jsonb_set(changed, '{0,protocolVersion}', '1');
  answer := public.push_operations(s,changed);
  if answer->'results'->0->>'reason' is distinct from 'ProtocolUnsupported' then
    raise exception 'Protocolo antigo não foi recusado';
  end if;
  reset role;

  perform set_config('request.jwt.claims', json_build_object('sub', u2, 'role', 'authenticated')::text, true);
  set local role authenticated;
  answer := public.join_with_code(code);
  if answer->>'spaceId' is distinct from s::text then
    raise exception 'Outro membro não entrou pelo convite';
  end if;
  changed := jsonb_set(op, '{0,operationId}', to_jsonb(gen_random_uuid()::text));
  changed := jsonb_set(changed, '{0,changes,0,id}', to_jsonb(gen_random_uuid()::text));
  answer := public.push_operations(s,changed);
  if answer->'results'->0->>'result' is distinct from 'applied' then
    raise exception 'Outra avaliação não acrescentou histórico';
  end if;
  if (select count(*) from public.space_entities where space_id=s and type='ClueEvaluation') <> 2 then
    raise exception 'Outro membro não lê as duas avaliações ou reenvio duplicou';
  end if;
  reset role;

  perform set_config('request.jwt.claims', json_build_object('sub', u3, 'role', 'authenticated')::text, true);
  set local role authenticated;
  if exists(select 1 from public.space_entities where space_id=s) then
    raise exception 'Não membro leu dados do espaço';
  end if;
  answer := public.push_operations(s,op);
  if answer->>'error' is distinct from 'not_member' then
    raise exception 'Não membro não foi recusado';
  end if;
  reset role;
  -- Exceção exclusiva de sucesso: reverte apenas a subtransação das fixtures.
  -- Qualquer outra exceção propaga, falha a consulta e também reverte as escritas.
  raise sqlstate 'ZX001' using message = 'passed-and-rolled-back';
  exception when sqlstate 'ZX001' then
    null;
  end;
  raise notice 'clue_evaluation_smoke: passed-and-rolled-back';
end;
$smoke$;
