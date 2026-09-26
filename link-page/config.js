// Anivio device-link page configuration.
// Fill these in with your own Supabase project (same values your app build uses:
// ANIVIO_SUPABASE_URL / ANIVIO_SUPABASE_ANON_KEY). On Vercel you can hardcode them
// here (the anon key is public/publishable by design) or generate this file at build.
window.ANIVIO_CONFIG = {
  supabaseUrl: "https://YOUR-PROJECT.supabase.co",
  supabaseAnonKey: "YOUR-SUPABASE-ANON-KEY",
  // RPC the page calls to approve a pending TV session for the signed-in user.
  // Must exist in your Supabase and set the session row's status to 'approved'
  // plus attach auth.uid(). Rename here if your function name differs.
  approveRpc: "approve_tv_login_session",
  // Query/param key that carries the user code in the link the TV shows.
  codeParam: "code",
};
