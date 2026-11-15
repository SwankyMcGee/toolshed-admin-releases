package com.learningtoolshed.admin

/* A tiny email check — good enough to decide whether the "Reply"
   button should appear. The sheet's "Name or email" is free text,
   so this is a heuristic, not validation. */
object EmailLooks {

    private val pattern = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

    fun looksLikeEmail(value: String): Boolean = pattern.containsMatchIn(value)

    fun extract(value: String): String? = pattern.find(value)?.value
}