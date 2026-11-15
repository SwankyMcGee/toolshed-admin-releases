# Prompt: Ship the Toolshed Admin app

Copy everything below this line and give it to the main project agent.

---

The Toolshed Admin Android app is built and sitting at
`toolshed-admin/app/build/outputs/apk/debug/app-debug.apk`. The backend
changes are in `SwankyMcGee.github.io/ideas-engine/Code.gs` and need
deploying. Do these steps in order:

## 1. Set the admin key and deploy the backend

The file `SwankyMcGee.github.io/ideas-engine/Code.gs` has
`const ADMIN_KEY = '';` — it's empty, which means every admin API call
gets refused. I need you to:

1. Generate a random key. Run this in PowerShell:
   `[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')`
2. Open the Google Sheet "The Learning Toolshed — Ideas" → Extensions →
   Apps Script.
3. Find `const ADMIN_KEY = '';` in `Code.gs` and paste the key between
   the quotes. Save.
4. Deploy → Manage deployments → edit → New version → Deploy.
5. Test in a browser:
   - `…/exec` → should return `{"ok":true,"service":"The Ideas Engine","api":1}`
   - `…/exec?key=THE_KEY&action=state` → should return `{"ok":true,"admin":true,"api":1}`
   - `…/exec?key=WRONG&action=ideas` → should return `{"ok":false,"error":"forbidden"}`
6. If all three pass, the backend is live. If anything fails, stop and
   tell me — don't touch the app yet.

## 2. Verify the APK is current

The APK at `toolshed-admin/app/build/outputs/apk/debug/app-debug.apk`
was built before the key was set. It doesn't contain the key — the key
is entered in the app's Settings screen at runtime. So the APK is fine
as-is, but confirm it exists and is recent:

```powershell
Get-Item "toolshed-admin\app\build\outputs\apk\debug\app-debug.apk"
```

If it's missing or older than today, rebuild it:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"
& "C:\Gradle\gradle-8.9\bin\gradle.bat" assembleDebug
```

(run from the `toolshed-admin` directory).

## 3. Update the site repo's SETUP.md

The file `SwankyMcGee.github.io/ideas-engine/SETUP.md` still says the
sheet has 5 columns. Update the "Notes" section to mention the new
columns (`Read | Status | Note`) and add a line pointing to `ADMIN.md`
for the admin app setup.

## 4. Commit and push the site repo

The backend changes in `SwankyMcGee.github.io/ideas-engine/` need to be
committed and pushed:

```powershell
cd SwankyMcGee.github.io
git add ideas-engine/Code.gs ideas-engine/ADMIN.md ideas-engine/SETUP.md
git commit -m "Ideas Engine: add admin API for Toolshed Admin app"
git push
```

## 5. Commit the Android app

```powershell
cd toolshed-admin
git add -A
git commit -m "Toolshed Admin app v1: ideas inbox, status tracking, notifications"
```

If there's no remote for this repo yet, create one on GitHub and push.
If you want it in the same repo as the site, move it into
`SwankyMcGee.github.io/toolshed-admin/` and push there instead.

## 6. Tell me

Report back:
- The admin key (so I can enter it in the app)
- The `/exec` URL (should be the same as `IDEAS_ENDPOINT` in `site.js`)
- The APK path (so I can copy it to my phone)
- Whether the backend test passed

---

## What the app does (for context)

- Reads all ideas from the sheet via the admin API
- Shows them newest-first with search and filters (unread / tool / feature)
- Tap an idea to read it — marks read here and in the sheet
- Set status per idea: new / reading / planned / done / shelved
- Keep a private note on an idea (stored in the sheet's Note column)
- "All read" to clear the badge in one go
- Notification when a new idea arrives (after first sync)
- 11 color palettes matching the site (calm, lavender, mint, peach,
  rose, ocean, low, high, dark, midnight, warm)

## Key files

| File | What it is |
|---|---|
| `toolshed-admin/app/build/outputs/apk/debug/app-debug.apk` | The built APK to install |
| `toolshed-admin/app/src/main/java/.../AdminClient.kt` | API client (endpoint + key settings) |
| `toolshed-admin/app/src/main/java/.../Palette.kt` | The 11 site palettes |
| `SwankyMcGee.github.io/ideas-engine/Code.gs` | Backend with admin API |
| `SwankyMcGee.github.io/ideas-engine/ADMIN.md` | How to set the key and test |