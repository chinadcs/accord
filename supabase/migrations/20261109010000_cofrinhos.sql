-- ADR 0060: subcontas reais, protocolo 3 e validação monetária na transação do espaço.
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan,IncomeCategory,ImportBatch,AlertEvent,IconAssociation,Intention,AccountMove,ClueEvaluation,SavingsPocket,SavingsMove
create or replace function accord_private.shared_types() returns text[] language sql immutable set search_path = '' as $$
select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income','Recurrence','Refund','Invoice','InvoicePayment','LimitPlan','IncomeCategory','ImportBatch','AlertEvent','IconAssociation','Intention','AccountMove','ClueEvaluation','SavingsPocket','SavingsMove'];
$$;

-- Projeção da operação inteira: nenhuma validação depende de estado enviado fora da transação.
create function accord_private.savings_states(p_space uuid,p_changes jsonb)
returns table(type text,id text,data jsonb) language sql stable set search_path = '' as $$
select e.type,e.id,e.state::jsonb from public.space_entities e where e.space_id=p_space
 and not exists(select 1 from jsonb_array_elements(p_changes) c where c->>'type'=e.type and c->>'id'=e.id)
union all select c->>'type',c->>'id',(c->>'state')::jsonb from jsonb_array_elements(p_changes) c;
$$;

