# Expanding the Project

A running list of things to add when the project grows. Nothing here is
scheduled — it's the parking lot for "we'll get to it."

Format: one section per feature, with what it is, why it'd matter, what
it'd take, and open questions to answer before building it.

---

## Background sync — Option C: Firebase Cloud Messaging (true push)

**What it is:** instead of the app polling the sheet every 15 minutes,
Firebase Cloud Messaging (FCM) pushes an instant notification to the
phone the moment a new idea lands. The phone doesn't need to be polling.

**Why it'd matter:** the current polling (Option A, WorkManager) can be
delayed 15–30 min by Android's Doze mode. For a "drop everything and
look at this" alert, that's too slow. FCM is instant even when the app
is killed.

**What it'd take:**

1. A Firebase project (free tier is enough for this).
2. `google-services.json` dropped into `toolshed-admin/app/`.
3. The app registers its FCM token with the backend on first launch and
   whenever it refreshes.
4. The backend (Apps Script) stores tokens in a new sheet or in Apps
   Script's `PropertiesService`, and pings FCM's HTTP endpoint on every
   new submission.
5. A new `doPost(action=registerToken)` (or similar) so the app can
   report its token.
6. The app's `FirebaseMessagingService` handles the incoming push and
   shows the notification (reusing the existing notification code).

**Open questions to answer before building:**

- Is the Google account that owns the sheet a Workspace account with
  FCM enabled, or a personal Gmail? (Workspace admins can restrict FCM.)
- Does the school network allow outbound connections to
  `fcm.googleapis.com` on port 443? (Some school filters block it.)
- Do we want the backend to send *only* to the admin's device, or to
  multiple devices (e.g. a second admin)? (The latter needs a token
  registry, not just one token.)
- Is the ~30-min worst-case latency of polling actually a problem in
  practice, or is "good enough" fine for now?

**Rough size when we do it:** 1–2 days of work, half on the Firebase
setup and half on the token registration flow.

---

## Replies — Option C: backend sends the email

**What it is:** v1 replies use Option A (draft in the app, open your
mail app to send). Option C skips the mail app: the backend sends the
email directly via Apps Script's `MailApp` or `GmailApp`, so "Send"
happens inside the Toolshed Admin app.

**Why it'd matter:** fewer steps, no switching apps, replies come from
the sheet's owner account automatically. Useful if you reply to most
submissions and the two-app dance gets old.

**What it'd take:**

1. A new backend action `doPost(action=reply, id=…, body=…)` that:
   - looks up the row by ID,
   - reads the `Name or email` column,
   - if it looks like an email, calls `MailApp.sendEmail()` (or
     `GmailApp.sendEmail()` for richer formatting / threading),
   - logs the reply in a new `Reply` column (or a `Replies` sheet if
     threading is wanted later).
2. The app's idea sheet gets a "Send reply" path that POSTs to the new
   action instead of opening the mail app.
3. Privacy: the reply is between you and the submitter — never shown on
   the site.

**Open questions to answer before building:**

- `MailApp` (plain text, simple) or `GmailApp` (HTML, threads, sent
  folder)? `GmailApp` needs the Gmail API enabled and is heavier.
- Daily quota: free Gmail is 100 recipients/day; Workspace is 1,500.
  Is that enough for a school inbox? (Almost certainly yes.)
- Should the reply come *from* the sheet's owner account (e.g. a
  school role address), or from a personal address? (Affects which
  account owns the sheet and which `MailApp` sends as.)
- Does a reply need to be private only, or could a "we're building
  this" note also be posted publicly? (See the public notification
  banner below — same backend could serve both.)

**Rough size when we do it:** half a day, mostly backend.

---

## Public notification banner

**What it is:** a spot where you type a custom message and it appears
in a banner on the site (e.g. the home page or a top strip on every
page). Different from the "what's new" ribbon, which is versioned and
dismissed — this is a free-text admin-set message.

**Why it'd matter:** "Math Playground will be down Thursday after
school", "new tool coming next week", "staff meeting moved to Friday" —
things that need to be seen by anyone visiting the site, not just ideas
submitters. The current "what's new" ribbon is too rigid for ad-hoc
notices.

**What it'd take:**

1. A new sheet ("Notices") with columns: `When | Text | Active | By`.
2. A backend action `doGet(action=notice)` that returns the currently
   active notice (or null).
3. The app gets a "Notice" composer (text field + "Post notice" /
   "Clear notice" buttons).
4. The site (`site.js`) fetches the active notice on page load and
   renders it in a banner. This is the part that goes through the main
   agent — it's site-repo work, not app work.

**Open questions to answer before building:**

- Where on the site should the banner appear? (Every page's top strip,
  or just the home page?)
- Should it be dismissible per-visitor (localStorage remembers "dismiss
  this notice"), or always visible until cleared?
- One active notice at a time, or a queue of several?
- Should the banner support rich text (links, bold), or plain text only?
- Does it need an expiry ("show until Friday 5pm"), or manual clear
  only?

**Rough size when we do it:** half a day on the app + backend; the
site-side rendering is a separate task for the main agent.

---

## Analytics — Option C: status history for funnel metrics

**What it is:** v1 analytics (Option A) shows counts and distributions
from the current sheet state: ideas per week, tool vs feature split,
status breakdown, top tools. That answers "what's in the inbox now"
but not "how fast do we close things" or "where do ideas get stuck."

Option C adds a second sheet ("History") that logs every status change:
row ID, timestamp, from-status, to-status. With that, you can chart
throughput (new → planned → done over time), aging (how long ideas sit
in each status), and closure rate.

**Why it'd matter:** the difference between "we have 40 ideas" and
"ideas sit in 'new' for 3 weeks before anyone reads them" — the latter
is the one that tells you the inbox is being ignored.

**What it'd take:**

1. A new "History" sheet with columns: `Idea ID | When | From | To`.
2. The backend writes a row to History on every `doPost(action=status)`
   call (in addition to updating the Ideas sheet).
3. A new `doGet(action=analytics)` endpoint that reads History and
   returns pre-aggregated counts (per status, per week, average time in
   status).
4. The app's analytics screen gains funnel and aging charts.

**Open questions to answer before building:**

- Backfill: do we seed History from the current sheet's existing rows
  (so historical ideas have a starting point), or start empty and only
  track from now on?
- Retention: keep History forever, or archive rows older than a year?
- Does the main agent need the analytics as a web page too, or is
  in-app enough? (If web, the endpoint is shared.)

**Rough size when we do it:** a day — half backend (History sheet +
endpoint), half app (new charts).

---

## (next feature goes here — add a new section above this line)