# Dantotsu Comments API — Reverse-Engineering Spec & Integration Handoff

**Purpose:** Complete technical spec of Dantotsu's comment service, produced by static
analysis of `dantotsu-3-2-2.apk`, for an integration agent to implement a comments feature
(against `api.dantotsu.app` directly, or as a cloned self-hosted contract).

**Target app:** Dantotsu v3.2.2 (`ani.dantotsu`, versionCode 300200200)
**Evidence base (REA / jadx-headless-mcp 0.7.1, verified pinned JAR):**

| Evidence ID | Contents |
|---|---|
| `ev_9714a94a41f4e2736fec26770cb92a1e98d02da5adaff9df42e344fda3649718` | full package + manifest |
| `ev_3418d0c2da0c0ea328ce2cbba0224c85ce1cc3f2fe88e596969350f6e02adbda` | `CommentsAPI` class inventory (42 methods, 11 fields) |
| `ev_0d64ed30035e3183c92635d7b29a25a7293009107db8c8829b9c8d5a8c62e120` | `fetchAuthToken()` (smali fallback — anti-decompile stub present) |
| `ev_d49516b4ef273251105b4ac63a85913898ca80e36506c012a5549802e4aeb219` | `getADDRESS()` (clean Java) |
| `ev_8c4c8d9ece09ebf166a4f7f75b1bb568a1693ea3c70d34454e99598e1f2a62bd` | `comment()` (clean Java, exact POST body) |

Analyzed APK sha256: `63075de70c448815da05582c8bc9ffe73dcc2ecf803c645bec1be832fa81a1ba`

**Static-analysis caveat:** the APK was never executed. Everything below is decompiled
observation unless marked `[INFERRED]`. A few verbs/params must be confirmed with live
requests (see §8) — the integrator has a working auth flow, so these are cheap to verify.

---

## 1. What the service is

- A **custom, self-hosted REST API** at `https://api.dantotsu.app`. **Not** Disqus/Firebase/
  Supabase/anything third-party. Client-side singleton:
  `ani.dantotsu.connections.comments.CommentsAPI` (Kotlin, object with `INSTANCE`).
- Identity = **AniList account**. The client exchanges the user's AniList OAuth token for a
  service JWT, then sends that JWT on every subsequent call (including reads).
- Server has a global kill switch semantics: client field `commentsEnabled` — when false,
  the client points at `LOCAL_HOST` instead of production (`getADDRESS()` = `commentsEnabled ? API_ADDRESS : LOCAL_HOST`), so the
  feature simply dies if upstream disables it.
- Client source is open (github.com/rebelonion/Dantotsu). The **server** is closed — it is NOT in the APK.

## 2. Liveness (verified via live probes)

| Probe | Result |
|---|---|
| `GET /` | `200` — `{"message":"Dantotsu comments API"}` |
| `GET /comments`, `/comments/1`, `/comments/parent/1`, `/comments/vote`, `/user`, `/notification/reply` | `401 Unauthorized` (all reads are auth-gated) |
| `POST /authenticate` with no real AniList token | `401 Unauthorized` |
| Anonymous tokenless access anywhere | none — fully closed API |

The operator confirmed interactively that **authenticating with a real AniList token works**.

## 3. Auth flow (from `fetchAuthToken` smali)

```
1. If CommentsAPI.authToken != null  -> return (already authed).
2. Read prefs: CommentAuthResponse (cached AuthResponse JSON) + CommentTokenExpiry (long).
   -> If cache present & still within its validity window (~6 days, window constant
      0x1EE62800 = 518,400,000 ms = exactly 6.0 days):
      - hydrate authToken / userId / isBanned / isAdmin / isMod / totalVotes from cache
      - call GET UserDetails (see §5) and return on success.
3. Otherwise re-authenticate:
      - URL = ADDRESS + "/authenticate"
      - POST with a JSON body containing the saved AniList token
        (client helper name seen in constant pool: writeGenerateAuthTokenRequestBodyToOutputStream)
      - up to 5 attempts (retry loop counter `0x5`), catching IOException per attempt
      - parse response as AuthResponse { authToken, user }
      - persist AuthResponse + a CommentTokenExpiry timestamp; hydrate the same fields
4. On any signed-in user fetch failure: user fields default (isBanned=false etc.);
   banned users get the feature hidden client-side.
```

