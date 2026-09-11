-- Per-profile settings blobs.
--
-- Two separate blob namespaces keyed by (profile, platform):
--   * sync_*_profile_settings_blob   from ProfileSettingsSync.kt      (platform "mobile")
--   * sync_*_home_catalog_settings   from HomeCatalogSettingsSyncService.kt (a shared platform key)
--
-- Both store an opaque JSON document the client owns; the server never interprets it, which is why
-- one table with a platform discriminator covers both rather than modelling every settings field.

create table if not exists public.profile_settings_blobs (
    user_id       uuid    not null references auth.users (id) on delete cascade,
    profile_id    integer not null,
    platform      text    not null,
    settings_json jsonb   not null default '{}'::jsonb,
    origin_client_id text,
    updated_at    timestamptz not null default now(),
    primary key (user_id, profile_id, platform)
);

alter table public.profile_settings_blobs enable row level security;

drop policy if exists profile_settings_blobs_owner on public.profile_settings_blobs;
create policy profile_settings_blobs_owner on public.profile_settings_blobs
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- SettingsBlobResponse expects profile_id, settings_json and a nullable updated_at string.
create or replace function public.sync_pull_profile_settings_blob(
    p_profile_id integer,
    p_platform text
)
returns table (
    profile_id    integer,
    settings_json jsonb,
    updated_at    text
)
language sql
security invoker
stable
as $$
    select b.profile_id, b.settings_json, b.updated_at::text
    from public.profile_settings_blobs b
    where b.user_id = auth.uid()
      and b.profile_id = p_profile_id
      and b.platform = p_platform;
$$;

create or replace function public.sync_push_profile_settings_blob(
    p_profile_id integer,
    p_platform text,
    p_settings_json jsonb,
    p_origin_client_id text default null
)
returns void
language plpgsql
security invoker
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null then
        raise exception 'sync_push_profile_settings_blob requires an authenticated user';
    end if;

    insert into public.profile_settings_blobs (
        user_id, profile_id, platform, settings_json, origin_client_id, updated_at
    )
    values (
        v_user, p_profile_id, p_platform, coalesce(p_settings_json, '{}'::jsonb),
        p_origin_client_id, now()
    )
    on conflict (user_id, profile_id, platform) do update
        set settings_json    = excluded.settings_json,
            origin_client_id = excluded.origin_client_id,
            updated_at       = now();
end;
$$;

-- SupabaseHomeCatalogSettingsBlob expects only profile_id and settings_json.
create or replace function public.sync_pull_home_catalog_settings(
    p_profile_id integer,
    p_platform text
)
returns table (
    profile_id    integer,
    settings_json jsonb
)
language sql
security invoker
stable
as $$
    select b.profile_id, b.settings_json
    from public.profile_settings_blobs b
    where b.user_id = auth.uid()
      and b.profile_id = p_profile_id
      and b.platform = p_platform;
$$;

create or replace function public.sync_push_home_catalog_settings(
    p_profile_id integer,
    p_platform text,
    p_settings_json jsonb,
    p_origin_client_id text default null
)
returns void
language plpgsql
security invoker
as $$
begin
    perform public.sync_push_profile_settings_blob(
        p_profile_id, p_platform, p_settings_json, p_origin_client_id
    );
end;
$$;

/**
 * Device registry. The client calls this on launch and ignores the result, so it exists mainly to
 * stop a PGRST202 on every cold start.
 */
create table if not exists public.registered_devices (
    user_id         uuid not null references auth.users (id) on delete cascade,
    installation_id text not null,
    device_name     text,
    platform        text,
    client_name     text,
    client_version  text,
    last_seen_at    timestamptz not null default now(),
    primary key (user_id, installation_id)
);

alter table public.registered_devices enable row level security;

drop policy if exists registered_devices_owner on public.registered_devices;
create policy registered_devices_owner on public.registered_devices
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create or replace function public.register_current_device(
    p_client_name text,
    p_client_version text,
    p_device_name text,
    p_installation_id text,
    p_platform text
)
returns void
language plpgsql
security invoker
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null or coalesce(p_installation_id, '') = '' then
        return;
    end if;

    insert into public.registered_devices (
        user_id, installation_id, device_name, platform, client_name, client_version, last_seen_at
    )
    values (v_user, p_installation_id, p_device_name, p_platform, p_client_name, p_client_version, now())
    on conflict (user_id, installation_id) do update
        set device_name    = excluded.device_name,
            platform       = excluded.platform,
            client_name    = excluded.client_name,
            client_version = excluded.client_version,
            last_seen_at   = now();
end;
$$;

grant execute on function public.sync_pull_profile_settings_blob(integer, text) to authenticated;
grant execute on function public.sync_push_profile_settings_blob(integer, text, jsonb, text) to authenticated;
grant execute on function public.sync_pull_home_catalog_settings(integer, text) to authenticated;
grant execute on function public.sync_push_home_catalog_settings(integer, text, jsonb, text) to authenticated;
grant execute on function public.register_current_device(text, text, text, text, text) to authenticated;
