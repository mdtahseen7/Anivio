-- Landing area for watch history migrated in from the Luna MongoDB `test.watches` collection.
--
-- public.watch_progress rows are keyed on auth.users.id, which does not exist until the account is
-- created in the app. So the migrated rows sit here keyed on the AniList username instead, and are
-- claimed on first sign-in.

create table if not exists public.imported_watch_progress (
    id                bigserial primary key,
    -- The AniList username the Mongo document belonged to (`watches.userName`).
    anilist_username  text    not null,
    progress_key      text    not null,
    content_id        text    not null,
    content_type      text    not null default 'series',
    video_id          text    not null,
    season            integer,
    episode           integer,
    "position"        bigint  not null default 0,
    duration          bigint  not null default 0,
    last_watched      bigint  not null default 0,
    -- Kept for reference only; Anivio resolves episode stills itself via ani.zip and Kitsu.
    episode_title     text,
    episode_image     text,
    series_title      text,
    claimed_by        uuid references auth.users (id) on delete set null,
    claimed_at        timestamptz,
    unique (anilist_username, progress_key)
);

create index if not exists imported_watch_progress_username_idx
    on public.imported_watch_progress (anilist_username)
    where claimed_by is null;

-- Deliberately no SELECT policy for regular users: this table holds one row per episode for a named
-- AniList account, so it is only ever reachable through the claim function below.
alter table public.imported_watch_progress enable row level security;

/**
 * Copies the rows staged for [p_anilist_username] into the caller's own watch_progress, then marks
 * them claimed so a second call is a no-op.
 *
 * SECURITY DEFINER because the caller has no direct read on the staging table. The username is
 * supplied by the client rather than verified server-side, so this only claims history the caller
 * already knows the AniList handle for — acceptable for a personal migration, and the reason the
 * function refuses to run twice for the same rows.
 */
create or replace function public.claim_imported_watch_progress(
    p_anilist_username text,
    p_profile_id integer default 1
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user    uuid := auth.uid();
    v_claimed integer := 0;
begin
    if v_user is null then
        raise exception 'claim_imported_watch_progress requires an authenticated user';
    end if;
    if coalesce(p_anilist_username, '') = '' then
        return 0;
    end if;

    with pending as (
        select *
        from public.imported_watch_progress
        where anilist_username = p_anilist_username
          and claimed_by is null
        for update
    ),
    inserted as (
        insert into public.watch_progress as wp (
            user_id, profile_id, progress_key, content_id, content_type, video_id,
            season, episode, "position", duration, last_watched, updated_at
        )
        select v_user, p_profile_id, p.progress_key, p.content_id, p.content_type, p.video_id,
               p.season, p.episode, p."position", p.duration, p.last_watched, now()
        from pending p
        on conflict (user_id, profile_id, progress_key) do update
            set "position"   = excluded."position",
                duration     = excluded.duration,
                last_watched = excluded.last_watched,
                updated_at   = now()
            -- Never let imported history overwrite something watched more recently in the app.
            where excluded.last_watched > wp.last_watched
        returning 1
    ),
    marked as (
        update public.imported_watch_progress i
        set claimed_by = v_user,
            claimed_at = now()
        where i.anilist_username = p_anilist_username
          and i.claimed_by is null
        returning 1
    )
    select count(*) into v_claimed from marked;

    -- Seed the delta log so other devices pull the imported rows instead of only seeing them after
    -- a full refresh.
    insert into public.watch_progress_events (
        user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
        season, episode, "position", duration, last_watched, origin_client_id
    )
    select v_user, p_profile_id, 'upsert', wp.progress_key, wp.content_id, wp.content_type,
           wp.video_id, wp.season, wp.episode, wp."position", wp.duration, wp.last_watched,
           'mongo-import'
    from public.watch_progress wp
    where wp.user_id = v_user
      and wp.profile_id = p_profile_id;

    return v_claimed;
end;
$$;

grant execute on function public.claim_imported_watch_progress(text, integer) to authenticated;
