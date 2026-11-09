-- ADR 0058: somente o endpoint confiável exporta dados próprios; não concede SELECT à coleta.
create or replace function public.privacy_export(p_account uuid, p_scope text, p_space uuid default null)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  v_profile jsonb;
  v_memberships jsonb;
  v_consents jsonb := '[]'::jsonb;
  v_collection jsonb := '[]'::jsonb;
  v_space jsonb;
  v_members jsonb;
  v_entities jsonb;
  v_operations jsonb;
begin
  select jsonb_build_object('accountId', id, 'email', email, 'createdAt', created_at, 'updatedAt', updated_at)
    into v_profile from auth.users where id = p_account;
  if v_profile is null then raise exception 'privacy_account_unavailable' using errcode = '42501'; end if;
  if p_scope = 'Personal' then
    select coalesce(jsonb_agg(jsonb_build_object('spaceId', m.space_id, 'nickname', m.nickname, 'joinedAt', m.joined_at)), '[]')
      into v_memberships from public.space_members m where m.account_id = p_account;
    -- A coleta e os Termos do ADR 0054 ainda são outra entrega. Não inventar registros nem aceites.
    -- Quando implementados, estas são as tabelas de integração e a coluna de titularidade.
    if pg_catalog.to_regclass('public.privacy_consent') is not null then
      execute 'select coalesce(jsonb_agg(to_jsonb(t)), ''[]''::jsonb) from public.privacy_consent t where account_id = $1'
        into v_consents using p_account;
    end if;
    if pg_catalog.to_regclass('public.chat_collection_event') is not null then
      execute 'select coalesce(jsonb_agg(to_jsonb(t)), ''[]''::jsonb) from public.chat_collection_event t where account_id = $1'
        into v_collection using p_account;
    end if;
    return jsonb_build_object('format', 'accord-service-export', 'version', 1, 'scope', p_scope,
      'createdAt', current_timestamp, 'profile', v_profile, 'memberships', v_memberships,
      'consents', v_consents, 'collectedChat', v_collection,
      'consentStorageAvailable', pg_catalog.to_regclass('public.privacy_consent') is not null,
      'collectionStorageAvailable', pg_catalog.to_regclass('public.chat_collection_event') is not null);
  elsif p_scope = 'Financial' then
    if not exists(select 1 from public.space_members where space_id = p_space and account_id = p_account) then
      raise exception 'privacy_not_member' using errcode = '42501';
    end if;
    select jsonb_build_object('id', id, 'name', name, 'createdAt', created_at, 'cursor', change_cursor)
      into v_space from public.spaces where id = p_space;
    select coalesce(jsonb_agg(jsonb_build_object('accountId', account_id, 'nickname', nickname, 'joinedAt', joined_at)), '[]')
      into v_members from public.space_members where space_id = p_space;
    select coalesce(jsonb_agg(to_jsonb(e) order by e.type, e.id), '[]')
      into v_entities from public.space_entities e where e.space_id = p_space;
    select coalesce(jsonb_agg(jsonb_build_object('operationId', operation_id, 'authorId', author_id,
      'result', result, 'receivedAt', received_at) order by received_at, operation_id), '[]')
      into v_operations from public.applied_operations where space_id = p_space;
    return jsonb_build_object('format', 'accord-service-export', 'version', 1, 'scope', p_scope,
      'createdAt', current_timestamp, 'space', v_space, 'members', v_members,
      'entities', v_entities, 'acknowledgedOperations', v_operations);
  end if;
  raise exception 'privacy_invalid_scope' using errcode = '22023';
end;
$$;
revoke all on function public.privacy_export(uuid, text, uuid) from public, anon, authenticated;
grant execute on function public.privacy_export(uuid, text, uuid) to service_role;
