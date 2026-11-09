-- ADR 0058. Todas as entradas administrativas são exclusivas do endpoint confiável.
create table public.privacy_account_deletion (
  id uuid primary key,
  subject_id uuid,
  proof_hash bytea not null,
  status text not null check(status in ('Prepared','AuthPending','Completed')),
  created_at timestamptz not null default now()
);
create unique index privacy_one_account_deletion on public.privacy_account_deletion(subject_id) where subject_id is not null;
create table public.privacy_space_deletion (
  id uuid primary key, space_id uuid not null, initiated_by uuid,
  member_signature text not null, members uuid[] not null, approvals uuid[] not null,
  status text not null check(status in ('AwaitingApprovals','Processing','Completed','Cancelled','Invalidated')),
  created_at timestamptz not null default now()
);
create unique index privacy_one_space_deletion on public.privacy_space_deletion(space_id) where status = 'AwaitingApprovals';
create table public.privacy_space_notice (
  account_id uuid not null references auth.users(id) on delete cascade,
  space_id uuid not null, space_name text not null, completed_at timestamptz not null default now(),
  primary key(account_id, space_id)
);
alter table public.privacy_account_deletion enable row level security;
alter table public.privacy_space_deletion enable row level security;
alter table public.privacy_space_notice enable row level security;
revoke all on public.privacy_account_deletion, public.privacy_space_deletion, public.privacy_space_notice from anon, authenticated;

-- A autoria financeira não mantém uma conta Auth viva depois da exclusão.
alter table public.spaces alter column created_by drop not null;
alter table public.spaces drop constraint spaces_created_by_fkey;
alter table public.spaces add constraint spaces_created_by_fkey foreign key(created_by) references auth.users(id) on delete set null;
alter table public.space_entities alter column author_id drop not null;
alter table public.space_entities drop constraint space_entities_author_id_fkey;
alter table public.space_entities add constraint space_entities_author_id_fkey foreign key(author_id) references auth.users(id) on delete set null;
alter table public.applied_operations alter column author_id drop not null;
alter table public.applied_operations drop constraint applied_operations_author_id_fkey;
alter table public.applied_operations add constraint applied_operations_author_id_fkey foreign key(author_id) references auth.users(id) on delete set null;

create or replace function accord_private.is_member(p_space uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.space_members m where m.space_id=p_space and m.account_id=(select auth.uid()))
    and not exists(select 1 from public.privacy_account_deletion d where d.subject_id=(select auth.uid()) and d.status<>'Completed');
$$;

create function accord_private.privacy_membership_change() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_space uuid;
begin
  if TG_OP='INSERT' then
    if exists(select 1 from public.privacy_account_deletion where subject_id=NEW.account_id and status<>'Completed') then
      raise exception 'privacy_account_deleting' using errcode='42501';
    end if;
    v_space := NEW.space_id;
  else v_space := OLD.space_id;
  end if;
  perform 1 from public.spaces where id=v_space for update;
  update public.privacy_space_deletion set status='Invalidated' where space_id=v_space and status='AwaitingApprovals';
  if TG_OP='UPDATE' and NEW.space_id<>OLD.space_id then
    update public.privacy_space_deletion set status='Invalidated' where space_id=NEW.space_id and status='AwaitingApprovals';
  end if;
  return coalesce(NEW,OLD);
end; $$;
revoke all on function accord_private.privacy_membership_change() from public, anon, authenticated;
create trigger privacy_membership_change before insert or update or delete on public.space_members
for each row execute function accord_private.privacy_membership_change();

