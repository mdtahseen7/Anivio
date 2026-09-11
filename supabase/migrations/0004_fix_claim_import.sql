-- Replaces claim_imported_watch_progress.
--
-- The first version chained three CTEs: a `pending ... for update` select, an INSERT reading from
-- it, and an UPDATE marking rows claimed. The UPDATE ran (22 rows marked) but the INSERT read zero
-- rows from the locking CTE, so history was marked as claimed without ever being copied — silent
-- data loss on a one-shot migration.
--
-- Sequential statements inside plpgsql each see the previous statement's effects, so ordering is
-- explicit: copy first, mark second, log third.

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

    -- 1. Copy staged history into the live table.
    insert into public.watch_progress as wp (
        user_id, profile_id, progress_key, content_id, content_type, video_id,
        season, episode, "position", duration, last_watched, updated_at
    )
    select v_user, p_profile_id, i.progress_key, i.content_id, i.content_type, i.video_id,
           i.season, i.episode, i."position", i.duration, i.last_watched, now()
    from public.imported_watch_progress i
    where i.anilist_username = p_anilist_username
      and i.claimed_by is null
    on conflict (user_id, profile_id, progress_key) do update
        set "position"   = excluded."position",
            duration     = excluded.duration,
            last_watched = excluded.last_watched,
            updated_at   = now()
        -- Imported history must never overwrite something watched more recently in the app.
        where excluded.last_watched > wp.last_watched;

    -- 2. Mark them claimed so a second call is a no-op.
    update public.imported_watch_progress
    set claimed_by = v_user,
        claimed_at = now()
    where anilist_username = p_anilist_username
      and claimed_by is null;
    get diagnostics v_claimed = row_count;

    -- 3. Seed the delta log for just those rows, so other devices pull them without a full refresh.
    insert into public.watch_progress_events (
        user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
        season, episode, "position", duration, last_watched, origin_client_id
    )
    select v_user, p_profile_id, 'upsert', wp.progress_key, wp.content_id, wp.content_type,
           wp.video_id, wp.season, wp.episode, wp."position", wp.duration, wp.last_watched,
           'mongo-import'
    from public.watch_progress wp
    join public.imported_watch_progress i
      on i.progress_key = wp.progress_key
     and i.anilist_username = p_anilist_username
     and i.claimed_by = v_user
    where wp.user_id = v_user
      and wp.profile_id = p_profile_id;

    return v_claimed;
end;
$$;

grant execute on function public.claim_imported_watch_progress(text, integer) to authenticated;
