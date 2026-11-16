package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* v5 moved the admin key out of the query string and into the body, so
   these are the tests that keep it there.

   The key is whatever the admin pasted in — if it contains '&' or '=' or
   '+' and the body isn't escaped, it silently becomes a second parameter
   and the backend answers "forbidden" with nothing to explain why. */

class RequestBodyTest {

    @Test
    fun `the key is the first field of the body`() {
        val body = formBody("s3cret", "ideas")
        assertTrue(body.startsWith("key=s3cret&"))
    }

    @Test
    fun `the action follows the key`() {
        val body = formBody("k", "changes", mapOf("since" to "1234"))
        assertEquals("key=k&action=changes&since=1234", body)
    }

    @Test
    fun `a key with an ampersand stays one field`() {
        val body = formBody("a&b=c", "ideas")
        assertEquals("key=a%26b%3Dc&action=ideas", body)
    }

    @Test
    fun `a key with a plus stays one field`() {
        /* URLEncoder writes a space as '+', so a literal '+' has to come
           out as %2B or it turns into a space on the way in. */
        assertEquals("key=a+b&action=ideas", formBody("a b", "ideas"))
        assertEquals("key=a%2Bb&action=ideas", formBody("a+b", "ideas"))
    }

    @Test
    fun `every field is escaped, not just the key`() {
        val body = formBody("k", "status", mapOf("note" to "yes & no=1"))
        assertEquals("key=k&action=status&note=yes+%26+no%3D1", body)
    }

    @Test
    fun `a unicode key survives`() {
        assertEquals("key=cl%C3%A9&action=ideas", formBody("clé", "ideas"))
    }

    @Test
    fun `nothing here builds a query string`() {
        /* The point of v5: a key in a URL ends up in history, proxy logs
           and the Referer header. If a request path ever needs a URL
           again, this is the line that should make someone look twice. */
        val body = formBody("k", "ideas")
        assertFalse(body.startsWith("?"))
        assertFalse(body.contains("?key="))
    }
}