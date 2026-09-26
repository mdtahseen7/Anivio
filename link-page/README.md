# Anivio device-link page

The web page users open to approve a TV / device sign-in. It's a single static
`index.html` — no build step — so it drops straight onto Vercel.

## Deploy on Vercel

1. Set your Supabase values in [`config.js`](./config.js) (`supabaseUrl`,
   `supabaseAnonKey` — the same ones your app build uses; the anon key is public
   by design).
2. Deploy this `link-page/` folder to Vercel (New Project → point at this
   directory, framework preset **Other**). No build command needed.
3. In your **app** build config, set `ANIVIO_DEVICE_LINK_URL` to the deployed
   URL plus `/link`, e.g. `https://link.anivio.app/link`. That's the address the
   TV shows and the phone opens.

## How it works

1. The TV calls `start_device_login_session` and shows a short code + this page's URL.
2. The user opens this page (the code rides in `?code=…`, or they type it).
3. They sign in with email + password (Supabase auth).
4. The page calls the **`approve_tv_login_session`** RPC to mark that code's
   session approved and attach the signed-in user.
5. The TV, still polling `poll_tv_login_session`, sees "approved" and finishes via
   the `tv-logins-exchange` edge function.

## Backend it expects

This page only calls one RPC: `approve_tv_login_session(p_user_code text)`. It must
exist in your Supabase, run as the authenticated user, and set the matching pending
session row to `approved` with `user_id = auth.uid()`. Rename it in `config.js` if
yours differs. The `start_device_login_session`, `poll_tv_login_session`, and
`tv-logins-exchange` pieces live in your backend and are called by the app, not this
page.
