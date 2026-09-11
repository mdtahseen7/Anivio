-- Watch progress sync backend for Anivio.
--
-- Shapes are dictated by the existing client in
-- composeApp/src/commonMain/kotlin/com/nuvio/app/features/watching/sync/SupabaseProgressSyncAdapter.kt
-- Every returned column name must match that file's @SerialName values, and every function name and
-- parameter name must match its rpc() calls, or the client fails to decode.
--
-- "position" is quoted throughout because it collides with the SQL POSITION function.

create table if not exists public.watch_progress (
    user_id      uuid        not null references auth.users (id) on delete cascade,
    profile_id   integer     not null default 1,
    -- Client-side identity is buildWatchProgressKey(): "<contentId>_s<season>e<episode>", or just
    -- the content id for a movie. Primary key so a re-push updates rather than duplicates.
    progress_key text        not null,
    content_id   text        not null,
    content_type text        not null,
    video_id     text        not null,
    season       integer,
    episode      integer,
    "position"   bigint      not null default 0,
    duration     bigint      not null default 0,
    -- Epoch millis, matching WatchProgressEntry.lastUpdatedEpochMs. Drives last-write-wins.
    last_watched bigint      not null default 0,
    updated_at   timestamptz not null default now(),
    primary key (user_id, profile_id, progress_key)
);

create index if not exists watch_progress_last_watched_idx
    on public.watch_progress (user_id, profile_id, last_watched desc);

-- Append-only log backing the delta pull, so a client that has been offline replays only what
-- changed instead of refetching every row.
create table if not exists public.watch_progress_events (
    event_id         bigserial primary key,
    user_id          uuid    not null references auth.users (id) on delete cascade,
    profile_id       integer not null,
    operation        text    not null check (operation in ('upsert', 'delete')),
    progress_key     text    not null,
    content_id       text    not null default '',
    content_type     text    not null default '',
    video_id         text    not null default '',
    season           integer,
    episode          integer,
    "position"       bigint  not null default 0,
    duration         bigint  not null default 0,
    last_watched     bigint  not null default 0,
    -- Lets a device ignore echoes of its own writes.
    origin_client_id text,
    created_at       timestamptz not null default now()
);

create index if not exists watch_progress_events_cursor_idx
    on public.watch_progress_events (user_id, profile_id, event_id);

alter table public.watch_progress enable row level security;
alter table public.watch_progress_events enable row level security;

drop policy if exists watch_progress_owner on public.watch_progress;
create policy watch_progress_owner on public.watch_progress
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists watch_progress_events_owner on public.watch_progress_events;
create policy watch_progress_events_owner on public.watch_progress_events
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- All functions are SECURITY INVOKER so row level security still applies; auth.uid() is the only
-- thing that decides which rows are visible.

create or replace function public.sync_pull_watch_progress(
    p_profile_id integer,
    p_since_last_watched bigint default null,
    p_limit integer default null
)
returns table (
    content_id   text,
    content_type text,
    video_id     text,
    season       integer,
    episode      integer,
    "position"   bigint,
    duration     bigint,
    last_watched bigint,
    progress_key text
)
language sql
security invoker
stable
as $$
    select wp.content_id,
           wp.content_type,
           wp.video_id,
           wp.season,
           wp.episode,
           wp."position",
           wp.duration,
           wp.last_watched,
           wp.progress_key
    from public.watch_progress wp
    where wp.user_id = auth.uid()
      and wp.profile_id = p_profile_id
      and (p_since_last_watched is null or wp.last_watched > p_since_last_watched)
    order by wp.last_watched desc
    limit coalesce(p_limit, 2000);
$$;

create or replace function public.sync_get_watch_progress_delta_cursor(p_profile_id integer)
returns bigint
language sql
security invoker
stable
as $$
    select coalesce(max(event_id), 0)::bigint
    from public.watch_progress_events
    where user_id = auth.uid()
      and profile_id = p_profile_id;
$$;

