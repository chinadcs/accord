-- Accord S2: espaço do casal, convite por código e sincronização de operações.
--
-- Autorização por associação ao espaço, verificada aqui no banco. Nenhuma escrita direta nas
-- tabelas: criar espaço, convidar, entrar, enviar e receber passam pelas funções abaixo, que
-- derivam a conta da sessão (auth.uid()) e nunca de um parâmetro. Não há servidor próprio.
-- Nenhum e-mail é enviado (Confirm Email desativado em config.toml, sem SMTP).
--
-- shared-types: Category,Account,Preferences,Movement,Card,CardPurchase,Boleto,Income,Recurrence,Refund,Invoice,InvoicePayment,LimitPlan

create schema if not exists accord_private;
revoke all on schema accord_private from public, anon, authenticated;
grant usage on schema accord_private to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Tabelas. Tabelas novas nascem sem acesso para anon e authenticated.
-- ---------------------------------------------------------------------------------------------

alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;
alter default privileges in schema public revoke execute on functions from public, anon, authenticated;
-- O responsável do piloto (chave de serviço, fora do aplicativo) administra todas as tabelas.
alter default privileges in schema public grant all on tables to service_role;
alter default privileges in schema public grant all on sequences to service_role;

create table public.spaces (
  id uuid primary key,
  name text not null check (char_length(btrim(name)) between 1 and 80),
  created_by uuid not null references auth.users (id),
  created_at timestamptz not null default now(),
  -- Último cursor entregue. Só avança sob o bloqueio da linha do espaço.
  change_cursor bigint not null default 0
);

create table public.space_members (
  space_id uuid not null references public.spaces (id),
  account_id uuid not null references auth.users (id),
  nickname text not null,
  joined_at timestamptz not null default now(),
  origin text not null check (origin in ('creation', 'invite')),
  primary key (space_id, account_id),
  -- Proposta do S2: uma conta participa de no máximo um espaço.
  unique (account_id)
);

create table public.space_invites (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references public.spaces (id),
  -- Só o resumo do código é guardado; o código aparece uma vez, ao ser gerado.
  code_digest bytea not null unique,
  generated_by uuid not null references auth.users (id),
  generated_at timestamptz not null default now(),
  valid_until timestamptz not null,
  state text not null check (state in ('active', 'consumed', 'replaced')),
  consumed_by uuid references auth.users (id),
  consumed_at timestamptz
);
-- No máximo um convite ativo por espaço.
create unique index space_invites_one_active on public.space_invites (space_id) where state = 'active';

create table public.invite_attempts (
  id bigint generated always as identity primary key,
  account_id uuid not null references auth.users (id),
  at timestamptz not null default now()
);
create index invite_attempts_account on public.invite_attempts (account_id, at);

create table public.space_entities (
  type text not null,
  id text not null,
  -- O espaço de uma entidade é fixado na primeira aplicação e nunca muda.
  space_id uuid not null references public.spaces (id),
  version bigint not null check (version >= 1),
  -- Estado completo, opaco para o serviço (texto JSON, preservado byte a byte).
  state text not null,
  tombstone boolean not null default false,
  author_id uuid not null references auth.users (id),
  service_time timestamptz not null default now(),
  change_cursor bigint not null,
  primary key (type, id)
);
create index space_entities_cursor on public.space_entities (space_id, change_cursor);

create table public.applied_operations (
  operation_id text primary key,
  space_id uuid not null references public.spaces (id),
  author_id uuid not null references auth.users (id),
  installation_id text not null,
  digest bytea not null,
  result jsonb not null,
  received_at timestamptz not null default now()
);

alter table public.spaces enable row level security;
alter table public.space_members enable row level security;
alter table public.space_invites enable row level security;
alter table public.invite_attempts enable row level security;
alter table public.space_entities enable row level security;
alter table public.applied_operations enable row level security;

-- Função auxiliar de privilégio controlado: evita recursão de políticas.
create function accord_private.is_member(p_space uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.space_members m where m.space_id = p_space and m.account_id = (select auth.uid())
  );
$$;
revoke all on function accord_private.is_member(uuid) from public, anon;
grant execute on function accord_private.is_member(uuid) to authenticated;

-- Leitura só por membros do próprio espaço. Nenhuma escrita direta, para ninguém.
grant select on public.spaces, public.space_members, public.space_entities to authenticated;

create policy spaces_select_members on public.spaces
  for select to authenticated using (accord_private.is_member(id));
create policy members_select_members on public.space_members
  for select to authenticated using (accord_private.is_member(space_id));
create policy entities_select_members on public.space_entities
  for select to authenticated using (accord_private.is_member(space_id));
