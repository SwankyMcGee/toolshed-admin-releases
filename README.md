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
   repo — it's the `ADMIN_KEY` you set in `Code.gs`, not the site's
   public key).
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

## Deliberate limits (v1)

- No offline queue: writes are optimistic in memory, but a failed save
  is reported rather than retried. If that bites, add WorkManager.
- No background sync: the app fetches on launch and on pull-to-refresh.
- The admin API version is `1`; the app checks nothing about it yet. If
  the sheet grows a column the app ignores it, which is safe.
- Sideload only; no Play Store listing, no signing key checked in.