create or replace function public.sync_pull_watch_progress_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id     bigint,
    operation    text,
    progress_key text,
    content_id   text,
    content_type text,
    video_id     text,
    season       integer,
    episode      integer,
    "position"   bigint,
    duration     bigint,
    last_watched bigint
)
language sql
security invoker
stable
as $$
    select e.event_id,
           e.operation,
           e.progress_key,
           e.content_id,
           e.content_type,
           e.video_id,
           e.season,
           e.episode,
           e."position",
           e.duration,
           e.last_watched
    from public.watch_progress_events e
    where e.user_id = auth.uid()
      and e.profile_id = p_profile_id
      and e.event_id > coalesce(p_since_event_id, 0)
    order by e.event_id
    limit coalesce(p_limit, 500);
$$;

create or replace function public.sync_push_watch_progress(
    p_profile_id integer,
    p_entries jsonb,
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
        raise exception 'sync_push_watch_progress requires an authenticated user';
    end if;
    if p_entries is null or jsonb_typeof(p_entries) <> 'array' then
        return;
    end if;

    with incoming as (
        select coalesce(nullif(x.progress_key, ''), x.content_id) as progress_key,
               x.content_id,
               x.content_type,
               x.video_id,
               x.season,
               x.episode,
               coalesce(x."position", 0)   as "position",
               coalesce(x.duration, 0)     as duration,
               coalesce(x.last_watched, 0) as last_watched
        from jsonb_to_recordset(p_entries) as x (
            content_id   text,
            content_type text,
            video_id     text,
            season       integer,
            episode      integer,
            "position"   bigint,
            duration     bigint,
            last_watched bigint,
            progress_key text
        )
        where coalesce(x.content_id, '') <> ''
    ),
    upserted as (
        insert into public.watch_progress as wp (
            user_id, profile_id, progress_key, content_id, content_type, video_id,
            season, episode, "position", duration, last_watched, updated_at
        )
        select v_user, p_profile_id, i.progress_key, i.content_id, i.content_type, i.video_id,
               i.season, i.episode, i."position", i.duration, i.last_watched, now()
        from incoming i
        on conflict (user_id, profile_id, progress_key) do update
            set content_id   = excluded.content_id,
                content_type = excluded.content_type,
                video_id     = excluded.video_id,
                season       = excluded.season,
                episode      = excluded.episode,
                "position"   = excluded."position",
                duration     = excluded.duration,
                last_watched = excluded.last_watched,
                updated_at   = now()
            -- Last write wins: an older device replaying stale progress must not rewind a newer
            -- position recorded elsewhere.
            where excluded.last_watched >= wp.last_watched
        returning wp.progress_key, wp.content_id, wp.content_type, wp.video_id,
                  wp.season, wp.episode, wp."position", wp.duration, wp.last_watched
    )
    insert into public.watch_progress_events (
        user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
        season, episode, "position", duration, last_watched, origin_client_id
    )
    select v_user, p_profile_id, 'upsert', u.progress_key, u.content_id, u.content_type, u.video_id,
           u.season, u.episode, u."position", u.duration, u.last_watched, p_origin_client_id
    from upserted u;
end;
$$;

create or replace function public.sync_delete_watch_progress(
    p_profile_id integer,
    p_keys jsonb,
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
        raise exception 'sync_delete_watch_progress requires an authenticated user';
    end if;
    if p_keys is null or jsonb_typeof(p_keys) <> 'array' then
        return;
    end if;

    with targets as (
        select value::text as progress_key
        from jsonb_array_elements_text(p_keys) as value
        where coalesce(value, '') <> ''
    ),
    removed as (
        delete from public.watch_progress wp
        using targets t
        where wp.user_id = v_user
          and wp.profile_id = p_profile_id
          and wp.progress_key = t.progress_key
        returning wp.progress_key, wp.content_id, wp.content_type, wp.video_id,
                  wp.season, wp.episode, wp.last_watched
    )
    insert into public.watch_progress_events (
        user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
        season, episode, last_watched, origin_client_id
    )
    select v_user, p_profile_id, 'delete', r.progress_key, r.content_id, r.content_type, r.video_id,
           r.season, r.episode, r.last_watched, p_origin_client_id
    from removed r;
end;
$$;

grant execute on function public.sync_pull_watch_progress(integer, bigint, integer) to authenticated;
grant execute on function public.sync_get_watch_progress_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watch_progress_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_watch_progress(integer, jsonb, text) to authenticated;
grant execute on function public.sync_delete_watch_progress(integer, jsonb, text) to authenticated;
