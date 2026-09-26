// Anivio device-link page configuration.
// Supabase URL + anon key copied from the app's local.properties (ANIVIO_SUPABASE_*).
// The anon key is the public/publishable key by design — safe in a static page; your
// Supabase Row-Level Security is what actually protects data.
window.ANIVIO_CONFIG = {
  supabaseUrl: 'https://cmfomalhazzwandvptgf.supabase.co',
  supabaseAnonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNtZm9tYWxoYXp6d2FuZHZwdGdmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg5NDIwNjAsImV4cCI6MjEwNDUxODA2MH0.-Ti8kdGMEG6teY1EalkIfmKIxDYCkj770WqOHcZ6oiM',
  // RPC the page calls to approve a pending TV session for the signed-in user.
  approveRpc: "approve_tv_login_session",
  // Query/param key that carries the user code in the link the TV shows.
  codeParam: "code",
};
