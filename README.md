# The Learning Toolshed — Toolshed Admin (Android)

A small admin app for the site's Ideas Engine: reads the ideas sheet,
shows them grouped and searchable, and pings you when a new one lands.

It is a thin client over the same Google Apps Script that the site's
form posts to (`SwankyMcGee.github.io/ideas-engine`), reading and
writing through the admin API added in that repo's `Code.gs`.

## Build

A pre-built debug APK is at `app/build/outputs/apk/debug/app-debug.apk`
(16 MB). Copy it to your phone and open it (allow "install unknown
apps" for your browser or Files app). No Play Store account needed.

To build from source: open this folder in **Android Studio** (Hedgehog
or newer), let Gradle sync, then **Run**. Or from the command line:

```
gradle assembleDebug
```

No `gradlew` is committed — Android Studio generates the wrapper on
first sync, or run `gradle wrapper` once.

## First run

1. **Settings** (gear in the top bar).
2. Paste the Apps Script `/exec` URL — same one as `IDEAS_ENDPOINT` in
   `site.js`.
3. Paste the **admin key** (see `ideas-engine/ADMIN.md` in the site
   repo — it's the `ADMIN_KEY` Script Property, not the site's public
   key).
4. **Save**. The inbox loads on the next refresh.

Both values stay in the app's own storage; nothing is shared with the
site or other apps.

## What it does now

- Reads every idea (newest first) into a list you can search.
- Filter by unread, by tool, by feature, or search the text.
- Tap an idea to read it; it marks read here and in the sheet.
- Set a status per idea: new / reading / planned / done / shelved.
- Keep a private note on an idea (stored in the sheet's `Note` column).
- "All read" to clear the badge in one go.
- A notification when a new idea arrives while the app has synced at
  least once this session.

## Layout

| File | What it is |
|---|---|
| `app/src/main/java/.../MainActivity.kt` | Entry point, palette state, notification permission |
| `ToolshedAppRoot.kt` | The inbox screen: top bar, filters, card list |
| `Sheets.kt` | Idea detail, connection settings, palette picker |
| `InboxViewModel.kt` | The list, filters, optimistic writes, notification |
| `AdminClient.kt` | The four admin API calls (HttpURLConnection + org.json) |
| `Palette.kt` | The eleven site palettes, copied from `site.css` |
| `Theme.kt` | Compose theme + the site's card/button/label shapes |
| `Bits.kt` | Header buttons, status chips, text fields |

## Keeping it in step with the site

The palettes and the "shape" of the UI (cards, pills, chips) mirror
`site.css` and `site.js`. If you change a palette on the site, change it
here too (`Palette.kt`), and the app keeps looking like the same shed.

The logo is the site's logo, not a drawing of one. `site.css` swaps
`toolshed-logo.png` for `toolshed-logo-light.png` on the dark themes;
`Brand.kt` picks between the same two files the same way, so a palette
change never leaves the brand upside down.

- `res/drawable-nodpi/ic_logo.png` — from the site's `toolshed-logo.png`
- `res/drawable-nodpi/ic_logo_inverse.png` — from `toolshed-logo-light.png`

Both are trimmed to the artwork's own bounding box (308×202) so the app
lays them out at their real shape instead of the site's 600×335 canvas,
which is mostly transparent padding. When the logo changes on the site,
copy the two files over and re-trim.

The launcher icon is the inverse logo on a `#151A20` plate (the "dark"
palette's background — the light artwork needs a dark ground). The
adaptive foreground and the five legacy `mipmap-*` densities are baked
from the same source.

The mark is also the refresh indicator: while a sync runs, the header's
refresh icon turns into the logo, turning (`BrandSpinner`). `ic_bell`
stays the notification's small icon — Android tints small icons, so a
coloured logo would arrive as a blob; the launcher plate is the large
icon instead.

## Deliberate limits

- Writes are optimistic in memory and reported rather than queued; a
  failed save is not retried. Reads do come from a delta sync and a
  background worker (v1.7).
- The admin API is **v5, POST-only** — every call, reads included, puts
  the key in the body. `GET` with a `key` is refused by the backend on
  purpose: a key in a query string ends up in history, proxy logs and
  the Referer header.
- Sideload only; no Play Store listing, no signing key or password
  checked in. The release signing config only exists when
  `TOOLSHED_KEYSTORE_PASSWORD` / `TOOLSHED_KEY_ALIAS` /
  `TOOLSHED_KEY_PASSWORD` are set (env) or `toolshedKeystorePassword` /
  `toolshedKeyAlias` / `toolshedKeyPassword` are in
  `~/.gradle/gradle.properties`. Without them `assembleRelease` warns
  loudly and produces an unsigned APK.
- `allowBackup` is off. The admin key lives in SharedPreferences, and a
  backup would carry it off the device; everything the app shows is a
  cache of the sheet, so nothing is lost by it.