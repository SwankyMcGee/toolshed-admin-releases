package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* v5 moved the admin credential out of the query string and into the
   body, so these are the tests that keep it there.

   The credential is whatever the admin pasted in — if it contains '&'
   or '=' or '+' and the body isn't escaped, it silently becomes a
   second parameter and the backend answers "forbidden" with nothing to
   explain why.

   v6 sends a Google ID token instead, which has exactly the same
   problem: it is three base64 parts joined by dots, and base64 is full
   of '=' padding. The token wins when both are present. */

class RequestBodyTest {

    @Test
    fun `the key is the first field of the body`() {
        val body = formBody("", "s3cret", "ideas")
        assertTrue(body.startsWith("key=s3cret&"))
    }

    @Test
    fun `the action follows the key`() {
        val body = formBody("", "k", "changes", mapOf("since" to "1234"))
        assertEquals("key=k&action=changes&since=1234", body)
    }

    @Test
    fun `a key with an ampersand stays one field`() {
        val body = formBody("", "a&b=c", "ideas")
        assertEquals("key=a%26b%3Dc&action=ideas", body)
    }

    @Test
    fun `a key with a plus stays one field`() {
        /* URLEncoder writes a space as '+', so a literal '+' has to come
           out as %2B or it turns into a space on the way in. */
        assertEquals("key=a+b&action=ideas", formBody("", "a b", "ideas"))
        assertEquals("key=a%2Bb&action=ideas", formBody("", "a+b", "ideas"))
    }

    @Test
    fun `every field is escaped, not just the key`() {
        val body = formBody("", "k", "status", mapOf("note" to "yes & no=1"))
        assertEquals("key=k&action=status&note=yes+%26+no%3D1", body)
    }

    @Test
    fun `a unicode key survives`() {
        assertEquals("key=cl%C3%A9&action=ideas", formBody("", "clé", "ideas"))
    }

    /* ---------- the token, from v6 ---------- */

    @Test
    fun `a token goes out as idToken`() {
        val body = formBody("abc.def.ghi", "", "ideas")
        assertTrue(body.startsWith("idToken=abc.def.ghi&"))
        assertEquals("idToken=abc.def.ghi&action=ideas", body)
    }

    @Test
    fun `the token wins when both are present`() {
        /* An app mid-migration should not be judged on the half it is
           leaving behind, and the server says so explicitly. */
        val body = formBody("tok", "old-key", "ideas")
        assertFalse(body.contains("key="))
        assertTrue(body.startsWith("idToken=tok&"))
    }

    @Test
    fun `a token's padding stays one field`() {
        /* This is the real shape of a JWT's last part: base64url with
           '=' padding. Unescaped, the '=' becomes a second parameter
           and Google's tokeninfo answers "bad token". */
        val body = formBody("a.b.c==", "", "ideas")
        assertEquals("idToken=a.b.c%3D%3D&action=ideas", body)
    }

    @Test
    fun `a token's url-safe characters are escaped`() {
        val body = formBody("a+b/c", "", "ideas")
        assertEquals("idToken=a%2Bb%2Fc&action=ideas", body)
    }

    /* ---------- the one public action ---------- */

    @Test
    fun `authconfig carries no credential at all`() {
        /* The app asks for the OAuth client before it has signed in, so
           this body has to be buildable without one. */
        assertEquals("action=authconfig", actionBody("authconfig"))
    }

    @Test
    fun `nothing here builds a query string`() {
        /* The point of v5: a credential in a URL ends up in history,
           proxy logs and the Referer header. If a request path ever
           needs a URL again, this is the line that should make someone
           look twice. */
        val body = formBody("tok", "k", "ideas")
        assertFalse(body.startsWith("?"))
        assertFalse(body.contains("?key="))
        assertFalse(body.contains("?idToken="))
    }
}