-- space_invites, invite_attempts e applied_operations: sem política e sem concessão.

-- ---------------------------------------------------------------------------------------------
-- Utilitários internos
-- ---------------------------------------------------------------------------------------------

create function accord_private.epoch_ms(p_ts timestamptz)
returns bigint
language sql
immutable
set search_path = ''
as $$ select (extract(epoch from p_ts) * 1000)::bigint; $$;

create function accord_private.shared_types()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['Category','Account','Preferences','Movement','Card','CardPurchase','Boleto','Income',
               'Recurrence','Refund','Invoice','InvoicePayment','LimitPlan'];
$$;

create function accord_private.is_json_object(p_text text)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
begin
  return jsonb_typeof(p_text::jsonb) = 'object';
exception when others then
  return false;
end;
$$;

create function accord_private.entity_json(e public.space_entities)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'type', e.type, 'id', e.id, 'version', e.version, 'state', e.state, 'tombstone', e.tombstone,
    'authorId', e.author_id, 'serviceTimeMillis', accord_private.epoch_ms(e.service_time),
    'cursor', e.change_cursor
  );
$$;

create function accord_private.rejected(p_operation text, p_reason text, p_detail text default null)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object('operationId', p_operation, 'result', 'rejected', 'reason', p_reason, 'detail', p_detail);
$$;

-- ---------------------------------------------------------------------------------------------
-- Espaço e convite
-- ---------------------------------------------------------------------------------------------

