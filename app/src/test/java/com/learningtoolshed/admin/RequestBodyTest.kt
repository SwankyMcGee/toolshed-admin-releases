package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* v5 moved the admin credential out of the query string and into the
   body, so these are the tests that keep it there. v6 dropped the
   shared key entirely: the credential is now a Google ID token, always.

   An ID token is three base64 parts joined by dots, and base64 is full
   of '=' padding — if the body isn't escaped, that padding silently
   becomes a second parameter and the backend answers "bad token" with
   nothing to explain why. */

class RequestBodyTest {

    @Test
    fun `the token is the first field of the body`() {
        val body = formBody("abc.def.ghi", "ideas")
        assertTrue(body.startsWith("idToken=abc.def.ghi&"))
        assertEquals("idToken=abc.def.ghi&action=ideas", body)
    }

    @Test
    fun `the action follows the token`() {
        val body = formBody("k", "changes", mapOf("since" to "1234"))
        assertEquals("idToken=k&action=changes&since=1234", body)
    }

    @Test
    fun `a token with an ampersand stays one field`() {
        val body = formBody("a&b=c", "ideas")
        assertEquals("idToken=a%26b%3Dc&action=ideas", body)
    }

    @Test
    fun `a token with a plus stays one field`() {
        /* URLEncoder writes a space as '+', so a literal '+' has to come
           out as %2B or it turns into a space on the way in. */
        assertEquals("idToken=a+b&action=ideas", formBody("a b", "ideas"))
        assertEquals("idToken=a%2Bb&action=ideas", formBody("a+b", "ideas"))
    }

    @Test
    fun `every field is escaped, not just the token`() {
        val body = formBody("k", "status", mapOf("note" to "yes & no=1"))
        assertEquals("idToken=k&action=status&note=yes+%26+no%3D1", body)
    }

    @Test
    fun `a unicode token survives`() {
        assertEquals("idToken=cl%C3%A9&action=ideas", formBody("clé", "ideas"))
    }

    @Test
    fun `a token's padding stays one field`() {
        /* The real shape of a JWT's last part: base64url with '='
           padding. Unescaped, the '=' becomes a second parameter and
           Google's tokeninfo answers "bad token". */
        val body = formBody("a.b.c==", "ideas")
        assertEquals("idToken=a.b.c%3D%3D&action=ideas", body)
    }

    @Test
    fun `a token's url-safe characters are escaped`() {
        val body = formBody("a+b/c", "ideas")
        assertEquals("idToken=a%2Bb%2Fc&action=ideas", body)
    }

    /* ---------- there is no key path any more ---------- */

    @Test
    fun `the body never carries a key`() {
        /* v6 refused the key on the server and this build has nowhere to
           put one: the signature takes a token or nothing. If a key ever
           comes back into the body, that is a decision, not an
           accident — this line is where to look. */
        assertFalse(formBody("tok", "ideas").contains("key="))
        assertFalse(formBody("", "ideas").contains("key="))
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
        val body = formBody("tok", "ideas")
        assertFalse(body.startsWith("?"))
        assertFalse(body.contains("?idToken="))
    }
}