**Key client state after auth:** `userId` (string), `authToken` (service JWT), `isBanned`,
`isAdmin`, `isMod`, `totalVotes` (server-side vote quota mirror).

`[INFERRED — must verify]` The per-request auth header produced by `headerBuilder()`.
Working hypothesis: `Authorization: Bearer <authToken>`. Verify with one real call (§8).

## 4. Endpoint map

All paths relative to `https://api.dantotsu.app` (production `API_ADDRESS`; a dev
`LOCAL_HOST` constant exists in the client).

| Method (client fn) | Endpoint | Verbs | Notes |
|---|---|---|---|
| `fetchAuthToken` | `POST /authenticate` | POST (confirmed by flow) | body = AniList token wrapper; resp = `AuthResponse`; 5 attempts |
| `getCommentsForId(mediaId, …, sort)` | `GET /comments/{mediaId}` | GET `[INFERRED verb]` | paginated; query params likely `page` & ordering — verify live (§8) |
| `getRepliesFromId(…)` | `GET /comments/parent/{id}` | GET `[INFERRED verb]` | reply thread of comment `id` |
| `getSingleComment(id)` | `GET /comments/…` | GET `[INFERRED verb]` | exact sub-path not present in string pool — verify |
| `comment(mediaId, parentCommentId, text, tag)` | `POST /comments` | **POST — exact body known** (see §5) | 300-char cap enforced client-side |
| `editComment(id, text)` | `/comments/{id}` or `/comments` | verb unknown `[INFERRED: PATCH]` | resp boolean |
| `deleteComment(id)` | `DELETE /comments/{id}` | DELETE `[INFERRED]` | resp boolean |
| `vote(commentId, score)` | `POST /comments/vote/{commentId}/{score}` pattern | POST/GET `[INFERRED]` | resp boolean; client mirrors `totalVotes` |
| `getNotifications` | `GET /notification/reply` | GET `[INFERRED]` | resp `NotificationResponse`; polled by `CommentNotificationWorker` |
| `reportComment(…)` | `POST /report/…` | POST `[INFERRED]` | string pool has `/report/` |
| `banUser(username)` | `/ban/…` | POST `[INFERRED]` | mod/admin only; string pool has `/ban/` |
| `getUserDetails` | `GET /user` | GET `[INFERRED]` | returns `User` (user object refresh) |

Path literals confirmed in the dex string pool: `/authenticate`, `/comments`,
`/comments/`, `/comments/parent/`, `/comments/vote/`, `/report/`, `/ban/`,
`/notification/reply`, `/user` (plus `https://api.dantotsu.app`).

## 5. Wire format & data models (kotlinx-serialization)

**Content type note:** POST /comments uses `application/x-www-form-urlencoded`
(decompiled `FormBody` builder), NOT JSON. JSON elsewhere per kotlinx models.

### POST /comments form fields (exact, from `comment()` decompile)
```
user_id            = <CommentsAPI.userId>        (required; null userId aborts)
media_id           = <int>                       (required)
content            = <comment text>              (required; max 300 chars client-side)
tag                = <int, optional>             (only sent if non-null)
parent_comment_id  = <int, optional>             (only sent when replying)
```
Response (HTTP 200/201): single `ReturnedComment` JSON. On explicit error the client calls
`errorReason(code, text)`.

### Comment (read model; list element)
```json
{
  "commentId":        int,
  "userId":           string,
  "mediaId":          int,
  "parentCommentId":  int?,          // null for top-level
  "content":          string,
  "timestamp":        string,
  "deleted":          bool,
  "tag":              int?,
  "upvotes":          int,
  "downvotes":        int,
  "userVoteType":     int?,          // current user's vote on this comment
  "totalVotes":       int,
  "replyCount":       int?,
  "username":         string,
  "profilePictureUrl": string,
  "isAdmin":          bool?,
  "isMod":            bool?
}
```

### User
```json
{ "id": string, "username": string, "profilePictureUrl": string,
  "isBanned": bool?, "isAdmin": bool?, "isMod": bool?,
  "totalVotes": int, "warnings": int }
```

### AuthResponse
```json
{ "authToken": string, "user": User }   // user nullable
```

### ReturnedComment (creation response; minimal row)
```json
{ "id": int, "userId": string, "mediaId": int, "parentCommentId": int?,
  "content": string, "timestamp": string, "deleted": bool, "tag": int? }
```

### CommentResponse (list envelope)
```json
{ "comments": [Comment], "totalPages": int }
```

