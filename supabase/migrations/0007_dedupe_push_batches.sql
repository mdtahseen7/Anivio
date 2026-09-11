-- Deduplicates incoming batches in every push function.
--
-- Postgres raises "ON CONFLICT DO UPDATE command cannot affect row a second time" (SQLSTATE 21000)
-- when one INSERT proposes two rows with the same conflict key. The client batches freely and can
-- easily include the same title twice — two code paths marking the same episode watched, a rewatch
-- and a resume in one flush — and the whole push would fail, not just the duplicate.
--
-- Each function now picks one row per identity with DISTINCT ON, keeping the freshest timestamp.

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

    with parsed as (
        select coalesce(nullif(x.progress_key, ''), x.content_id) as progress_key,
               x.content_id,
               coalesce(x.content_type, '')  as content_type,
               coalesce(x.video_id, '')      as video_id,
               x.season,
               x.episode,
               coalesce(x."position", 0)     as "position",
               coalesce(x.duration, 0)       as duration,
               coalesce(x.last_watched, 0)   as last_watched
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
    incoming as (
        select distinct on (progress_key) *
        from parsed
        order by progress_key, last_watched desc
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

    with parsed as (
        select x.content_id,
               coalesce(x.content_type, '') as content_type,
               coalesce(x.title, '')        as title,
               x.season,
               x.episode,
               coalesce(x.watched_at, 0)    as watched_at
        from jsonb_to_recordset(p_items) as x (
            content_id   text,
            content_type text,
            title        text,
            season       integer,
            episode      integer,
            watched_at   bigint
        )
        where coalesce(x.content_id, '') <> ''
    ),
    incoming as (
        select distinct on (content_id, coalesce(season, -1), coalesce(episode, -1)) *
        from parsed
        order by content_id, coalesce(season, -1), coalesce(episode, -1), watched_at desc
    ),
    upserted as (
        insert into public.watched_items as w (
            user_id, profile_id, content_id, content_type, title, season, episode,
            watched_at, updated_at
        )
        select v_user, p_profile_id, i.content_id, i.content_type, i.title, i.season, i.episode,
               i.watched_at, now()
        from incoming i
        on conflict (user_id, profile_id, content_id, coalesce(season, -1), coalesce(episode, -1))
            do update set content_type = excluded.content_type,
                          title        = excluded.title,
                          watched_at   = greatest(excluded.watched_at, w.watched_at),
                          updated_at   = now()
        returning w.content_id, w.content_type, w.title, w.season, w.episode, w.watched_at
    )
    insert into public.watched_items_events (
        user_id, profile_id, operation, content_id, content_type, title, season, episode,
        watched_at, origin_client_id
    )
    select v_user, p_profile_id, 'upsert', u.content_id, u.content_type, u.title,
           u.season, u.episode, u.watched_at, p_origin_client_id
    from upserted u;
end;
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

    with parsed as (
        select x.content_id,
               coalesce(x.content_type, '')      as content_type,
               coalesce(x.name, '')              as name,
               x.poster,
               coalesce(x.poster_shape, 'POSTER') as poster_shape,
               x.background,
               x.description,
               x.release_info,
               x.imdb_rating,
               coalesce(x.genres, '[]'::jsonb)   as genres,
               x.addon_base_url,
               coalesce(x.added_at, 0)           as added_at
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
    ),
    incoming as (
        select distinct on (content_id) *
        from parsed
        order by content_id, added_at desc
    ),
    upserted as (
        insert into public.library_items as l (
            user_id, profile_id, content_id, content_type, name, poster, poster_shape, background,
            description, release_info, imdb_rating, genres, addon_base_url, added_at, updated_at
        )
        select v_user, p_profile_id, i.content_id, i.content_type, i.name, i.poster, i.poster_shape,
               i.background, i.description, i.release_info, i.imdb_rating, i.genres,
               i.addon_base_url, i.added_at, now()
        from incoming i
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
                -- Keep the earliest non-zero save time so re-syncing never reorders the library.
                added_at       = coalesce(
                                     least(nullif(excluded.added_at, 0), nullif(l.added_at, 0)),
                                     nullif(excluded.added_at, 0),
                                     l.added_at
                                 ),
                updated_at     = now()
        returning l.content_id, l.content_type, l.name, l.poster, l.poster_shape, l.background,
                  l.description, l.release_info, l.imdb_rating, l.genres, l.addon_base_url, l.added_at
    )
    insert into public.library_items_events (
        user_id, profile_id, operation, content_id, content_type, name, poster, poster_shape,
        background, description, release_info, imdb_rating, genres, addon_base_url, added_at,
        origin_client_id
    )
    select v_user, p_profile_id, 'upsert', u.content_id, u.content_type, u.name, u.poster,
           u.poster_shape, u.background, u.description, u.release_info, u.imdb_rating, u.genres,
           u.addon_base_url, u.added_at, p_origin_client_id
    from upserted u;
end;
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

    with parsed as (
        select x.profile_index,
               coalesce(x.name, '')                    as name,
               coalesce(x.avatar_color_hex, '#1E88E5') as avatar_color_hex,
               x.avatar_id,
               x.avatar_url,
               x.profile_background_id,
               x.profile_background_url,
               coalesce(x.uses_primary_addons, false)  as uses_primary_addons,
               coalesce(x.uses_primary_plugins, false) as uses_primary_plugins,
               row_number() over () as ordinal
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
        where x.profile_index is not null
          and x.profile_index between 1 and least(coalesce(p_client_max_profiles, 6), 6)
    ),
    incoming as (
        -- Last occurrence wins, which is what a client sending a corrected duplicate expects.
        select distinct on (profile_index) *
        from parsed
        order by profile_index, ordinal desc
    )
    insert into public.profiles as pr (
        user_id, profile_index, name, avatar_color_hex, avatar_id, avatar_url,
        profile_background_id, profile_background_url, uses_primary_addons, uses_primary_plugins,
        updated_at
    )
    select v_user, i.profile_index, i.name, i.avatar_color_hex, i.avatar_id, i.avatar_url,
           i.profile_background_id, i.profile_background_url, i.uses_primary_addons,
           i.uses_primary_plugins, now()
    from incoming i
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
