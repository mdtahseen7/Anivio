-- Watched items and library sync.
--
-- These are why switching the watch progress source to "On this device" reported "changed but failed
-- to refresh", and why Continue Watching cards appeared then vanished: the local source refresh pulls
-- watched items and library alongside progress, both RPCs were missing, so the refresh reported
-- failure and `hasLoadedRemoteItems` flapped, which drops cached Next Up cards on each recomposition.
--
-- Contracts from SupabaseWatchedSyncAdapter.kt and SupabaseLibrarySyncAdapter.kt.

-- =============================================================================================
-- Watched items
-- =============================================================================================

create table if not exists public.watched_items (
    user_id      uuid    not null references auth.users (id) on delete cascade,
    profile_id   integer not null,
    content_id   text    not null,
    content_type text    not null,
    title        text    not null default '',
    -- Nulls are meaningful: a movie has no season/episode. Coalesced in the key below because
    -- Postgres unique constraints treat NULL as distinct, which would allow duplicate movie rows.
    season       integer,
    episode      integer,
    watched_at   bigint  not null default 0,
    updated_at   timestamptz not null default now(),
    id           bigserial primary key
);

-- Expression index rather than a table-level primary key: Postgres rejects expressions in a PK
-- constraint, and the coalesce is required because NULL season/episode (movies) would otherwise be
-- treated as distinct and allow duplicate rows. ON CONFLICT infers against this index.
create unique index if not exists watched_items_identity_idx
    on public.watched_items (user_id, profile_id, content_id, coalesce(season, -1), coalesce(episode, -1));

create index if not exists watched_items_profile_idx
    on public.watched_items (user_id, profile_id, watched_at desc);

create table if not exists public.watched_items_events (
    event_id         bigserial primary key,
    user_id          uuid    not null references auth.users (id) on delete cascade,
    profile_id       integer not null,
    operation        text    not null check (operation in ('upsert', 'delete')),
    content_id       text    not null,
    content_type     text    not null default '',
    title            text    not null default '',
    season           integer,
    episode          integer,
    watched_at       bigint  not null default 0,
    origin_client_id text,
    created_at       timestamptz not null default now()
);

create index if not exists watched_items_events_cursor_idx
    on public.watched_items_events (user_id, profile_id, event_id);

alter table public.watched_items enable row level security;
alter table public.watched_items_events enable row level security;

drop policy if exists watched_items_owner on public.watched_items;
create policy watched_items_owner on public.watched_items
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists watched_items_events_owner on public.watched_items_events;
create policy watched_items_events_owner on public.watched_items_events
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create or replace function public.sync_pull_watched_items(
    p_profile_id integer,
    p_page integer default 1,
    p_page_size integer default 500
)
returns table (
    content_id   text,
    content_type text,
    title        text,
    season       integer,
    episode      integer,
    watched_at   bigint
)
language sql
security invoker
stable
as $$
    select w.content_id, w.content_type, w.title, w.season, w.episode, w.watched_at
    from public.watched_items w
    where w.user_id = auth.uid()
      and w.profile_id = p_profile_id
    -- Deterministic order, otherwise the client's page loop can miss or repeat rows.
    order by w.watched_at desc, w.content_id, coalesce(w.season, -1), coalesce(w.episode, -1)
    limit greatest(coalesce(p_page_size, 500), 1)
    offset (greatest(coalesce(p_page, 1), 1) - 1) * greatest(coalesce(p_page_size, 500), 1);
$$;

create or replace function public.sync_get_watched_items_delta_cursor(p_profile_id integer)
returns bigint
language sql
security invoker
stable
as $$
    select coalesce(max(event_id), 0)::bigint
    from public.watched_items_events
    where user_id = auth.uid() and profile_id = p_profile_id;
$$;