### Notification (from /notification/reply)
```json
{ "notificationId": int, "commentId": int, "mediaId": int,
  "content": string, "username": string, "type": int? }
```
Wrapped in `NotificationResponse` (shape: list envelope `[NOT FULLY DUMPED]`).

### ErrorResponse
Present in clients; treat non-2xx as `{ json-with-error }` — text shown via `errorReason`.

Also present in the module (semantic details unknown): `UserResponse`, `NumericBooleanSerializer`
(server returns booleans sometimes as 0/1 ints — parse defensively), `CommentsEnabled`,
`CommentSortOrder`, `UnreadCommentNotifications`, `FirstComment` prefs.

**Media ID note:** `mediaId` in Dantotsu is an **AniList media id** (comments ride the AniList
login), so Anivio should key comments on AniList media ids or translate TMDB→AniList when
cross-referencing (AniZip mappings already used elsewhere in Anivio support this).

## 6. Client behaviors worth mirroring

- 300-char limit + empty-text rejection before POST (`"Comment cannot be empty"` /
  `"Comment cannot be longer than 300 characters"` strings).
- Optimistic UI: new comment = `ReturnedComment` + locally-known username/avatar.
- Votes tracked server-side; client mirrors cumulative `totalVotes` count.
- Sort orders exist (`CommentSortOrder`); default sort = timestamp-based per decompiled
  comparators (Top/近期 — verify labels at runtime).
- Moderator badges rendered when `isAdmin`/`isMod` true; banned users: feature hidden.
- Poll `NotificationResponse` on a background worker; interval pref
  `CommentNotificationInterval`, unread tracking pref `UnreadCommentNotifications`.
- Replies: nested one level (reply → parent via `parent_comment_id`), fetched via
  `/comments/parent/{id}` on expand.

### 6.5 UI layout, styling & user-facing options (from decoded AXML, `res/`)

Obsfuscated resource file names, real resource names (androguard-decoded from `resources.arsc`):

**Screen 1 — `fragment_comments` (`res/Cc.xml`, layout id `0x7F0D0087`):**
```
NestedScrollView
 └ SwipeRefreshLayout (background: color/val? none set)
    └ vertical LinearLayout
       ├ Horizontal row (3 buttons, 28dip each, selectableItemBackgroundBorderless,
       │                tint attr 7F040167):
       │    ├ btn (id 7F0A0559)  icon ic_round_info_24      contentDescription "Open Rules"
       │    ├ btn (id 7F0A017F)  icon ic_round_filter_alt_24 contentDescription "Sort By"
       │    └ btn (id 7F0A018D)  icon ic_round_sort_24      contentDescription "Sort By"
       │      (two sort affordances: filter icon → sort menu, sort icon → current-order pill;
       │       verify at runtime — menu is code-driven, not XML)
       ├ ProgressBar (id 7F0A01A2 `progress_bar`, centered, removed when loaded)
       └ FrameLayout (id 7F0A01A1)
          └ RecyclerView (id 7F0A01A0)
```

**Screen 2 — `item_comments` (`res/dG.xml` + v22 variant `res/kT.xml`, id `0x7F0D00A3`):**
avatar (`CircularImageView` style → rounded 50%), username row, content text, secondary meta
text, vote row with up/down `ImageButton` (`ic_round_upvote_inactive_24`), per-comment overflow
(`ic_round_dots_vertical_24`), mod badges `ic_crown` (admin) / `ic_shield` (mod), label chips
`ic_label_24` / `ic_label_off_24`, send button `ic_round_send_24`, close `ic_round_close_24`,
reply quick-view `ic_round_add_circle_24`, container outline drawable `card_outline`.

**Sort menu (code-built; string values verbatim):**
`Newest` (0x7F13036A) · `Oldest` (0x7F130395) · `Highest rated` (0x7F130275) — the
`CommentSortOrder` pref persists the choice; default is newest-first.

**Action menu per comment (pop-up, includes):**
`Reply` (0x7F130409), `Edit` (0x7F13019E, own comment), `Delete Comment` (0x7F130162, own),
`Report Comment` (0x7F13040E, others), `Ban User`/`Ban` (0x7F130094/93, admin only, gated).

**Composer:** embedded in `MediaDetailsActivity` layouts (`res/6W.xml`, `AF.xml`,
`UF.xml`, `kw.xml`) — bottom bar with `EditText` placeholder **“Write a response…”**
(0x7F13040A) + send button (`ic_round_send_24`), max 300 chars with live counter per §6.

