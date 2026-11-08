-- ADR 0053: espaço de até quatro pessoas e código de convite multiuso.
-- O código continua com 8 caracteres e validade de 3 dias; um só ativo por espaço. Em vez de
-- ser consumido por quem entra primeiro, serve até o espaço lotar, pode ser revogado e informa
-- quantas vagas restam. A migração antiga não é editada: as funções são recriadas aqui.

alter table public.space_invites drop constraint space_invites_state_check;
alter table public.space_invites
  add constraint space_invites_state_check check (state in ('active', 'consumed', 'replaced', 'revoked'));

-- Quem já entrou por cada código: torna a repetição da mesma conta idempotente.
create table public.space_invite_uses (
  invite_id uuid not null references public.space_invites (id),
  account_id uuid not null references auth.users (id),
  used_at timestamptz not null default now(),
  primary key (invite_id, account_id)
);
alter table public.space_invite_uses enable row level security;
-- Sem política e sem concessão: só as funções abaixo (security definer) leem e escrevem.

insert into public.space_invite_uses (invite_id, account_id, used_at)
select i.id, i.consumed_by, coalesce(i.consumed_at, now()) from public.space_invites i where i.consumed_by is not null;

create or replace function public.generate_invite(p_space uuid)
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
  if (select count(*) from public.space_members m where m.space_id = p_space) >= 4 then
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
    'validUntilMillis', accord_private.epoch_ms(v_until),
    'remainingSlots', 4 - (select count(*)::int from public.space_members m where m.space_id = p_space)
  );
end;
$$;

create or replace function public.active_invite(p_space uuid)
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
    'expired', now() >= v_invite.valid_until,
    'remainingSlots', greatest(0, 4 - (select count(*)::int from public.space_members m where m.space_id = p_space))
  ));
end;
$$;

-- Invalida o código ativo, sem gerar outro.
create function public.revoke_invite(p_space uuid)
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
  perform 1 from public.spaces s where s.id = p_space for update;
  update public.space_invites i set state = 'revoked' where i.space_id = p_space and i.state = 'active';
  return jsonb_build_object('revoked', true);
end;
$$;

create or replace function public.join_with_code(p_code text)
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

  -- Repetição pela conta que já entrou com este código: conclui a mesma associação, sem erro.
  if v_space is not null and exists (
    select 1 from public.space_invite_uses u where u.invite_id = v_invite.id and u.account_id = v_account
  ) and exists (select 1 from public.space_members m where m.space_id = v_space and m.account_id = v_account) then
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

  if (select count(*) from public.space_members m where m.space_id = v_space) >= 4 then
    return jsonb_build_object('error', 'space_full');
  end if;

  insert into public.space_members (space_id, account_id, nickname, origin)
  values (v_space, v_account, (select split_part(u.email, '@', 1) from auth.users u where u.id = v_account), 'invite');
  insert into public.space_invite_uses (invite_id, account_id) values (v_invite.id, v_account);
  select s.name into v_name from public.spaces s where s.id = v_space;
  return jsonb_build_object('spaceId', v_space, 'name', v_name);
end;
$$;

revoke all on function public.revoke_invite(uuid) from public, anon;
grant execute on function public.revoke_invite(uuid) to authenticated;
