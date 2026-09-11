-- Profiles backend. Contract comes from
-- composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileRepository.kt and
-- ProfileModels.kt. This is what unblocks sign-up: AuthRepository creates the GoTrue user, then the
-- app immediately calls sync_pull_profiles and cannot present a usable session without it.
--
-- NuvioProfile declares `id`, `user_id`, `created_at` and `updated_at` as Kotlin String, so those
-- columns are cast to text on the way out rather than returned as uuid/timestamptz.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id                      uuid        primary key default gen_random_uuid(),
    user_id                 uuid        not null references auth.users (id) on delete cascade,
    profile_index           integer     not null check (profile_index between 1 and 6),
    name                    text        not null default '',
    avatar_color_hex        text        not null default '#1E88E5',
    avatar_id               text,
    avatar_url              text,
    profile_background_id   text,
    profile_background_url  text,
    uses_primary_addons     boolean     not null default false,
    uses_primary_plugins    boolean     not null default false,
    -- Stored as a bcrypt hash via pgcrypto; the plaintext PIN never lands in a column.
    pin_hash                text,
    pin_failed_attempts     integer     not null default 0,
    pin_locked_until        timestamptz,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    unique (user_id, profile_index)
);

alter table public.profiles enable row level security;

drop policy if exists profiles_owner on public.profiles;
create policy profiles_owner on public.profiles
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Every signed-in account gets profile 1 automatically. Without this the app signs in to an empty
-- profile list and the gate has nothing to select.
create or replace function public.ensure_default_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (user_id, profile_index, name, uses_primary_addons, uses_primary_plugins)
    values (new.id, 1, 'Profile 1', true, true)
    on conflict (user_id, profile_index) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
    after insert on auth.users
    for each row execute function public.ensure_default_profile();

create or replace function public.sync_pull_profiles()
returns table (
    id                     text,
    user_id                text,
    profile_index          integer,
    name                   text,
    avatar_color_hex       text,
    avatar_id              text,
    avatar_url             text,
    profile_background_id  text,
    profile_background_url text,
    uses_primary_addons    boolean,
    uses_primary_plugins   boolean,
    pin_enabled            boolean,
    pin_locked_until       text,
    created_at             text,
    updated_at             text
)
language sql
security invoker
stable
as $$
    select p.id::text,
           p.user_id::text,
           p.profile_index,
           p.name,
           p.avatar_color_hex,
           p.avatar_id,
           p.avatar_url,
           p.profile_background_id,
           p.profile_background_url,
           p.uses_primary_addons,
           p.uses_primary_plugins,
           (p.pin_hash is not null) as pin_enabled,
           case when p.pin_locked_until is null then null else p.pin_locked_until::text end,
           p.created_at::text,
           p.updated_at::text
    from public.profiles p
    where p.user_id = auth.uid()
    order by p.profile_index;
$$;

create or replace function public.sync_pull_profile_locks()
returns table (
    profile_index    integer,
    pin_enabled      boolean,
    pin_locked_until text
)
language sql
security invoker
stable
as $$
    select p.profile_index,
           (p.pin_hash is not null) as pin_enabled,
           case when p.pin_locked_until is null then null else p.pin_locked_until::text end
    from public.profiles p
    where p.user_id = auth.uid()
    order by p.profile_index;
$$;