create or replace function public.sync_pull_watched_items_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id     bigint,
    operation    text,
    content_id   text,
    content_type text,
    title        text,
    season       integer,
    episode      integer,
    watched_at   bigint
)
language sql
security invoker
stable
as $$
    select e.event_id, e.operation, e.content_id, e.content_type, e.title,
           e.season, e.episode, e.watched_at
    from public.watched_items_events e
    where e.user_id = auth.uid()
      and e.profile_id = p_profile_id
      and e.event_id > coalesce(p_since_event_id, 0)
    order by e.event_id
    limit coalesce(p_limit, 500);
$$;

create or replace function public.sync_push_watched_items(
    p_profile_id integer,
    p_items jsonb,
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
        raise exception 'sync_push_watched_items requires an authenticated user';
    end if;
    if p_items is null or jsonb_typeof(p_items) <> 'array' then
        return;
    end if;

    insert into public.watched_items as w (
        user_id, profile_id, content_id, content_type, title, season, episode, watched_at, updated_at
    )
    select v_user, p_profile_id, x.content_id, coalesce(x.content_type, ''), coalesce(x.title, ''),
           x.season, x.episode, coalesce(x.watched_at, 0), now()
    from jsonb_to_recordset(p_items) as x (
        content_id   text,
        content_type text,
        title        text,
        season       integer,
        episode      integer,
        watched_at   bigint
    )
    where coalesce(x.content_id, '') <> ''
    on conflict (user_id, profile_id, content_id, coalesce(season, -1), coalesce(episode, -1))
        do update set content_type = excluded.content_type,
                      title        = excluded.title,
                      watched_at   = greatest(excluded.watched_at, w.watched_at),
                      updated_at   = now();

    insert into public.watched_items_events (
        user_id, profile_id, operation, content_id, content_type, title, season, episode,
        watched_at, origin_client_id
    )
    select v_user, p_profile_id, 'upsert', x.content_id, coalesce(x.content_type, ''),
           coalesce(x.title, ''), x.season, x.episode, coalesce(x.watched_at, 0), p_origin_client_id
    from jsonb_to_recordset(p_items) as x (
        content_id   text,
        content_type text,
        title        text,
        season       integer,
        episode      integer,
        watched_at   bigint
    )
    where coalesce(x.content_id, '') <> '';
end;
$$;

create or replace function public.sync_delete_watched_items(
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
        raise exception 'sync_delete_watched_items requires an authenticated user';
    end if;
    if p_keys is null or jsonb_typeof(p_keys) <> 'array' then
        return;
    end if;

    create temporary table if not exists tmp_watched_delete (
        content_id text,
        season     integer,
        episode    integer
    ) on commit drop;
    delete from tmp_watched_delete;

    insert into tmp_watched_delete (content_id, season, episode)
    select x.content_id, x.season, x.episode
    from jsonb_to_recordset(p_keys) as x (content_id text, season integer, episode integer)
    where coalesce(x.content_id, '') <> '';

    insert into public.watched_items_events (
        user_id, profile_id, operation, content_id, content_type, title, season, episode,
        watched_at, origin_client_id
    )
    select v_user, p_profile_id, 'delete', w.content_id, w.content_type, w.title,
           w.season, w.episode, w.watched_at, p_origin_client_id
    from public.watched_items w
    join tmp_watched_delete t
      on t.content_id = w.content_id
     and coalesce(t.season, -1) = coalesce(w.season, -1)
     and coalesce(t.episode, -1) = coalesce(w.episode, -1)
    where w.user_id = v_user and w.profile_id = p_profile_id;

    delete from public.watched_items w
    using tmp_watched_delete t
    where w.user_id = v_user
      and w.profile_id = p_profile_id
      and w.content_id = t.content_id
      and coalesce(w.season, -1) = coalesce(t.season, -1)
      and coalesce(w.episode, -1) = coalesce(t.episode, -1);
end;
$$;

-- =============================================================================================
-- Library
-- =============================================================================================

create table if not exists public.library_items (
    user_id       uuid    not null references auth.users (id) on delete cascade,
    profile_id    integer not null,
    content_id    text    not null,
    content_type  text    not null,
    name          text    not null default '',
    poster        text,
    poster_shape  text    not null default 'POSTER',
    background    text,
    description   text,
    release_info  text,
    -- LibrarySyncItem declares imdbRating as Float?, so real rather than numeric.
    imdb_rating   real,
    genres        jsonb   not null default '[]'::jsonb,
    addon_base_url text,
    added_at      bigint  not null default 0,
    updated_at    timestamptz not null default now(),
    primary key (user_id, profile_id, content_id)
);

create index if not exists library_items_profile_idx
    on public.library_items (user_id, profile_id, added_at desc);

create table if not exists public.library_items_events (
    event_id         bigserial primary key,
    user_id          uuid    not null references auth.users (id) on delete cascade,
    profile_id       integer not null,
    operation        text    not null check (operation in ('upsert', 'delete')),
    content_id       text    not null,
    content_type     text    not null default '',
    name             text    not null default '',
    poster           text,
    poster_shape     text    not null default 'POSTER',
    background       text,
    description      text,
    release_info     text,
    imdb_rating      real,
    genres           jsonb   not null default '[]'::jsonb,
    addon_base_url   text,
    added_at         bigint  not null default 0,
    origin_client_id text,
    created_at       timestamptz not null default now()
);

create index if not exists library_items_events_cursor_idx
    on public.library_items_events (user_id, profile_id, event_id);

alter table public.library_items enable row level security;
alter table public.library_items_events enable row level security;

drop policy if exists library_items_owner on public.library_items;
create policy library_items_owner on public.library_items
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists library_items_events_owner on public.library_items_events;
create policy library_items_events_owner on public.library_items_events
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create or replace function public.sync_pull_library(
    p_profile_id integer,
    p_limit integer default 500,
    p_offset integer default 0
)
returns table (
    content_id     text,
    content_type   text,
    name           text,
    poster         text,
    poster_shape   text,
    background     text,
    description    text,
    release_info   text,
    imdb_rating    real,
    genres         jsonb,
    addon_base_url text,
    added_at       bigint
)
language sql
security invoker
stable
as $$
    select l.content_id, l.content_type, l.name, l.poster, l.poster_shape, l.background,
           l.description, l.release_info, l.imdb_rating, l.genres, l.addon_base_url, l.added_at
    from public.library_items l
    where l.user_id = auth.uid()
      and l.profile_id = p_profile_id
    order by l.added_at desc, l.content_id
    limit greatest(coalesce(p_limit, 500), 1)
    offset greatest(coalesce(p_offset, 0), 0);
$$;

create or replace function public.sync_get_library_delta_cursor(p_profile_id integer)
returns bigint
language sql
security invoker
stable
as $$
    select coalesce(max(event_id), 0)::bigint
    from public.library_items_events
    where user_id = auth.uid() and profile_id = p_profile_id;
$$;

create or replace function public.sync_pull_library_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id       bigint,
    operation      text,
    content_id     text,
    content_type   text,
    name           text,
    poster         text,
    poster_shape   text,
    background     text,
    description    text,
    release_info   text,
    imdb_rating    real,
    genres         jsonb,
    addon_base_url text,
    added_at       bigint
)
language sql
security invoker
stable
as $$
    select e.event_id, e.operation, e.content_id, e.content_type, e.name, e.poster, e.poster_shape,
           e.background, e.description, e.release_info, e.imdb_rating, e.genres, e.addon_base_url,
           e.added_at
    from public.library_items_events e
    where e.user_id = auth.uid()
      and e.profile_id = p_profile_id
      and e.event_id > coalesce(p_since_event_id, 0)
    order by e.event_id
    limit coalesce(p_limit, 500);
$$;

create or replace function public.sync_push_library_items(
    p_profile_id integer,
    p_items jsonb,
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
        raise exception 'sync_push_library_items requires an authenticated user';
    end if;
    if p_items is null or jsonb_typeof(p_items) <> 'array' then
        return;
    end if;

    insert into public.library_items as l (
        user_id, profile_id, content_id, content_type, name, poster, poster_shape, background,
        description, release_info, imdb_rating, genres, addon_base_url, added_at, updated_at
    )
    select v_user, p_profile_id, x.content_id, coalesce(x.content_type, ''), coalesce(x.name, ''),
           x.poster, coalesce(x.poster_shape, 'POSTER'), x.background, x.description,
           x.release_info, x.imdb_rating, coalesce(x.genres, '[]'::jsonb), x.addon_base_url,
           coalesce(x.added_at, 0), now()
    from jsonb_to_recordset(p_items) as x (
        content_id     text,
        content_type   text,
        name           text,
        poster         text,
        poster_shape   text,
        background     text,
        description    text,
        release_info   text,
        imdb_rating    real,
        genres         jsonb,
        addon_base_url text,
        added_at       bigint
    )
    where coalesce(x.content_id, '') <> ''
    on conflict (user_id, profile_id, content_id) do update
        set content_type   = excluded.content_type,
            name           = excluded.name,
            poster         = excluded.poster,
            poster_shape   = excluded.poster_shape,
            background     = excluded.background,
            description    = excluded.description,
            release_info   = excluded.release_info,
            imdb_rating    = excluded.imdb_rating,
            genres         = excluded.genres,
            addon_base_url = excluded.addon_base_url,
            -- Keep the earliest save time: re-syncing must not reorder the library.
            added_at       = least(nullif(excluded.added_at, 0), nullif(l.added_at, 0)),
            updated_at     = now();

    insert into public.library_items_events (
        user_id, profile_id, operation, content_id, content_type, name, poster, poster_shape,
        background, description, release_info, imdb_rating, genres, addon_base_url, added_at,
        origin_client_id
    )
    select v_user, p_profile_id, 'upsert', x.content_id, coalesce(x.content_type, ''),
           coalesce(x.name, ''), x.poster, coalesce(x.poster_shape, 'POSTER'), x.background,
           x.description, x.release_info, x.imdb_rating, coalesce(x.genres, '[]'::jsonb),
           x.addon_base_url, coalesce(x.added_at, 0), p_origin_client_id
    from jsonb_to_recordset(p_items) as x (
        content_id     text,
        content_type   text,
        name           text,
        poster         text,
        poster_shape   text,
        background     text,
        description    text,
        release_info   text,
        imdb_rating    real,
        genres         jsonb,
        addon_base_url text,
        added_at       bigint
    )
    where coalesce(x.content_id, '') <> '';
end;
$$;

create or replace function public.sync_delete_library_items(
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
        raise exception 'sync_delete_library_items requires an authenticated user';
    end if;
    if p_keys is null or jsonb_typeof(p_keys) <> 'array' then
        return;
    end if;

    insert into public.library_items_events (
        user_id, profile_id, operation, content_id, content_type, name, poster, poster_shape,
        background, description, release_info, imdb_rating, genres, addon_base_url, added_at,
        origin_client_id
    )
    select v_user, p_profile_id, 'delete', l.content_id, l.content_type, l.name, l.poster,
           l.poster_shape, l.background, l.description, l.release_info, l.imdb_rating, l.genres,
           l.addon_base_url, l.added_at, p_origin_client_id
    from public.library_items l
    where l.user_id = v_user
      and l.profile_id = p_profile_id
      and l.content_id in (select jsonb_array_elements_text(p_keys));

    delete from public.library_items l
    where l.user_id = v_user
      and l.profile_id = p_profile_id
      and l.content_id in (select jsonb_array_elements_text(p_keys));
end;
$$;

grant execute on function public.sync_pull_watched_items(integer, integer, integer) to authenticated;
grant execute on function public.sync_get_watched_items_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watched_items_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_watched_items(integer, jsonb, text) to authenticated;
grant execute on function public.sync_delete_watched_items(integer, jsonb, text) to authenticated;
grant execute on function public.sync_pull_library(integer, integer, integer) to authenticated;
grant execute on function public.sync_get_library_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_library_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_library_items(integer, jsonb, text) to authenticated;
grant execute on function public.sync_delete_library_items(integer, jsonb, text) to authenticated;
