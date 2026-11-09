-- ADR 0056: avalia??o compartilhada append-only; mesmo RLS e protocolo 2.
-- Accord: transferências e ajustes de saldo (ADR 0051). Mesmo RLS e protocolo financeiro.
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation,Intention,AccountMove,ClueEvaluation

create or replace function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation','Intention','AccountMove','ClueEvaluation'];
$$;

-- ADR 0052: protocolo 2 exige clientes atualizados para preservar o cartao de cada agregado.
-- Mantem autorizacao, conflito, idempotencia e vinculo de conversao.
create or replace function accord_private.apply_operation(p_space uuid, p_account uuid, p_op jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id text := p_op ->> 'operationId';
  v_changes jsonb := coalesce(p_op -> 'changes', '[]'::jsonb);
  v_digest bytea;
  v_prev public.applied_operations;
  v_ch jsonb;
  v_ref jsonb;
  v_key_count int;
  v_distinct_count int;
  v_owner uuid;
  v_base bigint;
  v_current public.space_entities;
  v_divergent jsonb := '[]'::jsonb;
  v_absorbed jsonb := '[]'::jsonb;
  v_unchanged jsonb := '[]'::jsonb;
  v_versions jsonb := '[]'::jsonb;
  v_cursor bigint;
  v_version bigint;
  v_result jsonb;
  v_state text;
  v_origin text;
  v_other public.space_entities;
begin
  if v_id is null or v_id = '' then
    return accord_private.rejected('', 'InvalidEnvelope', 'operationId ausente');
  end if;
  if coalesce(p_op ->> 'protocolVersion', '0') <> '2' then
    return accord_private.rejected(v_id, 'ProtocolUnsupported');
  end if;
  if p_op ->> 'spaceId' is distinct from p_space::text then
    return accord_private.rejected(v_id, 'ForeignSpace', 'espaço da operação difere do espaço do envio');
  end if;

  v_digest := sha256(convert_to(jsonb_build_object('space', p_op ->> 'spaceId', 'changes', v_changes)::text, 'UTF8'));
  select * into v_prev from public.applied_operations o where o.operation_id = v_id;
  if found then
    if v_prev.space_id = p_space and v_prev.digest = v_digest then
      return jsonb_set(v_prev.result, '{repeated}', 'true'::jsonb);
    end if;
    return accord_private.rejected(v_id, 'DivergentOperation');
  end if;

  if jsonb_typeof(v_changes) <> 'array' or jsonb_array_length(v_changes) = 0 then
    return accord_private.rejected(v_id, 'InvalidEnvelope', 'operação sem alterações');
  end if;

  select count(*), count(distinct (c ->> 'type') || '|' || (c ->> 'id'))
  into v_key_count, v_distinct_count from jsonb_array_elements(v_changes) c;
  if v_key_count <> v_distinct_count then
    return accord_private.rejected(v_id, 'InvalidEnvelope', 'entidade repetida na operação');
  end if;

  for v_ch in select c from jsonb_array_elements(v_changes) c loop
    if coalesce(v_ch ->> 'type', '') = '' or coalesce(v_ch ->> 'id', '') = '' or not accord_private.is_json_object(v_ch ->> 'state') then
      return accord_private.rejected(v_id, 'InvalidEnvelope', 'alteração malformada');
    end if;
    if not (v_ch ->> 'type') = any (accord_private.shared_types()) then
      return accord_private.rejected(v_id, 'TypeNotAllowed', v_ch ->> 'type');
    end if;
    if jsonb_typeof(v_ch -> 'baseVersion') = 'number' and (v_ch ->> 'baseVersion')::bigint < 1 then
      return accord_private.rejected(v_id, 'InvalidEnvelope', 'versão base inválida');
    end if;
    if coalesce((v_ch ->> 'createIfAbsent')::boolean, false) and v_ch ->> 'type' <> 'AlertEvent' then
      return accord_private.rejected(v_id, 'InvalidEnvelope', 'createIfAbsent só vale para evento de aviso');
    end if;
    select e.space_id into v_owner from public.space_entities e where e.type = v_ch ->> 'type' and e.id = v_ch ->> 'id';
    if v_owner is not null and v_owner <> p_space then
      return accord_private.rejected(v_id, 'ForeignSpace');
    end if;
    for v_ref in select r from jsonb_array_elements(coalesce(v_ch -> 'references', '[]'::jsonb)) r loop
      select e.space_id into v_owner from public.space_entities e where e.type = v_ref ->> 'type' and e.id = v_ref ->> 'id';
      if v_owner is not null and v_owner <> p_space then
        return accord_private.rejected(v_id, 'ForeignSpace');
      end if;
    end loop;
  end loop;

  -- ADR 0056: avalia??es de pista s? acrescentam registros; idempot?ncia j? foi conferida acima.
  for v_ch in select c from jsonb_array_elements(v_changes) c where c ->> 'type' = 'ClueEvaluation' loop
    if coalesce((v_ch ->> 'tombstone')::boolean, false)
       or jsonb_typeof(v_ch -> 'baseVersion') = 'number'
       or exists (select 1 from public.space_entities e where e.type = 'ClueEvaluation' and e.id = v_ch ->> 'id') then
      return accord_private.rejected(v_id, 'ClueEvaluationImmutable');
    end if;
  end loop;

  -- ADR 0044: o evento de aviso é imutável. Já existente, só `createIfAbsent` é aceito e devolve o canônico, sem conflito nem nova versão.
  for v_ch in select c from jsonb_array_elements(v_changes) c where c ->> 'type' = 'AlertEvent' loop
    if coalesce((v_ch ->> 'tombstone')::boolean, false) then
      return accord_private.rejected(v_id, 'AlertEventImmutable', 'evento de aviso não se remove');
    end if;
    select * into v_current from public.space_entities e where e.type = 'AlertEvent' and e.id = v_ch ->> 'id';
    if found then
      if not coalesce((v_ch ->> 'createIfAbsent')::boolean, false) then
        return accord_private.rejected(v_id, 'AlertEventImmutable', 'evento de aviso não se altera');
      end if;
      v_absorbed := v_absorbed || accord_private.entity_json(v_current);
    elsif jsonb_typeof(v_ch -> 'baseVersion') = 'number' then
      return accord_private.rejected(v_id, 'InvalidEnvelope', 'versão base sem entidade');
    end if;
  end loop;

  for v_ch in select c from jsonb_array_elements(v_changes) c where c ->> 'type' <> 'AlertEvent' loop
    v_base := case when jsonb_typeof(v_ch -> 'baseVersion') = 'number' then (v_ch ->> 'baseVersion')::bigint end;
    select * into v_current from public.space_entities e where e.type = v_ch ->> 'type' and e.id = v_ch ->> 'id';
    if v_base is null and found then
      v_divergent := v_divergent || accord_private.entity_json(v_current);
    elsif v_base is not null and not found then
      return accord_private.rejected(v_id, 'InvalidEnvelope', 'versão base sem entidade');
    elsif v_base is not null and v_current.version <> v_base then
      v_divergent := v_divergent || accord_private.entity_json(v_current);
    elsif found then
      v_unchanged := v_unchanged || accord_private.entity_json(v_current);
    end if;
  end loop;
  if jsonb_array_length(v_divergent) > 0 then
    return jsonb_build_object('operationId', v_id, 'result', 'conflict', 'current', v_divergent, 'unchanged', v_unchanged);
  end if;

  -- ADR 0048: vínculo de conversão. Um compromisso novo com intenção de origem exige a intenção neste espaço (OriginNotFound) e o espaço
  -- aceita um só compromisso por intenção: o segundo volta como conflito com o já reconhecido, sem aplicar nada. A origem de quem já existe
  -- nunca muda (ver a gravação abaixo).
  for v_ch in
    select c from jsonb_array_elements(v_changes) c
    where c ->> 'type' in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence') and not coalesce((c ->> 'tombstone')::boolean, false)
      and ((c ->> 'state')::jsonb) ->> 'originIntentionId' is not null
  loop
    if exists (select 1 from public.space_entities e where e.type = v_ch ->> 'type' and e.id = v_ch ->> 'id') then
      continue;
    end if;
    v_origin := ((v_ch ->> 'state')::jsonb) ->> 'originIntentionId';
    if not exists (select 1 from public.space_entities i where i.type = 'Intention' and i.id = v_origin and i.space_id = p_space) then
      return accord_private.rejected(v_id, 'OriginNotFound', v_origin);
    end if;
    select * into v_other from public.space_entities e
    where e.space_id = p_space and e.type in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence') and ((e.state::jsonb) ->> 'originIntentionId') = v_origin
    limit 1;
    if found then
      return jsonb_build_object('operationId', v_id, 'result', 'conflict',
                                'current', jsonb_build_array(accord_private.entity_json(v_other)), 'unchanged', '[]'::jsonb);
    end if;
  end loop;

  -- ADR 0039: categoria removida. O serviço não guarda lançamento vivo apontando para categoria removida:
  -- a operação que remove precisa antes tirar os lançamentos (do estado guardado e da própria operação), e o lançamento
  -- que chega numa categoria já removida é recusado sem descartá-lo (o aparelho o mantém em "a classificar").
  for v_ch in
    select c from jsonb_array_elements(v_changes) c
    where c ->> 'type' = 'Category' and coalesce((((c ->> 'state')::jsonb) ->> 'removed')::boolean, false)
  loop
    if exists (
      select 1 from public.space_entities e
      where e.space_id = p_space and not e.tombstone
        and e.type in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence')
        and not exists (select 1 from jsonb_array_elements(v_changes) o where o ->> 'type' = e.type and o ->> 'id' = e.id)
        and jsonb_path_exists(e.state::jsonb, '$.**.classificationId ? (@ == $id)', jsonb_build_object('id', v_ch ->> 'id'))
    ) or exists (
      select 1 from jsonb_array_elements(v_changes) o
      where o ->> 'type' in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence')
        and not coalesce((o ->> 'tombstone')::boolean, false)
        and jsonb_path_exists((o ->> 'state')::jsonb, '$.**.classificationId ? (@ == $id)', jsonb_build_object('id', v_ch ->> 'id'))
    ) then
      return accord_private.rejected(v_id, 'CategoryInUse', v_ch ->> 'id');
    end if;
  end loop;
  for v_ch in
    select c from jsonb_array_elements(v_changes) c
    where c ->> 'type' in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence') and not coalesce((c ->> 'tombstone')::boolean, false)
  loop
    if exists (
      select 1
      from jsonb_path_query((v_ch ->> 'state')::jsonb, '$.**.classificationId') as q (cid)
      join public.space_entities k on k.type = 'Category' and k.space_id = p_space and k.id = (q.cid #>> '{}')
      where coalesce(((k.state::jsonb) ->> 'removed')::boolean, false)
    ) then
      return accord_private.rejected(v_id, 'CategoryRemoved', v_ch ->> 'id');
    end if;
  end loop;

  select s.change_cursor into v_cursor from public.spaces s where s.id = p_space;
  for v_ch in
    select c from jsonb_array_elements(v_changes) c
    where not (c ->> 'type' = 'AlertEvent' and exists (select 1 from public.space_entities e where e.type = 'AlertEvent' and e.id = c ->> 'id'))
  loop
    update public.spaces s set change_cursor = s.change_cursor + 1 where s.id = p_space returning s.change_cursor into v_cursor;
    v_state := v_ch ->> 'state';
    -- ADR 0048: atualização de compromisso que omite ou troca a origem não a apaga nem a muda; vale a do estado guardado.
    if v_ch ->> 'type' in ('Movement', 'CardPurchase', 'Boleto', 'Recurrence') then
      select ((e.state::jsonb) ->> 'originIntentionId') into v_origin
      from public.space_entities e where e.type = v_ch ->> 'type' and e.id = v_ch ->> 'id';
      -- Só reescreve quando há vínculo a preservar ou a descartar: o estado comum segue byte a byte como chegou.
      if found and (v_origin is not null or (v_state::jsonb) ? 'originIntentionId') then
        v_state := (((v_state::jsonb) - 'originIntentionId')
                    || case when v_origin is null then '{}'::jsonb else jsonb_build_object('originIntentionId', v_origin) end)::text;
      end if;
    end if;
    insert into public.space_entities as e (type, id, space_id, version, state, tombstone, author_id, service_time, change_cursor)
    values (v_ch ->> 'type', v_ch ->> 'id', p_space, 1, v_state, coalesce((v_ch ->> 'tombstone')::boolean, false), p_account, now(), v_cursor)
    on conflict (type, id) do update
      set version = e.version + 1, state = excluded.state, tombstone = excluded.tombstone,
          author_id = excluded.author_id, service_time = excluded.service_time, change_cursor = excluded.change_cursor
    returning e.version into v_version;
    v_versions := v_versions || jsonb_build_object('type', v_ch ->> 'type', 'id', v_ch ->> 'id', 'version', v_version);
  end loop;

  v_result := jsonb_build_object('operationId', v_id, 'result', 'applied', 'versions', v_versions, 'cursor', v_cursor, 'repeated', false, 'absorbed', v_absorbed);
  insert into public.applied_operations (operation_id, space_id, author_id, installation_id, digest, result)
  values (v_id, p_space, p_account, coalesce(p_op ->> 'installationId', ''), v_digest, v_result);
  return v_result;
exception when others then
  -- Qualquer defeito inesperado desfaz esta operação (ponto de salvamento do bloco) e a recusa.
  return accord_private.rejected(coalesce(v_id, ''), 'InvalidEnvelope', sqlerrm);
end;
$$;