create or replace function public.sync_push_profiles(
    p_client_max_profiles integer,
    p_profiles jsonb,
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
        raise exception 'sync_push_profiles requires an authenticated user';
    end if;
    if p_profiles is null or jsonb_typeof(p_profiles) <> 'array' then
        return;
    end if;

    insert into public.profiles as pr (
        user_id, profile_index, name, avatar_color_hex, avatar_id, avatar_url,
        profile_background_id, profile_background_url, uses_primary_addons, uses_primary_plugins,
        updated_at
    )
    select v_user,
           x.profile_index,
           coalesce(x.name, ''),
           coalesce(x.avatar_color_hex, '#1E88E5'),
           x.avatar_id,
           x.avatar_url,
           x.profile_background_id,
           x.profile_background_url,
           coalesce(x.uses_primary_addons, false),
           coalesce(x.uses_primary_plugins, false),
           now()
    from jsonb_to_recordset(p_profiles) as x (
        profile_index          integer,
        name                   text,
        avatar_color_hex       text,
        uses_primary_addons    boolean,
        uses_primary_plugins   boolean,
        avatar_id              text,
        avatar_url             text,
        profile_background_id  text,
        profile_background_url text
    )
    -- Honour the client's own cap so a tampered payload cannot create unlimited profiles.
    where x.profile_index is not null
      and x.profile_index between 1 and least(coalesce(p_client_max_profiles, 6), 6)
    on conflict (user_id, profile_index) do update
        set name                   = excluded.name,
            avatar_color_hex       = excluded.avatar_color_hex,
            avatar_id              = excluded.avatar_id,
            avatar_url             = excluded.avatar_url,
            profile_background_id  = excluded.profile_background_id,
            profile_background_url = excluded.profile_background_url,
            uses_primary_addons    = excluded.uses_primary_addons,
            uses_primary_plugins   = excluded.uses_primary_plugins,
            updated_at             = now();
end;
$$;

create or replace function public.sync_delete_profile_data(
    p_profile_id integer,
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
        raise exception 'sync_delete_profile_data requires an authenticated user';
    end if;

    delete from public.watch_progress
    where user_id = v_user and profile_id = p_profile_id;
    delete from public.watch_progress_events
    where user_id = v_user and profile_id = p_profile_id;

    -- Profile 1 is the account's primary; wipe its data but keep the row so the gate always has a
    -- profile to land on.
    if p_profile_id = 1 then
        return;
    end if;

    delete from public.profiles
    where user_id = v_user and profile_index = p_profile_id;
end;
$$;

create or replace function public.verify_profile_pin(
    p_profile_id integer,
    p_pin text
)
returns table (
    unlocked            boolean,
    retry_after_seconds integer,
    message             text
)
language plpgsql
security invoker
as $$
declare
    v_user   uuid := auth.uid();
    v_row    public.profiles;
    v_locked integer;
begin
    select * into v_row
    from public.profiles
    where user_id = v_user and profile_index = p_profile_id;

    if not found then
        return query select false, 0, 'Profile not found';
        return;
    end if;
    if v_row.pin_hash is null then
        return query select true, 0, null::text;
        return;
    end if;
    if v_row.pin_locked_until is not null and v_row.pin_locked_until > now() then
        v_locked := ceil(extract(epoch from (v_row.pin_locked_until - now())))::integer;
        return query select false, v_locked, 'Too many attempts';
        return;
    end if;

    if v_row.pin_hash = crypt(p_pin, v_row.pin_hash) then
        update public.profiles
        set pin_failed_attempts = 0, pin_locked_until = null, updated_at = now()
        where id = v_row.id;
        return query select true, 0, null::text;
    else
        -- Five strikes, then a one minute lockout. Cheap brute-force resistance for a 4 digit PIN.
        update public.profiles
        set pin_failed_attempts = v_row.pin_failed_attempts + 1,
            pin_locked_until = case
                when v_row.pin_failed_attempts + 1 >= 5 then now() + interval '1 minute'
                else null
            end,
            updated_at = now()
        where id = v_row.id;
        return query select false, 0, 'Incorrect PIN';
    end if;
end;
$$;

create or replace function public.set_profile_pin(
    p_profile_id integer,
    p_pin text,
    p_current_pin text default null
)
returns void
language plpgsql
security invoker
as $$
declare
    v_user uuid := auth.uid();
    v_row  public.profiles;
begin
    select * into v_row
    from public.profiles
    where user_id = v_user and profile_index = p_profile_id;

    if not found then
        raise exception 'Profile not found';
    end if;
    if v_row.pin_hash is not null then
        if p_current_pin is null or v_row.pin_hash <> crypt(p_current_pin, v_row.pin_hash) then
            raise exception 'Current PIN required';
        end if;
    end if;

    update public.profiles
    set pin_hash = crypt(p_pin, gen_salt('bf')),
        pin_failed_attempts = 0,
        pin_locked_until = null,
        updated_at = now()
    where id = v_row.id;
end;
$$;

create or replace function public.clear_profile_pin(
    p_profile_id integer,
    p_current_pin text default null
)
returns void
language plpgsql
security invoker
as $$
declare
    v_user uuid := auth.uid();
    v_row  public.profiles;
begin
    select * into v_row
    from public.profiles
    where user_id = v_user and profile_index = p_profile_id;

    if not found then
        raise exception 'Profile not found';
    end if;
    if v_row.pin_hash is not null then
        if p_current_pin is null or v_row.pin_hash <> crypt(p_current_pin, v_row.pin_hash) then
            raise exception 'Current PIN required';
        end if;
    end if;

    update public.profiles
    set pin_hash = null, pin_failed_attempts = 0, pin_locked_until = null, updated_at = now()
    where id = v_row.id;
end;
$$;

/**
 * Escape hatch for a forgotten PIN: clears it after re-checking the account password.
 *
 * SECURITY DEFINER because verifying the password means reading auth.users.encrypted_password,
 * which the authenticated role cannot select. Scoped strictly to auth.uid()'s own row.
 */
create or replace function public.clear_profile_pin_with_account_password(
    p_account_password text,
    p_profile_id integer
)
returns void
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
    v_user uuid := auth.uid();
    v_hash text;
begin
    if v_user is null then
        raise exception 'clear_profile_pin_with_account_password requires an authenticated user';
    end if;

    select encrypted_password into v_hash from auth.users where id = v_user;
    if v_hash is null or v_hash <> crypt(p_account_password, v_hash) then
        raise exception 'Incorrect account password';
    end if;

    update public.profiles
    set pin_hash = null, pin_failed_attempts = 0, pin_locked_until = null, updated_at = now()
    where user_id = v_user and profile_index = p_profile_id;
end;
$$;

grant execute on function public.sync_pull_profiles() to authenticated;
grant execute on function public.sync_pull_profile_locks() to authenticated;
grant execute on function public.sync_push_profiles(integer, jsonb, text) to authenticated;
grant execute on function public.sync_delete_profile_data(integer, text) to authenticated;
grant execute on function public.verify_profile_pin(integer, text) to authenticated;
grant execute on function public.set_profile_pin(integer, text, text) to authenticated;
grant execute on function public.clear_profile_pin(integer, text) to authenticated;
grant execute on function public.clear_profile_pin_with_account_password(text, integer) to authenticated;
