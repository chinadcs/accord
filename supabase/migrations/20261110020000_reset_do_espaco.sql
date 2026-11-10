-- ADR 0088: reset do espaço. Apaga só os dados financeiros (entidades e operações aplicadas), com aprovação de todos os membros
-- atuais, e preserva espaço, membros, convites e contas. `reset_epoch` sobe a cada reset: é o que cada aparelho compara para limpar
-- seu banco local. O cursor de mudanças não volta atrás, então um aparelho que pede mudanças desde um cursor antigo continua válido.
alter table public.spaces add column reset_epoch bigint not null default 0;
alter table public.privacy_space_deletion add column kind text not null default 'Delete' check (kind in ('Delete','Reset'));

create function accord_private.privacy_reset_space(p_space uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.spaces where id=p_space for update;
  delete from public.applied_operations where space_id=p_space;
  delete from public.space_entities where space_id=p_space;
  update public.spaces set reset_epoch=reset_epoch+1 where id=p_space;
end; $$;
revoke all on function accord_private.privacy_reset_space(uuid) from public, anon, authenticated;

create or replace function public.privacy_space_action(p_account uuid,p_action text,p_id uuid,p_space uuid default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_job public.privacy_space_deletion; v_members uuid[]; v_signature text; v_kind text;
begin
  if p_action in ('request_space_deletion','request_space_reset') then
    v_kind := case when p_action='request_space_reset' then 'Reset' else 'Delete' end;
    perform 1 from public.spaces where id=p_space for update;
    if not exists(select 1 from public.space_members where space_id=p_space and account_id=p_account) then
      raise exception 'privacy_not_member' using errcode='42501';
    end if;
    select * into v_job from public.privacy_space_deletion where id=p_id;
    if found then
      if v_job.space_id<>p_space or v_job.kind<>v_kind then raise exception 'privacy_invalid_request' using errcode='42501'; end if;
    else
      select * into v_job from public.privacy_space_deletion where space_id=p_space and status='AwaitingApprovals';
      if found then
        -- Só um pedido por vez no espaço: excluir e resetar não correm juntos, e um não é aprovado como se fosse o outro.
        if v_job.kind<>v_kind then raise exception 'privacy_request_exists' using errcode='42501'; end if;
      else
        select array_agg(account_id order by account_id),string_agg(account_id::text||':'||joined_at::text,',' order by account_id)
          into v_members,v_signature from public.space_members where space_id=p_space;
        insert into public.privacy_space_deletion(id,space_id,initiated_by,member_signature,members,approvals,status,kind)
          values(p_id,p_space,p_account,v_signature,v_members,array[p_account],'AwaitingApprovals',v_kind) returning * into v_job;
      end if;
    end if;
  else
    select * into v_job from public.privacy_space_deletion where id=p_id;
    if not found then raise exception 'privacy_request_unavailable' using errcode='42501'; end if;
    perform 1 from public.spaces where id=v_job.space_id for update;
    select * into v_job from public.privacy_space_deletion where id=p_id for update;
    if not exists(select 1 from public.space_members where space_id=v_job.space_id and account_id=p_account) then
      if v_job.status='Completed' and v_job.kind='Delete' and p_account=any(v_job.members) then
        return jsonb_build_object('requestId',v_job.id,'status',v_job.status,'spaceId',v_job.space_id,'kind',v_job.kind);
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
    if v_job.kind='Reset' then perform accord_private.privacy_reset_space(v_job.space_id);
    else perform accord_private.privacy_purge_space(v_job.space_id); end if;
    update public.privacy_space_deletion set status='Completed' where id=v_job.id returning * into v_job;
  end if;
  return jsonb_build_object('requestId',v_job.id,'status',v_job.status,'spaceId',v_job.space_id,'kind',v_job.kind,
    'members',to_jsonb(v_job.members),'approvals',to_jsonb(v_job.approvals));
end; $$;

-- Pedidos de exclusão seguem todos na lista (como antes); dos resets só o que espera aprovação e o último concluído/recusado,
-- para a lista não crescer a cada reset. `spaces` leva a época do reset, que cada aparelho compara com a que já aplicou.
create or replace function public.privacy_status(p_account uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('requests',coalesce((select jsonb_agg(jsonb_build_object('requestId',d.id,'spaceId',d.space_id,'status',d.status,'kind',d.kind,'members',d.members,'approvals',d.approvals))
    from public.privacy_space_deletion d where exists(select 1 from public.space_members m where m.space_id=d.space_id and m.account_id=p_account)
      and (d.kind='Delete' or d.status='AwaitingApprovals' or d.id=(select r.id from public.privacy_space_deletion r where r.space_id=d.space_id and r.kind='Reset' and r.status<>'AwaitingApprovals' order by r.created_at desc,r.id limit 1))), '[]'),
    'closedSpaces',coalesce((select jsonb_agg(jsonb_build_object('spaceId',space_id,'name',space_name)) from public.privacy_space_notice where account_id=p_account),'[]'),
    'spaces',coalesce((select jsonb_agg(jsonb_build_object('spaceId',s.id,'resetEpoch',s.reset_epoch)) from public.spaces s
      where exists(select 1 from public.space_members m where m.space_id=s.id and m.account_id=p_account)),'[]'));
$$;

revoke all on function public.privacy_space_action(uuid,text,uuid,uuid), public.privacy_status(uuid) from public, anon, authenticated;
grant execute on function public.privacy_space_action(uuid,text,uuid,uuid), public.privacy_status(uuid) to service_role;