**Palette / tokens (exact ARSC values, light / dark where the theme splits):**
- Background: `color/bg` = `#FFEEEEEE` / dark `#FF000000`; opposite `bg_opp` inverted pair
- Content/reply background: `color/nav_bg` = `#FFFFFFFF` / dark `#FF1C1C1C`
- Scrim/tab overlay: `color/nav_tab` = `#40000000` / dark `#40FFFFFF`
- Accent options: `color/incognito` = `#FF291B65` / dark `#FF6347D4` (Tachiyomi-inherited
  accent class Dantotsu reuses for selectable/hover states)
- Vote-active highlight colors: `0x7F06059B` (`transparent`, used as unset) and `0x7F06056C`
  (`nav_bg` — i.e. a comment's vote row tints **by inverting vs the card bg**, not a brand red/green)
- Stroke/outline color: `0x7F060028`/`0x7F060025` = `bg_opp`/`bg` (1dip hairline via `card_outline`)
- Typography: **Poppins** everywhere — `font/poppins_bold`, `font/poppins_family`,
  `font/poppins_semi_bold` (content = semi-bold, meta/counter = family regular, username = bold)
- Shapes: avatars `CircularImageView` (circular 50%), images `roundedImageView` (16 dip),
  buttons `Widget.Material3.Button.OutlinedButton` (24 dip, 1 dip outline stroke)

**Replication target for Anivio (what actually matters):** don't clone Dantotsu's look
pixel-for-pixel. Match the interaction set (rules entry, sort triad, per-comment menu, composer placeholder,
300-char counter, mod/ban affordances shown only to mods) and the RTL-safe string values above;
use Anivio's own theme tokens mapped onto the same semantic slots (bg/card/overlay/accent,
demi-bold content text, circular avatars, outlined send button).

## 7. Recommended Anivio integration architecture

Option A (consume Dantotsu's server): thin client module only.
Option B (own cloned backend): implement the same contract (§4–5) on Supabase/Postgres or a
small Go/Node service; reuse the exact paths/field names to keep this spec as the client code
path; auth = same AniList-token exchange, mint your own JWT.

Recommended for Anivio: **Option B** (spec-compatible, no upstream kill switch / shared
operational dependency), with the Dantotsu service as a QA oracle for response shapes.

For either option, surface a plugin/user setting "Comments" (the app already has
`hasSettings` providers; a global toggle + login gate via existing AniList auth in Anivio).

## 8. Open items — cheap to resolve with the working auth

1. **Auth header** for subsequent calls (hypothesis: `Authorization: Bearer <JWT>`) — one call confirms.
2. **`GET /comments/{mediaId}` full query params** (page/offset/sort names) — one call with a JWT + a known media id.
3. **Verbs** for edit/delete/vote/report/ban (curl -X probes; 404/405 diffing settles them).
4. **`UserResponse` & `NotificationResponse` full field lists** — decompile in REA if needed:
   `ani.dantotsu.connections.comments.UserResponse` / `.NotificationResponse`.
5. **`getSingleComment` exact path** — decompile its method body the same way as `comment()`.
6. **`authRequest` exact JSON body keys** — decompile
   `CommentsAPI.CronetEngineBackPressedCallback$...` inner/`writeGenerateAuthTokenRequestBodyToOutputStream`
   helper; alternatively capture with a proxy during a real login.

Probe template (once JWT known):
```
curl -H "Authorization: Bearer $JWT" "https://api.dantotsu.app/comments/<anilist_media_id>?page=0"
```
A 405 response = wrong verb; 404 = wrong path shape; both distinguish without side effects.

## 9. Security & etiquette checklist

- Treat the AniList token as a secret: never log it; only ever POST to `/authenticate`; store the returned JWT, not the AniList token, afterward.
- Persist JWT + expiry; don't re-auth on every app start (server rate).
- Long/expired JWT handling: on any 401 mid-session, invalidate cache and re-run the §3 flow once; on repeat 401, hard-fail to logged-out comments.
- Respect `NumericBooleanSerializer` (`0/1` vs `true/false`) when deserializing booleans.
- No public scraping path exists — anything beyond the JWT-authenticated calls above is out of scope and unsupported by the API.
- If cloning the server (Option B): rate-limit per user, and cap `content` length server-side too (300 chars is client-side only).
