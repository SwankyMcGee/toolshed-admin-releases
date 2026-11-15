package com.learningtoolshed.admin

/* ============================================================
   One row of the PageViews sheet, as the backend reports it:
   a path on a day, with a view count and total seconds.

   The sheet itself is one row per page view (Path | When |
   Seconds | Referrer); the backend groups by path + day before
   returning, so the app receives:

     { path: "/", day: "2026-11-15", views: 3, seconds: 412 }

   `views` is how many reports landed for that path that day
   (roughly one per visit that reported), and `seconds` is the
   sum of their elapsed times — capped per report at an hour by
   the backend, so an idle tab can't inflate a page.
   ============================================================ */

data class PageView(
    val path: String,
    val day: String,      // "2026-11-15"
    val views: Int,
    val seconds: Int
)