create function accord_private.savings_uses_account(p_data jsonb,p_ids text[]) returns boolean
language plpgsql immutable set search_path = '' as $$
declare k text; v jsonb;
begin
 if jsonb_typeof(p_data)='object' then
  for k,v in select * from jsonb_each(p_data) loop
   if k in ('accountId','fromAccountId','toAccountId','defaultAccountId') and (v #>> '{}')=any(p_ids) then return true; end if;
   if accord_private.savings_uses_account(v,p_ids) then return true; end if;
  end loop;
 elsif jsonb_typeof(p_data)='array' then
  for v in select value from jsonb_array_elements(p_data) loop
   if accord_private.savings_uses_account(v,p_ids) then return true; end if;
  end loop;
 end if;
 return false;
end; $$;

create function accord_private.savings_pocket_days(p_space uuid,p_changes jsonb,p_id text)
returns table(event_day bigint,balance numeric) language sql stable set search_path = '' as $$
with states as materialized(select * from accord_private.savings_states(p_space,p_changes)),
p as(select data from states where type='SavingsPocket' and id=p_id),
events as(
 select (data->>'epochDay')::bigint event_day,
  case when data->>'direction'='Deposit' then (data->>'amountCents')::numeric else -(data->>'amountCents')::numeric end cents
 from states where type='SavingsMove' and data->>'pocketId'=p_id and not coalesce((data->>'voided')::boolean,false)
 union all select (data->>'cutoffEpochDay')::bigint,0::numeric from p
),daily as(select event_day,sum(cents) cents from events,p where event_day >= (p.data->>'cutoffEpochDay')::bigint group by event_day)
select event_day,(p.data->>'initialCents')::numeric+sum(cents) over(order by event_day) from daily,p;
$$;

-- Espelha BalanceReader: somente recebimentos/saídas efetivos, sem rendas previstas nem compras no cartão.
create function accord_private.savings_account_days(p_space uuid,p_changes jsonb,p_id text,p_from bigint)
returns table(event_day bigint,balance numeric) language sql stable set search_path = '' as $$
with states as materialized(select * from accord_private.savings_states(p_space,p_changes)),
a as(select data account_data from states where type='Account' and id=p_id),
live as(select * from states where not coalesce((data->>'voided')::boolean,false)),
events as(
 select (data->>'epochDay')::bigint event_day,-(data->>'amountCents')::numeric cents from live,a
 where type='Movement' and (data->>'accountId'=p_id or (data->>'accountId' is null and data->>'kind'='Cash' and coalesce((a.account_data->>'isWallet')::boolean,false)))
 union all select (data->>'receivedEpochDay')::bigint,coalesce(data->>'receivedCents',data->>'expectedCents')::numeric from live
 where type='Income' and data->>'accountId'=p_id and data->>'receivedEpochDay' is not null
 union all select (data->>'paidEpochDay')::bigint,-coalesce(data->>'paidCents',data->>'expectedCents')::numeric from live
 where type='Boleto' and data->>'accountId'=p_id and data->>'paidEpochDay' is not null
 union all select (data->>'epochDay')::bigint,-(data->>'amountCents')::numeric from live where type='InvoicePayment' and data->>'accountId'=p_id
 union all select (data->>'epochDay')::bigint,(data->>'amountCents')::numeric from live where type='AccountMove' and data->>'toAccountId'=p_id
 union all select (data->>'epochDay')::bigint,-(data->>'amountCents')::numeric from live where type='AccountMove' and data->>'fromAccountId'=p_id
 union all select (data->>'epochDay')::bigint,case when data->>'direction'='Deposit' then -(data->>'amountCents')::numeric else (data->>'amountCents')::numeric end from live where type='SavingsMove' and data->>'accountId'=p_id
 union all select (s->>'epochDay')::bigint,case when data->>'kind'='Income' then (s->>'cents')::numeric else -(s->>'cents')::numeric end
 from live cross join lateral jsonb_array_elements(coalesce(data->'settlements','[]'::jsonb)) s
 where type='Recurrence' and data->>'form'='OffCard' and s->>'accountId'=p_id
 union all select p_from,0::numeric
),daily as(select event_day,sum(cents) cents from events,a where event_day >= (a.account_data->>'balanceCutoffEpochDay')::bigint group by event_day),
running as(select event_day,(a.account_data->>'initialBalanceCents')::numeric+sum(cents) over(order by event_day) balance from daily,a)
select * from running where event_day>=p_from;
$$;

create function accord_private.validate_savings(p_space uuid,p_changes jsonb,p_actor uuid) returns text
language plpgsql stable set search_path = '' as $$
declare c jsonb; p jsonb; m jsonb; a jsonb; old jsonb; old_a jsonb; ids text[]; cutoff bigint;
 today bigint := (now() at time zone 'America/Sao_Paulo')::date-date '1970-01-01';
begin
 if not exists(select 1 from public.space_entities where space_id=p_space and type='SavingsPocket')
 and not exists(select 1 from jsonb_array_elements(p_changes) ch where ch->>'type' in ('SavingsPocket','SavingsMove')) then return null; end if;
 select array_agg(id) into ids from accord_private.savings_states(p_space,p_changes) where type='SavingsPocket';
 for c in select ch from jsonb_array_elements(p_changes) ch where ch->>'type' not in ('SavingsPocket','SavingsMove') loop
  if accord_private.savings_uses_account((c->>'state')::jsonb,ids) then return 'Resgate o cofrinho antes de pagar.'; end if;
 end loop;
 for c in select ch from jsonb_array_elements(p_changes) ch where ch->>'type'='SavingsPocket' loop
  p:=(c->>'state')::jsonb;
  if not (p ?& array['id','accountId','name','createdBy','initialCents','cutoffEpochDay','goalCents','deadlineEpochDay','archived'])
   or jsonb_typeof(p->'id')<>'string' or jsonb_typeof(p->'accountId')<>'string' or jsonb_typeof(p->'name')<>'string' or jsonb_typeof(p->'createdBy')<>'string'
   or jsonb_typeof(p->'initialCents')<>'number' or jsonb_typeof(p->'cutoffEpochDay')<>'number'
   or jsonb_typeof(p->'goalCents') not in ('number','null') or jsonb_typeof(p->'deadlineEpochDay') not in ('number','null')
   or jsonb_typeof(p->'archived')<>'boolean' then return 'Cadastro de cofrinho inválido.'; end if;
  if (p->>'deadlineEpochDay')::bigint not between -365243219162 and 365241780471 then return 'Prazo inválido.'; end if;
  if (p->>'cutoffEpochDay')::bigint not between -365243219162 and 365241780471 then return 'Data inválida.'; end if;
  select state::jsonb into old from public.space_entities where space_id=p_space and type='SavingsPocket' and id=c->>'id';
  if coalesce((c->>'tombstone')::boolean,false) or p->>'id' is distinct from c->>'id' or coalesce(length(trim(p->>'name')),0) not between 1 and 80
   or (p->>'initialCents')::bigint is null or (p->>'initialCents')::bigint<0 or (p->>'cutoffEpochDay')::bigint is null or (p->>'cutoffEpochDay')::bigint>today
   or (p->>'goalCents')::bigint<=0
   or not exists(select 1 from accord_private.savings_states(p_space,p_changes) where type='Account' and id=p->>'accountId') then return 'Cadastro de cofrinho inválido.'; end if;
  if old is null and p->>'createdBy' is distinct from p_actor::text then return 'Autoria inválida.'; end if;
  if old is not null and (p->'accountId' is distinct from old->'accountId' or p->'initialCents' is distinct from old->'initialCents' or p->'cutoffEpochDay' is distinct from old->'cutoffEpochDay' or p->'createdBy' is distinct from old->'createdBy') then return 'Vínculo e saldo inicial são fixos.'; end if;
  if coalesce((old->>'archived')::boolean,false) and p<>old then return 'Cofrinho arquivado.'; end if;
  if exists(select 1 from accord_private.savings_pocket_days(p_space,p_changes,c->>'id') where balance<0 or balance>9223372036854775807::numeric) then return 'Saldo do cofrinho inválido.'; end if;
  if coalesce((p->>'archived')::boolean,false) and (select balance from accord_private.savings_pocket_days(p_space,p_changes,c->>'id') order by event_day desc limit 1)<>0 then return 'Resgate o saldo antes de arquivar.'; end if;
 end loop;
 for c in select ch from jsonb_array_elements(p_changes) ch where ch->>'type'='SavingsMove' loop
  m:=(c->>'state')::jsonb;
  if not (m ?& array['id','pocketId','accountId','direction','authorId','amountCents','epochDay','voided'])
   or jsonb_typeof(m->'id')<>'string' or jsonb_typeof(m->'pocketId')<>'string' or jsonb_typeof(m->'accountId')<>'string'
   or jsonb_typeof(m->'direction')<>'string' or jsonb_typeof(m->'authorId')<>'string'
   or jsonb_typeof(m->'amountCents')<>'number' or jsonb_typeof(m->'epochDay')<>'number' or jsonb_typeof(m->'voided')<>'boolean' then return 'Movimentação inválida.'; end if;
  select state::jsonb into old from public.space_entities where space_id=p_space and type='SavingsMove' and id=c->>'id';
  select data into p from accord_private.savings_states(p_space,p_changes) where type='SavingsPocket' and id=m->>'pocketId';
  select data into a from accord_private.savings_states(p_space,p_changes) where type='Account' and id=p->>'accountId';
  if p is null or a is null or m->>'id' is distinct from c->>'id' or m->>'accountId' is distinct from p->>'accountId' or coalesce((p->>'archived')::boolean,false)
   or (m->>'amountCents')::bigint is null or (m->>'amountCents')::bigint<=0 or coalesce(m->>'direction','') not in ('Deposit','Withdraw')
   or (m->>'epochDay')::bigint is null or (m->>'epochDay')::bigint>today or (m->>'epochDay')::bigint<(p->>'cutoffEpochDay')::bigint
   or coalesce((c->>'tombstone')::boolean,false)<>coalesce((m->>'voided')::boolean,false) then return 'Movimentação inválida.'; end if;
  if not exists(select 1 from jsonb_array_elements(p_changes) ch where ch->>'type'='SavingsPocket' and ch->>'id'=m->>'pocketId')
   or not exists(select 1 from jsonb_array_elements(p_changes) ch where ch->>'type'='Account' and ch->>'id'=m->>'accountId') then return 'Movimentação precisa das versões da conta e do cofrinho.'; end if;
  select state::jsonb into old_a from public.space_entities where space_id=p_space and type='Account' and id=m->>'accountId';
  if old_a is not null and old_a<>a then return 'Movimentação não pode alterar o cadastro da conta.'; end if;
  if old is null then
   if coalesce((m->>'voided')::boolean,false) or m->>'authorId' is distinct from p_actor::text then return 'Autoria ou estado inválido.'; end if;
  elsif coalesce((old->>'voided')::boolean,false) or not coalesce((m->>'voided')::boolean,false) or (m-'voided')<>(old-'voided') then return 'Movimento é imutável; apenas desfazer é permitido.'; end if;
  cutoff:=(a->>'balanceCutoffEpochDay')::bigint;
  if cutoff is not null and (m->>'epochDay')::bigint<cutoff then return 'Data anterior ao saldo próprio da conta.'; end if;
  if exists(select 1 from accord_private.savings_pocket_days(p_space,p_changes,m->>'pocketId') where balance<0 or balance>9223372036854775807::numeric) then return 'Saldo insuficiente no cofrinho.'; end if;
  if (old is null and m->>'direction'='Deposit') or (old is not null and m->>'direction'='Withdraw') then
   if a->>'initialBalanceCents' is null or cutoff is null then return 'Saldo próprio não informado.'; end if;
   if exists(select 1 from accord_private.savings_account_days(p_space,p_changes,m->>'accountId',(m->>'epochDay')::bigint) where balance<0 or balance>9223372036854775807::numeric) then return 'Saldo próprio insuficiente.'; end if;
  end if;
 end loop;
 return null;
end; $$;

revoke all on function accord_private.savings_states(uuid,jsonb), accord_private.savings_uses_account(jsonb,text[]),
 accord_private.savings_pocket_days(uuid,jsonb,text), accord_private.savings_account_days(uuid,jsonb,text,bigint),
 accord_private.validate_savings(uuid,jsonb,uuid) from public,anon,authenticated;

-- Protocol 3: identity and versions before money validation.
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
  v_savings_error text;
  v_other public.space_entities;
begin
  if v_id is null or v_id = '' then
    return accord_private.rejected('', 'InvalidEnvelope', 'operationId ausente');
  end if;
  if coalesce(p_op ->> 'protocolVersion', '0') <> '3' then
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

  v_savings_error := accord_private.validate_savings(p_space,v_changes,p_account);
  if v_savings_error is not null then
    if v_savings_error in ('Saldo próprio insuficiente.','Saldo insuficiente no cofrinho.','Saldo do cofrinho inválido.','Saldo próprio não informado.') then
      select coalesce(jsonb_agg(accord_private.entity_json(e)),'[]'::jsonb) into v_divergent from public.space_entities e
      where e.space_id=p_space and exists(select 1 from jsonb_array_elements(v_changes) c where c->>'type'=e.type and c->>'id'=e.id);
      return jsonb_build_object('operationId',v_id,'result','conflict','current',v_divergent,'unchanged','[]'::jsonb);
    end if;
    return accord_private.rejected(v_id,'SavingsInvalid',v_savings_error);
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

create or replace function accord_private.privacy_scrub_author(p_value jsonb,p_account uuid) returns jsonb
language plpgsql immutable set search_path = '' as $$
declare v_result jsonb; v_key text; v_item jsonb;
begin
  if jsonb_typeof(p_value)='object' then
    v_result:='{}';
    for v_key,v_item in select * from jsonb_each(p_value) loop
      if v_key in ('authorId','createdBy','personId','author_id','accountId','account_id') and v_item=to_jsonb(p_account::text) then
        v_result:=v_result||jsonb_build_object(v_key,'deleted');
      else v_result:=v_result||jsonb_build_object(v_key,accord_private.privacy_scrub_author(v_item,p_account)); end if;
    end loop;
    return v_result;
  elsif jsonb_typeof(p_value)='array' then
    select coalesce(jsonb_agg(accord_private.privacy_scrub_author(value,p_account)),'[]') into v_result from jsonb_array_elements(p_value);
    return v_result;
  end if;
  return p_value;
end; $$;
revoke all on function accord_private.privacy_scrub_author(jsonb,uuid) from public, anon, authenticated;
