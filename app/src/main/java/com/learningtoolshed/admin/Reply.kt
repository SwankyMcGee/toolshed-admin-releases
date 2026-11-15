package com.learningtoolshed.admin

/* One reply in a thread. Mirrors the backend's Replies sheet. */
data class Reply(
    val id: Int,
    val ideaId: Int,
    val whenIso: String,
    val author: String,
    val body: String
)