create function accord_private.privacy_purge_space(p_space uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.spaces where id=p_space for update;
  insert into public.privacy_space_notice(account_id,space_id,space_name)
    select m.account_id,m.space_id,s.name from public.space_members m join public.spaces s on s.id=m.space_id where m.space_id=p_space
    on conflict(account_id,space_id) do nothing;
  delete from public.space_invite_uses where invite_id in(select id from public.space_invites where space_id=p_space);
  delete from public.space_invites where space_id=p_space;
  delete from public.applied_operations where space_id=p_space;
  delete from public.space_entities where space_id=p_space;
  delete from public.space_members where space_id=p_space;
  delete from public.spaces where id=p_space;
end; $$;
revoke all on function accord_private.privacy_purge_space(uuid) from public, anon, authenticated;

create function accord_private.privacy_scrub_author(p_value jsonb,p_account uuid) returns jsonb
language plpgsql immutable set search_path = '' as $$
declare v_result jsonb; v_key text; v_item jsonb;
begin
  if jsonb_typeof(p_value)='object' then
    v_result:='{}';
    for v_key,v_item in select * from jsonb_each(p_value) loop
      if v_key in ('authorId','personId','author_id','accountId','account_id') and v_item=to_jsonb(p_account::text) then
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

create function public.privacy_prepare_account(p_account uuid,p_id uuid,p_proof text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_existing public.privacy_account_deletion;
begin
  if p_proof is null or length(p_proof)<64 or not exists(select 1 from auth.users where id=p_account) then
    raise exception 'privacy_invalid_request' using errcode='42501';
  end if;
  perform 1 from auth.users where id=p_account for update;
  select * into v_existing from public.privacy_account_deletion where id=p_id or subject_id=p_account for update;
  if found then
    if v_existing.subject_id is distinct from p_account then
      raise exception 'privacy_request_exists' using errcode='42501';
    end if;
    -- Nova autenticação por senha pode recuperar uma solicitação cujo aparelho/comprovante se perdeu.
    update public.privacy_account_deletion set id=p_id,proof_hash=extensions.digest(p_proof,'sha256') where id=v_existing.id;
    return jsonb_build_object('requestId',p_id,'status',v_existing.status);
  end if;
  insert into public.privacy_account_deletion(id,subject_id,proof_hash,status) values(p_id,p_account,extensions.digest(p_proof,'sha256'),'Prepared');
  return jsonb_build_object('requestId',p_id,'status','Prepared');
end; $$;

create function public.privacy_purge_account(p_id uuid,p_proof text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_job public.privacy_account_deletion; v_space uuid; v_row public.space_entities; v_state text; v_cursor bigint; v_changed boolean;
begin
  select * into v_job from public.privacy_account_deletion where id=p_id and proof_hash=extensions.digest(p_proof,'sha256') for update;
  if not found then raise exception 'privacy_invalid_proof' using errcode='42501'; end if;
  if v_job.status='Completed' then return jsonb_build_object('requestId',p_id,'status','Completed'); end if;
  if v_job.status='AuthPending' then return jsonb_build_object('requestId',p_id,'status','AuthPending','accountId',v_job.subject_id); end if;
  select space_id into v_space from public.space_members where account_id=v_job.subject_id;
  if v_space is not null then
    perform 1 from public.spaces where id=v_space for update;
    if (select count(*) from public.space_members where space_id=v_space)=1 then
      perform accord_private.privacy_purge_space(v_space);
    else
      -- Publicar uma nova versão preserva identidades, expõe a mudança no pull e impede aplicação sobre base velha.
      for v_row in select * from public.space_entities where space_id=v_space order by type,id loop
        begin
          v_state:=accord_private.privacy_scrub_author(v_row.state::jsonb,v_job.subject_id)::text;
          v_changed:=v_state::jsonb<>v_row.state::jsonb;
        exception when invalid_text_representation then
          v_state:=v_row.state; v_changed:=false;
        end;
        if v_row.author_id=v_job.subject_id or v_changed then
          update public.spaces set change_cursor=change_cursor+1 where id=v_space returning change_cursor into v_cursor;
          update public.space_entities set state=v_state, author_id=case when author_id=v_job.subject_id then null else author_id end,
            version=version+1,change_cursor=v_cursor,service_time=now() where type=v_row.type and id=v_row.id;
        end if;
      end loop;
      update public.applied_operations set author_id=null,installation_id='deleted' where author_id=v_job.subject_id;
      update public.applied_operations set result=accord_private.privacy_scrub_author(result,v_job.subject_id) where space_id=v_space;
      update public.spaces set created_by=null where created_by=v_job.subject_id;
      delete from public.space_members where account_id=v_job.subject_id;
    end if;
  end if;
  delete from public.space_invite_uses where account_id=v_job.subject_id or invite_id in(select id from public.space_invites where generated_by=v_job.subject_id);
  delete from public.space_invites where generated_by=v_job.subject_id;
  update public.space_invites set consumed_by=null where consumed_by=v_job.subject_id;
  delete from public.invite_attempts where account_id=v_job.subject_id;
  delete from public.privacy_space_notice where account_id=v_job.subject_id;
  update public.privacy_space_deletion set initiated_by=case when initiated_by=v_job.subject_id then null else initiated_by end,
    members=array_remove(members,v_job.subject_id), approvals=array_remove(approvals,v_job.subject_id),
    status=case when status='AwaitingApprovals' then 'Invalidated' else status end
    where initiated_by=v_job.subject_id or v_job.subject_id=any(members);
  if pg_catalog.to_regclass('public.privacy_consent') is not null then
    execute 'delete from public.privacy_consent where account_id=$1' using v_job.subject_id;
  end if;
  if pg_catalog.to_regclass('public.chat_collection_event') is not null then
    execute 'delete from public.chat_collection_event where account_id=$1' using v_job.subject_id;
  end if;
  update public.privacy_account_deletion set status='AuthPending' where id=p_id;
  return jsonb_build_object('requestId',p_id,'status','AuthPending','accountId',v_job.subject_id);
end; $$;

create function public.privacy_complete_account(p_id uuid,p_proof text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_job public.privacy_account_deletion;
begin
  select * into v_job from public.privacy_account_deletion where id=p_id and proof_hash=extensions.digest(p_proof,'sha256') for update;
  if not found then raise exception 'privacy_invalid_proof' using errcode='42501'; end if;
  if v_job.status='Completed' then return jsonb_build_object('requestId',p_id,'status','Completed'); end if;
  if v_job.status<>'AuthPending' or exists(select 1 from auth.users where id=v_job.subject_id) then
    return jsonb_build_object('requestId',p_id,'status',v_job.status);
  end if;
  update public.privacy_account_deletion set status='Completed',subject_id=null where id=p_id;
  return jsonb_build_object('requestId',p_id,'status','Completed');
end; $$;

create function public.privacy_space_action(p_account uuid,p_action text,p_id uuid,p_space uuid default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_job public.privacy_space_deletion; v_members uuid[]; v_signature text;
begin
  if p_action='request_space_deletion' then
    perform 1 from public.spaces where id=p_space for update;
    if not exists(select 1 from public.space_members where space_id=p_space and account_id=p_account) then
      raise exception 'privacy_not_member' using errcode='42501';
    end if;
    select * into v_job from public.privacy_space_deletion where id=p_id;
    if found then
      if v_job.space_id<>p_space then raise exception 'privacy_invalid_request' using errcode='42501'; end if;
    else
      select * into v_job from public.privacy_space_deletion where space_id=p_space and status='AwaitingApprovals';
      if not found then
        select array_agg(account_id order by account_id),string_agg(account_id::text||':'||joined_at::text,',' order by account_id)
          into v_members,v_signature from public.space_members where space_id=p_space;
        insert into public.privacy_space_deletion(id,space_id,initiated_by,member_signature,members,approvals,status)
          values(p_id,p_space,p_account,v_signature,v_members,array[p_account],'AwaitingApprovals') returning * into v_job;
      end if;
    end if;
  else
    select * into v_job from public.privacy_space_deletion where id=p_id;
    if not found then raise exception 'privacy_request_unavailable' using errcode='42501'; end if;
    perform 1 from public.spaces where id=v_job.space_id for update;
    select * into v_job from public.privacy_space_deletion where id=p_id for update;
    if not exists(select 1 from public.space_members where space_id=v_job.space_id and account_id=p_account) then
      if v_job.status='Completed' and p_account=any(v_job.members) then
        return jsonb_build_object('requestId',v_job.id,'status',v_job.status,'spaceId',v_job.space_id);
      end if;
      raise exception 'privacy_not_member' using errcode='42501';
    end if;
    if v_job.status='AwaitingApprovals' then
      select string_agg(account_id::text||':'||joined_at::text,',' order by account_id) into v_signature from public.space_members where space_id=v_job.space_id;
      if v_signature<>v_job.member_signature then update public.privacy_space_deletion set status='Invalidated' where id=v_job.id;
      elsif p_action='cancel_space_deletion' then update public.privacy_space_deletion set status='Cancelled' where id=v_job.id;
      elsif p_action='approve_space_deletion' then update public.privacy_space_deletion set approvals=(select array_agg(distinct a) from unnest(approvals||array[p_account]) a) where id=v_job.id;
      else raise exception 'privacy_invalid_action' using errcode='22023'; end if;
      select * into v_job from public.privacy_space_deletion where id=v_job.id;
    end if;
  end if;
  if v_job.status='AwaitingApprovals' and v_job.members<@v_job.approvals then
    update public.privacy_space_deletion set status='Processing' where id=v_job.id;
    perform accord_private.privacy_purge_space(v_job.space_id);
    update public.privacy_space_deletion set status='Completed' where id=v_job.id returning * into v_job;
  end if;
  return jsonb_build_object('requestId',v_job.id,'status',v_job.status,'spaceId',v_job.space_id,'members',to_jsonb(v_job.members),'approvals',to_jsonb(v_job.approvals));
end; $$;

create function public.privacy_status(p_account uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('requests',coalesce((select jsonb_agg(jsonb_build_object('requestId',d.id,'spaceId',d.space_id,'status',d.status,'members',d.members,'approvals',d.approvals))
    from public.privacy_space_deletion d where exists(select 1 from public.space_members m where m.space_id=d.space_id and m.account_id=p_account)), '[]'),
    'closedSpaces',coalesce((select jsonb_agg(jsonb_build_object('spaceId',space_id,'name',space_name)) from public.privacy_space_notice where account_id=p_account),'[]'));
$$;

revoke all on function public.privacy_prepare_account(uuid,uuid,text), public.privacy_purge_account(uuid,text),
  public.privacy_complete_account(uuid,text), public.privacy_space_action(uuid,text,uuid,uuid), public.privacy_status(uuid) from public, anon, authenticated;
grant execute on function public.privacy_prepare_account(uuid,uuid,text), public.privacy_purge_account(uuid,text),
  public.privacy_complete_account(uuid,text), public.privacy_space_action(uuid,text,uuid,uuid), public.privacy_status(uuid) to service_role;