create function public.create_space(p_space uuid, p_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account uuid := (select auth.uid());
  v_existing uuid;
  v_name text;
begin
  if v_account is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  select m.space_id into v_existing from public.space_members m where m.account_id = v_account;
  if v_existing is not null then
    select s.name into v_name from public.spaces s where s.id = v_existing;
    return jsonb_build_object('spaceId', v_existing, 'name', v_name, 'adoptedExisting', v_existing <> p_space);
  end if;

  -- O ID de um espaço de outras pessoas não pode ser tomado.
  if exists (select 1 from public.spaces s where s.id = p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;

  begin
    insert into public.spaces (id, name, created_by) values (p_space, btrim(p_name), v_account);
    insert into public.space_members (space_id, account_id, nickname, origin)
    values (p_space, v_account, (select split_part(u.email, '@', 1) from auth.users u where u.id = v_account), 'creation');
  exception when unique_violation then
    -- Duas chamadas simultâneas da mesma conta: vale a que chegou primeiro.
    select m.space_id into v_existing from public.space_members m where m.account_id = v_account;
    select s.name into v_name from public.spaces s where s.id = v_existing;
    return jsonb_build_object('spaceId', v_existing, 'name', v_name, 'adoptedExisting', v_existing <> p_space);
  end;
  return jsonb_build_object('spaceId', p_space, 'name', btrim(p_name), 'adoptedExisting', false);
end;
$$;

create function public.current_space()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account uuid := (select auth.uid());
  v_space uuid;
  v_name text;
begin
  if v_account is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  select m.space_id, s.name into v_space, v_name
  from public.space_members m join public.spaces s on s.id = m.space_id
  where m.account_id = v_account;
  if v_space is null then
    return jsonb_build_object('space', null);
  end if;
  return jsonb_build_object('space', jsonb_build_object('spaceId', v_space, 'name', v_name));
end;
$$;

create function public.generate_invite(p_space uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account uuid := (select auth.uid());
  v_alphabet constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
  v_code text := '';
  v_byte int;
  v_now timestamptz := now();
  v_until timestamptz := v_now + interval '3 days';
begin
  if v_account is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if not accord_private.is_member(p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;
  -- Serializa as mudanças de convite e de associação deste espaço.
  perform 1 from public.spaces s where s.id = p_space for update;
  if (select count(*) from public.space_members m where m.space_id = p_space) >= 2 then
    return jsonb_build_object('error', 'space_full');
  end if;

  -- 8 caracteres alfanuméricos em caixa alta, sem 0/O e 1/I/L; descarta bytes que enviesariam o sorteio.
  while char_length(v_code) < 8 loop
    v_byte := get_byte(extensions.gen_random_bytes(1), 0);
    if v_byte < 248 then
      v_code := v_code || substr(v_alphabet, (v_byte % 31) + 1, 1);
    end if;
  end loop;

  update public.space_invites i set state = 'replaced' where i.space_id = p_space and i.state = 'active';
  insert into public.space_invites (space_id, code_digest, generated_by, generated_at, valid_until, state)
  values (p_space, sha256(convert_to(v_code, 'UTF8')), v_account, v_now, v_until, 'active');

  return jsonb_build_object(
    'code', v_code,
    'generatedAtMillis', accord_private.epoch_ms(v_now),
    'validUntilMillis', accord_private.epoch_ms(v_until)
  );
end;
$$;

create function public.active_invite(p_space uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_invite public.space_invites;
begin
  if (select auth.uid()) is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if not accord_private.is_member(p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;
  select * into v_invite from public.space_invites i
  where i.space_id = p_space and i.state = 'active' order by i.generated_at desc limit 1;
  if not found then
    return jsonb_build_object('invite', null);
  end if;
  return jsonb_build_object('invite', jsonb_build_object(
    'validUntilMillis', accord_private.epoch_ms(v_invite.valid_until),
    'expired', now() >= v_invite.valid_until
  ));
end;
$$;

create function public.join_with_code(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account uuid := (select auth.uid());
  v_digest bytea;
  v_space uuid;
  v_invite public.space_invites;
  v_attempts int;
  v_oldest timestamptz;
  v_name text;
begin
  if v_account is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  -- Limite de tentativas falhas por conta (proposta do S2): 5 em 10 minutos.
  delete from public.invite_attempts a where a.account_id = v_account and a.at <= now() - interval '10 minutes';
  select count(*), min(a.at) into v_attempts, v_oldest from public.invite_attempts a where a.account_id = v_account;
  if v_attempts >= 5 then
    return jsonb_build_object(
      'error', 'rate_limited',
      'retryAfterMillis', accord_private.epoch_ms(v_oldest + interval '10 minutes') - accord_private.epoch_ms(now())
    );
  end if;

  v_digest := sha256(convert_to(upper(regexp_replace(p_code, '\s', '', 'g')), 'UTF8'));

  -- Primeiro o espaço (sempre nesta ordem, como em generate_invite), depois o convite.
  select i.space_id into v_space from public.space_invites i where i.code_digest = v_digest;
  if v_space is not null then
    perform 1 from public.spaces s where s.id = v_space for update;
    select * into v_invite from public.space_invites i where i.code_digest = v_digest for update;
  end if;

  -- Repetição pela conta que consumiu: conclui a mesma associação, sem erro.
  if v_space is not null and v_invite.state = 'consumed' and v_invite.consumed_by = v_account then
    select s.name into v_name from public.spaces s where s.id = v_space;
    return jsonb_build_object('spaceId', v_space, 'name', v_name);
  end if;

  if exists (select 1 from public.space_members m where m.account_id = v_account) then
    return jsonb_build_object('error', 'already_in_another_space');
  end if;

  if v_space is null or v_invite.state <> 'active' or now() >= v_invite.valid_until then
    insert into public.invite_attempts (account_id) values (v_account);
    return jsonb_build_object('error', 'invalid_invite');
  end if;

  if (select count(*) from public.space_members m where m.space_id = v_space) >= 2 then
    return jsonb_build_object('error', 'space_full');
  end if;

  insert into public.space_members (space_id, account_id, nickname, origin)
  values (v_space, v_account, (select split_part(u.email, '@', 1) from auth.users u where u.id = v_account), 'invite');
  update public.space_invites i set state = 'consumed', consumed_by = v_account, consumed_at = now() where i.id = v_invite.id;
  select s.name into v_name from public.spaces s where s.id = v_space;
  return jsonb_build_object('spaceId', v_space, 'name', v_name);
end;
$$;

create function public.list_participants(p_space uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (select auth.uid()) is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if not accord_private.is_member(p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;
  return jsonb_build_object(
    'items', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'accountId', m.account_id, 'nickname', m.nickname, 'joinedAtMillis', accord_private.epoch_ms(m.joined_at)
      ) order by m.joined_at, m.account_id), '[]'::jsonb)
      from public.space_members m where m.space_id = p_space
    ),
    'checkedAtMillis', accord_private.epoch_ms(clock_timestamp())
  );
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Envio e recebimento
-- ---------------------------------------------------------------------------------------------

-- Aplica uma operação sob o bloqueio do espaço (já obtido por push_operations). Regras de negócio
-- viram resultado (aplicada, conflito, recusada), nunca exceção: nada aplicado pela metade.
create function accord_private.apply_operation(p_space uuid, p_account uuid, p_op jsonb)
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
  v_unchanged jsonb := '[]'::jsonb;
  v_versions jsonb := '[]'::jsonb;
  v_cursor bigint;
  v_version bigint;
  v_result jsonb;
begin
  if v_id is null or v_id = '' then
    return accord_private.rejected('', 'InvalidEnvelope', 'operationId ausente');
  end if;
  if coalesce(p_op ->> 'protocolVersion', '0') <> '1' then
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

  for v_ch in select c from jsonb_array_elements(v_changes) c loop
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

  for v_ch in select c from jsonb_array_elements(v_changes) c loop
    update public.spaces s set change_cursor = s.change_cursor + 1 where s.id = p_space returning s.change_cursor into v_cursor;
    insert into public.space_entities as e (type, id, space_id, version, state, tombstone, author_id, service_time, change_cursor)
    values (v_ch ->> 'type', v_ch ->> 'id', p_space, 1, v_ch ->> 'state', coalesce((v_ch ->> 'tombstone')::boolean, false), p_account, now(), v_cursor)
    on conflict (type, id) do update
      set version = e.version + 1, state = excluded.state, tombstone = excluded.tombstone,
          author_id = excluded.author_id, service_time = excluded.service_time, change_cursor = excluded.change_cursor
    returning e.version into v_version;
    v_versions := v_versions || jsonb_build_object('type', v_ch ->> 'type', 'id', v_ch ->> 'id', 'version', v_version);
  end loop;

  v_result := jsonb_build_object('operationId', v_id, 'result', 'applied', 'versions', v_versions, 'cursor', v_cursor, 'repeated', false);
  insert into public.applied_operations (operation_id, space_id, author_id, installation_id, digest, result)
  values (v_id, p_space, p_account, coalesce(p_op ->> 'installationId', ''), v_digest, v_result);
  return v_result;
exception when others then
  -- Qualquer defeito inesperado desfaz esta operação (ponto de salvamento do bloco) e a recusa.
  return accord_private.rejected(coalesce(v_id, ''), 'InvalidEnvelope', sqlerrm);
end;
$$;

create function public.push_operations(p_space uuid, p_operations jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account uuid := (select auth.uid());
  v_results jsonb := '[]'::jsonb;
  v_op jsonb;
begin
  if v_account is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if not accord_private.is_member(p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;
  if jsonb_typeof(p_operations) <> 'array' then
    return jsonb_build_object('error', 'invalid_batch');
  end if;
  -- Serializa as escritas do espaço: o cursor avança sem lacunas visíveis.
  perform 1 from public.spaces s where s.id = p_space for update;
  for v_op in select o from jsonb_array_elements(p_operations) o loop
    v_results := v_results || accord_private.apply_operation(p_space, v_account, v_op);
  end loop;
  return jsonb_build_object('results', v_results);
end;
$$;

create function public.pull_changes(p_space uuid, p_after bigint, p_limit int)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_limit int := greatest(coalesce(p_limit, 100), 1);
  v_rows jsonb;
  v_count int;
  v_last bigint;
begin
  if (select auth.uid()) is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if not accord_private.is_member(p_space) then
    return jsonb_build_object('error', 'not_member');
  end if;
  select coalesce(jsonb_agg(accord_private.entity_json(e) order by e.change_cursor), '[]'::jsonb), count(*)
  into v_rows, v_count
  from (
    select * from public.space_entities x
    where x.space_id = p_space and x.change_cursor > p_after
    order by x.change_cursor limit v_limit + 1
  ) e;
  if v_count > v_limit then
    v_rows := v_rows - v_limit;  -- descarta a linha extra, lida só para saber se há mais
  end if;
  v_last := (select max((r ->> 'cursor')::bigint) from jsonb_array_elements(v_rows) r);
  return jsonb_build_object('changes', v_rows, 'nextCursor', coalesce(v_last, p_after), 'hasMore', v_count > v_limit);
end;
$$;

grant all on all tables in schema public to service_role;
grant all on all sequences in schema public to service_role;

-- Apenas contas autenticadas executam as funções expostas; anon não executa nenhuma.
revoke all on function public.create_space(uuid, text) from public, anon;
revoke all on function public.current_space() from public, anon;
revoke all on function public.generate_invite(uuid) from public, anon;
revoke all on function public.active_invite(uuid) from public, anon;
revoke all on function public.join_with_code(text) from public, anon;
revoke all on function public.list_participants(uuid) from public, anon;
revoke all on function public.push_operations(uuid, jsonb) from public, anon;
revoke all on function public.pull_changes(uuid, bigint, int) from public, anon;
grant execute on function public.create_space(uuid, text) to authenticated;
grant execute on function public.current_space() to authenticated;
grant execute on function public.generate_invite(uuid) to authenticated;
grant execute on function public.active_invite(uuid) to authenticated;
grant execute on function public.join_with_code(text) to authenticated;
grant execute on function public.list_participants(uuid) to authenticated;
grant execute on function public.push_operations(uuid, jsonb) to authenticated;
grant execute on function public.pull_changes(uuid, bigint, int) to authenticated;
revoke all on function accord_private.apply_operation(uuid, uuid, jsonb) from public, anon, authenticated;
